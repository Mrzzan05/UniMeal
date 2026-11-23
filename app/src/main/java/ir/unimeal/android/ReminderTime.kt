package ir.unimeal.android

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** The next time a weekly reminder is due: [day] is ISO (1 = Monday … 7 = Sunday). Pure, so it is unit-tested. */
fun nextReminder(now: ZonedDateTime, day: Int, hour: Int, minute: Int): ZonedDateTime {
    val d = day.coerceIn(1, 7)
    var date = now.toLocalDate()
    val ahead = (d - date.dayOfWeek.value + 7) % 7
    date = date.plusDays(ahead.toLong())
    var t = date.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(now.zone)
    if (!t.isAfter(now.truncatedTo(ChronoUnit.SECONDS))) t = t.toLocalDate().plusWeeks(1).atTime(t.toLocalTime()).atZone(now.zone)
    return t
}
