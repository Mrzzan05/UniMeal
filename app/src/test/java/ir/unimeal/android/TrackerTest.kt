package ir.unimeal.android

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackerTest {
    private fun ok(published: Int, waiting: Boolean, failed: List<String> = emptyList(), poor: List<String> = emptyList()) =
        Outcome(Status.OK, failed = failed, poor = poor, waiting = waiting, weeksPublished = published)

    @Test fun keepsWaitingUntilANewWeekAppearsThenStops() {
        val t = AccountTracker(preview = false, waitNext = true)
        assertEquals(Verdict.KEEP_GOING, t.next(ok(1, true)))
        assertEquals(Verdict.KEEP_GOING, t.next(ok(1, true)))
        assertEquals(Verdict.DONE, t.next(ok(2, true)))
    }

    @Test fun stopsAtOnceWhenNotWaiting() {
        assertEquals(Verdict.DONE, AccountTracker(false, waitNext = false).next(ok(1, true)))
        assertEquals(Verdict.DONE, AccountTracker(false, true).next(ok(2, false)))
    }

    @Test fun problemsStop() {
        assertEquals(Verdict.POOR, AccountTracker(false, true).next(ok(1, true, poor = listOf("x"))))
        assertEquals(Verdict.LOGIN, AccountTracker(false, true).next(Outcome(Status.LOGIN_FAILED)))
        assertEquals(Verdict.SELF, AccountTracker(false, true).next(Outcome(Status.WRONG_SELF)))
    }

    @Test fun previewStopsAfterOnePass() {
        assertEquals(Verdict.PREVIEW_DONE, AccountTracker(true, true).next(ok(1, true)))
    }

    @Test fun repeatedFailuresGiveUp() {
        val t = AccountTracker(false, true)
        repeat(4) { assertEquals(Verdict.KEEP_GOING, t.next(ok(1, false, failed = listOf("a")))) }
        assertEquals(Verdict.FAILED, t.next(ok(1, false, failed = listOf("a"))))
    }

    @Test fun networkRetriesThenGivesUp() {
        val t = AccountTracker(false, true)
        repeat(19) { assertEquals(Verdict.KEEP_GOING, t.next(Outcome(Status.NETWORK))) }
        assertEquals(Verdict.NET, t.next(Outcome(Status.NETWORK)))
    }
}
