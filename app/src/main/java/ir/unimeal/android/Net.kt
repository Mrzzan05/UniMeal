package ir.unimeal.android

import java.io.IOException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLPeerUnverifiedException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * HTTPS only, system trust store only. Redirects are followed by hand so that the session cookie and
 * the password can never be sent to another host or over plain HTTP: a redirect to http://nds... is
 * upgraded to https, a redirect anywhere else is refused. A certificate problem is never bypassed.
 */
class OkHttpHttp : Http {
    private val jar = object : CookieJar {
        private val store = ArrayList<Cookie>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path } }
            store += cookies
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            store.removeAll { it.expiresAt < now }
            return store.filter { it.matches(url) }
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    /** Session cookies for the SAMAD host, to hand over to the in-app browser. */
    fun cookies(): List<Cookie> = jar.loadForRequest("https://${Urls.HOST}/".toHttpUrl())

    override fun get(url: String): String = exec(Request.Builder().url(url).header("User-Agent", UA).get().build())

    override fun post(url: String, form: List<Pair<String, String>>): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return exec(Request.Builder().url(url).header("User-Agent", UA).header("Origin", "https://${Urls.HOST}").post(body).build())
    }

    private fun exec(first: Request): String {
        var req = first
        repeat(8) {
            ensureHost(req.url)
            try {
                client.newCall(req).execute().use { r ->
                    if (r.isRedirect) {
                        val loc = r.header("Location") ?: throw NetProblem("redirect without target")
                        val next = req.url.resolve(loc) ?: throw NetProblem("bad redirect")
                        val upgraded = if (next.scheme == "http" && next.host == Urls.HOST) next.newBuilder().scheme("https").build() else next
                        ensureHost(upgraded)
                        // 307/308 keep the method; everything else becomes a plain GET.
                        req = if (r.code == 307 || r.code == 308) req.newBuilder().url(upgraded).build()
                        else Request.Builder().url(upgraded).header("User-Agent", UA).get().build()
                        return@use
                    }
                    if (r.code !in 200..299) throw NetProblem("HTTP ${r.code}")
                    return r.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                if (isCertProblem(e)) throw CertProblem(e.javaClass.simpleName)
                throw NetProblem(e.javaClass.simpleName)
            }
        }
        throw NetProblem("too many redirects")
    }

    /** Only a real trust failure counts as one; a dropped TLS connection on a bad network is just a network problem. */
    private fun isCertProblem(e: Throwable): Boolean {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth++ < 8) {
            if (t is CertificateException || t is SSLPeerUnverifiedException) return true
            t = t.cause
        }
        return false
    }

    private fun ensureHost(u: HttpUrl) {
        if (u.scheme != "https" || u.host != Urls.HOST) throw NetProblem("refused host")
    }

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14) UniMeal"
    }
}
