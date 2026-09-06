package com.aasra.sherpa

/** CPU only. Accelerator EPs aborted native ONNX on this device. */
internal object SherpaRuntime {
    val providers = arrayOf("cpu")

    inline fun <T> open(create: (String) -> T): T {
        var last: Throwable? = null
        for (provider in providers) {
            try {
                return create(provider)
            } catch (error: Throwable) {
                last = error
            }
        }
        throw last ?: IllegalStateException("No sherpa runtime opened")
    }
}
