package ir.unimeal.android

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the UI shows, plus a plain-text log kept on the phone. Holds and writes no secrets. */
object RunState {
    data class State(
        val running: Boolean = false,
        val line: Int = R_IDLE,
        val credit: Long? = null,
        /** Account id -> last status code. */
        val acc: Map<String, Int> = emptyMap(),
    )

    const val R_IDLE = 0
    const val R_CHECKING = 1
    const val R_WAITING = 2
    const val R_DONE = 3
    const val R_PREVIEW_DONE = 4
    const val R_LOGIN = 5
    const val R_CERT = 6
    const val R_NET = 7
    const val R_ODD = 8
    const val R_SELF = 9
    const val R_POOR = 10
    const val R_OVER = 11
    const val R_STOPPED = 12
    const val R_FAILED = 13

    private const val MAX_LINES = 400
    private val _s = MutableStateFlow(State())
    val state = _s.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log = _log.asStateFlow()
    private var file: File? = null

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "log.txt")
        file = f
        _log.value = runCatching { f.readLines().takeLast(MAX_LINES).reversed() }.getOrDefault(emptyList())
    }

    fun set(f: (State) -> State) { _s.value = f(_s.value) }

    @Synchronized fun log(msg: String) {
        val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = "$stamp  $msg"
        _log.value = (listOf(line) + _log.value).take(MAX_LINES)
        runCatching {
            file?.let { f ->
                f.appendText(line + "\n")
                if (f.length() > 200_000) f.writeText(_log.value.reversed().joinToString("\n") + "\n")
            }
        }
    }

    @Synchronized fun clearLog() {
        _log.value = emptyList()
        runCatching { file?.writeText("") }
    }
}
