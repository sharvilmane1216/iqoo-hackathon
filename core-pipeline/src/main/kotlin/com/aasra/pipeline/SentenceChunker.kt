package com.aasra.pipeline

/**
 * Sentence chunker feeding the TTS queue (PLAN 4.5/4.6).
 *
 * Boundaries: Latin [.?!] plus the Hindi danda [।] and common elderly-speech
 * abbreviations guarded (Mr./Mrs./Dr./No./डॉ०). A sentence is emitted only past
 * [minSentenceChars] so "Haan." doesn't flush a 5-char TTS job, and [push] also
 * force-emits past [maxBufferedChars] so a run-on LLM turn can't stall speech.
 * Pure Kotlin, JVM-testable.
 */
class SentenceChunker(
    private val minSentenceChars: Int = 8,
    private val maxBufferedChars: Int = 80,
) : TextChunker {
    private val buf = StringBuilder()

    private val abbreviations = setOf(
        "mr.", "mrs.", "ms.", "dr.", "no.", "st.", "vs.",
        "डॉ०", "श्री", "श्रीमती",
    )

    override fun push(token: String): List<String> {
        buf.append(token)
        val out = ArrayList<String>()
        var boundary = findBoundary()
        while (boundary >= 0) {
            val sentence = buf.substring(0, boundary + 1).trim()
            buf.delete(0, boundary + 1)
            if (sentence.length >= minSentenceChars) {
                out.add(sentence)
            } else {
                // Too short to speak alone (e.g. "Ji."): keep accumulating, but
                // restore a space so words don't fuse.
                buf.insert(0, "$sentence ")
                break
            }
            boundary = findBoundary()
        }
        if (out.isEmpty() && buf.length >= maxBufferedChars) {
            // Run-on turn: speak what we have rather than stalling.
            val forced = buf.toString().trim()
            buf.clear()
            if (forced.isNotEmpty()) out.add(forced)
        }
        return out
    }

    override fun flush(): List<String> {
        val tail = buf.toString().trim()
        buf.clear()
        return if (tail.isEmpty()) emptyList() else listOf(tail)
    }

    override fun reset() {
        buf.clear()
    }

    /** Index of the last char of the first complete sentence, or -1. */
    private fun findBoundary(): Int {
        var i = 0
        while (i < buf.length) {
            val c = buf[i]
            if (c == '.' || c == '?' || c == '!' || c == '।' || c == ',' || c == ';' || c == '،') {
                val next = buf.getOrNull(i + 1)
                if (next != null && next.isDigit()) {
                    i++
                    continue
                }
                if ((c == ',' || c == ';' || c == '،') && next != null && !next.isWhitespace()) {
                    i++
                    continue
                }
                val word = currentWord(i)
                if (c == '.' && abbreviations.contains(word.lowercase())) {
                    i++
                    continue
                }
                if (c == '.' && next == '.') {
                    i++
                    continue
                }
                return i
            }
            i++
        }
        return -1
    }

    private fun currentWord(dotIndex: Int): String {
        var s = dotIndex
        while (s > 0 && !buf[s - 1].isWhitespace()) s--
        return buf.substring(s, dotIndex + 1)
    }
}
