package ir.unimeal.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the SAMAD reservation page already signed in. The app signs in itself (the same safe HTTPS client as the
 * automatic run, including the switch to the Taak self) and hands only the session cookie to a locked-down WebView.
 * The password never goes into the page. Only nds.urmia.ac.ir over HTTPS can be shown; certificate errors are not bypassed.
 */
class SamadActivity : ComponentActivity() {
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT)
        val s = Vault(this).load()
        setContent {
            UniMealTheme(s.theme) {
                SamadScreen(s, accountId, { webView = it }, { finish() })
            }
        }
    }

    override fun onDestroy() {
        runCatching { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() }
        webView?.destroy(); webView = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        fun intent(ctx: Context, accountId: String?): Intent =
            Intent(ctx, SamadActivity::class.java).putExtra(EXTRA_ACCOUNT, accountId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private const val PH_SIGNING = 0
private const val PH_READY = 1
private const val PH_FAILED = 2

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SamadScreen(s: Settings, accountId: String?, hold: (WebView?) -> Unit, close: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val account = s.accounts.firstOrNull { it.id == accountId && it.password.isNotEmpty() } ?: s.defaultAccount
    var phase by remember { mutableIntStateOf(PH_SIGNING) }
    var attempt by remember { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<WebView?>(null) }
    var failMsg by remember { mutableIntStateOf(R.string.web_failed) }

    LaunchedEffect(attempt) {
        phase = PH_SIGNING
        if (account == null) { failMsg = R.string.web_no_account; phase = PH_FAILED; return@LaunchedEffect }
        val http = OkHttpHttp()
        val st = withContext(Dispatchers.IO) {
            Runner(http, account.username, account.password, Wanted(s.meals), s.selfId, true).signIn()
        }
        if (st != Status.OK) {
            failMsg = when (st) { Status.LOGIN_FAILED -> R.string.st_login; Status.CERT_PROBLEM -> R.string.st_cert; else -> R.string.web_failed }
            phase = PH_FAILED
            return@LaunchedEffect
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        for (c in http.cookies()) cm.setCookie("https://${Urls.HOST}", c.toString())
        cm.flush()
        phase = PH_READY
    }

    BackHandler { val v = view; if (v != null && v.canGoBack()) v.goBack() else close() }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (phase == PH_READY) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                val u = r.url
                                if (u.host != Urls.HOST) return true // never leave the university site
                                if (u.scheme == "http") { v.loadUrl(u.buildUpon().scheme("https").build().toString()); return true }
                                return false
                            }
                        }
                        loadUrl(Urls.PANEL)
                        view = this
                        hold(this)
                    }
                },
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (phase == PH_SIGNING) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.web_signing), Modifier.padding(top = 20.dp), textAlign = TextAlign.Center)
                } else {
                    Text(stringResource(failMsg), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button({ attempt++ }, Modifier.padding(top = 20.dp)) { Text(stringResource(R.string.retry)) }
                    OutlinedButton({
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Urls.PANEL))) }
                    }, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.open_in_browser)) }
                    OutlinedButton(close, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}
