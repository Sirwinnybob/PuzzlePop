package com.family.puzzlepop.render

import android.graphics.Path
import com.family.puzzlepop.engine.Cubic

/**
 * Converts a chain of cubic segments to an [android.graphics.Path]. Image point p maps to
 * ((p.x - originX) * scale, (p.y - originY) * scale). The path is closed when the chain is non-empty.
 */
fun List<Cubic>.toAndroidPath(scale: Float, originX: Float, originY: Float): Path {
    val path = Path()
    if (isEmpty()) return path
    val first = this[0].p0
    path.moveTo((first.x - originX) * scale, (first.y - originY) * scale)
    for (c in this) {
        path.cubicTo(
            (c.c1.x - originX) * scale, (c.c1.y - originY) * scale,
            (c.c2.x - originX) * scale, (c.c2.y - originY) * scale,
            (c.p3.x - originX) * scale, (c.p3.y - originY) * scale,
        )
    }
    path.close()
    return path
}
