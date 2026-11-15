package com.family.puzzlepop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * True on a phone held sideways: too short to stack a screen's parts, but wide enough to put them side by
 * side. Tablets (about 700 dp tall in landscape) are never short.
 */
@Composable
fun isShortLandscape(): Boolean {
    val c = LocalConfiguration.current
    return c.screenHeightDp < 500 && c.screenWidthDp > c.screenHeightDp
}

/** True on a phone held upright (narrow portrait), where wide tablet rows have to wrap or stack. */
@Composable
fun isNarrowPortrait(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp < 600 && c.screenHeightDp > c.screenWidthDp
}
