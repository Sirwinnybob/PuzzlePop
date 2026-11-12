package com.family.puzzlepop.engine

import kotlinx.serialization.Serializable

/** A 2D point in piece-local or image coordinates. */
@Serializable
data class Pt(val x: Float, val y: Float)

/** A cubic Bezier segment from [p0] to [p3] with control points [c1] and [c2]. */
@Serializable
data class Cubic(val p0: Pt, val c1: Pt, val c2: Pt, val p3: Pt) {
    /** The same segment traversed in the opposite direction. */
    fun reversed(): Cubic = Cubic(p3, c2, c1, p0)
}

/** A chain of cubic segments describing one piece edge. */
typealias EdgeCurve = List<Cubic>

/** The same edge traversed in the opposite direction: segment order reversed, each segment reversed. */
fun EdgeCurve.reversedCurve(): EdgeCurve = asReversed().map { it.reversed() }

/** An axis-aligned rectangle. */
@Serializable
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A puzzle grid of [rows] by [cols] pieces. */
@Serializable
data class Grid(val rows: Int, val cols: Int) {
    val count: Int get() = rows * cols
}
