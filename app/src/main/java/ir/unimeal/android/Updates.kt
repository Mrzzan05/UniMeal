package ir.unimeal.android

/** Pure helpers for the manual "check for update" (no Android classes, so they are unit-tested). */
object Versions {
    /** True when [latest] (like "v0.4.0") is higher than [current] (like "0.3.0"). */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-', '+').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

object Repo {
    const val OWNER = "Mrzzan05"
    const val URL = "https://github.com/Mrzzan05/UniMeal"
    const val API_LATEST = "https://api.github.com/repos/Mrzzan05/UniMeal/releases/latest"

    /** Only pages of this project's own repository are ever opened. */
    fun safe(url: String?, fallback: String = "$URL/releases"): String =
        if (url != null && url.startsWith("$URL/")) url else fallback
}

data class LatestRelease(val tag: String, val pageUrl: String, val apkUrl: String?)

object UpdateParse {
    private fun field(json: String, name: String): String? =
        Regex("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)?.replace("\\/", "/")

    /** Reads the few fields we need from GitHub's `releases/latest` answer. */
    fun latest(json: String): LatestRelease? {
        val tag = field(json, "tag_name")?.trim().orEmpty()
        if (tag.isEmpty()) return null
        val page = Repo.safe(field(json, "html_url"))
        val apk = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"").findAll(json)
            .map { it.groupValues[1].replace("\\/", "/") }
            .firstOrNull { it.startsWith(Repo.URL + "/releases/download/") }
        return LatestRelease(tag, page, apk)
    }
}
