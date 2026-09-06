package com.aasra.companion.care

data class CareCard(
    val title: String,
    val steps: List<String>,
    val urgent: Boolean = false,
    val heard: String = "",
)

object CareCards {
    fun complaint(raw: String): Boolean {
        val t = norm(raw)
        if (t.length < 3 || commandOnly(t)) return false
        return fromCatalog(raw, "hi") != null || hit(t,
            "pain", "hurt", "ache", "dard", "दर्द", "sick", "unwell", "takleef", "tabiyat",
            "bimaar", "तकलीफ", "तबीयत", "बीमार", "problem", "dikkat", "kharab", "खराब",
        )
    }

    fun urgent(raw: String) = fromCatalog(raw, "hi")?.urgent == true

    fun fromCatalog(raw: String, language: String): CareCard? {
        val t = norm(raw)
        if (t.length < 3) return null
        val spec = CareCatalog.all.firstOrNull { spec ->
            runCatching { spec.pattern.containsMatchIn(t) }.getOrDefault(false)
        } ?: return null
        val hi = hindi(raw, language)
        return CareCard(
            if (hi) spec.hi else spec.en,
            if (hi) spec.hiSteps else spec.enSteps,
            spec.urgent,
            raw.trim(),
        )
    }

    fun problemName(raw: String, language: String): String {
        fromCatalog(raw, language)?.title?.let { return it }
        extract(raw)?.let { return it }
        val t = raw.trim().trimEnd('.', '!', '?', '।').replace(Regex("(?i)^aasra[,.]?\\s*"), "").trim()
        return t.take(48).ifBlank { if (hindi(raw, language)) "तबीयत खराब" else "this problem" }
    }

    fun keepAfter(raw: String) = complaint(raw) || !hit(norm(raw),
        "what time", "what date", "kya time", "kitna baja", "volume", "notification",
        "weather", "news", "set a reminder")

    fun parse(generated: String?, heard: String, language: String): CareCard? {
        val body = generated?.trim().orEmpty()
        if (body.isBlank()) return null
        val title = titleLine.find(body)
            ?.groupValues?.get(1)?.trim()?.trimEnd('.')
            ?: problemName(heard, language)
        val steps = body.lineSequence().map { it.trim() }
            .mapNotNull { stepLine.find(it)?.groupValues?.get(1)?.trim() }
            .filter { it.length > 3 }
            .take(6)
            .toList()
        if (steps.size < 2) return null
        return CareCard(title.take(48), steps, urgent(heard) || hit(body.lowercase(), "urgent", "एसओएस"), heard.trim())
    }

    fun fallback(heard: String, language: String): CareCard =
        fromCatalog(heard, language) ?: run {
            val hi = hindi(heard, language)
            val name = problemName(heard, language)
            val urgent = urgent(heard)
            CareCard(
                name,
                if (hi) listOf(
                    "$name हो तो बैठें या लेटें। तंग कपड़े ढीले करें।",
                    "पानी पिएँ। देखें $name बढ़ रहा है या कम।",
                    "परिवार से कहें: आपको $name है। केवल डॉक्टर वाली पुरानी दवाई लें।",
                    "$name बढ़े तो डॉक्टर को फोन करें। सीने में दर्द या साँस न आए तो एसओएस।",
                ) else listOf(
                    "You said $name. Sit or lie down. Loosen tight clothes.",
                    "Sip water. Notice if $name is getting worse or easing.",
                    "Tell family you have $name. Take only medicine your doctor already gave you for this.",
                    "Call a doctor if $name lasts or gets worse. Use SOS for chest pain or if you cannot breathe.",
                ),
                urgent,
                heard.trim(),
            )
        }

    private val hiExtract = Regex("""(?:मुझे|मेरे|मुझको)\s+(.+?)(?:\s+है|\s+हैं|\s+हो|\s+लग|$)""")
    private val hiLatinExtract = Regex("""(?:mujhe|mere|mujhko)\s+(.+?)(?:\s+hai|\s+hain|\s+ho|\s+lag|$)""", RegexOption.IGNORE_CASE)
    private val enExtract = Regex("""(?:i(?:'m| am)? having|i have got|i have|i feel|feeling)\s+(.+)""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("\\s+")
    private val titleLine = Regex("""(?im)^(?:TITLE|शीर्षक)\s*[:\-]\s*(.+)$""")
    private val stepLine = Regex("""^(?:\d+[\.)]|[-•])\s*(.+)$""")

    private fun extract(raw: String): String? {
        val t = raw.trim().trimEnd('.', '!', '?', '।')
        hiExtract.find(t)?.groupValues?.get(1)?.trim()?.takeIf { it.length in 2..48 }?.let { return it }
        hiLatinExtract.find(t)?.groupValues?.get(1)?.trim()?.takeIf { it.length in 2..48 }?.let { return it }
        enExtract.find(t)?.groupValues?.get(1)?.trim()?.takeIf { it.length in 2..48 }?.let { return it }
        return null
    }

    private fun commandOnly(t: String) = hit(t, "call", "dial", "message", "sms", "reminder",
        "alarm", "what time", "kya time", "volume", "notification", "कॉल", "फोन", "याद") &&
        !hit(t, "fever", "pain", "dard", "बुखार", "दर्द", "sick", "takleef")

    private fun hindi(raw: String, language: String) =
        language.startsWith("hi") || raw.any { it in '\u0900'..'\u097F' }

    private fun norm(raw: String) = raw.lowercase().replace(spaces, " ").trim()

    private fun hit(t: String, vararg keys: String) = keys.any { it in t }
}
