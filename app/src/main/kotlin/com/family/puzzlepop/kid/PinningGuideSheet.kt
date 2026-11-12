package com.family.puzzlepop.kid

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.puzzlepop.R

/**
 * Plain steps for turning on Android's screen pinning with "Ask for PIN before unpinning", so a child
 * who pins-and-unpins can't get out. Shown before the first kid mode start; the settings screen reuses it
 * with [onStart] = null (then it only has a Done button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinningGuideSheet(onDismiss: () -> Unit, onStart: (() -> Unit)? = null) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_grownup), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
                Text(stringResource(R.string.pin_guide_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.pin_guide_intro), style = MaterialTheme.typography.bodyLarge)
            val steps = listOf(
                R.string.pin_guide_step1,
                R.string.pin_guide_step2,
                R.string.pin_guide_step3,
                R.string.pin_guide_step4,
                R.string.pin_guide_step5,
            )
            steps.forEachIndexed { i, res ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
                }
            }
            FilledTonalButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.pin_guide_open_settings), fontSize = 18.sp) }
            Button(
                onClick = onStart ?: onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(if (onStart != null) R.string.pin_guide_start else R.string.pin_guide_done), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
