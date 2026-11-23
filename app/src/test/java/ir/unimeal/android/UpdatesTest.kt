package ir.unimeal.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatesTest {
    @Test fun versionCompare() {
        assertTrue(Versions.isNewer("v0.4.0", "0.3.0"))
        assertTrue(Versions.isNewer("0.3.1", "0.3.0"))
        assertTrue(Versions.isNewer("v1.0", "0.9.9"))
        assertFalse(Versions.isNewer("v0.3.0", "0.3.0"))
        assertFalse(Versions.isNewer("v0.2.9", "0.3.0"))
    }

    private val sample = """{"url":"x","html_url":"https:\/\/github.com\/Mrzzan05\/UniMeal\/releases\/tag\/v0.4.0",
        "tag_name":"v0.4.0","assets":[
        {"name":"SHA256SUMS.txt","browser_download_url":"https://github.com/Mrzzan05/UniMeal/releases/download/v0.4.0/SHA256SUMS.txt"},
        {"name":"UniMeal-v0.4.0.apk","browser_download_url":"https://github.com/Mrzzan05/UniMeal/releases/download/v0.4.0/UniMeal-v0.4.0.apk"}]}"""

    @Test fun parsesTheLatestRelease() {
        val r = assertNotNull(UpdateParse.latest(sample))
        assertEquals("v0.4.0", r.tag)
        assertEquals("https://github.com/Mrzzan05/UniMeal/releases/tag/v0.4.0", r.pageUrl)
        assertEquals("https://github.com/Mrzzan05/UniMeal/releases/download/v0.4.0/UniMeal-v0.4.0.apk", r.apkUrl)
    }

    @Test fun neverOpensForeignLinks() {
        val evil = """{"tag_name":"v9.0.0","html_url":"https://evil.example/x",
            "assets":[{"browser_download_url":"https://evil.example/UniMeal.apk"}]}"""
        val r = assertNotNull(UpdateParse.latest(evil))
        assertEquals(Repo.URL + "/releases", r.pageUrl)
        assertNull(r.apkUrl)
    }

    @Test fun emptyAnswerIsNotARelease() {
        assertNull(UpdateParse.latest("{}"))
    }
}
