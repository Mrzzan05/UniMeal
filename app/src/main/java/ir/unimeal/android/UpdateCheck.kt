package ir.unimeal.android

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Manual "Check for update": asks GitHub for the latest release of this project. It only runs when the user taps
 * the button, sends no account data, and never installs anything: it reports the newest version and opens the
 * project's own release page or APK in the browser.
 */
object UpdateCheck {
    sealed interface Result {
        data class UpToDate(val current: String) : Result
        data class Available(val latest: String, val pageUrl: String, val apkUrl: String?) : Result
        data class Failed(val reason: String) : Result
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun check(current: String): Result = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(Repo.API_LATEST)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "UniMeal/$current").build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@withContext Result.Failed("No release yet")
                if (!resp.isSuccessful) return@withContext Result.Failed("HTTP ${resp.code}")
                val latest = UpdateParse.latest(resp.body?.string().orEmpty()) ?: return@withContext Result.Failed("No release found")
                if (Versions.isNewer(latest.tag, current)) Result.Available(latest.tag.removePrefix("v"), latest.pageUrl, latest.apkUrl)
                else Result.UpToDate(current)
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
