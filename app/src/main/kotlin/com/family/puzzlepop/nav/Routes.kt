package com.family.puzzlepop.nav

import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
data class PlayRoute(val puzzleId: String, val kid: Boolean = false)

@Serializable
object PinSetupRoute

/** Kid mode's home: big picture tiles and the three puzzle sizes. Back does nothing here. */
@Serializable
object KidHomeRoute

/** Grown-up settings (time limits, kid piece range, PIN). Reached from the library behind the gate. */
@Serializable
object GrownUpSettingsRoute

/** About, privacy and open-source licenses, opened from grown-up settings. */
@Serializable
object AboutRoute

/** Hidden speed test, opened from grown-up settings (tap the title 7 times). */
@Serializable
object SpeedTestRoute
