package ir.unimeal.android

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Everything that understands the SAMAD reservation page, with no Android dependency so it can be
 * unit-tested against saved pages. The page is a Struts form: every food option of the week is one
 * `userWeekReserves[N]` row, and "Confirm" posts the whole form with `method:doReserve`.
 */

enum class Meal(val persian: String, val fallbackTypeId: String) {
    BREAKFAST("صبحانه", "2"),
    LUNCH("ناهار", "3"),
    DINNER("شام", "1"),
}

/** One selectable (or already reserved) food option of the week. */
data class FoodRow(
    val index: Int,
    val programId: String,
    val mealTypeId: String,
    val programDate: String,
    val programDateTime: String,
    val selfId: String,
    val foodTypeId: String,
    val foodId: String,
    val priorReserveDate: String,
    val freeFoodSelected: String,
    /** Hidden `id` of the reservation: empty until the server has saved it. */
    val reservedId: String,
    val checked: Boolean,
    val disabled: Boolean,
    val count: String,
    val label: String,
    val price: Long?,
) {
    val saved: Boolean get() = reservedId.isNotBlank() && reservedId != "null"
    /** Already reserved (or ticked) – never touch it. */
    val taken: Boolean get() = checked || saved
}

data class ReservePage(
    val csrf: String,
    val weekStart: String,
    val credit: Long,
    val selfId: String,
    val selfOptions: List<Pair<String, String>>,
    val mealIds: Map<Meal, String>,
    val rows: List<FoodRow>,
    /** Hidden fields of the form that are not food rows, in page order (without _csrf and the week/credit ones). */
    val extraFields: List<Pair<String, String>>,
    val messages: List<String>,
) {
    fun mealId(m: Meal): String = mealIds[m] ?: m.fallbackTypeId
    fun rowsFor(m: Meal): List<FoodRow> = rows.filter { it.mealTypeId == mealId(m) }
}

object SamadParser {
    private val tagRe = Regex("<input\\b[^>]*>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val quoted = Regex("\"[^\"]*\"|'[^']*'")

    fun looksLikeLogin(html: String): Boolean =
        html.contains("j_security_check") || Regex("type\\s*=\\s*[\"']password[\"']", RegexOption.IGNORE_CASE).containsMatchIn(html)

    fun isReservePage(html: String): Boolean = html.contains("id=\"resFinalform\"") || html.contains("name=\"resFinalform\"")

