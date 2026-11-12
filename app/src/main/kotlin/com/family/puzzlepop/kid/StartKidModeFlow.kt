package com.family.puzzlepop.kid

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.puzzlepop.PuzzlePopApp
import com.family.puzzlepop.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val STEP_NO_LOCK = 0
private const val STEP_CHECK_GUIDE = 1
private const val STEP_GUIDE = 2

/**
 * The grown-up's "Start kid mode" sequence: a friendly warning when the tablet has no screen lock (still
 * allowed), the pinning guide the first time, then [onStarted]. Kid mode is switched on here; the kid home
 * asks the system to pin the app when it opens.
 */
@Composable
fun StartKidModeFlow(onCancel: () -> Unit, onStarted: () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as PuzzlePopApp).container.settings
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(if (ScreenPinning.hasScreenLock(context)) STEP_CHECK_GUIDE else STEP_NO_LOCK) }

    fun begin() {
        scope.launch {
            ScreenPinning.resetAttempts()
            settings.setPinningGuideSeen(true)
            settings.setKidModeActive(true)
            onStarted()
        }
    }

    when (step) {
        STEP_NO_LOCK -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.kid_no_lock_title)) },
            text = { Text(stringResource(R.string.kid_no_lock_body), fontSize = 18.sp) },
            confirmButton = {
                TextButton(onClick = { step = STEP_CHECK_GUIDE }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.kid_no_lock_continue), fontSize = 18.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.kid_no_lock_cancel), fontSize = 18.sp)
                }
            },
        )
        STEP_CHECK_GUIDE -> LaunchedEffect(Unit) {
            if (settings.pinningGuideSeen.first()) begin() else step = STEP_GUIDE
        }
        else -> PinningGuideSheet(onDismiss = onCancel, onStart = ::begin)
    }
}
