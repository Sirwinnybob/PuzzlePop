package com.family.puzzlepop.play

import androidx.compose.runtime.Composable

/** Release no-op twin of the debug-only button, so release resources carry no debug strings. */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun DebugSolveButton(onClick: () -> Unit) = Unit
