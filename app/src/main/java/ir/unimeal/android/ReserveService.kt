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
 * Runs passes until everything is reserved, a problem needs the user, or Stop is pressed.
 * A wake lock is held only while it runs, and never longer than [MAX_RUN_MS].
 */
class ReserveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopRun(RunState.R_STOPPED); return START_NOT_STICKY }
        Notify.channels(this)
        ServiceCompat.startForeground(
            this, Notify.ID_RUN, Notify.running(this, getString(R.string.running)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (job?.isActive == true) return START_NOT_STICKY
        val s = Vault(this).load()
        if (!s.hasLogin || s.meals.isEmpty()) { stopRun(RunState.R_STOPPED); return START_NOT_STICKY }
        val pm = getSystemService(PowerManager::class.java)
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UniMeal:run").apply { acquire(MAX_RUN_MS) }
        RunState.set { it.copy(running = true, line = RunState.R_CHECKING, arg = "", log = emptyList()) }
        job = scope.launch { loop(s) }
        return START_NOT_STICKY
    }

    private suspend fun loop(s: Settings) {
        val http = OkHttpHttp()
        val started = System.currentTimeMillis()
        var netFails = 0
        var badPasses = 0
        while (scope.isActive) {
            RunState.set { it.copy(line = RunState.R_CHECKING) }
            val o = withContext(Dispatchers.IO) {
                Runner(http, s.username, s.password, Wanted(s.meals, maxSpend = s.maxSpend), s.selfId, s.preview, log = RunState::log).pass()
            }
            RunState.set { it.copy(credit = o.credit ?: it.credit) }
            when (o.status) {
                Status.LOGIN_FAILED -> return finish(RunState.R_LOGIN, getString(R.string.n_login_title), getString(R.string.n_login_text))
                Status.CERT_PROBLEM -> return finish(RunState.R_CERT, getString(R.string.n_cert_title), getString(R.string.n_cert_text))
                Status.UNEXPECTED -> return finish(RunState.R_ODD, getString(R.string.n_odd_title), getString(R.string.n_odd_text))
                Status.WRONG_SELF -> return finish(RunState.R_SELF, getString(R.string.n_self_title), getString(R.string.n_self_text))
                Status.NETWORK -> {
                    RunState.log(getString(R.string.log_net))
                    if (++netFails >= MAX_NET_FAILS) return finish(RunState.R_NET, getString(R.string.n_net_title), getString(R.string.n_net_text))
                    delay(pause())
                    continue
                }
                Status.OK -> netFails = 0
            }
            RunState.log(getString(R.string.log_pass, o.alreadyReserved, o.reserved.size + o.wouldReserve.size))
            if (o.reserved.isNotEmpty()) Notify.result(this, getString(R.string.n_reserved_title), o.reserved.joinToString("\n"))
            if (o.poor.isNotEmpty()) return finish(RunState.R_POOR, getString(R.string.n_poor_title), getString(R.string.n_poor_text, o.poor.joinToString("، ")))
            if (o.overBudget.isNotEmpty()) return finish(RunState.R_OVER, getString(R.string.n_over_title), getString(R.string.n_over_text, o.overBudget.joinToString("، ")))
            if (s.preview) {
                val t = if (o.wouldReserve.isEmpty()) getString(R.string.n_preview_none) else o.wouldReserve.joinToString("\n")
                return finish(RunState.R_PREVIEW_DONE, getString(R.string.n_preview_title), t)
            }
            if (o.failed.isNotEmpty() && ++badPasses >= 5) {
                return finish(RunState.R_ODD, getString(R.string.n_failed_title), getString(R.string.n_failed_text, o.failed.joinToString("، ")))
            }
            val keepWaiting = o.waiting && s.waitNextWeek
            if (o.failed.isEmpty() && !keepWaiting) {
                val extra = if (o.unavailable.isNotEmpty()) "\n" + getString(R.string.n_sold_out, o.unavailable.joinToString("، ")) else ""
                return finish(RunState.R_DONE, getString(R.string.n_done_title), getString(R.string.n_done_text) + extra)
            }
            if (System.currentTimeMillis() - started > MAX_RUN_MS) {
                return finish(RunState.R_STOPPED, getString(R.string.n_timeout_title), getString(R.string.n_timeout_text))
            }
            RunState.set { it.copy(line = RunState.R_WAITING) }
            Notify.updateRunning(this, getString(R.string.waiting))
            delay(pause())
        }
    }

    /** 40–70 s: quick enough to be early on Wednesday, slow enough not to hammer the university server. */
    private fun pause(): Long = 40_000L + (Math.random() * 30_000L).toLong()

    private fun finish(reason: Int, title: String, text: String) {
        Notify.result(this, title, text)
        stopRun(reason)
    }

    private fun stopRun(reason: Int) {
        job?.cancel(); job = null
        RunState.set { it.copy(running = false, line = reason) }
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        wake = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        scope.cancel()
        RunState.set { if (it.running) it.copy(running = false, line = RunState.R_STOPPED) else it }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "ir.unimeal.android.STOP"
        private const val MAX_RUN_MS = 12L * 60 * 60 * 1000
        private const val MAX_NET_FAILS = 20

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, ReserveService::class.java))
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, ReserveService::class.java).setAction(ACTION_STOP)) }
    }
}
