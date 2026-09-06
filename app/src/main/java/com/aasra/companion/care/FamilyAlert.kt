package com.aasra.companion.care

import android.content.Context
import com.aasra.data.AasraDatabase
import com.aasra.data.normalizePhoneNumber
import com.aasra.tools.SmsTools

/** SMS the primary emergency contact when the user reports feeling unwell. */
object FamilyAlert {
    @Volatile var userName: String = ""

    suspend fun notify(context: Context, heard: String, language: String) {
        val to = AasraDatabase.get(context).contacts().emergency()
            .firstOrNull { normalizePhoneNumber(it.phone) != null } ?: return
        val who = userName.trim().ifBlank {
            if (language.startsWith("hi")) "परिवार का सदस्य" else "A family member"
        }
        val problem = CareCards.problemName(heard, language)
        val said = heard.trim().take(70)
        val body = if (language.startsWith("hi")) {
            "आसरा: $who की तबीयत ठीक नहीं है ($problem)। उन्होंने कहा: $said"
        } else {
            "Aasra: $who is not feeling well ($problem). They said: $said"
        }
        SmsTools(context).sendDirect(to.phone, body)
    }
}
