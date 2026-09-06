package com.aasra.sherpa

import com.aasra.pipeline.RecognitionResult
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/** Oriserve Whisper-Hindi2Hinglish-Swift, quantized sherpa export. Returns Romanized Hinglish. */
class HinglishStt(private val filesDir: File) {
    @Volatile var ready = false
        private set
    private var recognizer: OfflineRecognizer? = null

    @Synchronized fun init(): Boolean {
        close()
        val encoder = File(filesDir, "$MODEL_DIR/encoder.int8.onnx")
        val decoder = File(filesDir, "$MODEL_DIR/decoder.int8.onnx")
        val tokens = File(filesDir, "$MODEL_DIR/tokens.txt")
        if (!encoder.isFile || !decoder.isFile || !tokens.isFile) return false
        return try {
            recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = encoder.absolutePath, decoder = decoder.absolutePath,
                        language = "hi", task = "transcribe",
                    ),
                    tokens = tokens.absolutePath, numThreads = 4, modelType = "whisper",
                ),
            ))
            ready = true
            true
        } catch (error: Throwable) {
            android.util.Log.e("HinglishStt", "Initialization failed", error)
            false
        }
    }

    @Synchronized fun decodeSegment(samples: ShortArray): RecognitionResult {
        val active = recognizer
        if (!ready || active == null || samples.isEmpty()) return RecognitionResult("", true, 0f, "hi")
        return try {
            val stream = active.createStream()
            try {
                stream.acceptWaveform(FloatArray(samples.size) { samples[it] / 32768f }, 16000)
                active.decode(stream)
                val text = active.getResult(stream).text.trim()
                // The Kotlin API has no calibrated confidence score; never claim certainty.
                RecognitionResult(text, true, if (text.isBlank()) 0f else 0.6f, "hi")
            } finally { stream.release() }
        } catch (error: Throwable) {
            android.util.Log.e("HinglishStt", "Recognition failed", error)
            ready = false
            RecognitionResult("", true, 0f, "hi")
        }
    }

    @Synchronized fun close() {
        runCatching { recognizer?.release() }
        recognizer = null
        ready = false
    }

    companion object {
        const val MODEL_DIR = "models/hi-hinglish-swift"
    }
}
