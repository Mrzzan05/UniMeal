package ir.unimeal.android

import java.time.LocalDate

/** Minimal HTTP surface the runner needs; the real one is OkHttp, tests use a fake server. */
interface Http {
    /** Returns the body of the final page. Throws [CertProblem] or [NetProblem]. */
    fun get(url: String): String
    fun post(url: String, form: List<Pair<String, String>>): String
}

/** The server's certificate was not trusted: we stop and never talk to it (credentials stay safe). */
class CertProblem(message: String) : Exception(message)
class NetProblem(message: String) : Exception(message)

object Urls {
    const val HOST = "nds.urmia.ac.ir"
    const val LOGIN_PAGE = "https://$HOST/index.rose"
    const val LOGIN_POST = "https://$HOST/j_security_check"
    const val PANEL = "https://$HOST/nurture/user/multi/reserve/showPanel.rose"
    const val RESERVE = "https://$HOST/nurture/user/multi/reserve/reserve.rose"
}

enum class Status { OK, LOGIN_FAILED, CERT_PROBLEM, NETWORK, UNEXPECTED, WRONG_SELF }

data class Outcome(
    val status: Status,
    val detail: String = "",
    val reserved: List<String> = emptyList(),
    /** Preview mode: what would have been reserved. */
    val wouldReserve: List<String> = emptyList(),
    /** Ticked and confirmed, but the server did not save them (probably sold out in the meantime). */
    val failed: List<String> = emptyList(),
    val poor: List<String> = emptyList(),
    val overBudget: List<String> = emptyList(),
    val unavailable: List<String> = emptyList(),
    val alreadyReserved: Int = 0,
    /** The first week without a published menu was reached: more can appear later. */
    val waiting: Boolean = false,
    /** How many weeks (starting with the current one) already have a menu for the wanted meals. */
    val weeksPublished: Int = 0,
    val credit: Long? = null,
    val serverMessages: List<String> = emptyList(),
)

/**
 * One pass over the weeks that are published: sign in, read each week, reserve what is missing,
 * move on. It never un-ticks anything, never logs a body or a password, and signs in again at most
 * once per pass (a wrong password is not retried: repeated failures trigger a captcha or a lockout).
 */
