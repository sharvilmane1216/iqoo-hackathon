package com.aasra.companion.medicine

import com.aasra.cloud.AasraPrompts
import com.aasra.cloud.CallMissedClient
import com.aasra.cloud.ChatMessage
import com.aasra.cloud.ChatStreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray

object MedicineName {
    data class Extracted(val name: String)

    suspend fun extract(cloud: CallMissedClient, ocr: String, language: String): Extracted =
        withContext(Dispatchers.IO) {
            val out = StringBuilder()
            cloud.chat.stream(
                listOf(ChatMessage("user", "PACK TEXT:\n$ocr")),
                AasraPrompts.medicineExtract(language),
                tools = buildJsonArray { },
            ).collect { event ->
                if (event is ChatStreamEvent.Content) out.append(event.delta)
            }
            parse(out.toString())
        }

    fun parse(raw: String): Extracted {
        val name = field(raw, "NAME")
            .replace(Regex("(?i)\\b(unknown|none|n/?a|not found)\\b"), "")
            .trim(' ', '.', '"', '\'')
        return Extracted(name)
    }

    private fun field(raw: String, key: String): String =
        Regex("""(?im)^$key\s*:\s*(.+)$""").find(raw)?.groupValues?.get(1)?.trim().orEmpty()
}
