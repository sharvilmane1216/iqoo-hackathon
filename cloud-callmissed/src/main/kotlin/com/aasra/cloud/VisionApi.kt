package com.aasra.cloud

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val visionJson = Json { ignoreUnknownKeys = true }

/** Reads handwriting or print from a photo. Result is plain text for Sarvam. */
class VisionApi(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = CallMissedConfig.BASE_URL,
    private val usage: UsageCounter = UsageCounter(),
) {
    suspend fun transcribePrescription(jpeg: ByteArray, language: String): String = withContext(Dispatchers.IO) {
        val prompt = if (language.startsWith("en")) {
            "Transcribe this doctor's prescription. Keep every medicine name, strength, timing, and instruction you can read. Plain text only. No markdown."
        } else {
            "इस डॉक्टर के पर्चे का पूरा पाठ लिखें। दवाई का नाम, ताकत, समय और निर्देश रखें। केवल सादा पाठ। मार्कडाउन नहीं।"
        }
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val body = buildJsonObject {
            put("model", CallMissedConfig.CHAT_MODEL_FALLBACK_2)
            put("stream", false)
            put("temperature", 0.1)
            put("max_tokens", 700)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject { put("type", "text"); put("text", prompt) })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:image/jpeg;base64,$b64")
                            })
                        })
                    })
                })
            })
        }.toString()
        val req = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            usage.record(resp.headers)
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw CallMissedException(resp.code, CloudErrors.parseCode(raw), raw.take(500))
            }
            readContent(raw)
        }
    }

    private fun readContent(raw: String): String {
        val root = visionJson.parseToJsonElement(raw).jsonObject
        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: return ""
        val content = message["content"] ?: return ""
        return try {
            content.jsonPrimitive.contentOrNull.orEmpty()
        } catch (_: Exception) {
            (content as? JsonArray)?.joinToString("") { part ->
                try {
                    part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }.orEmpty()
        }.trim()
    }
}
