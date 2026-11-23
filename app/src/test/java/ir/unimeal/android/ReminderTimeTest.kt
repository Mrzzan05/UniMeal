package ir.unimeal.android

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderTimeTest {
    private val z = ZoneId.of("Asia/Tehran")
    private fun at(s: String) = ZonedDateTime.parse(s + "+03:30[Asia/Tehran]")

    @Test fun laterThisWeek() {
        // Monday 2026-11-23 11:00 -> Wednesday (3) 20:00 the same week.
        assertEquals(at("2026-11-25T20:00:00"), nextReminder(at("2026-11-23T11:00:00"), 3, 20, 0))
    }

    @Test fun sameDayBeforeAndAfterTheTime() {
        assertEquals(at("2026-11-25T20:00:00"), nextReminder(at("2026-11-25T19:59:00"), 3, 20, 0))
        assertEquals(at("2026-12-02T20:00:00"), nextReminder(at("2026-11-25T20:00:00"), 3, 20, 0))
        assertEquals(at("2026-12-02T20:00:00"), nextReminder(at("2026-11-25T21:00:00"), 3, 20, 0))
    }

    @Test fun dayAlreadyPassedGoesToNextWeek() {
        assertEquals(at("2026-12-02T08:30:00"), nextReminder(at("2026-11-27T10:00:00"), 3, 8, 30))
    }
}
