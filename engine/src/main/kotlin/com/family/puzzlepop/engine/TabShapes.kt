package com.family.puzzlepop.engine

import kotlin.math.PI
import kotlin.math.sin

/** Random draws for one interior edge (always all drawn, so RNG consumption is style independent). */
internal class TabParams(
    val sideSign: Float,
    val centre01: Double,
    val height01: Double,
    val neckJitter01: Double,
    val headJitter01: Double,
    val wobble01: Double,
)

/**
 * Builds the tab-bearing edge curve from [a] to [b].
 *
 * The tab is described in edge-local coordinates: u runs along a->b from 0 to 1 and v is the
 * perpendicular offset as a fraction of the edge length (positive = left of a->b in image
 * coordinates, i.e. rotated +90 degrees). Six cubics: baseline, left flank, head left,
 * head right, right flank, baseline.
 *
 * Hard limits: the tab stays within u in [0.2, 0.8] and |v| <= 0.32.
 */
internal object TabShapes {
    private const val MAX_V = 0.32
    private const val U_MIN = 0.2
    private const val U_MAX = 0.8

    fun tabEdge(a: Pt, b: Pt, style: PieceStyle, p: TabParams): EdgeCurve {
        var neckHalf: Double
        var headHalf: Double
        var centre: Double
        var height: Double
        var wobbleAmp = 0.0
        when (style) {
            PieceStyle.CHUNKY -> {
                neckHalf = 0.10; headHalf = 0.17; centre = 0.5; height = 0.30
            }
            PieceStyle.CLASSIC -> {
                neckHalf = 0.10 * (1.0 + 0.1 * (2 * p.neckJitter01 - 1))
                headHalf = 0.17 * (1.0 + 0.1 * (2 * p.headJitter01 - 1))
                centre = 0.40 + 0.20 * p.centre01
                height = 0.22 + 0.05 * p.height01
            }
            PieceStyle.WOBBLY -> {
                neckHalf = 0.10 * (1.0 + 0.1 * (2 * p.neckJitter01 - 1))
                headHalf = 0.17 * (1.0 + 0.1 * (2 * p.headJitter01 - 1))
                centre = 0.35 + 0.30 * p.centre01
                height = 0.18 + 0.12 * p.height01
                wobbleAmp = 0.04 * (2 * p.wobble01 - 1)
            }
            PieceStyle.RIBBON, PieceStyle.WAVY_RIBBON, PieceStyle.FREEFORM, PieceStyle.HEARTS, PieceStyle.PARTY ->
                throw IllegalArgumentException("$style is cut by StyleCuts, not TabShapes")
        }
        // Enforce the hard limits.
        centre = centre.coerceIn(U_MIN + headHalf, U_MAX - headHalf)
        height = minOf(height, MAX_V - kotlin.math.abs(wobbleAmp))

        val s = p.sideSign.toDouble()
        val h = height
        val cu = centre
        val n = neckHalf
        val w = headHalf

        // Edge-local control polygon: (u, v) before the baseline wobble is added.
        fun uv(u: Double, v: Double) = doubleArrayOf(u, v * s)
        val seg = listOf(
            // baseline in
            arrayOf(uv(0.0, 0.0), uv((cu - n) / 3, 0.0), uv(2 * (cu - n) / 3, 0.0), uv(cu - n, 0.0)),
            // left flank: neck up and out to the widest part of the head
            arrayOf(uv(cu - n, 0.0), uv(cu - n, 0.25 * h), uv(cu - w, 0.30 * h), uv(cu - w, 0.65 * h)),
            // head, left half
            arrayOf(uv(cu - w, 0.65 * h), uv(cu - w, 0.90 * h), uv(cu - 0.5 * w, h), uv(cu, h)),
            // head, right half
            arrayOf(uv(cu, h), uv(cu + 0.5 * w, h), uv(cu + w, 0.90 * h), uv(cu + w, 0.65 * h)),
            // right flank
            arrayOf(uv(cu + w, 0.65 * h), uv(cu + w, 0.30 * h), uv(cu + n, 0.25 * h), uv(cu + n, 0.0)),
            // baseline out
            arrayOf(uv(cu + n, 0.0), uv(cu + n + (1 - cu - n) / 3, 0.0), uv(cu + n + 2 * (1 - cu - n) / 3, 0.0), uv(1.0, 0.0)),
        )

        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        // perp = (-dy, dx) has length == edge length, so v is a fraction of edge length.
        fun map(uvPt: DoubleArray, isEndpoint: Boolean): Pt {
            val u = uvPt[0]
            var v = uvPt[1]
            if (wobbleAmp != 0.0 && !isEndpoint) v += wobbleAmp * sin(2 * PI * u)
            return Pt((a.x + dx * u - dy * v).toFloat(), (a.y + dy * u + dx * v).toFloat())
        }

        val out = ArrayList<Cubic>(seg.size)
        for ((i, s4) in seg.withIndex()) {
            // Segment endpoints are shared between neighbours; compute them identically so the chain is exact.
            val p0 = if (i == 0) a else out[i - 1].p3
            val p3 = if (i == seg.size - 1) b else map(s4[3], false)
            out += Cubic(p0, map(s4[1], false), map(s4[2], false), p3)
        }
        return out
    }

    /** A single straight cubic from [a] to [b] with control points at 1/3 and 2/3. */
    fun straightEdge(a: Pt, b: Pt): EdgeCurve = listOf(
        Cubic(
            a,
            Pt(a.x + (b.x - a.x) / 3f, a.y + (b.y - a.y) / 3f),
            Pt(a.x + (b.x - a.x) * 2f / 3f, a.y + (b.y - a.y) * 2f / 3f),
            b,
        )
    )
}
