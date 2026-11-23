@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ir.unimeal.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun AccountsScreen(
    s: Settings, edit: List<Account>, setEdit: (List<Account>) -> Unit,
    shown: Set<String>, setShown: (Set<String>) -> Unit, persist: (Settings) -> Unit,
) {
    val st by RunState.state.collectAsState()
    val dirty = edit != s.accounts
    val savedIds = s.accounts.map { it.id }.toSet()
    val defaultId = s.defaultAccount?.id

    fun change(id: String, f: (Account) -> Account) { setEdit(edit.map { if (it.id == id) f(it) else it }) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.accounts_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        edit.forEachIndexed { i, a ->
            Section {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (i == 0) R.string.account_main else R.string.account_n, i + 1),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(stringResource(shortStatus(st.acc[a.id])), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    FilterChip(
                        selected = a.id == defaultId,
                        onClick = { if (a.id in savedIds) persist(s.copy(defaultAccountId = a.id)) },
                        enabled = a.id in savedIds,
                        label = { Text(stringResource(R.string.default_account)) },
                    )
                    Row {
                        TextButton({ setShown(if (a.id in shown) shown - a.id else shown + a.id) }) {
                            Text(stringResource(if (a.id in shown) R.string.hide else R.string.show))
                        }
                        TextButton({ setEdit(edit.filter { it.id != a.id }) }, enabled = !st.running) { Text(stringResource(R.string.remove)) }
                    }
                }
            }
        }
        OutlinedButton({ setEdit(edit + Account()) }, Modifier.fillMaxWidth(), enabled = !st.running && edit.size < 8) {
            Text(stringResource(R.string.add_account))
        }
        Button(
            onClick = {
                val clean = edit.map { it.copy(username = it.username.trim(), label = it.label.trim()) }
                setEdit(clean)
                persist(s.copy(accounts = clean))
            },
            enabled = dirty && !st.running,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.save)) }
        if (dirty) Text(stringResource(R.string.unsaved), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.default_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.privacy), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}
