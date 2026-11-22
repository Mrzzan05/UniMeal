package ir.unimeal.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notify.channels(this)
        RunState.init(applicationContext)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1B7F5C), secondary = Color(0xFF1B7F5C))) {
                Root()
            }
        }
    }
}

private fun statusText(line: Int) = when (line) {
    RunState.R_CHECKING -> R.string.st_checking
    RunState.R_WAITING -> R.string.st_waiting
    RunState.R_DONE -> R.string.st_done
    RunState.R_PREVIEW_DONE -> R.string.st_preview_done
    RunState.R_LOGIN -> R.string.st_login
    RunState.R_CERT -> R.string.st_cert
    RunState.R_NET -> R.string.st_net
    RunState.R_ODD -> R.string.st_odd
    RunState.R_SELF -> R.string.st_self
    RunState.R_POOR -> R.string.st_poor
    RunState.R_OVER -> R.string.st_over
    RunState.R_FAILED -> R.string.st_failed
    RunState.R_STOPPED -> R.string.st_stopped
    else -> R.string.st_idle
}

private fun shortStatus(code: Int?) = when (code) {
    null -> R.string.as_idle
    RunState.R_CHECKING -> R.string.as_checking
    RunState.R_WAITING -> R.string.as_waiting
    RunState.R_DONE -> R.string.as_done
    RunState.R_PREVIEW_DONE -> R.string.as_preview
    RunState.R_LOGIN -> R.string.as_login
    RunState.R_POOR -> R.string.as_poor
    else -> R.string.as_problem
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val vault = remember { Vault(ctx) }
    var s by remember { mutableStateOf(vault.load()) }
    var edit by remember { mutableStateOf(s.accounts) }
    var shown by remember { mutableStateOf(setOf<String>()) }
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    fun persist(n: Settings) { s = n; vault.save(n) }
    fun go(i: Int) { scope.launch { pager.animateScrollToPage(i) } }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
        TabRow(pager.currentPage, containerColor = Color.Transparent) {
            Tab(pager.currentPage == 0, { go(0) }, text = { Text(stringResource(R.string.tab_home)) })
            Tab(pager.currentPage == 1, { go(1) }, text = { Text(stringResource(R.string.tab_accounts)) })
            Tab(pager.currentPage == 2, { go(2) }, text = { Text(stringResource(R.string.tab_log)) })
        }
        // Swiping the page changes the tab; every page keeps its own scroll.
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                0 -> HomeTab(s, ::persist) { go(1) }
                1 -> AccountsTab(s, edit, { edit = it }, shown, { shown = it }, ::persist)
                else -> LogTab()
            }
        }
    }
}

