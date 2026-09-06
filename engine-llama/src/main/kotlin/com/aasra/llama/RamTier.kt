package com.aasra.llama

/**
 * Decides which Qwen GGUF to load from the device's CURRENT free RAM.
 *
 * If free RAM < 6 GB at load time, use Qwen3.5-0.8B instead of Qwen3.5-4B.
 */
object RamTier {

    const val LOW_RAM_THRESHOLD_BYTES: Long = 6L * 1024L * 1024L * 1024L

    enum class LlmTier(
        /** File name of the GGUF in [com.aasra.models.ModelPaths.modelsDir]. */
        val modelFileName: String,
    ) {
        FULL("Qwen3.5-4B-Instruct-Q4_K_M.gguf"),
        SMALL("Qwen3.5-0.8B-Instruct-Q4_K_M.gguf"),
    }

    /** Pure function — unit-testable without Android. */
    fun decide(freeBytes: Long): LlmTier =
        if (freeBytes < LOW_RAM_THRESHOLD_BYTES) LlmTier.SMALL else LlmTier.FULL

    /** Android entry point: reads [android.app.ActivityManager.MemoryInfo.availMem]. */
    fun decide(context: android.content.Context): LlmTier {
        val am = context.getSystemService(android.app.ActivityManager::class.java) ?: return LlmTier.SMALL
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return decide(info.availMem)
    }
}
