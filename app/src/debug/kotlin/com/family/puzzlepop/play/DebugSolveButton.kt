package com.family.puzzlepop.play

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.family.puzzlepop.R

/** Debug builds only: jumps the puzzle to one move from done. Release has a no-op twin with no string resource. */
@Composable
internal fun DebugSolveButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(stringResource(R.string.debug_solve_all_but_one)) }
}
