package com.family.puzzlepop.kid

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.puzzlepop.R
import com.family.puzzlepop.play.formatElapsed

/** A countdown shown to a child: whole seconds, rounded up, as m:ss. */
internal fun formatCountdown(ms: Long): String = formatElapsed(((ms.coerceAtLeast(0) + 999) / 1000) * 1000)

/**
 * The calm full-screen break: a sleepy moon, "Time for a break!" and how long until play is back.
 * It covers whatever is below and swallows every touch; only the grown-up button works.
 */
@Composable
fun BreakScreen(
    endsAtEpochMs: Long,
    now: StateFlow<Long>,
    onAddTime: (Int) -> Unit,
    onEndBreak: () -> Unit,
    onLeaveKidMode: () -> Unit,
) {
    val nowEpochMs by now.collectAsState() // collected here so only this screen recomposes each second
    TimeScreenFrame(
        gradient = listOf(colorResource(R.color.icon_grape_light), colorResource(R.color.icon_sky_light)),
        title = stringResource(R.string.time_break_title),
        subtitle = stringResource(R.string.time_break_back, formatCountdown(endsAtEpochMs - nowEpochMs)),
        onAddTime = onAddTime,
        onEndBreak = onEndBreak,
        onLeaveKidMode = onLeaveKidMode,
    )
}

/** Shared by [BreakScreen] and [DoneForTodayScreen]. [onEndBreak] null hides "End break". */
@Composable
internal fun TimeScreenFrame(
    gradient: List<Color>,
    title: String,
    subtitle: String?,
    onAddTime: (Int) -> Unit,
    onEndBreak: (() -> Unit)?,
    onLeaveKidMode: () -> Unit,
) {
    BackHandler {}
    val bob by rememberInfiniteTransition(label = "moon").animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "bob",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient))
            // Being a pointer-input node on top is enough: nothing underneath receives touches.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 24.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_moon),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(240.dp).graphicsLayer { translationY = bob },
            )
            Text(
                title,
                fontSize = 46.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = colorResource(R.color.icon_grape_dark),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = colorResource(R.color.icon_ink),
                )
            }
        }
        var options by remember { mutableStateOf(false) }
        GrownUpButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            onUnlocked = { options = true },
        )
        if (options) {
            GrownUpOptionsDialog(
                onDismiss = { options = false },
                onAddTime = { options = false; onAddTime(10) },
                onEndBreak = onEndBreak?.let { f -> { options = false; f() } },
                onLeaveKidMode = { options = false; onLeaveKidMode() },
            )
        }
    }
}

/** What a grown-up can do after unlocking on the break / done screens. */
@Composable
private fun GrownUpOptionsDialog(
    onDismiss: () -> Unit,
    onAddTime: () -> Unit,
    onEndBreak: (() -> Unit)?,
    onLeaveKidMode: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionButton(R.drawable.ic_add_time, stringResource(R.string.time_add_10), onAddTime, primary = true)
                if (onEndBreak != null) OptionButton(R.drawable.ic_play, stringResource(R.string.time_end_break), onEndBreak)
                OptionButton(R.drawable.ic_kid_mode, stringResource(R.string.time_leave_kid), onLeaveKidMode)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.grownup_close), fontSize = 18.sp)
            }
        },
    )
}

@Composable
private fun OptionButton(@DrawableRes icon: Int, text: String, onClick: () -> Unit, primary: Boolean = false) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
    val m = Modifier.fillMaxWidth().heightIn(min = 60.dp)
    if (primary) Button(onClick = onClick, modifier = m) { content() } else FilledTonalButton(onClick = onClick, modifier = m) { content() }
}
