package com.aasra.companion.medicine

import android.content.Context
import com.aasra.data.AasraDatabase
import com.aasra.data.MedicineNote
import com.aasra.data.Reminder
import com.aasra.tools.ReminderTools

object MedicineSave {
    const val TIMED = "at a set time"
    val whenKeys = listOf(
        "before breakfast",
        "after breakfast",
        "before lunch",
        "after lunch",
        "afternoon",
        "evening",
        "before dinner",
        "after dinner",
        "night",
        "before sleep",
        TIMED,
    )

    suspend fun saveFamily(context: Context, name: String, whenKey: String, language: String): MedicineNote {
        val clean = name.trim()
        val note = MedicineNote(name = clean, timeText = whenKey, spoken = "$clean, $whenKey")
        AasraDatabase.get(context).medicines().upsert(note)
        cancelNamed(context, clean)
        schedule(context, note, language)
        return note
    }

    suspend fun remove(context: Context, note: MedicineNote) {
        cancelNamed(context, note.name)
        AasraDatabase.get(context).medicines().deleteById(note.id)
    }

    fun match(ocr: String, saved: List<MedicineNote>, packName: String = ""): MedicineNote? {
        val hay = normalize(ocr)
        val pack = normalize(packName.ifBlank { MedicineOcr.packName(ocr) })
        return saved.firstOrNull { note -> samePack(hay, pack, note.name) }
    }

    private fun samePack(hay: String, pack: String, savedName: String): Boolean {
        val keys = nameKeys(savedName)
        if (keys.isEmpty()) {
            val compact = normalize(savedName).filter { it.isLetter() || it in '\u0900'..'\u097F' }
            return compact.length >= 3 && word(compact).containsMatchIn(hay)
        }
        val brand = keys.maxBy { it.length }
        if (brand !in hay) return false
        if (pack.isBlank()) return keys.all { it in hay }
        return keys.any { it in pack }
    }

    private fun nameKeys(name: String): List<String> =
        normalize(name).split(' ').filter { token ->
            token.length >= 4 &&
                token.any { it.isLetter() || it in '\u0900'..'\u097F' } &&
                token !in GENERIC
        }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097f]+"), " ").trim()
    private fun word(token: String) = Regex("(?<![a-z0-9\\u0900-\\u097f])${Regex.escape(token)}(?![a-z0-9\\u0900-\\u097f])")

    private val GENERIC = setOf(
        "tablet", "tablets", "capsule", "capsules", "syrup", "drops", "cream",
        "film", "coated", "uncoated", "oral", "each", "strip", "pack",
        "dose", "dosage", "medicine", "contains",
    )

    private suspend fun schedule(context: Context, note: MedicineNote, language: String) {
        val tools = ReminderTools(context, AasraDatabase.get(context).reminders())
        val whenLabel = MedicineTiming.phrase(note.timeText, language).ifBlank { note.timeText }
        for (clock in MedicineTiming.reminderClocks(note.timeText)) {
            val whenMs = ReminderTools.parseSpokenTrigger(clock) ?: continue
            val text = if (language.startsWith("en")) {
                "Take ${note.name}, $whenLabel"
            } else {
                "${note.name} लें, $whenLabel"
            }
            tools.setReminder(text, whenMs, Reminder.REPEAT_DAILY)
        }
    }

    private suspend fun cancelNamed(context: Context, name: String) {
        val db = AasraDatabase.get(context)
        val tools = ReminderTools(context, db.reminders())
        db.reminders().all().filter { it.text.contains(name, ignoreCase = true) }.forEach {
            tools.cancelReminder(it.id)
        }
    }
}
