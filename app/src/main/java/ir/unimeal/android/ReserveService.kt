package ir.unimeal.android

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs passes for every enabled account (first one first) until each is finished, a problem needs the
 * user, or Stop is pressed. A wake lock is held only while it runs, and never longer than [MAX_RUN_MS].
 */
class ReserveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var net: NetWatch? = null
    private var pollMs = 60_000L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RunState.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { RunState.log(getString(R.string.log_stopped)); stopRun(RunState.R_STOPPED); return START_NOT_STICKY }
        Notify.channels(this)
        ServiceCompat.startForeground(
            this, Notify.ID_RUN, Notify.running(this, getString(R.string.running)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (job?.isActive == true) return START_NOT_STICKY
        val s = Vault(this).load()
        if (s.usableAccounts.isEmpty() || s.meals.isEmpty()) { stopRun(RunState.R_STOPPED); return START_NOT_STICKY }
        val pm = getSystemService(PowerManager::class.java)
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UniMeal:run").apply { acquire(MAX_RUN_MS) }
        RunState.set { it.copy(running = true, line = RunState.R_CHECKING, acc = emptyMap(), wouldReserve = emptyList()) }
        RunState.log(getString(R.string.log_start, s.usableAccounts.size, if (s.preview) "preview" else "real"))
        pollMs = s.pollSeconds.coerceIn(30, 600) * 1000L
        net = NetWatch(this).also { it.start() }
        job = scope.launch { loop(s) }
        return START_NOT_STICKY
    }

    private class Slot(val acc: Account, val tracker: AccountTracker, val http: Http = OkHttpHttp()) {
        var done = false
        var lastWouldReserve: List<String> = emptyList()
        var lastUnavailable: List<String> = emptyList()
    }

    private suspend fun loop(s: Settings) {
        val slots = s.usableAccounts.map { Slot(it, AccountTracker(s.preview, s.waitNextWeek)) }
        val multi = slots.size > 1
        val started = System.currentTimeMillis()
        val notes = ArrayList<String>()
        var overall = RunState.R_DONE
        var site = false
        var offlineLogged = false

        fun who(sl: Slot) = if (multi) sl.acc.shownName + ": " else ""
        fun status(sl: Slot, code: Int) = RunState.set { it.copy(acc = it.acc + (sl.acc.id to code)) }

        while (scope.isActive) {
            if (System.currentTimeMillis() - started > MAX_RUN_MS) {
                Notify.result(this, getString(R.string.n_timeout_title), getString(R.string.n_timeout_text))
                RunState.log(getString(R.string.n_timeout_text))
                return stopRun(RunState.R_STOPPED)
            }
            val nw = net
            if (nw != null && !nw.online.value) {
                RunState.set { it.copy(line = RunState.R_OFFLINE) }
                if (!offlineLogged) {
                    RunState.log(getString(if (nw.captive) R.string.log_captive else R.string.log_offline))
                    offlineLogged = true
                }
                Notify.updateRunning(this, getString(R.string.st_offline_short))
                nw.awaitOnline(pause())
                continue
            }
            if (offlineLogged) {
                RunState.log(getString(R.string.log_online))
                offlineLogged = false
                delay(2_000L)
            }
            for ((idx, sl) in slots.filter { !it.done }.withIndex()) {
                if (idx > 0) delay(2_000L + (Math.random() * 2_000L).toLong())
                RunState.set { it.copy(line = RunState.R_CHECKING) }
                status(sl, RunState.R_CHECKING)
                val tag = sl.acc.shownName
                val o = withContext(Dispatchers.IO) {
                    Runner(
                        sl.http, sl.acc.username, sl.acc.password, Wanted(s.meals, maxSpend = s.maxSpend), s.selfId, s.preview,
                        log = { RunState.log("$tag: $it") },
                    ).pass()
                }
                RunState.set { it.copy(credit = o.credit ?: it.credit) }
                if (o.status == Status.OK) {
                    RunState.log(getString(R.string.log_pass, tag, o.alreadyReserved, o.reserved.size + o.wouldReserve.size))
                    if (o.unavailable.isNotEmpty()) RunState.log("$tag: " + getString(R.string.n_sold_out, o.unavailable.joinToString("، ")))
                    if (o.serverMessages.isNotEmpty()) RunState.log("$tag: server: " + o.serverMessages.joinToString(" | ").take(300))
                    if (o.reserved.isNotEmpty()) Notify.result(this, getString(R.string.n_reserved_title), who(sl) + o.reserved.joinToString("\n"))
                } else {
                    RunState.log("$tag: ${o.status}" + if (o.detail.isNotBlank()) " (${o.detail})" else "")
                }
                sl.lastWouldReserve = o.wouldReserve
                sl.lastUnavailable = o.unavailable

                val v = sl.tracker.next(o)
                val code: Int
                var title = ""
                var text = ""
                var open = false
                when (v) {
                    Verdict.KEEP_GOING -> { status(sl, if (o.status == Status.OK) RunState.R_WAITING else RunState.R_NET); continue }
                    Verdict.DONE -> { code = RunState.R_DONE }
                    Verdict.PREVIEW_DONE -> { code = RunState.R_PREVIEW_DONE }
                    Verdict.LOGIN -> { code = RunState.R_LOGIN; title = getString(R.string.n_login_title); text = getString(R.string.n_login_text) }
                    Verdict.CERT -> { code = RunState.R_CERT; title = getString(R.string.n_cert_title); text = getString(R.string.n_cert_text) }
                    Verdict.ODD -> { code = RunState.R_ODD; title = getString(R.string.n_odd_title); text = getString(R.string.n_odd_text); open = true }
                    Verdict.SELF -> { code = RunState.R_SELF; title = getString(R.string.n_self_title); text = getString(R.string.n_self_text); open = true }
                    Verdict.NET -> { code = RunState.R_NET; title = getString(R.string.n_net_title); text = getString(R.string.n_net_text) }
                    Verdict.POOR -> { code = RunState.R_POOR; title = getString(R.string.n_poor_title); text = getString(R.string.n_poor_text, o.poor.joinToString("، ")); open = true }
                    Verdict.OVER -> { code = RunState.R_OVER; title = getString(R.string.n_over_title); text = getString(R.string.n_over_text, o.overBudget.joinToString("، ")); open = true }
                    Verdict.FAILED -> { code = RunState.R_FAILED; title = getString(R.string.n_failed_title); text = getString(R.string.n_failed_text, o.failed.joinToString("، ")); open = true }
                }
                sl.done = true
                status(sl, code)
                if (title.isNotEmpty()) {
                    Notify.result(this, title, who(sl) + text, open, sl.acc.id)
                    if (overall == RunState.R_DONE || overall == RunState.R_PREVIEW_DONE) overall = code
                    RunState.log("$tag: " + getString(R.string.log_finished))
                } else if (v == Verdict.PREVIEW_DONE) {
                    overall = RunState.R_PREVIEW_DONE
                    val lines = o.wouldReserve.map { w -> who(sl) + w }
                    RunState.set { st -> st.copy(wouldReserve = st.wouldReserve + lines) }
                    notes += who(sl) + if (o.wouldReserve.isEmpty()) getString(R.string.n_preview_none) else o.wouldReserve.joinToString("\n")
                } else {
                    if (o.unavailable.isNotEmpty()) { notes += who(sl) + getString(R.string.n_sold_out, o.unavailable.joinToString("، ")); site = true }
                    RunState.log("$tag: " + getString(R.string.log_finished))
                }
            }
            if (slots.all { it.done }) {
                if (overall == RunState.R_PREVIEW_DONE) {
                    Notify.result(this, getString(R.string.n_preview_title), notes.joinToString("\n").ifBlank { getString(R.string.n_preview_none) })
                } else if (overall == RunState.R_DONE) {
                    Notify.result(this, getString(R.string.n_done_title), (listOf(getString(R.string.n_done_text)) + notes).joinToString("\n"), site, slots.firstOrNull()?.acc?.id)
                }
                return stopRun(overall)
            }
            RunState.set { it.copy(line = RunState.R_WAITING) }
            Notify.updateRunning(this, getString(R.string.waiting))
            delay(pause())
        }
    }

    /** The chosen interval ±25 %: quick enough to be early on Wednesday, slow enough not to hammer the server. */
    private fun pause(): Long = (pollMs * 0.75 + Math.random() * pollMs * 0.5).toLong()

    private fun stopRun(reason: Int) {
        job?.cancel(); job = null
        net?.stop(); net = null
        RunState.set { it.copy(running = false, line = reason) }
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        wake = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        net?.stop(); net = null
        scope.cancel()
        RunState.set { if (it.running) it.copy(running = false, line = RunState.R_STOPPED) else it }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "ir.unimeal.android.STOP"
        private const val MAX_RUN_MS = 12L * 60 * 60 * 1000

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, ReserveService::class.java))
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, ReserveService::class.java).setAction(ACTION_STOP)) }
    }
}
