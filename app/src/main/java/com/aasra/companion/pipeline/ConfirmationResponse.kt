package com.aasra.companion.pipeline

internal enum class ConfirmationAnswer { YES, NO, UNKNOWN }

private val affirmativeResponse = Regex(
    """(?:(?:yes|yeah|yep|ok|okay|confirm|haan|han|hao|हाँ|हां|जी)(?: (?:please|ji|जी|haan|हाँ|हां))?(?: (?:call(?: him| her| them)?|send(?: it)?|go ahead|kar do|kardo|laga do|bhej do|कर दो|भेज दो))?|go ahead|theek hai|thik hai|kar do|kardo|laga do|bhej do|कर दो|भेज दो)""",
)
private val negativeResponse = Regex(
    """\b(nahi|nahin|na|no|nope|not|don't|dont|mat karo|cancel|ruk jao|rehn do|rehen do)\b|नहीं|नही|मत करो|रुको|रद्द""",
)

internal fun classifyConfirmation(text: String): ConfirmationAnswer {
    val n = text.lowercase()
        .replace(Regex("[’'`´]"), "")
        .replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val padded = " $n "
    if (negativeResponse.containsMatchIn(padded)) return ConfirmationAnswer.NO
    val first = n.substringBefore(' ')
    val rest = n.substringAfter(' ', "")
    val yesStart = first in setOf("yes", "yeah", "yep", "yup", "haan", "han", "hao", "हाँ", "हां", "जी")
    val redirect = Regex("""\b(call|phone|message|text|sms|remind|कॉल|फोन|संदेश)\b""").containsMatchIn(rest)
    return when {
        yesStart && redirect -> ConfirmationAnswer.UNKNOWN
        yesStart -> ConfirmationAnswer.YES
        n in setOf("ok", "okay", "theek hai", "thik hai", "go ahead", "kar do", "kardo") -> ConfirmationAnswer.YES
        affirmativeResponse.matches(n) -> ConfirmationAnswer.YES
        else -> ConfirmationAnswer.UNKNOWN
    }
}
