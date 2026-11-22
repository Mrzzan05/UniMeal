package ir.unimeal.android

import android.Manifest
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notify.channels(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1B7F5C), secondary = Color(0xFF1B7F5C))) {
                Screen()
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
    RunState.R_STOPPED -> R.string.st_stopped
    else -> R.string.st_idle
}

@Composable
private fun Screen() {
    val ctx = LocalContext.current
    val vault = remember { Vault(ctx) }
    var s by remember { mutableStateOf(vault.load()) }
    var user by remember { mutableStateOf(s.username) }
    var pass by remember { mutableStateOf(s.password) }
    var show by remember { mutableStateOf(false) }
    var spend by remember { mutableStateOf(if (s.maxSpend > 0) s.maxSpend.toString() else "") }
    val st by RunState.state.collectAsState()

    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun persist(n: Settings) { s = n; vault.save(n) }
    fun saveAccount() = persist(s.copy(username = user.trim(), password = pass))

    Column(
        Modifier.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(statusText(st.line)), fontSize = 18.sp, textAlign = TextAlign.Center)
                st.credit?.let { Text(stringResource(R.string.credit, "%,d".format(it)), color = Color.Gray) }
                if (st.running) {
                    Button(
                        onClick = { ReserveService.stop(ctx) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                    ) { Text(stringResource(R.string.stop), fontSize = 18.sp) }
                } else {
                    Button(
                        onClick = {
                            saveAccount()
                            if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                            ReserveService.start(ctx)
                        },
                        enabled = user.isNotBlank() && pass.isNotEmpty() && (s.lunch || s.dinner),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(stringResource(if (s.preview) R.string.start_preview else R.string.start), fontSize = 18.sp) }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.account), fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    user, { user = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.student_no)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !st.running,
                )
                OutlinedTextField(
                    pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !st.running,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ show = !show }) { Text(stringResource(if (show) R.string.hide else R.string.show)) }
                    OutlinedButton({ saveAccount() }, enabled = !st.running) { Text(stringResource(R.string.save)) }
                }
                Text(stringResource(R.string.privacy), fontSize = 12.sp, color = Color.Gray)
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

        if (st.log.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.log), fontWeight = FontWeight.SemiBold)
                    st.log.take(12).forEach { Text(it, fontSize = 12.sp, color = Color.DarkGray) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
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
