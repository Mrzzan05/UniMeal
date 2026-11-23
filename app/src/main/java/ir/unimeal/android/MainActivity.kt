package ir.unimeal.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notify.channels(this)
        RunState.init(applicationContext)
        Reminder.schedule(this, Vault(this).load()) // re-arm the weekly alarm (e.g. after a force-stop)
        setContent { Root() }
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val vault = remember { Vault(ctx) }
    var s by remember { mutableStateOf(vault.load()) }
    fun persist(n: Settings) { s = n; vault.save(n) }
    UniMealTheme(s.theme) { Shell(s, ::persist) }
}

@Composable
private fun Shell(s: Settings, persist: (Settings) -> Unit) {
    // Unsaved account edits live here so they survive swiping between pages.
    var edit by remember { mutableStateOf(s.accounts) }
    var shown by remember { mutableStateOf(setOf<String>()) }
    val pager = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    fun go(i: Int) { scope.launch { pager.animateScrollToPage(i) } }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Text(
            stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
        )
        // Swiping the page changes the tab; every page keeps its own scroll.
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                0 -> HomeScreen(s, persist) { go(1) }
                1 -> AccountsScreen(s, edit, { edit = it }, shown, { shown = it }, persist)
                2 -> LogScreen()
                else -> SettingsScreen(s, persist)
            }
        }
        NavigationBar {
            NavItem(pager.currentPage == 0, { go(0) }, Icons.Filled.Home, R.string.tab_home)
            NavItem(pager.currentPage == 1, { go(1) }, Icons.Filled.Person, R.string.tab_accounts)
            NavItem(pager.currentPage == 2, { go(2) }, Icons.Filled.Info, R.string.tab_log)
            NavItem(pager.currentPage == 3, { go(3) }, Icons.Filled.Settings, R.string.tab_settings)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int,
) {
    NavigationBarItem(selected, onClick, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) })
}
