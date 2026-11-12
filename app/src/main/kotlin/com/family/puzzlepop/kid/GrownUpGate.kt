package com.family.puzzlepop.kid

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.family.puzzlepop.PuzzlePopApp
import com.family.puzzlepop.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PIN = 6
private val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/**
 * A small, calm button for grown-ups. It must be held for 2 seconds (a ring fills around it); a quick tap
 * only starts the ring. The moment the ring is full it calls [onHeld] (an exit that handles its own check),
 * or else opens [GrownUpGateDialog] and calls [onUnlocked] once it passes.
 */
@Composable
fun GrownUpButton(modifier: Modifier = Modifier, onHeld: (() -> Unit)? = null, onUnlocked: () -> Unit) {
    val currentHeld by rememberUpdatedState(onHeld)
    var showGate by rememberSaveable { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val label = stringResource(R.string.grownup_button_a11y)

    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val hold = HoldToOpen()
                    hold.down(SystemClock.uptimeMillis())
                    coroutineScope {
                        // Animate the ring from the frame clock while the finger is down. The gate opens the
                        // moment the ring is full (an unbroken hold); letting go or sliding off before that
                        // cancels this loop, so nothing opens.
                        val ring = launch {
                            while (true) {
                                val full = androidx.compose.runtime.withFrameMillis {
                                    val now = SystemClock.uptimeMillis()
                                    progress = hold.progress(now)
                                    hold.isFull(now)
                                }
                                if (full) {
                                    val held = currentHeld
                                    if (held != null) held() else showGate = true
                                    break
                                }
                            }
                        }
                        tryAwaitRelease()
                        ring.cancel()
                        hold.up(SystemClock.uptimeMillis())
                    }
                    progress = 0f
                })
            }
            .drawBehind {
                val w = 2.dp.toPx()
                val inset = w / 2
                val arcSize = Size(size.width - w, size.height - w)
                drawCircle(trackColor, radius = size.minDimension / 2 - inset, style = Stroke(width = 1.dp.toPx()))
                if (progress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = w, cap = StrokeCap.Round),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_grownup),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp).graphicsLayer { alpha = 0.75f },
        )
    }

    if (showGate) {
        GrownUpGateDialog(
            onDismiss = { showGate = false },
            onUnlocked = { showGate = false; onUnlocked() },
        )
    }
}

/** Fingerprint/face/phone unlock (when available) or the app PIN. Wrong PINs shake gently; no lockout. */
@Composable
fun GrownUpGateDialog(onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity ?: context.findFragmentActivity()
    val settings = (context.applicationContext as PuzzlePopApp).container.settings
    val scope = rememberCoroutineScope()
    val currentUnlocked by rememberUpdatedState(onUnlocked)

    val biometricAvailable = remember(context) {
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
    // PIN lost (corrupt settings, no mirror): the device credential is the only check, then a new PIN is set.
    val hasPin by settings.hasPin.collectAsState(initial = true)
    var digits by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    fun launchBiometric() {
        val act = activity ?: return
        val prompt = BiometricPrompt(
            act,
            ContextCompat.getMainExecutor(act),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    currentUnlocked()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(act.getString(R.string.grownup_biometric_title))
            .setSubtitle(act.getString(R.string.grownup_biometric_subtitle))
            .setAllowedAuthenticators(AUTHENTICATORS) // no negative button when DEVICE_CREDENTIAL is allowed
            .build()
        prompt.authenticate(info)
    }

    fun submit() {
        if (checking || digits.length < 4) return
        checking = true
        val attempt = digits
        scope.launch {
            val ok = withContext(Dispatchers.Default) { settings.checkPin(attempt) }
            checking = false
            if (ok) {
                currentUnlocked()
            } else {
                wrong = true
                digits = ""
                shake.shakeOnce()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 380.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    stringResource(R.string.grownup_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                if (!hasPin) {
                    Text(
                        stringResource(R.string.grownup_lost_pin),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        // No device lock at all means nothing else can protect her either; let the parent through.
                        onClick = if (biometricAvailable) ::launchBiometric else ({ currentUnlocked() }),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            stringResource(if (biometricAvailable) R.string.grownup_use_biometric else R.string.grownup_lost_pin_continue),
                            fontSize = 18.sp,
                        )
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.height(52.dp)) {
                        Text(stringResource(R.string.grownup_close), fontSize = 18.sp)
                    }
                    return@Column
                }
                if (biometricAvailable) {
                    Button(
                        onClick = ::launchBiometric,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.grownup_use_biometric), fontSize = 18.sp) }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.grownup_or_pin),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Box(Modifier.graphicsLayer { translationX = shake.value }) {
                    PinDots(count = digits.length)
                }
                Text(
                    if (wrong) stringResource(R.string.grownup_wrong_pin) else "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(24.dp),
                )
                PinPad(
                    onDigit = { if (!checking && digits.length < MAX_PIN) { digits += it; wrong = false } },
                    onBackspace = { if (!checking) digits = digits.dropLast(1) },
                    onOk = ::submit,
                    okEnabled = digits.length >= 4 && !checking,
                )
                TextButton(onClick = onDismiss, modifier = Modifier.height(52.dp)) {
                    Text(stringResource(R.string.grownup_close), fontSize = 18.sp)
                }
            }
        }
    }
}


internal suspend fun Animatable<Float, *>.shakeOnce() {
    for (target in floatArrayOf(-18f, 16f, -12f, 8f, -4f, 0f)) animateTo(target, tween(55))
}

/** One dot per digit entered, up to the longest PIN; the dots after the entered ones are hollow. */
@Composable
internal fun PinDots(count: Int, modifier: Modifier = Modifier) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    val a11y = stringResource(R.string.pin_entered_a11y, count)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.height(28.dp).semantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(MAX_PIN) { i ->
            Box(
                Modifier
                    .size(if (i < count) 20.dp else 14.dp)
                    .clip(CircleShape)
                    .background(if (i < count) filled else empty),
            )
        }
    }
}

/** Big-key number pad: 1-9, backspace, 0, OK. */
@Composable
internal fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onOk: () -> Unit,
    okEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in listOf("123", "456", "789")) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d -> PadKey(onClick = { onDigit(d) }) { Text(d.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val back = stringResource(R.string.pin_backspace_a11y)
            PadKey(onClick = onBackspace, plain = true, label = back) {
                Icon(painterResource(R.drawable.ic_backspace), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
            }
            PadKey(onClick = { onDigit('0') }) { Text("0", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            PadKey(onClick = onOk, enabled = okEnabled, plain = true) {
                Text(
                    stringResource(R.string.pin_ok),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (okEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun PadKey(
    onClick: () -> Unit,
    enabled: Boolean = true,
    plain: Boolean = false,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (plain) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
