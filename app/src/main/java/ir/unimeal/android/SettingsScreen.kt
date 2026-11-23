@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ir.unimeal.android

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(s: Settings, persist: (Settings) -> Unit) {
    val ctx = LocalContext.current
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var showTime by remember { mutableStateOf(false) }
    var upd by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<UpdateCheck.Result?>(null) }
    val scope = rememberCoroutineScope()
    var unrestricted by remember { mutableStateOf(Battery.isUnrestricted(ctx)) }
    LaunchedEffect(Unit) { while (true) { unrestricted = Battery.isUnrestricted(ctx); delay(2_000) } }

    /** Saves and re-arms the weekly alarm. */
    fun update(n: Settings) { persist(n); Reminder.schedule(ctx, n) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Section(stringResource(R.string.appearance), Modifier.enter(0)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to R.string.theme_system, 1 to R.string.theme_light, 2 to R.string.theme_dark).forEach { (v, label) ->
                    FilterChip(s.theme == v, { persist(s.copy(theme = v)) }, { Text(stringResource(label)) })
                }
            }
        }

        Section(stringResource(R.string.reminder), Modifier.enter(1)) {
            ToggleRow(stringResource(R.string.reminder_on), stringResource(R.string.reminder_hint), s.reminderOn) { on ->
                if (on && Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                update(s.copy(reminderOn = on))
            }
            if (s.reminderOn) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Saturday first, like the university week.
                    listOf(6, 7, 1, 2, 3, 4, 5).forEach { d ->
                        FilterChip(
                            s.reminderDay == d, { update(s.copy(reminderDay = d)) },
                            { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                FilledTonalButton({ showTime = true }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reminder_time, "%02d:%02d".format(s.reminderHour, s.reminderMinute)))
                }
            }
        }

        Section(stringResource(R.string.background), Modifier.enter(2)) {
            Text(
                stringResource(if (unrestricted) R.string.battery_ok else R.string.battery_warn),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton({ Battery.request(ctx) }, Modifier.fillMaxWidth()) {
                Text(stringResource(if (unrestricted) R.string.battery_check else R.string.battery_allow))
            }
            Text(stringResource(R.string.poll_title), fontSize = 14.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(45 to R.string.poll_45, 60 to R.string.poll_60, 120 to R.string.poll_120, 180 to R.string.poll_180).forEach { (v, label) ->
                    FilterChip(s.pollSeconds == v, { persist(s.copy(pollSeconds = v)) }, { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.poll_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section(stringResource(R.string.about), Modifier.enter(3)) {
            Text("UniMeal " + BuildConfig.VERSION_NAME, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.about_author, Repo.OWNER), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(upd, label = "update") { u ->
                when (u) {
                    null -> Spacer(Modifier.height(0.dp))
                    UPD_CHECKING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.upd_checking), Modifier.padding(start = 10.dp), fontSize = 13.sp)
                    }
                    else -> Text(u, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            val avail = result as? UpdateCheck.Result.Available
            if (avail != null) {
                if (avail.apkUrl != null) {
                    Button({ openUrl(ctx, avail.apkUrl) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.upd_download, avail.latest)) }
                }
                OutlinedButton({ openUrl(ctx, avail.pageUrl) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.upd_notes)) }
            }
            FilledTonalButton(
                {
                    upd = UPD_CHECKING; result = null
                    scope.launch {
                        val r = UpdateCheck.check(BuildConfig.VERSION_NAME)
                        result = r
                        upd = when (r) {
                            is UpdateCheck.Result.UpToDate -> ctx.getString(R.string.upd_up_to_date, r.current)
                            is UpdateCheck.Result.Available -> ctx.getString(R.string.upd_available, r.latest)
                            is UpdateCheck.Result.Failed -> ctx.getString(R.string.upd_failed, r.reason)
                        }
                    }
                },
                Modifier.fillMaxWidth(), enabled = upd != UPD_CHECKING,
            ) { Text(stringResource(R.string.upd_check)) }
            OutlinedButton({ openUrl(ctx, Repo.URL) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_github)) }
            Text(stringResource(R.string.privacy), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showTime) {
        val ts = rememberTimePickerState(s.reminderHour, s.reminderMinute, true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton({ update(s.copy(reminderHour = ts.hour, reminderMinute = ts.minute)); showTime = false }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton({ showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(ts) },
        )
    }
}

private const val UPD_CHECKING = "\u0000checking"

private fun openUrl(ctx: android.content.Context, url: String) {
    // Only this project's own GitHub pages are ever opened.
    val safe = Repo.safe(url)
    runCatching {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(safe)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
