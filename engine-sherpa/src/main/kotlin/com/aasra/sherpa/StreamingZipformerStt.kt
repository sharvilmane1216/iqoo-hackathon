package com.aasra.sherpa

import com.aasra.pipeline.RecognitionResult
import com.aasra.pipeline.SpeechRecognizer
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

class StreamingZipformerStt(
    private val filesDir: File,
    private val openAsset: ((String) -> java.io.InputStream?)? = null,
) : SpeechRecognizer {
    private var listener: ((RecognitionResult) -> Unit)? = null
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    @Volatile var ready = false
        private set

    @Synchronized
    fun init(): Boolean {
        close()
        ready = try {
            android.util.Log.i(TAG, "Opening Zipformer at $filesDir/$MODEL_DIR")
            val encoder = ModelAssets.ensureFile(filesDir, "$MODEL_DIR/encoder.onnx", openAsset)
            val decoder = ModelAssets.ensureFile(filesDir, "$MODEL_DIR/decoder.onnx", openAsset)
            val joiner = ModelAssets.ensureFile(filesDir, "$MODEL_DIR/joiner.onnx", openAsset)
            val tokens = ModelAssets.ensureFile(filesDir, "$MODEL_DIR/tokens.txt", openAsset)
            if (encoder == null || decoder == null || joiner == null || tokens == null) return false
            recognizer = OnlineRecognizer(
                config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE_HZ, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = encoder.absolutePath,
                            decoder = decoder.absolutePath,
                            joiner = joiner.absolutePath,
                        ),
                        tokens = tokens.absolutePath,
                        numThreads = 4,
                        provider = "cpu",
                        modelType = "zipformer2",
                    ),
                    enableEndpoint = false,
                    decodingMethod = "modified_beam_search",
                    maxActivePaths = 4,
                ),
            )
            stream = recognizer?.createStream()
            stream != null
        } catch (error: Throwable) {
            android.util.Log.e(TAG, "Initialization failed", error)
            recognizer = null
            stream = null
            false
        }
        return ready
    }

    override fun setListener(listener: ((RecognitionResult) -> Unit)?) { this.listener = listener }

    @Synchronized
    override fun acceptAudio(frame: ShortArray) {
        val activeRecognizer = recognizer ?: return
        val activeStream = stream ?: return
        if (!ready) return
        try {
            activeStream.acceptWaveform(FloatArray(frame.size) { frame[it] / 32768f }, SAMPLE_RATE_HZ)
            while (activeRecognizer.isReady(activeStream)) activeRecognizer.decode(activeStream)
            activeRecognizer.getResult(activeStream).text.takeIf(String::isNotBlank)?.let {
                emit(RecognitionResult(it, false, 1f, "en"))
            }
        } catch (error: Throwable) {
            android.util.Log.e(TAG, "Inference failed", error)
            ready = false
        }
    }

    @Synchronized
    fun decodeSegment(samples: ShortArray, hotwords: String = ""): RecognitionResult {
        val activeRecognizer = recognizer
        if (!ready || activeRecognizer == null || samples.isEmpty()) return emptyResult()
        val segment = try {
            if (hotwords.isBlank()) activeRecognizer.createStream()
            else activeRecognizer.createStream(hotwords)
        } catch (_: Throwable) {
            activeRecognizer.createStream()
        }
        return try {
            segment.acceptWaveform(FloatArray(samples.size) { samples[it] / 32768f }, SAMPLE_RATE_HZ)
            segment.inputFinished()
            while (activeRecognizer.isReady(segment)) activeRecognizer.decode(segment)
            val text = activeRecognizer.getResult(segment).text.trim()
            RecognitionResult(text, true, if (text.isBlank()) 0f else 0.7f, "en")
        } catch (error: Throwable) {
            android.util.Log.e(TAG, "Segment decode failed", error)
            ready = false
            emptyResult()
        } finally {
            runCatching { segment.release() }
            reset()
        }
    }

    @Synchronized
    override fun reset() {
        try {
            val activeRecognizer = recognizer ?: return
            stream?.release()
            stream = activeRecognizer.createStream()
        } catch (error: Throwable) {
            android.util.Log.e(TAG, "Reset failed", error)
            ready = false
        }
    }

    @Synchronized
    override fun close() {
        runCatching { stream?.release() }
        runCatching { recognizer?.release() }
        stream = null
        recognizer = null
        ready = false
    }

    private fun emptyResult() = RecognitionResult("", true, 0f, "en").also(::emit)
    private fun emit(result: RecognitionResult) { runCatching { listener?.invoke(result) } }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val MODEL_DIR = "models/sherpa-onnx-streaming-zipformer-en-2023-06-26-int8"
        private const val TAG = "StreamingZipformerStt"
    }
}
