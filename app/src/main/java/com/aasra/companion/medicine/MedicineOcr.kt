package com.aasra.companion.medicine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** On-device OCR for printed packs, plus upright JPEG helpers for the doctor's note. */
object MedicineOcr {
    fun upright(image: ImageProxy): Bitmap {
        val raw = image.toBitmap()
        val deg = image.imageInfo.rotationDegrees
        if (deg == 0) return raw
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    fun decode(context: Context, uri: Uri): Bitmap? {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val deg = context.contentResolver.openInputStream(uri)?.use { input ->
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
        if (deg == 0) return bmp
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
        if (rotated !== bmp) bmp.recycle()
        return rotated
    }

    fun jpeg(bitmap: Bitmap, max: Int = 1800, quality: Int = 85): ByteArray {
        val scaled = scale(bitmap, max)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bitmap) scaled.recycle()
        return out.toByteArray()
    }

    data class PackText(val text: String, val lines: List<PackLine> = emptyList())
    data class PackLine(val text: String, val area: Int)

    suspend fun read(bitmap: Bitmap): PackText = withContext(Dispatchers.Default) {
        val scaled = scale(bitmap, 2000)
        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val hindi = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        try {
            val image = InputImage.fromBitmap(scaled, 0)
            val areas = linkedMapOf<String, Int>()
            addLines(areas, latin.process(image).await())
            addLines(areas, hindi.process(image).await())
            PackText(areas.keys.joinToString("\n"), areas.map { PackLine(it.key, it.value) })
        } finally {
            latin.close()
            hindi.close()
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    fun packName(ocr: String): String = packName(
        PackText(ocr, ocr.lineSequence().map { PackLine(it.trim(), it.length) }.toList()),
    )

    fun packName(pack: PackText): String {
        val picked = pack.lines.mapNotNull { line ->
            val t = line.text.replace(Regex("\\s+"), " ").trim()
            if (!usableName(t)) return@mapNotNull null
            var score = line.area
            val words = t.split(' ')
            if (words.size <= 2) score = (score * 5) / 4
            if (t.any { it == '-' || it.isDigit() }) score = (score * 11) / 10
            t to score
        }.maxByOrNull { it.second }?.first ?: return ""
        return picked.replace(Regex("""(?i)\s+(tablets?|capsules?|syrup|suspension)$"""), "").trim()
    }

    private fun addLines(areas: MutableMap<String, Int>, result: Text) {
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val t = line.text.replace(Regex("\\s+"), " ").trim()
                if (t.isBlank()) continue
                val box = line.boundingBox
                val area = if (box != null) box.width() * box.height() else t.length * 12
                areas[t] = maxOf(areas[t] ?: 0, area)
            }
        }
    }

    private fun usableName(t: String): Boolean {
        if (t.length !in 3..36) return false
        if (t.split(' ').size > 4) return false
        if (SKIP.containsMatchIn(t) || GENERIC_LINE.matches(t)) return false
        val letters = t.count { it.isLetter() || it in '\u0900'..'\u097F' }
        if (letters < 3 || t.count(Char::isDigit) * 2 >= t.length) return false
        val tokens = t.lowercase().split(Regex("[^a-z0-9\\u0900-\\u097f]+")).filter { it.isNotEmpty() }
        return tokens.any { it.length >= 3 && it !in GENERIC_WORD }
    }

    private val SKIP = Regex(
        """(?i)\b(mfg|mfr|mrp|exp|batch|b\.?no|licen[cs]e|gst|fssai|manufactur|marketed|address|composition|ingredient|warning|store|keep|direction|dosage|net wt|schedule|limited|pvt|private|ltd)\b""",
    )
    private val GENERIC_LINE = Regex("""(?i)^(tablets?|capsules?|syrup|ointment|cream|drops|suspension)$""")
    private val GENERIC_WORD = setOf(
        "tablet", "tablets", "capsule", "capsules", "syrup", "drops", "cream",
        "film", "coated", "uncoated", "oral", "each", "strip", "pack",
        "dose", "dosage", "medicine", "contains", "mg", "mcg", "ml",
    )

    private fun scale(bitmap: Bitmap, max: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= max) return bitmap
        val s = max.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
    }
}
