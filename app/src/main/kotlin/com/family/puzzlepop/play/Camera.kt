package com.family.puzzlepop.play

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.family.puzzlepop.engine.Box
import kotlin.math.max
import kotlin.math.min

/** Maps world (image pixel) coordinates to screen pixels: `screen = world * scale + offset`. */
data class Camera(val scale: Float, val offsetX: Float, val offsetY: Float) {

    fun toWorld(screen: Offset): Offset = Offset((screen.x - offsetX) / scale, (screen.y - offsetY) / scale)

    fun toScreen(world: Offset): Offset = Offset(world.x * scale + offsetX, world.y * scale + offsetY)

    /**
     * Zooms by [zoom] about the screen point [pivot] (the world point under it stays put), then pans by
     * ([panX], [panY]) screen pixels. The resulting scale is clamped to [minScale]..[maxScale].
     */
    fun transformed(pivot: Offset, zoom: Float, panX: Float, panY: Float, minScale: Float, maxScale: Float): Camera {
        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
        val wx = (pivot.x - offsetX) / scale
        val wy = (pivot.y - offsetY) / scale
        return Camera(newScale, pivot.x + panX - wx * newScale, pivot.y + panY - wy * newScale)
    }

    /** Pans so the world point at the viewport centre stays inside [world] (the table can't be lost). */
    fun keepCentreInside(world: Box, viewport: Size): Camera {
        val cx = (viewport.width / 2 - offsetX) / scale
        val cy = (viewport.height / 2 - offsetY) / scale
        val ccx = cx.coerceIn(world.left, world.right)
        val ccy = cy.coerceIn(world.top, world.bottom)
        if (ccx == cx && ccy == cy) return this
        return Camera(scale, offsetX - (ccx - cx) * scale, offsetY - (ccy - cy) * scale)
    }

    /**
     * Kid mode's pan limit: along each axis, a table smaller than [area] (on screen) is centred in it, and a
     * bigger one is kept covering it, so no empty space opens past its edges. At the fitted zoom this is the
     * fitted camera, so the board can't be pushed around until it is zoomed in.
     */
    fun keepTableInView(world: Box, area: androidx.compose.ui.geometry.Rect): Camera {
        fun axis(lo: Float, hi: Float, off: Float, a0: Float, a1: Float): Float {
            val w0 = lo * scale + off
            val w1 = hi * scale + off
            return when {
                w1 - w0 <= a1 - a0 -> off + ((a0 + a1) - (w0 + w1)) / 2
                w0 > a0 -> off - (w0 - a0)
                w1 < a1 -> off + (a1 - w1)
                else -> off
            }
        }
        val ox = axis(world.left, world.right, offsetX, area.left, area.right)
        val oy = axis(world.top, world.bottom, offsetY, area.top, area.bottom)
        return if (ox == offsetX && oy == offsetY) this else Camera(scale, ox, oy)
    }

    companion object {
        /** The camera that shows all of [world] centred in [viewport], with [padding] screen px on every side. */
        fun fitIn(world: Box, area: androidx.compose.ui.geometry.Rect, padding: Float): Camera {
            val c = fit(world, Size(area.width, area.height), padding)
            return Camera(c.scale, c.offsetX + area.left, c.offsetY + area.top)
        }

        fun fit(world: Box, viewport: Size, padding: Float): Camera {
            val availW = max(1f, viewport.width - 2 * padding)
            val availH = max(1f, viewport.height - 2 * padding)
            val scale = min(availW / world.width, availH / world.height)
            val offsetX = (viewport.width - world.width * scale) / 2 - world.left * scale
            val offsetY = (viewport.height - world.height * scale) / 2 - world.top * scale
            return Camera(scale, offsetX, offsetY)
        }
    }
}
