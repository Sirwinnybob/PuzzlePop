package com.family.puzzlepop.render

import kotlin.math.max
import kotlin.math.min

/** Coarse colour buckets for sorting and filtering the tray. */
enum class ColorGroup { RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, LIGHT, DARK }

object ColorGroups {
    private const val GREY_SATURATION = 0.18f
    private const val LIGHT_VALUE = 0.6f

    /** The bucket for an average colour (0..255 channels). */
    fun of(r: Int, g: Int, b: Int): ColorGroup {
        val mx = max(r, max(g, b)) / 255f
        val mn = min(r, min(g, b)) / 255f
        val s = if (mx == 0f) 0f else (mx - mn) / mx
        if (s < GREY_SATURATION) return if (mx > LIGHT_VALUE) ColorGroup.LIGHT else ColorGroup.DARK
        val h = hue(r, g, b)
        return when {
            h >= 345f || h < 15f -> ColorGroup.RED
            h < 45f -> ColorGroup.ORANGE
            h < 70f -> ColorGroup.YELLOW
            h < 170f -> ColorGroup.GREEN
            h < 255f -> ColorGroup.BLUE
            else -> ColorGroup.PURPLE
        }
    }

    /** Hue in degrees, 0 until 360 (0 for greys). */
    fun hue(r: Int, g: Int, b: Int): Float {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = (mx - mn).toFloat()
        if (d == 0f) return 0f
        val h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return if (h < 0f) h + 360f else h
    }
}
