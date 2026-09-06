package com.aasra.tools

import java.util.Locale
import org.json.JSONObject

/**
 * Tiny on-device command router. Device actions skip the LLM.
 * Hindi and English only; not a language model.
 */
object CommandIntent {
    data class Hit(val name: String, val argumentsJson: String)

    fun parse(raw: String, contactNames: List<String> = emptyList()): Hit? {
        val t = normalize(raw)
        if (t.isEmpty()) return null
        return call(t, contactNames) ?: recoverCall(t, contactNames) ?: sms(t, contactNames)
            ?: volume(t) ?: torch(t) ?: clock(t) ?: reminder(t) ?: notifications(t) ?: health(t) ?: sos(t)
    }

    private fun normalize(raw: String): String {
        val lower = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("[\"'`´?!.,;()\\[\\]{}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return glueSpread(lower)
    }

    /** Recovers "र ा त 1 1 ब ज े" back to "रात 11 बजे". Leaves normal words alone. */
    fun glueSpread(s: String): String {
        val parts = s.split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return s
        val out = ArrayList<String>()
        var i = 0
        while (i < parts.size) {
            if (parts[i].length == 1) {
                val start = i
                while (i < parts.size && parts[i].length == 1) i++
                var run = parts.subList(start, i).joinToString("")
                run = run.replace(Regex("(?<=[\\u0900-\\u097F])(?=\\d)"), " ")
                    .replace(Regex("(?<=\\d)(?=[\\u0900-\\u097F])"), " ")
                    .replace(Regex("(?<=बजे)(?=[\\u0900-\\u097F])"), " ")
                out.addAll(run.split(' ').filter { it.isNotEmpty() })
            } else {
                out.add(parts[i])
                i++
            }
        }
        return out.joinToString(" ").replace(Regex("(?<=बजे)(?=[\\u0900-\\u097F])"), " ")
    }

    private fun call(t: String, names: List<String>): Hit? {
        val raw = CALL_LEAD.find(t)?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+ko\\s+(?:call|phone|fon)(?:\\s+karo)?$").find(t)?.groupValues?.get(1)
            ?: Regex("^(?:कॉल|फोन)\\s+(?:करो\\s+|लगाओ\\s+)?(.+)$").find(t)?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+को\\s+(?:कॉल|फोन)").find(t)?.groupValues?.get(1)?.removeSuffix(" करो")?.trim()
            ?: return null
        if (raw in SKIP_CALL) return null
        return hit("call_contact", "name" to (closestName(raw, names) ?: raw))
    }

    private fun volume(t: String): Hit? = when {
        UP.any { t.contains(it) } -> hit("set_volume", "direction" to "up")
        DOWN.any { t.contains(it) } -> hit("set_volume", "direction" to "down")
        else -> Regex("(?:volume|आवाज़|आवाज)\\s+(\\d{1,3})").find(t)?.groupValues?.get(1)?.let {
            hit("set_volume", "level" to it)
        }
    }

    private fun torch(t: String): Hit? = when {
        TORCH_ON.any { t.contains(it) } -> hit("flashlight", "on" to "true")
        TORCH_OFF.any { t.contains(it) } -> hit("flashlight", "on" to "false")
        else -> null
    }

    private fun clock(t: String): Hit? = when {
        TIME.any { t == it || t.startsWith("$it ") } -> Hit("get_time", "{}")
        DATE.any { t == it || t.startsWith("$it ") } -> Hit("get_date", "{}")
        else -> null
    }

    private fun sms(t: String, names: List<String>): Hit? {
        val m = Regex("^(?:please )?(?:send (?:an? )?(?:sms|text|message)|text|message|sms|संदेश(?: भेजो)?)\\s+(?:to )?(.+)$").find(t)
            ?: return null
        val rest = m.groupValues[1].trim()
        val split = Regex("^(.+?)\\s+(?:that |saying |keho |की |कि )?(.+)$").find(rest)
        val who = (split?.groupValues?.get(1) ?: rest).trim()
        val body = split?.groupValues?.get(2).orEmpty().trim()
        if (who in SKIP_CALL) return null
        return hit("send_sms", "name" to (closestName(who, names) ?: who), "message" to body)
    }

    private fun reminder(t: String): Hit? {
        if (hasPhrase(t, LIST)) return Hit("list_reminders", "{}")
        if (!hasPhrase(t, REMIND)) return null
        val relative = RELATIVE.find(t)
        val clock = CLOCK.findAll(t).map { it.value.trim() }.filter { it.any(Char::isDigit) }.maxByOrNull { it.length }
        val englishAt = Regex("(?:at|on|को)\\s+(.+)$").find(t)?.groupValues?.get(1).orEmpty()
        val time = relative?.value ?: clock ?: englishAt
        var text = t.replace(Regex("^(?:remind me to|remind me|set a reminder(?: to)?|रिमाइंडर(?: जोड़ो| लगाओ)?)\\s*"), "")
            .replace(Regex("\\s*(?:की)?\\s*याद\\s*दिला(?:ओ|ना)?\\s*$"), "")
        text = relative?.value?.let { text.replace(it, " ") } ?: clock?.let { c ->
            val i = text.indexOf(c)
            if (i >= 0) text.removeRange(i, i + c.length) else text
        } ?: text
        text = glueSpread(
            text.replace(Regex("\\s+(?:at|on|को)\\s+.+$"), "")
                .replace(Regex("\\s+"), " ")
                .replace(Regex("[।.,]+$"), "")
                .replace(Regex("\\s*की$"), "")
                .trim(),
        )
        return hit("set_reminder", "text" to text.ifBlank { t }, "time" to time.ifBlank { t })
    }

    private fun hasPhrase(t: String, options: List<String>): Boolean {
        val packed = t.replace(" ", "")
        return options.any { it in t || it.replace(" ", "") in packed }
    }

    private fun health(t: String): Hit? = when {
        hasPhrase(t, HEALTH_ALL) -> Hit("get_health", "{}")
        hasPhrase(t, STEPS) -> Hit("get_steps", "{}")
        hasPhrase(t, HEART) -> Hit("get_heart_rate", "{}")
        hasPhrase(t, OXYGEN) -> Hit("get_oxygen", "{}")
        else -> null
    }

    private fun notifications(t: String): Hit? =
        if (NOTIF.any { t.contains(it) }) Hit("read_notifications", "{}") else null

    private fun sos(t: String): Hit? =
        if (t == "sos" || t == "emergency" || t == "आपातकाल" || t == "help me" || t == "i need help" ||
            t == "madad" || t == "bachao" || t.contains("मदद") || t.contains("बचाओ")
        ) Hit("sos", "{}") else null

    private fun hit(name: String, vararg args: Pair<String, String>): Hit {
        val o = JSONObject()
        args.forEach { (k, v) -> o.put(k, v.trim()) }
        return Hit(name, o.toString())
    }

    private fun recoverCall(t: String, contactNames: List<String>): Hit? {
        val spoken = Regex("^(.+?)\\s+ko\\s+(?:call|phone|fon|karo)").find(t)?.groupValues?.get(1) ?: return null
        val name = closestName(spoken, contactNames) ?: return null
        return hit("call_contact", "name" to name)
    }

    private fun closestName(spoken: String, contactNames: List<String>): String? {
        if (contactNames.isEmpty()) return null
        val spokenNorm = ContactTools.normalize(spoken)
        if (spokenNorm.isEmpty()) return null
        val allow = if (spokenNorm.length >= 6) 3 else 2
        val match = contactNames.minByOrNull { name ->
            val n = ContactTools.normalize(name)
            n.split(" ").plus(n).minOf { part ->
                if (part.isEmpty()) 99 else ContactTools.levenshtein(spokenNorm, part)
            }
        } ?: return null
        val dist = ContactTools.normalize(match).split(" ").plus(ContactTools.normalize(match)).minOf {
            if (it.isEmpty()) 99 else ContactTools.levenshtein(spokenNorm, it)
        }
        return if (dist <= allow) match else null
    }

    private val CALL_LEAD = Regex(
        "^(?:please |can you |could you )?(?:call|called|calling|coal|col|kall|phone|dial|ring|fon)\\s+(.+)$",
    )
    private val SKIP_CALL = setOf("me", "you", "it", "this", "that", "emergency")
    private val UP = listOf("volume up", "turn up", "louder", "increase volume", "आवाज़ बढ़ा", "आवाज बढ़ा", "आवाज तेज", "आवाज़ तेज")
    private val DOWN = listOf("volume down", "turn down", "quieter", "decrease volume", "आवाज़ कम", "आवाज कम", "आवाज़ धीमी", "आवाज धीमी")
    private val TORCH_ON = listOf(
        "torch on", "flashlight on", "light on", "turn on the torch", "turn on the flashlight",
        "turn on torch", "turn on flashlight", "switch on the torch", "torch chalu", "टॉर्च चालू", "बत्ती जला",
    )
    private val TORCH_OFF = listOf(
        "torch off", "flashlight off", "light off", "turn off the torch", "turn off the flashlight",
        "turn off torch", "turn off flashlight", "switch off the torch", "torch band", "टॉर्च बंद", "बत्ती बुझा",
    )
    private val TIME = listOf(
        "what time is it", "what's the time", "whats the time", "tell me the time", "time now",
        "kitne baje", "time kya hai", "samay kya hai", "क्या समय है", "समय क्या है", "कितने बजे",
    )
    private val DATE = listOf(
        "what's the date", "whats the date", "what day is it", "aaj kya tarikh", "tarikh kya hai",
        "आज क्या तारीख है", "तारीख क्या है", "आज कौन सा दिन",
    )
    private val REMIND = listOf("remind me", "set a reminder", "set reminder", "रिमाइंडर", "याद दिला")
    private val LIST = listOf("list reminders", "my reminders", "रिमाइंडर बताओ", "मेरे रिमाइंडर")
    private val RELATIVE = Regex("(?:in|after|baad)\\s+\\d{1,3}\\s*(?:seconds?|secs?|minutes?|mins?|hours?|hrs?|मिनट|घंटे|सेकंड)")
    private val CLOCK = Regex("(?:सुबह|दोपहर|शाम|रात|subah|dopahar|shaam|raat)?\\s*\\d{1,2}(?::\\d{2})?\\s*(?:बजे|baje|a\\.?m\\.?|p\\.?m\\.?|am|pm)?")
    private val NOTIF = listOf(
        "read my notifications", "read notifications", "any notifications", "what notifications",
        "नोटिफिकेशन", "सूचनाएँ", "सूचनाएं",
    )
    private val STEPS = listOf("how many steps", "step count", "steps today", "my steps", "कितने कदम", "मेरे कदम", "आज के कदम")
    private val HEART = listOf("heart rate", "my pulse", "pulse rate", "हृदय गति", "दिल की धड़कन")
    private val OXYGEN = listOf("spo2", "sp o2", "blood oxygen", "oxygen level", "ऑक्सीजन", "स्पो2", "स्पॉ2")
    private val HEALTH_ALL = listOf("my health", "health reading", "watch health", "मेरी सेहत", "सेहत बताओ")
}
