package ir.unimeal.android

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal fun statusText(line: Int) = when (line) {
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
    RunState.R_OFFLINE -> R.string.st_offline
    RunState.R_STOPPED -> R.string.st_stopped
    else -> R.string.st_idle
}

private fun isProblem(line: Int) = line in setOf(
    RunState.R_LOGIN, RunState.R_CERT, RunState.R_NET, RunState.R_ODD, RunState.R_SELF,
    RunState.R_POOR, RunState.R_OVER, RunState.R_FAILED,
)

private fun heroColor(line: Int, running: Boolean): Color = when {
    line == RunState.R_OFFLINE -> Color(0xFFB26A00)
    isProblem(line) -> Color(0xFFB3261E)
    line == RunState.R_DONE || line == RunState.R_PREVIEW_DONE -> Color(0xFF2E7D32)
    running -> Color(0xFF0F6E8C)
    else -> Color(0xFF1B7F5C)
}

private fun heroIcon(line: Int, running: Boolean): ImageVector = when {
    line == RunState.R_OFFLINE || isProblem(line) -> Icons.Filled.Warning
    line == RunState.R_DONE || line == RunState.R_PREVIEW_DONE -> Icons.Filled.Check
    running -> Icons.Filled.Refresh
    else -> Icons.Filled.PlayArrow
}

@Composable
private fun Pulse(active: Boolean, icon: ImageVector, problem: Boolean, line: Int) {
    val t = rememberInfiniteTransition(label = "pulse")
    val ring1 by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing)), label = "ring1",
    )
    val ring2 by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), initialStartOffset = StartOffset(900)), label = "ring2",
    )
    val pop = rememberPop(icon)
    val shake = rememberShake(problem)
    val spin = rememberSpin(active && icon == Icons.Filled.Refresh)
    Box(Modifier.size(96.dp).graphicsLayer { translationX = shake.dp.toPx() }, contentAlignment = Alignment.Center) {
        if (active) {
            for (r in listOf(ring1, ring2)) {
                Box(Modifier.size(56.dp).graphicsLayer { scaleX = 1f + r * 0.7f; scaleY = 1f + r * 0.7f; alpha = (1f - r) * 0.35f }
                    .clip(CircleShape).background(Color.White))
            }
        }
        Box(
            Modifier.size(60.dp).graphicsLayer { scaleX = pop; scaleY = pop }.clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = spin })
        }
    }
}

@Composable
fun HomeScreen(s: Settings, persist: (Settings) -> Unit, goAccounts: () -> Unit) {
    val ctx = LocalContext.current
    val st by RunState.state.collectAsState()
    var spend by remember { mutableStateOf(if (s.maxSpend > 0) s.maxSpend.toString() else "") }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var unrestricted by remember { mutableStateOf(Battery.isUnrestricted(ctx)) }
    LaunchedEffect(Unit) { while (true) { unrestricted = Battery.isUnrestricted(ctx); delay(2_000) } }

    fun start() {
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        ReserveService.start(ctx)
    }

    val base by animateColorAsState(heroColor(st.line, st.running), tween(600), label = "hero")

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(base, base.copy(alpha = 0.78f)))).animateContentSize().enter(0),
        ) {
            Confetti(st.line == RunState.R_DONE, Modifier.matchParentSize())
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pulse(st.running, heroIcon(st.line, st.running), isProblem(st.line) || st.line == RunState.R_OFFLINE, st.line)
                Text(
                    stringResource(statusText(st.line)), color = Color.White, fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                )
                st.credit?.let { c ->
                    val shown by animateIntAsState(c.toInt().coerceAtLeast(0), tween(900), label = "credit")
                    Text(stringResource(R.string.credit, "%,d".format(shown)), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp) }
                Spacer(Modifier.height(4.dp))
                val btn = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = base)
                if (st.running) {
                    val src = remember { MutableInteractionSource() }; val sc = rememberPressScale(src)
                    Button({ ReserveService.stop(ctx) }, Modifier.fillMaxWidth().height(56.dp).scale(sc), colors = btn, interactionSource = src) {
                        Icon(Icons.Filled.Close, null); Text(stringResource(R.string.stop), Modifier.padding(start = 8.dp), fontSize = 18.sp)
                    }
                } else if (s.usableAccounts.isEmpty()) {
                    val src = remember { MutableInteractionSource() }; val sc = rememberPressScale(src)
                    Button(goAccounts, Modifier.fillMaxWidth().height(56.dp).scale(sc), colors = btn, interactionSource = src) {
                        Text(stringResource(R.string.add_account), fontSize = 18.sp)
                    }
                } else {
                    val src = remember { MutableInteractionSource() }; val sc = rememberPressScale(src)
                    Button({ start() }, Modifier.fillMaxWidth().height(56.dp).scale(sc), enabled = s.lunch || s.dinner, colors = btn, interactionSource = src) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Text(stringResource(if (s.preview) R.string.start_preview else R.string.start), Modifier.padding(start = 8.dp), fontSize = 18.sp)
                    }
                }
            }
        }

        FilledTonalButton(
            { ctx.startActivity(SamadActivity.intent(ctx, s.defaultAccount?.id)) },
            Modifier.fillMaxWidth().height(48.dp).enter(1),
        ) { Text(stringResource(R.string.open_samad)) }

        AnimatedVisibility(!st.running && st.wouldReserve.isNotEmpty()) {
            Section(stringResource(R.string.would_reserve)) {
                st.wouldReserve.forEach { Text(it) }
                Button({ persist(s.copy(preview = false)); start() }, Modifier.fillMaxWidth().height(50.dp)) {
                    Text(stringResource(R.string.reserve_now))
                }
            }
        }

        AnimatedVisibility(!unrestricted) {
            Section(stringResource(R.string.battery_title)) {
                Text(stringResource(R.string.battery_warn), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton({ Battery.request(ctx) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.battery_allow)) }
            }
        }

        Section(stringResource(R.string.meals), Modifier.enter(2)) {
            ToggleRow(stringResource(R.string.lunch), null, s.lunch, !st.running) { persist(s.copy(lunch = it)) }
            ToggleRow(stringResource(R.string.dinner), null, s.dinner, !st.running) { persist(s.copy(dinner = it)) }
            ToggleRow(stringResource(R.string.wait_next), stringResource(R.string.wait_next_hint), s.waitNextWeek, !st.running) { persist(s.copy(waitNextWeek = it)) }
            ToggleRow(stringResource(R.string.preview), stringResource(R.string.preview_hint), s.preview, !st.running) { persist(s.copy(preview = it)) }
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
        Spacer(Modifier.height(8.dp))
    }
}
