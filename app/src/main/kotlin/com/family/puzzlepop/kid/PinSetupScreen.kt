package com.family.puzzlepop.kid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.puzzlepop.PuzzlePopApp
import com.family.puzzlepop.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Choose a 4-6 digit PIN: type it, then type it again. First launch can't skip it (kid mode needs it);
 * in [changeMode] (behind the gate) a Cancel button is shown.
 */
@Composable
fun PinSetupScreen(onDone: () -> Unit, changeMode: Boolean = false, onCancel: () -> Unit = {}) {
    val settings = (LocalContext.current.applicationContext as PuzzlePopApp).container.settings
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf<String?>(null) } // set once the first entry is done
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    fun submit() {
        if (saving) return
        if (digits.length < 4) {
            message = R.string.pin_setup_too_short
            scope.launch { shake.shakeOnce() }
            return
        }
        val firstEntry = first
        if (firstEntry == null) {
            first = digits
            digits = ""
            message = null
        } else if (firstEntry == digits) {
            saving = true
            val pin = digits
            scope.launch {
                withContext(Dispatchers.Default) { settings.setPin(pin) }
                onDone()
            }
        } else {
            first = null
            digits = ""
            message = R.string.pin_setup_mismatch
            scope.launch { shake.shakeOnce() }
        }
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(
                    when {
                        first != null -> R.string.pin_setup_confirm_title
                        changeMode -> R.string.pin_change_title
                        else -> R.string.pin_setup_title
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.pin_setup_body),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.graphicsLayer { translationX = shake.value }) { PinDots(count = digits.length) }
            Text(
                message?.let { stringResource(it) } ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(28.dp),
            )
            PinPad(
                onDigit = { if (digits.length < 6) { digits += it; message = null } },
                onBackspace = { digits = digits.dropLast(1) },
                onOk = ::submit,
                okEnabled = digits.length >= 4 && !saving,
            )
            if (changeMode) {
                TextButton(onClick = onCancel, modifier = Modifier.height(52.dp)) {
                    Text(stringResource(R.string.pin_cancel), fontSize = 18.sp)
                }
            }
        }
    }
}
