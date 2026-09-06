package com.aasra.companion.health

import java.time.Duration
import java.time.Instant

internal enum class HealthProvider { CHECKING, AVAILABLE, INSTALL_REQUIRED, UNAVAILABLE, ERROR }

internal sealed interface HealthMetric<out T> {
    data object PermissionRequired : HealthMetric<Nothing>
    data object Revoked : HealthMetric<Nothing>
    data object NoData : HealthMetric<Nothing>
    data object Error : HealthMetric<Nothing>
    data class Data<T>(val value: T) : HealthMetric<T>
}

internal data class StepsSummary(
    val count: Long,
    val origins: Set<String>,
    val lastRecordUpdate: Instant?,
)

internal data class HeartReading(
    val beatsPerMinute: Long,
    val recordedAt: Instant,
    val origin: String,
    val lastRecordUpdate: Instant,
)

internal data class OxygenReading(
    val percent: Double,
    val recordedAt: Instant,
    val origin: String,
    val lastRecordUpdate: Instant,
)

internal data class HealthSnapshot(
    val provider: HealthProvider = HealthProvider.CHECKING,
    val steps: HealthMetric<StepsSummary> = HealthMetric.PermissionRequired,
    val heartRate: HealthMetric<HeartReading> = HealthMetric.PermissionRequired,
    val oxygen: HealthMetric<OxygenReading> = HealthMetric.PermissionRequired,
    val checkedAt: Instant? = null,
)

internal fun missingHealthPermission(previouslyGranted: Boolean): HealthMetric<Nothing> =
    if (previouslyGranted) HealthMetric.Revoked else HealthMetric.PermissionRequired

// This describes data age, never the user's medical condition.
internal enum class HealthFreshness { RECENT, OLDER, CLOCK_MISMATCH }

internal fun healthFreshness(recordedAt: Instant, now: Instant): HealthFreshness = when {
    recordedAt > now -> HealthFreshness.CLOCK_MISMATCH
    Duration.between(recordedAt, now) <= Duration.ofMinutes(15) -> HealthFreshness.RECENT
    else -> HealthFreshness.OLDER
}

/** Record start order is not sample order; a page can contain overlapping series. */
internal fun latestHeartReading(
    readings: Sequence<HeartReading>,
    start: Instant,
    end: Instant,
): HeartReading? = readings.filter { it.recordedAt >= start && it.recordedAt < end }
    .maxWithOrNull(compareBy<HeartReading> { it.recordedAt }.thenBy { it.lastRecordUpdate })

internal fun HealthSnapshot.spoken(kind: String, lang: String): String {
    val hi = lang.startsWith("hi")
    val need = if (hi) "सेहत स्क्रीन पर Health Connect की अनुमति दें।" else "Allow Health Connect on the Health screen, then ask again."
    val fail = if (hi) "यह आँकड़ा अभी नहीं पढ़ा जा सका।" else "I could not read that figure just now."
    return when (kind) {
        "steps" -> speak(steps, need, if (hi) "आज के कदम Health Connect में नहीं हैं।" else "Today's steps are not in Health Connect.", fail) {
            if (hi) "आज ${it.count} कदम दर्ज हैं।" else "Today's steps are ${it.count}."
        }
        "heart" -> speak(heartRate, need, if (hi) "हृदय गति Health Connect में नहीं है।" else "Heart rate is not in Health Connect.", fail) {
            if (hi) "हृदय गति ${it.beatsPerMinute} धड़कन प्रति मिनट है।" else "Heart rate is ${it.beatsPerMinute} beats per minute."
        }
        "oxygen" -> speak(oxygen, need, if (hi) "SpO2 Health Connect में नहीं है।" else "SpO2 is not in Health Connect.", fail) {
            val n = if (it.percent == it.percent.toLong().toDouble()) it.percent.toLong().toString() else it.percent.toString()
            if (hi) "स्पॉ2 $n प्रतिशत है।" else "SpO2 is $n percent."
        }
        "all" -> listOf(spoken("steps", lang), spoken("heart", lang), spoken("oxygen", lang)).joinToString(" ")
        else -> fail
    }
}

private fun <T> speak(
    metric: HealthMetric<T>,
    need: String,
    none: String,
    fail: String,
    ok: (T) -> String,
): String = when (metric) {
    is HealthMetric.Data -> ok(metric.value)
    HealthMetric.PermissionRequired, HealthMetric.Revoked -> need
    HealthMetric.NoData -> none
    HealthMetric.Error -> fail
}
