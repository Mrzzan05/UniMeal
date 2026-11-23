package ir.unimeal.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Knows whether the phone has a usable network. A Wi-Fi that still wants a sign-in (captive portal) counts as
 * offline. The system's "validated" flag is deliberately not required: it needs Google servers, which are not
 * always reachable here although the university site is.
 */
class NetWatch(ctx: Context) {
    private val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
    val online = MutableStateFlow(check())
    @Volatile var captive = false
        private set

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { update(cm.getNetworkCapabilities(network)) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { update(caps) }
        override fun onLost(network: Network) { captive = false; online.value = false }
    }

    private fun update(c: NetworkCapabilities?) {
        captive = c?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
        online.value = c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !captive
    }

    private fun check(): Boolean {
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    }

    fun start() {
        online.value = check()
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    fun stop() { runCatching { cm.unregisterNetworkCallback(callback) } }

    /** Waits until the network is back or [timeoutMs] passed. Returns whether it is online. */
    suspend fun awaitOnline(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { online.first { it }; true } ?: false
}
