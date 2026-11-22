package ir.unimeal.android

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunnerTest {
    private val full = javaClass.getResourceAsStream("/reserve_week.html")!!.readBytes().toString(Charsets.UTF_8)

    private fun reopen(html: String, n: Int): String {
        var s = html
        val tag = Regex("<input[^>]*name=\"userWeekReserves\\[$n\\]\\.selected\"[^>]*>", RegexOption.DOT_MATCHES_ALL).find(s)!!.value
        s = s.replace(tag, tag.replace(Regex("\\schecked\\s"), " ").replace(Regex("\\sdisabled\\s"), " "))
        return s.replace(Regex("(name=\"userWeekReserves\\[$n\\]\\.id\"\\s+value=\")\\d+\""), "$1\"")
    }


    private class Fake(val before: String, val after: String, val loginOk: Boolean = true) : Http {
        val posts = ArrayList<Pair<String, List<Pair<String, String>>>>()
        var confirmed = false
        var cert = false
        var switchedTo: String? = null
        var startSelf: String? = null
        private fun page(): String {
            val p = if (confirmed) after else before
            val self = startSelf
            return if (self != null && switchedTo == null) p.replace("value=\"12\" id=\"selfHiddenId\"", "value=\"$self\" id=\"selfHiddenId\"") else p
        }
        override fun get(url: String): String {
            if (cert) throw CertProblem("bad chain")
            return when (url) {
                Urls.LOGIN_PAGE -> """<form><input type="hidden" name="_csrf" value="tok-1"/><input name="password" type="password"/></form>"""
                Urls.PANEL, Urls.RESERVE -> if (loginOk) page() else """<form><input type="hidden" name="_csrf" value="x"/><input name="password" type="password"/></form>"""
                else -> throw NetProblem("unexpected $url")
            }
        }
        override fun post(url: String, form: List<Pair<String, String>>): String {
            posts += url to form
            if (url == Urls.RESERVE) {
                if (form.any { it.first == "method:showPanel" }) {
                    switchedTo = form.first { it.first == "selectedSelfDefId" }.second
                    return page()
                }
                if (form.any { it.first == "method:doReserve" }) confirmed = true
                // The next week looks like the same page with another week start.
                if (form.any { it.first == "method:showNextWeek" }) return page().replace("1795260092993", "1795864892993")
                return page()
            }
            return ""
        }
    }

    private val today = LocalDate.parse("2026-11-22")
    private fun runner(h: Http, preview: Boolean, meals: Set<Meal> = setOf(Meal.DINNER), maxWeeks: Int = 1) =
        Runner(h, "00000000000", "pw", Wanted(meals), "12", preview, today, maxWeeks)

    @Test fun previewNeverPostsAConfirm() {
        val h = Fake(reopen(full, 3), full)
        val o = runner(h, preview = true).pass()
        assertEquals(Status.OK, o.status)
        assertEquals(1, o.wouldReserve.size)
        assertTrue(o.reserved.isEmpty())
        assertTrue(h.posts.none { p -> p.second.any { it.first == "method:doReserve" } })
    }

    @Test fun reservesAndVerifies() {
        val h = Fake(reopen(full, 3), full)
        val o = runner(h, preview = false).pass()
        assertEquals(Status.OK, o.status)
        assertEquals(1, o.reserved.size)
        assertTrue(o.failed.isEmpty())
        val body = h.posts.last { p -> p.second.any { it.first == "method:doReserve" } }.second
        assertEquals("true", body.first { it.first == "userWeekReserves[3].selected" }.second)
    }

    @Test fun serverNotSavingIsReportedAsFailed() {
        val h = Fake(reopen(full, 3), reopen(full, 3))
        val o = runner(h, preview = false).pass()
        assertEquals(1, o.failed.size)
        assertTrue(o.reserved.isEmpty())
    }

    @Test fun wrongPasswordStopsAtOnce() {
        val h = Fake(full, full, loginOk = false)
        assertEquals(Status.LOGIN_FAILED, runner(h, false).pass().status)
    }

    @Test fun badCertificateStops() {
        val h = Fake(full, full).also { it.cert = true }
        assertEquals(Status.CERT_PROBLEM, runner(h, false).pass().status)
    }

    @Test fun switchesToTheWantedSelfLikeTheDropDown() {
        val h = Fake(reopen(full, 3), full).also { it.startSelf = "1" }
        val o = runner(h, preview = false).pass()
        assertEquals(Status.OK, o.status)
        assertEquals("12", h.switchedTo)
        assertEquals(1, o.reserved.size)
    }

    @Test fun countsPublishedWeeks() {
        val o = runner(Fake(full, full), false, maxWeeks = 1).pass()
        assertEquals(1, o.weeksPublished)
        val l = runner(Fake(full, full), false, meals = setOf(Meal.LUNCH)).pass()
        assertEquals(0, l.weeksPublished)
    }

    @Test fun unknownSelfIsNeverReservedAt() {
        val h = Fake(reopen(full, 3), full)
        val o = Runner(h, "u", "p", Wanted(setOf(Meal.DINNER)), "99", false, today).pass()
        assertEquals(Status.WRONG_SELF, o.status)
        assertTrue(h.posts.none { p -> p.second.any { it.first == "method:doReserve" } })
    }

    @Test fun unpublishedMenuMeansWaiting() {
        val o = runner(Fake(full, full), false, meals = setOf(Meal.LUNCH)).pass()
        assertEquals(Status.OK, o.status)
        assertTrue(o.waiting)
    }

    @Test fun allReservedIsDone() {
        val o = runner(Fake(full, full), false).pass()
        assertEquals(Status.OK, o.status)
        assertFalse(o.waiting)
        assertTrue(o.reserved.isEmpty() && o.failed.isEmpty())
        assertEquals(4, o.alreadyReserved) // 11-21 is in the past and ignored
    }

    @Test fun movesToTheNextWeekAndStopsWhenItDoesNotChange() {
        val h = Fake(full, full)
        runner(h, false, maxWeeks = 3).pass()
        assertEquals(2, h.posts.count { p -> p.second.any { it.first == "method:showNextWeek" } })
    }

    @Test fun lowCreditIsReportedNotRetried() {
        val low = reopen(full, 3).replace("1390000", "1000")
        val o = runner(Fake(low, full), false).pass()
        assertEquals(1, o.poor.size)
        assertTrue(o.reserved.isEmpty())
    }
}
