package ir.unimeal.android

import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SamadTest {
    private fun fixture(): String =
        javaClass.getResourceAsStream("/reserve_week.html")!!.readBytes().toString(Charsets.UTF_8)

    /** The saved week has every dinner reserved; this reopens the dinner of row [n] as the page looks before it is ticked. */
    private fun reopen(html: String, n: Int): String {
        var s = html
        val tag = Regex("<input[^>]*name=\"userWeekReserves\\[$n\\]\\.selected\"[^>]*>", RegexOption.DOT_MATCHES_ALL).find(s)!!.value
        s = s.replace(tag, tag.replace(Regex("\\schecked\\s"), " ").replace(Regex("\\sdisabled\\s"), " "))
        s = s.replace(Regex("(name=\"userWeekReserves\\[$n\\]\\.id\"\\s+value=\")\\d+\""), "$1\"")
        return s
    }

    @Test fun parsesTheWeek() {
        val p = SamadParser.parse(fixture())
        assertNotNull(p)
        assertEquals(5, p.rows.size)
        assertEquals(1_390_000L, p.credit)
        assertEquals("12", p.selfId)
        assertEquals("1795260092993", p.weekStart)
        assertEquals("1", p.mealId(Meal.DINNER))
        assertEquals("3", p.mealId(Meal.LUNCH))
        assertEquals("2", p.mealId(Meal.BREAKFAST))
        assertTrue(p.selfOptions.any { it.first == "12" && it.second.contains("تاک دامپزشکی") })
        assertEquals(250_000L, p.rows[0].price)
        assertEquals("غذای تاک متوسط هزینه | غذای تاک", p.rows[0].label)
        assertEquals("2026-11-21", p.rows[0].programDate)
    }

    @Test fun reservedRowsAreTakenAndLockedOnesDisabled() {
        val p = SamadParser.parse(fixture())!!
        assertTrue(p.rows.all { it.taken })
        assertTrue(p.rows[0].disabled && p.rows[1].disabled && p.rows[2].disabled)
        assertFalse(p.rows[3].disabled)
        assertTrue(p.rows[3].saved)
    }

    @Test fun clientTemplateIsNotAServerMessage() {
        assertTrue(SamadParser.parse(fixture())!!.messages.isEmpty())
    }

    @Test fun detectsPages() {
        val html = fixture()
        assertTrue(SamadParser.isReservePage(html))
        assertFalse(SamadParser.looksLikeLogin(html))
        val login = """<form action="/j_security_check"><input type="hidden" name="_csrf" value="abc-123"/><input type="password" name="password"/></form>"""
        assertTrue(SamadParser.looksLikeLogin(login))
        assertEquals("abc-123", SamadParser.csrf(login))
        assertNull(SamadParser.parse(login))
    }

    @Test fun confirmBodyMatchesTheBrowsersBody() {
        val page = SamadParser.parse(reopen(fixture(), 3))!!
        assertFalse(page.rows[3].taken)
        val body = SamadForm.build(page, "doReserve", setOf(3))
        // The keys, in order, of the request a real browser sent when the dinner of row 3 was ticked.
        val expected = listOf("weekStartDateTime", "remainCredit", "method:doReserve", "_csrf", "selfChangeReserveId", "weekStartDateTimeAjx", "freeRestaurant", "selectedSelfDefId") +
            (0..4).flatMap { i ->
                listOf("selected", "selectedCount", "id", "programId", "mealTypeId", "programDateTime", "selfId", "foodTypeId", "foodId", "priorReserveDateStr", "freeFoodSelected")
                    .map { "userWeekReserves[$i].$it" }
            } + "_csrf"
        assertEquals(expected, body.map { it.first })
        assertEquals("", body.first { it.first == "userWeekReserves[3].id" }.second)
        assertEquals("true", body.first { it.first == "userWeekReserves[3].selected" }.second)
        assertEquals("9607416", body.first { it.first == "userWeekReserves[4].id" }.second)
        assertEquals("12", body.first { it.first == "selectedSelfDefId" }.second)
    }

    @Test fun untouchedRowsAreNeverUnticked() {
        val page = SamadParser.parse(fixture())!!
        val body = SamadForm.build(page, "showNextWeek")
        assertEquals(5, body.count { it.first.endsWith(".selected") && it.second == "true" })
    }

    @Test fun plansTheOpenDinner() {
        val page = SamadParser.parse(reopen(fixture(), 3))!!
        val plan = Planner.plan(page, Wanted(meals = setOf(Meal.DINNER)))
        assertEquals(1, plan.toReserve.size)
        assertEquals(3, plan.toReserve[0].second.index)
        assertEquals(4, plan.alreadyReserved.size)
        assertEquals(250_000L, plan.cost)
        assertFalse(plan.menuNotPublished)
    }

    @Test fun lunchWithoutOptionsMeansMenuNotPublished() {
        val plan = Planner.plan(SamadParser.parse(fixture())!!, Wanted(meals = setOf(Meal.LUNCH)))
        assertTrue(plan.menuNotPublished)
        assertTrue(plan.toReserve.isEmpty())
    }

    @Test fun stopsWhenCreditIsTooLow() {
        val page = SamadParser.parse(reopen(fixture(), 3))!!.copy(credit = 100_000L)
        val plan = Planner.plan(page, Wanted(meals = setOf(Meal.DINNER)))
        assertTrue(plan.toReserve.isEmpty())
        assertEquals(1, plan.notEnoughCredit.size)
    }

    @Test fun respectsTheSpendLimit() {
        val page = SamadParser.parse(reopen(reopen(fixture(), 3), 4))!!
        val plan = Planner.plan(page, Wanted(meals = setOf(Meal.DINNER), maxSpend = 300_000L))
        assertEquals(1, plan.toReserve.size)
        assertEquals(1, plan.overBudget.size)
    }

    @Test fun skipsAvoidedFoodAndClosedSlots() {
        val open = SamadParser.parse(reopen(fixture(), 3))!!
        val avoided = Planner.plan(open, Wanted(meals = setOf(Meal.DINNER), avoid = listOf("تاک")))
        assertTrue(avoided.toReserve.isEmpty())
        assertEquals(1, avoided.unavailable.size)
        val locked = open.copy(rows = open.rows.map { if (it.index == 3) it.copy(disabled = true) else it })
        assertEquals(1, Planner.plan(locked, Wanted(meals = setOf(Meal.DINNER))).unavailable.size)
    }

    @Test fun onlyWantedWeekdays() {
        val page = SamadParser.parse(reopen(fixture(), 3))!!
        // Row 3 is 2026-11-24 (Tuesday).
        val tue = Planner.plan(page, Wanted(setOf(Meal.DINNER), days = setOf(DayOfWeek.TUESDAY)))
        assertEquals(1, tue.toReserve.size)
        assertEquals(0, tue.alreadyReserved.size)
        assertEquals(0, Planner.plan(page, Wanted(setOf(Meal.DINNER), days = setOf(DayOfWeek.FRIDAY))).toReserve.size)
    }

    @Test fun verifiesWhatTheServerSaved() {
        val before = SamadParser.parse(reopen(fixture(), 3))!!
        val picked = listOf(before.rows[3])
        val ok = SamadParser.parse(fixture())!!
        assertEquals(1, Planner.verify(ok, picked).first.size)
        assertEquals(1, Planner.verify(before, picked).second.size)
    }
}
