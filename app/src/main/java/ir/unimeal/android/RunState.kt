package ir.unimeal.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the UI shows. Holds no secrets. */
object RunState {
    data class State(
        val running: Boolean = false,
        val line: Int = R_IDLE,
        val arg: String = "",
        val credit: Long? = null,
        val log: List<String> = emptyList(),
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

    private val _s = MutableStateFlow(State())
    val state = _s.asStateFlow()

    fun set(f: (State) -> State) { _s.value = f(_s.value) }

    fun log(msg: String) = set { s ->
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        s.copy(log = (listOf("$stamp  $msg") + s.log).take(40))
    }
}
