package dev.piko.ui.format

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.Instant

/** Stable ASCII clock digits on every platform; segment handles also pad the hours. */
internal fun formatDurationMillis(millis: Long, padHours: Boolean = false): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    val minutes = (totalSeconds / 60 % 60).toString().padStart(2, '0')
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        val hourText = hours.toString().let { if (padHours) it.padStart(2, '0') else it }
        "$hourText:$minutes:$seconds"
    } else {
        "$minutes:$seconds"
    }
}

/** Playback speeds are small finite values; round HALF_UP, then omit trailing zeroes. */
internal fun formatPlaybackSpeed(speed: Float): String {
    require(speed.isFinite())
    val hundredths = floor(abs(speed.toDouble()) * 100 + 0.5).toLong()
    val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    val sign = if (speed < 0 && hundredths != 0L) "-" else ""
    return sign + (hundredths / 100).toString() + if (fraction.isEmpty()) "" else ".$fraction"
}

private fun LocalDate.chineseLabel(includeYear: Boolean): String =
    (if (includeYear) "${year.toString().padStart(4, '0')} 年 " else "") + "${month.number} 月 $day 日"

/** Share and membership dates retain the calendar date in the server's explicit offset. */
internal fun formatOffsetDate(value: String, includeYear: Boolean = false): String? = runCatching {
    DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET.parse(value).toLocalDateTime().date.chineseLabel(includeYear)
}.getOrNull()

/** History dates, unlike share dates, are displayed in the viewer's local time zone. */
internal fun formatLibraryEventTime(
    value: String,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String? = runCatching {
    val time = Instant.parse(value).toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date
    val date = time.date
    val clock = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    when {
        date == today -> "今天 $clock"
        date == today + DatePeriod(days = -1) -> "昨天 $clock"
        else -> date.chineseLabel(includeYear = date.year != today.year)
    }
}.getOrNull()

private val quotaZone = TimeZone.of("UTC+08:00")

internal fun monthlyQuotaResetLabel(now: Instant = Clock.System.now()): String {
    val today = now.toLocalDateTime(quotaZone).date
    val month = today.month.number
    val reset = LocalDate(today.year + month / 12, month % 12 + 1, 1)
    return "${today.daysUntil(reset)} 天后重置"
}

internal fun dailyQuotaResetLabel(now: Instant = Clock.System.now()): String {
    val tomorrow = now.toLocalDateTime(quotaZone).date + DatePeriod(days = 1)
    val hours = (tomorrow.atStartOfDayIn(quotaZone) - now).inWholeHours
    return if (hours < 1) "1 小时内重置" else "$hours 小时后重置"
}
