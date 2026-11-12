package com.family.puzzlepop.kid

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.family.puzzlepop.R

/** Shown until midnight once the daily limit is used up. "+10 minutes" gives her more time today. */
@Composable
fun DoneForTodayScreen(onAddTime: (Int) -> Unit, onLeaveKidMode: () -> Unit) {
    TimeScreenFrame(
        gradient = listOf(colorResource(R.color.icon_sky_light), colorResource(R.color.icon_grape_light)),
        title = stringResource(R.string.time_done_title),
        subtitle = null,
        onAddTime = onAddTime,
        onEndBreak = null,
        onLeaveKidMode = onLeaveKidMode,
    )
}
