package com.aasra.tools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * get_time, get_date, set_volume, flashlight (PLAN 6.1). All fully offline.
 * Pass "hi" for Hindi phrasing, anything else for English.
 */
class SystemTools(private val context: Context) {

    fun getTime(lang: String = "en"): ToolResult {
        val now = LocalTime.now()
        val h24 = now.hour % 12
        val h12 = if (h24 == 0) 12 else h24
        val spoken = if (lang.startsWith("hi")) {
            "अभी ${hindiPeriod(now.hour)} के $h12 बजकर ${now.minute} मिनट हुए हैं।"
        } else {
            val ampm = if (now.hour < 12) "in the morning" else if (now.hour < 17) "in the afternoon" else "in the evening"
            "It is $h12:${now.minute.toString().padStart(2, '0')} $ampm."
        }
        return ToolResult(true, spoken)
    }

    fun getDate(lang: String = "en"): ToolResult {
        val today = LocalDate.now()
        val spoken = if (lang.startsWith("hi")) {
            val day = HINDI_DAYS[today.dayOfWeek.value % 7]
            "आज $day है, ${today.dayOfMonth} ${HINDI_MONTHS[today.monthValue - 1]} ${today.year}।"
        } else {
            val day = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            val month = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            "Today is $day, $month ${today.dayOfMonth}, ${today.year}."
        }
        return ToolResult(true, spoken)
    }

    /** [level] 0 (silent) .. 10 (loudest); coerced. */
    fun setVolume(level: Int, lang: String = "en"): ToolResult {
        val am = context.getSystemService(AudioManager::class.java)
            ?: return ToolResult(false, if (lang.startsWith("hi")) "आवाज़ नहीं बदल सकी।" else "I could not reach the volume controls on this phone.")
        val max = am.getStreamMaxVolume(volumeStream(am))
        return applyVolume(am, (level.coerceIn(0, 10) / 10f * max).toInt(), lang)
    }

    fun adjustVolume(steps: Int, lang: String = "en"): ToolResult {
        val am = context.getSystemService(AudioManager::class.java)
            ?: return ToolResult(false, if (lang.startsWith("hi")) "आवाज़ नहीं बदल सकी।" else "I could not reach the volume controls on this phone.")
        val stream = volumeStream(am)
        val max = am.getStreamMaxVolume(stream)
        val step = (max / 10).coerceAtLeast(1)
        return applyVolume(am, am.getStreamVolume(stream) + steps * step, lang)
    }

    private fun volumeStream(am: AudioManager): Int =
        if (android.os.Build.VERSION.SDK_INT >= 26) AudioManager.STREAM_ACCESSIBILITY else AudioManager.STREAM_MUSIC

    private fun applyVolume(am: AudioManager, index: Int, lang: String): ToolResult = try {
        val streams = buildList {
            add(volumeStream(am))
            add(AudioManager.STREAM_MUSIC)
        }.distinct()
        var shown = 0
        for (stream in streams) {
            val max = am.getStreamMaxVolume(stream)
            if (max <= 0) continue
            val idx = index.coerceIn(0, max)
            am.setStreamVolume(stream, idx, if (shown == 0) AudioManager.FLAG_SHOW_UI else 0)
            if (shown == 0) shown = if (max == 0) 0 else ((idx * 10f) / max).toInt().coerceIn(0, 10)
        }
        ToolResult(true, if (lang.startsWith("hi")) {
            if (shown == 0) "आवाज़ बंद है।" else "आवाज़ $shown है।"
        } else {
            if (shown == 0) "Sound is off." else "Volume is $shown out of 10."
        })
    } catch (_: SecurityException) {
        ToolResult(false, if (lang.startsWith("hi")) "फ़ोन ने आवाज़ बदलने नहीं दी। साइड बटन दबाएँ।" else "Your phone did not let me change the volume. Please use the side buttons.")
    } catch (_: Exception) {
        ToolResult(false, if (lang.startsWith("hi")) "आवाज़ नहीं बदल सकी। साइड बटन दबाएँ।" else "I could not change the volume. Please use the side buttons.")
    }

    fun setFlashlight(on: Boolean, lang: String = "en"): ToolResult {
        val hi = lang.startsWith("hi")
        val cm = context.getSystemService(CameraManager::class.java)
            ?: return ToolResult(false, if (hi) "इस फ़ोन में टॉर्च नहीं है।" else "This phone has no torch I can switch.")
        return try {
            val id = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ToolResult(false, if (hi) "इस फ़ोन में टॉर्च नहीं है।" else "This phone has no torch I can switch.")
            cm.setTorchMode(id, on)
            ToolResult(true, if (hi) {
                if (on) "टॉर्च चालू है।" else "टॉर्च बंद है।"
            } else {
                if (on) "Torch is on." else "Torch is off."
            })
        } catch (_: SecurityException) {
            ToolResult(false, if (hi) "टॉर्च के लिए कैमरा अनुमति चाहिए।" else "I need the camera permission to use the torch. Please allow it in Settings.")
        } catch (_: Exception) {
            ToolResult(false, if (hi) "टॉर्च नहीं चल सकी।" else "I could not reach the torch on this phone.")
        }
    }

    companion object {
        private val HINDI_DAYS = arrayOf("रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार")
        private val HINDI_MONTHS = arrayOf(
            "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
            "जुलाई", "अगस्त", "सितंबर", "अक्तूबर", "नवंबर", "दिसंबर",
        )

        private fun hindiPeriod(hour: Int): String = when (hour) {
            in 4..11 -> "सुबह"
            in 12..16 -> "दोपहर"
            in 17..20 -> "शाम"
            else -> "रात"
        }
    }
}
