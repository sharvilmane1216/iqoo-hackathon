package com.aasra.companion.care

import com.aasra.cloud.AasraPrompts
import com.aasra.cloud.CallMissedClient
import com.aasra.cloud.ChatMessage
import com.aasra.cloud.ChatStreamEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonArray

object CareCardBus {
    @Volatile var language: String = "hi"
    @Volatile var onUnwell: ((heard: String, language: String) -> Unit)? = null
    private val mutable = MutableStateFlow<CareCard?>(null)
    val card = mutable.asStateFlow()

    fun begin(text: String, language: String = this.language) {
        this.language = language
        runCatching { beginInner(text, language) }
    }

    private fun beginInner(text: String, language: String) {
        if (!CareCards.complaint(text)) {
            if (!CareCards.keepAfter(text)) mutable.value = null
            return
        }
        val heard = text.trim()
        val existing = mutable.value
        if (existing?.heard == heard && existing.steps.isNotEmpty()) return
        val first = existing?.heard != heard
        CareCards.fromCatalog(text, language)?.let { mutable.value = it }
            ?: run {
                val name = CareCards.problemName(text, language)
                mutable.value = CareCard(name, emptyList(), CareCards.urgent(text), heard)
            }
        if (first) onUnwell?.invoke(heard, language)
    }

    suspend fun write(cloud: CallMissedClient?, text: String, language: String = this.language) {
        this.language = language
        begin(text, language)
        if (mutable.value?.heard != text.trim()) return
        if (mutable.value?.steps?.isNotEmpty() == true) return
        val generated = pull(cloud, text, language)
        val next = CareCards.parse(generated, text, language) ?: CareCards.fallback(text, language)
        if (mutable.value?.heard == text.trim()) mutable.value = next
    }

    fun clear() {
        mutable.value = null
    }

    private suspend fun pull(cloud: CallMissedClient?, text: String, language: String): String? {
        val c = cloud ?: return null
        val out = StringBuilder()
        return try {
            c.chat.stream(
                listOf(ChatMessage("user", text)),
                AasraPrompts.careCard(language),
                tools = buildJsonArray { },
            ).collect { event ->
                if (event is ChatStreamEvent.Content) out.append(event.delta)
            }
            out.toString().trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
