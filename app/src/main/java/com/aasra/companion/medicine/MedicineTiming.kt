package com.aasra.companion.medicine

/** Meal and part-of-day timing from a pack or prescription. Not a clock. */
object MedicineTiming {
    fun phrase(text: String, language: String): String {
        val t = text.lowercase().replace('–', '-').replace(Regex("\\s+"), " ")
        clockLabel(t, language)?.let { return it }
        return doseCode(t, language)
            ?: shorthand(t, language)
            ?: meals(t, language)
            ?: period(t, language)
            .orEmpty()
    }

    fun reminderClocks(phrase: String): List<String> {
        val p = phrase.lowercase().replace('–', '-').replace(Regex("\\s+"), " ")
        clockLabel(p, "en")?.let { return listOf(it) }
        val out = linkedSetOf<String>()
        when {
            "before breakfast" in p || "नाश्ते से पहले" in p -> out += "7:30 am"
            "after breakfast" in p || "नाश्ते के बाद" in p -> out += "8:30 am"
            "morning" in p || "सुबह" in p -> out += "8:00 am"
        }
        when {
            "before lunch" in p || "दोपहर के खाने से पहले" in p -> out += "1:00 pm"
            "after lunch" in p || "दोपहर के खाने के बाद" in p -> out += "2:30 pm"
            "afternoon" in p || "दोपहर" in p -> out += "2:00 pm"
        }
        if ("evening" in p || "शाम" in p) out += "6:00 pm"
        when {
            "before dinner" in p || "रात के खाने से पहले" in p -> out += "7:00 pm"
            "after dinner" in p || "रात के खाने के बाद" in p -> out += "8:30 pm"
            "before sleep" in p || "सोने से पहले" in p || "bedtime" in p -> out += "10:00 pm"
            "night" in p || "bed" in p || ("रात" in p && "खाने" !in p) -> out += "9:00 pm"
        }
        if (out.isEmpty() && ("after food" in p || "खाने के बाद" in p)) out += "8:30 am"
        if (out.isEmpty() && ("before food" in p || "खाने से पहले" in p)) out += "7:30 am"
        return out.toList()
    }

    private fun doseCode(t: String, language: String): String? {
        val m = Regex("""\b([01])[\s./-]+([01])[\s./-]+([01])\b""").find(t) ?: return null
        val parts = buildList {
            if (m.groupValues[1] == "1") add(word("morning", "सुबह", language))
            if (m.groupValues[2] == "1") add(word("afternoon", "दोपहर", language))
            if (m.groupValues[3] == "1") add(word("night", "रात", language))
        }
        return join(parts, language).takeIf { it.isNotBlank() }
    }

    private fun shorthand(t: String, language: String): String? = when {
        Regex("""\b(hs|bedtime|qhs)\b""").containsMatchIn(t) -> word("night", "रात", language)
        Regex("""\b(tds|tid|thrice)\b""").containsMatchIn(t) ->
            join(listOf(word("morning", "सुबह", language), word("afternoon", "दोपहर", language), word("night", "रात", language)), language)
        Regex("""\b(bd|bid|twice)\b""").containsMatchIn(t) ->
            join(listOf(word("morning", "सुबह", language), word("night", "रात", language)), language)
        Regex("""\b(od|once daily|once a day)\b""").containsMatchIn(t) -> word("morning", "सुबह", language)
        else -> null
    }

    private fun meals(t: String, language: String): String? {
        val hits = buildList {
            if (has(t, "before breakfast", "नाश्ते से पहले", "breakfast se pehle")) add(word("before breakfast", "नाश्ते से पहले", language))
            if (has(t, "after breakfast", "नाश्ते के बाद", "breakfast ke baad")) add(word("after breakfast", "नाश्ते के बाद", language))
            if (has(t, "before lunch", "दोपहर के खाने से पहले", "lunch se pehle")) add(word("before lunch", "दोपहर के खाने से पहले", language))
            if (has(t, "after lunch", "दोपहर के खाने के बाद", "lunch ke baad")) add(word("after lunch", "दोपहर के खाने के बाद", language))
            if (has(t, "before dinner", "रात के खाने से पहले", "dinner se pehle")) add(word("before dinner", "रात के खाने से पहले", language))
            if (has(t, "after dinner", "रात के खाने के बाद", "dinner ke baad")) add(word("after dinner", "रात के खाने के बाद", language))
            if (has(t, "before sleep", "सोने से पहले", "bedtime", "before bed")) add(word("before sleep", "सोने से पहले", language))
            if (isEmpty() && has(t, "after food", "after meal", "खाने के बाद", "after meals")) {
                add(word("after food", "खाने के बाद", language))
            }
            if (isEmpty() && has(t, "before food", "before meal", "खाने से पहले", "before meals", "empty stomach", "खाली पेट")) {
                add(word("before food", "खाने से पहले", language))
            }
        }
        return join(hits, language).takeIf { it.isNotBlank() }
    }

    private fun period(t: String, language: String): String? {
        val hits = buildList {
            if (has(t, "morning", "सुबह")) add(word("morning", "सुबह", language))
            if (has(t, "afternoon", "दोपहर")) add(word("afternoon", "दोपहर", language))
            if (has(t, "evening", "शाम")) add(word("evening", "शाम", language))
            if (has(t, "night", "रात", "bedtime")) add(word("night", "रात", language))
        }
        return join(hits, language).takeIf { it.isNotBlank() }
    }

    fun clockText(hour12: Int, minute: Int, pm: Boolean): String {
        val h = hour12.coerceIn(1, 12)
        val m = minute.coerceIn(0, 59).toString().padStart(2, '0')
        return "$h:$m ${if (pm) "pm" else "am"}"
    }

    private fun clockLabel(t: String, language: String): String? {
        val m = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?|am|pm)\b""").find(t) ?: return null
        val hour = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].ifBlank { "00" }.padStart(2, '0')
        val mer = if (m.groupValues[3].startsWith("p")) "pm" else "am"
        return if (language.startsWith("en")) "$hour:$min $mer" else "$hour:$min बजे"
    }

    private fun has(t: String, vararg keys: String) = keys.any { it.lowercase() in t }
    private fun word(en: String, hi: String, language: String) = if (language.startsWith("en")) en else hi
    private fun join(parts: List<String>, language: String) =
        if (language.startsWith("en")) parts.joinToString(" and ") else parts.joinToString(" और ")
}