private fun openSamad(ctx: android.content.Context) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Urls.PANEL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun HomeTab(s: Settings, persist: (Settings) -> Unit, goAccounts: () -> Unit) {
    val ctx = LocalContext.current
    val st by RunState.state.collectAsState()
    var spend by remember { mutableStateOf(if (s.maxSpend > 0) s.maxSpend.toString() else "") }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val tint = when (st.line) {
            RunState.R_DONE, RunState.R_PREVIEW_DONE -> Color(0xFFDDF3E8)
            RunState.R_LOGIN, RunState.R_CERT, RunState.R_NET, RunState.R_ODD, RunState.R_SELF, RunState.R_POOR, RunState.R_OVER, RunState.R_FAILED -> Color(0xFFFBE3E1)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = tint)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(statusText(st.line)), fontSize = 18.sp, textAlign = TextAlign.Center)
                if (!st.running && st.wouldReserve.isNotEmpty()) {
                    Text(stringResource(R.string.would_reserve), fontWeight = FontWeight.SemiBold)
                    st.wouldReserve.forEach { Text(it, fontSize = 14.sp, textAlign = TextAlign.Center) }
                    Button(
                        onClick = {
                            persist(s.copy(preview = false))
                            if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                            ReserveService.start(ctx)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.reserve_now)) }
                }
                st.credit?.let { Text(stringResource(R.string.credit, "%,d".format(it)), color = Color.Gray) }
                if (st.running) {
                    Button(
                        onClick = { ReserveService.stop(ctx) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                    ) { Text(stringResource(R.string.stop), fontSize = 18.sp) }
                } else if (s.usableAccounts.isEmpty()) {
                    Text(stringResource(R.string.need_account), color = Color.Gray, textAlign = TextAlign.Center)
                    Button(onClick = goAccounts, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.add_account), fontSize = 18.sp) }
                } else {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                            ReserveService.start(ctx)
                        },
                        enabled = s.lunch || s.dinner,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(stringResource(if (s.preview) R.string.start_preview else R.string.start), fontSize = 18.sp) }
                }
                OutlinedButton(onClick = { openSamad(ctx) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_samad)) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.meals), fontWeight = FontWeight.SemiBold)
                Toggle(stringResource(R.string.lunch), null, s.lunch, !st.running) { persist(s.copy(lunch = it)) }
                Toggle(stringResource(R.string.dinner), null, s.dinner, !st.running) { persist(s.copy(dinner = it)) }
                Toggle(stringResource(R.string.wait_next), stringResource(R.string.wait_next_hint), s.waitNextWeek, !st.running) { persist(s.copy(waitNextWeek = it)) }
                Toggle(stringResource(R.string.preview), stringResource(R.string.preview_hint), s.preview, !st.running) { persist(s.copy(preview = it)) }
                OutlinedTextField(
                    spend, { v ->
                        spend = v.filter { it.isDigit() }.take(9)
                        persist(s.copy(maxSpend = spend.toLongOrNull() ?: 0L))
                    },
                    Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.spend_limit)) },
                    supportingText = { Text(stringResource(R.string.spend_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !st.running,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AccountsTab(
    s: Settings, edit: List<Account>, setEdit: (List<Account>) -> Unit,
    shown: Set<String>, setShown: (Set<String>) -> Unit, persist: (Settings) -> Unit,
) {
    val st by RunState.state.collectAsState()
    val dirty = edit != s.accounts

    fun change(id: String, f: (Account) -> Account) { setEdit(edit.map { if (it.id == id) f(it) else it }) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.accounts_hint), fontSize = 13.sp, color = Color.Gray)
        edit.forEachIndexed { i, a ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (i == 0) R.string.account_main else R.string.account_n, i + 1) +
                                "  ·  " + stringResource(shortStatus(st.acc[a.id])),
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        )
                        Switch(a.enabled, { v -> change(a.id) { it.copy(enabled = v) } }, enabled = !st.running)
                    }
                    OutlinedTextField(
                        a.label, { v -> change(a.id) { it.copy(label = v.take(20)) } }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.name_optional)) }, enabled = !st.running,
                    )
                    OutlinedTextField(
                        a.username, { v -> change(a.id) { it.copy(username = v.filter { c -> c.isLetterOrDigit() }.take(20)) } },
                        Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.student_no)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !st.running,
                    )
                    OutlinedTextField(
                        a.password, { v -> change(a.id) { it.copy(password = v) } }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = if (a.id in shown) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !st.running,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton({ setShown(if (a.id in shown) shown - a.id else shown + a.id) }) {
                            Text(stringResource(if (a.id in shown) R.string.hide else R.string.show))
                        }
                        TextButton({ setEdit(edit.filter { it.id != a.id }) }, enabled = !st.running) { Text(stringResource(R.string.remove)) }
                    }
                }
            }
        }
        OutlinedButton({ setEdit(edit + Account()) }, Modifier.fillMaxWidth(), enabled = !st.running && edit.size < 8) { Text(stringResource(R.string.add_account)) }
        Button(
            onClick = {
                val clean = edit.map { it.copy(username = it.username.trim(), label = it.label.trim()) }
                setEdit(clean)
                persist(s.copy(accounts = clean))
            },
            enabled = dirty && !st.running,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.save)) }
        if (dirty) Text(stringResource(R.string.unsaved), fontSize = 12.sp, color = Color(0xFFB3261E))
        Text(stringResource(R.string.privacy), fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LogTab() {
    val lines by RunState.log.collectAsState()
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ clip.setText(AnnotatedString(lines.reversed().joinToString("\n"))) }, enabled = lines.isNotEmpty()) { Text(stringResource(R.string.copy_log)) }
            OutlinedButton({ RunState.clearLog() }, enabled = lines.isNotEmpty()) { Text(stringResource(R.string.clear_log)) }
        }
        Text(stringResource(R.string.log_hint), fontSize = 12.sp, color = Color.Gray)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (lines.isEmpty()) Text(stringResource(R.string.log_empty), color = Color.Gray)
            lines.forEach { Text(it, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray) }
        }
    }
}

@Composable
private fun Toggle(title: String, hint: String?, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (hint != null) Text(hint, fontSize = 12.sp, color = Color.Gray)
        }
        Switch(value, onChange, enabled = enabled)
    }
}
