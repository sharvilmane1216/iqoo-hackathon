package com.aasra.llama

/**
 * Builds the Qwen ChatML prompt for the local LLM.
 *
 * PLAN 4.5 constraints, all enforced here in one place so local and cloud
 * prompts cannot drift apart:
 * - short, spoken style: the model answers in 1–2 sentences, plain speech
 * - no markdown, no emoji (the sentence chunker feeds TTS directly)
 * - respectful address of an elderly user
 * - confirm before ANY device action (call / SMS / alarm / SOS …)
 * - medical safety: never give dosages, always defer to a doctor, escalate
 */
object QwenChatTemplate {

    const val LANGUAGE_EN = "en"
    const val LANGUAGE_HI = "hi"

    /**
     * Non-technical spoken fallback used when the GGUF is missing or load
     * failed. This is what the user HEARS — never a stack trace or file name.
     */
    fun missingModelMessage(language: String = LANGUAGE_EN): String =
        if (language == LANGUAGE_HI) {
            "माफ़ कीजिए, मैं अभी तैयार नहीं हूँ। कृपया थोड़ी देर बाद फिर बोलिए।"
        } else {
            "Sorry, I am not ready just yet. Please try again in a moment."
        }

    /** System prompt shared by [LlamaEngine] and the cloud escalation path. */
    fun systemPrompt(userName: String?, language: String = LANGUAGE_EN): String {
        val name = userName?.takeIf { it.isNotBlank() } ?: if (language == LANGUAGE_HI) "आप" else "you"
        return if (language == LANGUAGE_HI) {
            "आप आसरा हैं, एक महिला आवाज़ सहायक। हमेशा $name कहकर सम्मान से बात करें। " +
                "जवाब केवल देवनागरी हिन्दी में, छोटे बोले गए वाक्यों में। " +
                "मार्कडाउन या इमोजी न लिखें। काम से पहले ज़ोर से पुष्टि लें। दवाई की खुराक न बताएँ।"
        } else {
            "You are Aasra, a respectful voice companion for elderly users. " +
                "Address the user as $name. Reply only in simple spoken English. Never Hindi. " +
                "No markdown or emoji. Confirm out loud before any action. Never give medicine dosages."
        }
    }

    /**
     * Full ChatML document: system prompt + last turns + current user text.
     * Tool schema is described in prose (no markdown tables) to keep the
     * small model on rails without burning context.
     */
    fun build(
        userText: String,
        history: List<Pair<String, String>> = emptyList(),
        userName: String? = null,
        language: String = LANGUAGE_EN,
    ): String = buildString {
        append("<|im_start|>system\n")
        append(systemPrompt(userName, language))
        append("\nYou can act with tool calls written as <tool_call>{\"name\": " +
            "\"call_contact\"|\"send_sms\"|\"set_reminder\"|\"list_reminders\"|" +
            "\"cancel_reminder\"|\"sos\"|\"get_time\"|\"get_date\"|" +
            "\"set_volume\"|\"flashlight\"|\"read_notifications\"|" +
            "\"escalate\", \"arguments\": {...}}</tool_call>. ")
        append("If you cannot help on device, emit escalate with a reason.")
        append("\n<|im_end|>\n")
        for ((role, text) in history.takeLast(20)) {
            val r = if (role == "assistant") "assistant" else "user"
            append("<|im_start|>$r\n$text\n<|im_end|>\n")
        }
        append("<|im_start|>user\n$userText\n<|im_end|>\n<|im_start|>assistant\n")
        // Qwen3.5's official enable_thinking=false generation prefix.
        append("<think>\n\n</think>\n\n")
    }
}
