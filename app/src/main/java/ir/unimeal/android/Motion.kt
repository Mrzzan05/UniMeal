package ir.unimeal.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Springy "press" feedback: the element shrinks a little while a finger is down. */
@Composable
fun rememberPressScale(source: MutableInteractionSource, pressed: Float = 0.94f): Float {
    val down by source.collectIsPressedAsState()
    val v by animateFloatAsState(
        if (down) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press",
    )
    return v
}

/** Slides and fades a block in, [index] × 70 ms after the page appears (staggered entrance). */
fun Modifier.enter(index: Int): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 70L); shown = true }
    val p by animateFloatAsState(
        if (shown) 1f else 0f, tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "enter",
    )
    graphicsLayer { alpha = p; translationY = (1f - p) * 48.dp.toPx() }
}

/** Page-swipe polish: the page shrinks and fades slightly as it slides away. */
fun Modifier.pagerParallax(pager: PagerState, page: Int): Modifier = graphicsLayer {
    val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
    alpha = 1f - 0.45f * off
    val s = 1f - 0.06f * off
    scaleX = s; scaleY = s
}

/** Icon pops in with a spring whenever [key] changes. */
@Composable
fun rememberPop(key: Any?): Float {
    val a = remember { Animatable(1f) }
    LaunchedEffect(key) {
        a.snapTo(0.4f)
        a.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    return a.value
}

/** Horizontal "no" shake, played each time [trigger] turns true. */
@Composable
fun rememberShake(trigger: Boolean): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            for (x in listOf(-14f, 12f, -9f, 6f, -3f, 0f)) a.animateTo(x, tween(55, easing = LinearEasing))
        }
    }
    return a.value
}

/** Endless 0..360 rotation while [on]. */
@Composable
fun rememberSpin(on: Boolean): Float {
    if (!on) return 0f
    val t = rememberInfiniteTransition(label = "spin")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "rot")
    return r
}

/** Short burst of coloured confetti over its parent, played once each time [play] turns true. */
@Composable
fun Confetti(play: Boolean, modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(play) {
        if (play) { p.snapTo(0f); p.animateTo(1f, tween(1800, easing = LinearEasing)) }
    }
    if (p.value <= 0f || p.value >= 1f) return
    val colors = remember { listOf(Color(0xFFFFC107), Color(0xFFFF5252), Color(0xFF40C4FF), Color(0xFFB2FF59), Color(0xFFE040FB), Color.White) }
    Canvas(modifier) {
        val t = p.value
        val cx = size.width / 2f
        val cy = size.height * 0.35f
        for (i in 0 until 42) {
            val ang = (i * 137.5f) * (Math.PI.toFloat() / 180f)
            val speed = 0.35f + (i % 7) * 0.09f
            val x = cx + cos(ang) * speed * size.width * t
            val y = cy + sin(ang) * speed * size.width * t * 0.8f + t * t * size.height * 0.55f
            drawCircle(colors[i % colors.size].copy(alpha = 1f - t), radius = 4.dp.toPx() + (i % 3) * 1.5f, center = Offset(x, y))
        }
    }
}
