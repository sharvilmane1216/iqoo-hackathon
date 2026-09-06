package com.aasra.llama

import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge

class LlamatikBackend : LlamaEngine.LlamaBackend {
    override val supportsVulkan: Boolean = vulkanLibraryPresent()
    @Volatile
    private var loaded = false

    override val isLoaded: Boolean get() = loaded

    @Synchronized
    override fun load(params: LlamaEngine.LlamaLoadParams) {
        if (loaded) return
        LlamaBridge.updateGenerateParams(
            temperature = 0.4f,
            maxTokens = 220,
            topP = 0.9f,
            topK = 40,
            repeatPenalty = 1.1f,
            contextLength = params.nCtx,
            numThreads = params.nThreads,
            useMmap = true,
            flashAttention = true,
            batchSize = 128,
            gpuLayers = if (params.preferVulkan) -1 else 0,
        )
        loaded = LlamaBridge.initGenerateModel(params.modelFile.absolutePath)
        check(loaded) { "llama.cpp could not load ${params.modelFile.name}" }
    }

    @Synchronized
    override fun unload() {
        if (loaded) LlamaBridge.shutdown()
        loaded = false
    }

    @Synchronized
    override fun generate(prompt: String, onToken: (String) -> Unit) {
        check(loaded) { "LLM is not loaded" }
        var failure: String? = null
        LlamaBridge.generateStream(prompt, object : GenStream {
            override fun onDelta(text: String) = onToken(text)
            override fun onComplete() = Unit
            override fun onError(message: String) {
                failure = message
            }
        })
        failure?.let { error(it) }
    }

    override fun cancel() {
        if (loaded) LlamaBridge.nativeCancelGenerate()
    }

    companion object {
        private val vulkanSo: Boolean by lazy {
            try {
                System.loadLibrary("ggml-vulkan")
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            }
        }

        fun vulkanLibraryPresent(): Boolean = vulkanSo
    }
}