class Runner(
    private val http: Http,
    private val username: String,
    private val password: String,
    private val wanted: Wanted,
    private val selfId: String,
    private val preview: Boolean,
    private val today: LocalDate = LocalDate.now(),
    private val maxWeeks: Int = 4,
    private val log: (String) -> Unit = {},
) {
    private class LoginFailed : Exception()
    private class Unexpected(m: String) : Exception(m)

    private var signedIn = false

    private fun login() {
        val first = http.get(Urls.LOGIN_PAGE)
        val csrf = SamadParser.csrf(first) ?: throw Unexpected("login page has no token")
        http.post(Urls.LOGIN_POST, SamadForm.login(csrf, username, password))
        signedIn = true
    }

    private fun reservePage(): ReservePage {
        var html = http.get(Urls.PANEL)
        if (SamadParser.looksLikeLogin(html)) throw LoginFailed()
        if (!SamadParser.isReservePage(html)) html = http.get(Urls.RESERVE)
        if (SamadParser.looksLikeLogin(html)) throw LoginFailed()
        return SamadParser.parse(html) ?: throw Unexpected("reservation page not recognised")
    }

    private fun parseOrRefetch(html: String): ReservePage {
        if (SamadParser.looksLikeLogin(html)) throw LoginFailed()
        SamadParser.parse(html)?.let { return it }
        val again = http.get(Urls.RESERVE)
        if (SamadParser.looksLikeLogin(again)) throw LoginFailed()
        return SamadParser.parse(again) ?: throw Unexpected("reservation page not recognised")
    }

    fun pass(): Outcome {
        val reserved = ArrayList<String>(); val would = ArrayList<String>(); val failed = ArrayList<String>()
        val poor = ArrayList<String>(); val over = ArrayList<String>(); val unavailable = ArrayList<String>()
        val messages = ArrayList<String>()
        var already = 0
        var waiting = false
        var published = 0
        var credit: Long? = null
        try {
            // The cookies of an earlier pass may still be valid (then the login page is not shown at all).
            var page = try { reservePage().also { log("session still valid") } } catch (e: LoginFailed) {
                login()
                log("signed in")
                reservePage()
            }
            log("page: self ${page.selfId}, credit ${page.credit}, ${page.rows.size} options")
            if (page.selfId != selfId) {
                if (page.selfOptions.none { it.first == selfId }) {
                    return Outcome(Status.WRONG_SELF, detail = page.selfId, credit = page.credit)
                }
                // Same as picking the self in the drop-down on the website.
                log("switching self ${page.selfId} -> $selfId")
                page = parseOrRefetch(http.post(Urls.RESERVE, SamadForm.build(page, "showPanel", selfId = selfId)))
                if (page.selfId != selfId) return Outcome(Status.WRONG_SELF, detail = page.selfId, credit = page.credit)
                log("page: self ${page.selfId}, ${page.rows.size} options")
            }
            var spent = 0L
            for (week in 0 until maxWeeks) {
                credit = page.credit
                messages += page.messages
                // Days that are already over are ignored for planning, but stay in the posted form.
                val future = page.copy(rows = page.rows.filter { it.programDate >= today.toString() })
                val budget = if (wanted.maxSpend > 0) maxOf(1L, wanted.maxSpend - spent) else 0L
                val plan = Planner.plan(future, wanted.copy(maxSpend = budget))
                already += plan.alreadyReserved.size
                unavailable += plan.unavailable.map { it.title }
                poor += plan.notEnoughCredit.map { it.first.title }
                over += plan.overBudget.map { it.first.title }
                log("week ${week + 1}: reserved ${plan.alreadyReserved.size}, to reserve ${plan.toReserve.size}, closed ${plan.unavailable.size}, no credit ${plan.notEnoughCredit.size}")
                if (plan.menuNotPublished) { waiting = true; log("week ${week + 1}: menu not published yet"); break }
                published++
                if (plan.toReserve.isNotEmpty()) {
                    if (preview) {
                        would += plan.toReserve.map { it.first.title + " — " + it.second.label }
                        log("preview: would reserve " + plan.toReserve.joinToString("; ") { it.first.title })
                    } else {
                        val picked = plan.toReserve.map { it.second }
                        log("reserving ${picked.size}")
                        val body = SamadForm.build(page, "doReserve", picked.map { it.index }.toSet())
                        val after = parseOrRefetch(http.post(Urls.RESERVE, body))
                        val (ok, bad) = Planner.verify(after, picked)
                        val byProgram = plan.toReserve.associate { it.second.programId to it.first.title }
                        reserved += ok.map { byProgram[it.programId] ?: it.programDate }
                        failed += bad.map { byProgram[it.programId] ?: it.programDate }
                        spent += ok.sumOf { it.price ?: 0L }
                        messages += after.messages
                        page = after
                        credit = page.credit
                    }
                }
                if (week == maxWeeks - 1) break
                val next = parseOrRefetch(http.post(Urls.RESERVE, SamadForm.build(page, "showNextWeek")))
                if (next.weekStart == page.weekStart) break
                page = next
            }
            return Outcome(
                Status.OK, "", reserved, would, failed, poor.distinct(), over.distinct(), unavailable.distinct(),
                already, waiting, published, credit, messages.distinct(),
            )
        } catch (e: LoginFailed) {
            return Outcome(Status.LOGIN_FAILED)
        } catch (e: CertProblem) {
            return Outcome(Status.CERT_PROBLEM, e.message ?: "")
        } catch (e: NetProblem) {
            return Outcome(Status.NETWORK, e.message ?: "")
        } catch (e: Unexpected) {
            return Outcome(Status.UNEXPECTED, e.message ?: "")
        }
    }
}

enum class Verdict { KEEP_GOING, DONE, PREVIEW_DONE, LOGIN, CERT, ODD, SELF, POOR, OVER, FAILED, NET }

/**
 * Decides after every pass whether one account is finished. Waiting for the next week ends as soon as
 * a week appears that was not there at the first pass (and what it offers has been reserved).
 */
class AccountTracker(private val preview: Boolean, private val waitNext: Boolean) {
    private var baseline: Int? = null
    private var netFails = 0
    private var badPasses = 0

    fun next(o: Outcome): Verdict {
        when (o.status) {
            Status.LOGIN_FAILED -> return Verdict.LOGIN
            Status.CERT_PROBLEM -> return Verdict.CERT
            Status.UNEXPECTED -> return Verdict.ODD
            Status.WRONG_SELF -> return Verdict.SELF
            Status.NETWORK -> return if (++netFails >= MAX_NET_FAILS) Verdict.NET else Verdict.KEEP_GOING
            Status.OK -> netFails = 0
        }
        val base = baseline ?: o.weeksPublished.also { baseline = it }
        if (o.poor.isNotEmpty()) return Verdict.POOR
        if (o.overBudget.isNotEmpty()) return Verdict.OVER
        if (preview) return Verdict.PREVIEW_DONE
        if (o.failed.isNotEmpty() && ++badPasses >= 5) return Verdict.FAILED
        val keepWaiting = o.waiting && waitNext && o.weeksPublished <= base
        return if (o.failed.isEmpty() && !keepWaiting) Verdict.DONE else Verdict.KEEP_GOING
    }

    private companion object { const val MAX_NET_FAILS = 20 }
}