    /** The anti-forgery token of any page (login page or reserve page). */
    fun csrf(html: String): String? =
        Regex("name=[\"']_csrf[\"']\\s+value=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("value=[\"']([^\"']+)[\"']\\s+name=[\"']_csrf[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

    private fun attr(tag: String, name: String): String? =
        Regex("(?<![\\w:-])" + Regex.escape(name) + "\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
            ?: Regex("(?<![\\w:-])" + Regex.escape(name) + "\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    private fun flag(tag: String, name: String): Boolean =
        Regex("(?<![\\w:-])" + name + "(?![\\w:-])", RegexOption.IGNORE_CASE).containsMatchIn(quoted.replace(tag, "\"\""))

    private fun digits(s: String): String = buildString {
        for (c in s) append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
        )
    }

    private fun plain(html: String): String =
        html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    fun parse(html: String): ReservePage? {
        val formStart = html.indexOf("id=\"resFinalform\"").takeIf { it >= 0 } ?: html.indexOf("name=\"resFinalform\"")
        if (formStart < 0) return null
        val formEnd = html.indexOf("</form>", formStart).let { if (it < 0) html.length else it }
        val form = html.substring(formStart, formEnd)

        val hidden = ArrayList<Pair<String, String>>()
        val boxes = HashMap<Int, String>() // row index -> checkbox tag
        for (m in tagRe.findAll(form)) {
            val tag = m.value
            val type = attr(tag, "type")?.lowercase() ?: "text"
            val name = attr(tag, "name") ?: continue
            if (type == "checkbox") {
                Regex("userWeekReserves\\[(\\d+)\\]\\.selected").matchEntire(name)?.let { boxes[it.groupValues[1].toInt()] = tag }
            } else if (type == "hidden" || type == "text") {
                hidden += name to (attr(tag, "value") ?: "")
            }
        }
        val rowHidden = HashMap<Int, MutableMap<String, String>>()
        val rowSelectedByHidden = HashSet<Int>()
        val extra = ArrayList<Pair<String, String>>()
        val rowName = Regex("userWeekReserves\\[(\\d+)\\]\\.(\\w+)")
        for ((n, v) in hidden) {
            val r = rowName.matchEntire(n)
            if (r != null) {
                val idx = r.groupValues[1].toInt()
                val field = r.groupValues[2]
                if (field == "selected") { if (v == "true") rowSelectedByHidden += idx } else rowHidden.getOrPut(idx) { HashMap() }[field] = v
            } else if (n != "_csrf" && n != "weekStartDateTime" && n != "remainCredit") {
                extra += n to v
            }
        }
        val selects = HashMap<Int, String>()
        Regex("<select\\b[^>]*name=\"userWeekReserves\\[(\\d+)\\]\\.selectedCount\"[^>]*>(.*?)</select>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(form).forEach { m ->
                val opts = Regex("<option\\b([^>]*)>", RegexOption.IGNORE_CASE).findAll(m.groupValues[2]).toList()
                val sel = opts.firstOrNull { flag(it.value, "selected") } ?: opts.firstOrNull()
                selects[m.groupValues[1].toInt()] = sel?.let { attr(it.value, "value") } ?: "1"
            }

        val rows = ArrayList<FoodRow>()
        for (idx in (boxes.keys + rowHidden.keys).toSortedSet()) {
            val tag = boxes[idx]
            val h = rowHidden[idx] ?: continue
            val label = Regex("<label[^>]*for=\"userWeekReserves\\.selected$idx\"[^>]*>(.*?)</label>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(form)?.groupValues?.get(1)?.let { plain(it).trim('|', ' ') } ?: ""
            val programId = h["programId"].orEmpty()
            val price = Regex("id=\"foodPriceTooltip_" + Regex.escape(programId) + "\"[^>]*>\\s*([\\d٠-٩۰-۹,]+)", RegexOption.IGNORE_CASE)
                .find(form)?.groupValues?.get(1)?.let { digits(it).replace(",", "").toLongOrNull() }
            rows += FoodRow(
                index = idx,
                programId = programId,
                mealTypeId = h["mealTypeId"] ?: (tag?.let { attr(it, "mealTypeId") } ?: ""),
                programDate = tag?.let { attr(it, "programDate") } ?: "",
                programDateTime = h["programDateTime"].orEmpty(),
                selfId = h["selfId"].orEmpty(),
                foodTypeId = h["foodTypeId"].orEmpty(),
                foodId = h["foodId"].orEmpty(),
                priorReserveDate = h["priorReserveDateStr"] ?: "null",
                freeFoodSelected = h["freeFoodSelected"] ?: "false",
                reservedId = h["id"].orEmpty(),
                checked = (tag != null && flag(tag, "checked")) || idx in rowSelectedByHidden,
                disabled = tag == null || flag(tag, "disabled"),
                count = selects[idx] ?: "1",
                label = label,
                price = price,
            )
        }

        fun hiddenValue(n: String) = hidden.firstOrNull { it.first == n }?.second
        val credit = (hiddenValue("remainCredit") ?: Regex("id=\"creditId\"[^>]*>\\s*([\\d٠-٩۰-۹,]+)").find(html)?.groupValues?.get(1) ?: "0")
            .let { digits(it).replace(",", "").toLongOrNull() } ?: 0L

        val selfOptions = Regex("<option\\s+value=\"(\\d+)\"[^>]*>([^<]*)</option>")
            .findAll(html.substring(maxOf(0, html.indexOf("id=\"selfId\"")).coerceAtMost(html.length)).take(6000))
            .map { it.groupValues[1] to it.groupValues[2].trim() }.toList()

        val mealIds = LinkedHashMap<Meal, String>()
        for (m in Meal.entries) {
            Regex(m.persian + "\\s*<div id=\"ftActionContainerId_(\\d+)\"").find(html)?.let { mealIds[m] = it.groupValues[1] }
        }

        val messages = ArrayList<String>()
        Regex("<div\\s+class=\"(?:error|errorMessage|actionError|message|actionMessage|success|warning|info)\"[^>]*>(.*?)</div>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html).forEach { m ->
                val t = plain(m.groupValues[1])
                // The page ships an unfilled client-side template ("{0}" / "{1}"); it is not a server message.
                if (t.isNotBlank() && !t.contains("{0}")) messages += t
            }

        return ReservePage(
            csrf = csrf(form) ?: csrf(html) ?: "",
            weekStart = hiddenValue("weekStartDateTime") ?: "",
            credit = credit,
            selfId = hiddenValue("selectedSelfDefId") ?: "",
            selfOptions = selfOptions,
            mealIds = mealIds,
            rows = rows,
            extraFields = extra,
            messages = messages.distinct(),
        )
    }
}

/** Builds the exact form body a browser would send for one of the page's buttons. */
object SamadForm {
    fun login(csrf: String, username: String, password: String): List<Pair<String, String>> =
        listOf("_csrf" to csrf, "username" to username, "password" to password, "login" to "")

    /**
     * [method] is the Struts button: doReserve, showNextWeek, showPreviousWeek, showCurrentWeek or showPanel.
     * [pick] are rows that should be ticked in addition to the ones already reserved. Nothing is ever
     * un-ticked, so this can never cancel a reservation.
     */
    fun build(page: ReservePage, method: String, pick: Set<Int> = emptySet(), selfId: String? = null): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        out += "weekStartDateTime" to page.weekStart
        out += "remainCredit" to page.credit.toString()
        out += "method:$method" to "Submit"
        out += "_csrf" to page.csrf
        for ((n, v) in page.extraFields) out += n to (if (n == "selectedSelfDefId" && selfId != null) selfId else v)
        for (r in page.rows) {
            val on = r.taken || r.index in pick
            val p = "userWeekReserves[${r.index}]."
            if (on) {
                out += p + "selected" to "true"
                out += p + "selectedCount" to r.count.ifBlank { "1" }
            }
            out += p + "id" to (if (r.saved) r.reservedId else "")
            out += p + "programId" to r.programId
            out += p + "mealTypeId" to r.mealTypeId
            out += p + "programDateTime" to r.programDateTime
            out += p + "selfId" to r.selfId
            out += p + "foodTypeId" to r.foodTypeId
            out += p + "foodId" to r.foodId
            out += p + "priorReserveDateStr" to r.priorReserveDate
            out += p + "freeFoodSelected" to r.freeFoodSelected
        }
        out += "_csrf" to page.csrf
        return out
    }
}

data class Slot(val date: String, val meal: Meal, val rows: List<FoodRow>) {
    val taken: FoodRow? get() = rows.firstOrNull { it.taken }
    val title: String get() = date + " " + meal.persian
}

data class Plan(
    val toReserve: List<Pair<Slot, FoodRow>>,
    val alreadyReserved: List<Slot>,
    /** Every option is disabled (sold out, closed, or past): shown to the user, never retried blindly. */
    val unavailable: List<Slot>,
    val notEnoughCredit: List<Pair<Slot, FoodRow>>,
    val overBudget: List<Pair<Slot, FoodRow>>,
    /** The page has no options at all for the wanted meals: the menu is not published yet. */
    val menuNotPublished: Boolean,
) {
    val cost: Long get() = toReserve.sumOf { it.second.price ?: 0L }
}

data class Wanted(
    val meals: Set<Meal>,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val prefer: List<String> = emptyList(),
    val avoid: List<String> = emptyList(),
    /** Most rials one run may add; 0 = unlimited. */
    val maxSpend: Long = 0L,
)

object Planner {
    private fun norm(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ').replace(Regex("\\s+"), " ").trim()

    fun slots(page: ReservePage, wanted: Wanted): List<Slot> {
        val out = ArrayList<Slot>()
        for (m in Meal.entries.filter { it in wanted.meals }) {
            page.rowsFor(m).groupBy { it.programDate }.toSortedMap().forEach { (date, rows) ->
                val dow = runCatching { LocalDate.parse(date).dayOfWeek }.getOrNull()
                if (dow == null || dow in wanted.days) out += Slot(date, m, rows)
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.meal.ordinal }))
    }

    fun plan(page: ReservePage, wanted: Wanted): Plan {
        val slots = slots(page, wanted)
        val reserved = ArrayList<Slot>()
        val unavailable = ArrayList<Slot>()
        val candidates = ArrayList<Pair<Slot, FoodRow>>()
        for (s in slots) {
            if (s.taken != null) { reserved += s; continue }
            val usable = s.rows.filter { !it.disabled && wanted.avoid.none { a -> norm(it.label).contains(norm(a)) && norm(a).isNotEmpty() } }
            val pick = wanted.prefer.firstNotNullOfOrNull { p -> usable.firstOrNull { norm(it.label).contains(norm(p)) && norm(p).isNotEmpty() } }
                ?: usable.firstOrNull()
            if (pick == null) unavailable += s else candidates += s to pick
        }
        var credit = page.credit
        var spent = 0L
        val ok = ArrayList<Pair<Slot, FoodRow>>()
        val poor = ArrayList<Pair<Slot, FoodRow>>()
        val over = ArrayList<Pair<Slot, FoodRow>>()
        for (c in candidates) {
            val price = c.second.price ?: 0L
            when {
                price > credit -> poor += c
                wanted.maxSpend > 0 && spent + price > wanted.maxSpend -> over += c
                else -> { ok += c; credit -= price; spent += price }
            }
        }
        val any = wanted.meals.any { page.rowsFor(it).isNotEmpty() }
        return Plan(ok, reserved, unavailable, poor, over, menuNotPublished = !any)
    }

    /** After a confirm: which of the rows we ticked did the server really save? */
    fun verify(after: ReservePage, picked: List<FoodRow>): Pair<List<FoodRow>, List<FoodRow>> {
        val saved = ArrayList<FoodRow>()
        val failed = ArrayList<FoodRow>()
        for (p in picked) {
            val now = after.rows.firstOrNull { it.programId == p.programId }
            if (now != null && now.saved) saved += now else failed += p
        }
        return saved to failed
    }
}
