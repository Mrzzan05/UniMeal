package ir.unimeal.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val problemWords = listOf("LOGIN_FAILED", "UNEXPECTED", "CERT_PROBLEM", "NETWORK", "offline", "WRONG_SELF")

@Composable
fun LogScreen() {
    val lines by RunState.log.collectAsState()
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ clip.setText(AnnotatedString(lines.reversed().joinToString("\n"))) }, enabled = lines.isNotEmpty()) {
                Text(stringResource(R.string.copy_log))
            }
            OutlinedButton({ RunState.clearLog() }, enabled = lines.isNotEmpty()) { Text(stringResource(R.string.clear_log)) }
        }
        Text(stringResource(R.string.log_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Section(modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (lines.isEmpty()) Text(stringResource(R.string.log_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                lines.forEach { line ->
                    val bad = problemWords.any { line.contains(it) }
                    Text(
                        line, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}
