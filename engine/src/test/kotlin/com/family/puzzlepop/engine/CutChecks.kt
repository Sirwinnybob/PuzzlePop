package com.family.puzzlepop.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import kotlin.math.abs
import kotlin.math.min

/** Shared geometry checks for the non-legacy cut styles (see the cut styles design, sections 5 and 9). */
object CutChecks {

    /** Per-edge knob envelope: along the edge u in [0.2, 0.8], perpendicular |v| <= 0.32 (fractions of chord length). */
    private const val KNOB_U_MIN = 0.2f
    private const val KNOB_U_MAX = 0.8f
    private const val KNOB_V_MAX = 0.32f

    /**
     * Pass as `baselineSlack` to [assertEnvelope] for styles whose knob-free baseline bends away from its chord
     * (fraction of chord length). Wavy ribbon's baseline follows its wave between corners, which sags off the
     * chord by up to ~0.07 on a 3-line grid. Every other style uses 0.
     */
    const val WAVY_BASELINE_SLACK = 0.08f

    /** Float noise on a chord-flat baseline; below this, a point is not part of a knob. */
    private const val V_NOISE = 0.005f

    private const val SAMPLES_PER_CUBIC = 12

    /** The geometry-check specs: 12 and 1000 pieces at 4:3, 300 pieces very wide and very tall; seeds 1, 2, 3. */
    @Suppress("FunctionName")
    fun SPECS_FOR(style: PieceStyle): List<PuzzleSpec> {
        val shapes = listOf(Triple(4000, 3000, 12), Triple(4000, 3000, 1000), Triple(4000, 1000, 300), Triple(1000, 4000, 300))
        return shapes.flatMap { (w, h, n) -> (1L..3L).map { seed -> PuzzleSpec(w, h, n, style, seed) } }
    }

    /** Every interior side equals its neighbour's matching side reversed, exactly (no tolerance). */
    fun assertSharedEdgesExact(l: PuzzleLayout) {
        val g = l.grid
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            val p = l.piece(r, c)
            if (c < g.cols - 1) assertEquals(p.sides.right, l.piece(r, c + 1).sides.left.reversedCurve(), "right of ($r,$c) ${l.spec}")
            if (r < g.rows - 1) assertEquals(p.sides.bottom, l.piece(r + 1, c).sides.top.reversedCurve(), "bottom of ($r,$c) ${l.spec}")
        }
    }

    /**
     * Two envelopes per interior edge:
     * - total: every sampled point lies within [maxFrac] x min(cellW, cellH) of the straight grid line the edge replaces;
     * - knob: in the edge's own chord frame, |v| <= 0.32 (+ [baselineSlack]), and anything that rises above
     *   [baselineSlack] lies within u in [0.2, 0.8].
     */
    fun assertEnvelope(l: PuzzleLayout, maxFrac: Float = 0.45f, baselineSlack: Float = 0f) {
        val g = l.grid
        val limit = maxFrac * min(l.cellWidth, l.cellHeight)
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            val p = l.piece(r, c)
            if (c < g.cols - 1) {
                val lineX = (c + 1) * l.cellWidth
                for (pt in samples(p.sides.right)) {
                    val d = abs(pt.x - lineX)
                    if (d > limit) fail<Unit>("right of ($r,$c) strays $d > $limit from x=$lineX at $pt, ${l.spec}")
                }
                assertKnobEnvelope(p.sides.right, "right of ($r,$c) ${l.spec}", baselineSlack)
            }
            if (r < g.rows - 1) {
                val lineY = (r + 1) * l.cellHeight
                for (pt in samples(p.sides.bottom)) {
                    val d = abs(pt.y - lineY)
                    if (d > limit) fail<Unit>("bottom of ($r,$c) strays $d > $limit from y=$lineY at $pt, ${l.spec}")
                }
                assertKnobEnvelope(p.sides.bottom, "bottom of ($r,$c) ${l.spec}", baselineSlack)
            }
        }
    }

    private fun assertKnobEnvelope(side: EdgeCurve, what: String, slack: Float) {
        val a = side.first().p0
        val b = side.last().p3
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        for (pt in samples(side)) {
            val u = ((pt.x - a.x) * dx + (pt.y - a.y) * dy) / len2
            val v = (-(pt.x - a.x) * dy + (pt.y - a.y) * dx) / len2
            if (abs(v) > KNOB_V_MAX + slack) fail<Unit>("$what: |v|=${abs(v)} too tall at $pt")
            if (abs(v) > slack + V_NOISE && (u < KNOB_U_MIN || u > KNOB_U_MAX)) fail<Unit>("$what: knob at u=$u outside [0.2,0.8]")
        }
    }

    /** No two different sides of one piece cross (sampled polylines; touching at the shared corner is allowed). */
    fun assertNoSelfCrossing(l: PuzzleLayout) {
        for (p in l.pieces) {
            val s = p.sides
            val polys = listOf(s.top, s.right, s.bottom, s.left).map { samples(it) }
            for (i in 0 until 4) for (j in i + 1 until 4) {
                val a = polys[i]
                val b = polys[j]
                for (m in 0 until a.size - 1) {
                    val a0 = a[m]
                    val a1 = a[m + 1]
                    for (n in 0 until b.size - 1) {
                        val b0 = b[n]
                        val b1 = b[n + 1]
                        // Adjacent sides meet at a corner; segments touching that corner are not a crossing.
                        if (a0 == b0 || a0 == b1 || a1 == b0 || a1 == b1) continue
                        if (segmentsCross(a0, a1, b0, b1)) {
                            fail<Unit>("piece ${p.id} (${p.row},${p.col}): side $i crosses side $j near $a0, ${l.spec}")
                        }
                    }
                }
            }
        }
    }

    /** Exactly the border cells are edge pieces: border sides are one cubic, interior sides are longer chains. */
    fun assertEdgeCountIsBorder(l: PuzzleLayout) {
        val g = l.grid
        for (p in l.pieces) {
            val s = p.sides
            assertEquals(p.row == 0, s.top.size == 1, "top of ${p.id}")
            assertEquals(p.col == g.cols - 1, s.right.size == 1, "right of ${p.id}")
            assertEquals(p.row == g.rows - 1, s.bottom.size == 1, "bottom of ${p.id}")
            assertEquals(p.col == 0, s.left.size == 1, "left of ${p.id}")
        }
        assertEquals(2 * g.cols + 2 * g.rows - 4, l.pieces.count { it.isEdge }, "isEdge count ${l.spec}")
    }

    /** Points along [side], [SAMPLES_PER_CUBIC] per cubic, first and last points exact. */
    fun samples(side: EdgeCurve): List<Pt> {
        val out = ArrayList<Pt>(side.size * SAMPLES_PER_CUBIC + 1)
        out += side.first().p0
        for (cubic in side) {
            for (i in 1..SAMPLES_PER_CUBIC) out += if (i == SAMPLES_PER_CUBIC) cubic.p3 else pointAt(cubic, i / SAMPLES_PER_CUBIC.toFloat())
        }
        return out
    }

    fun pointAt(c: Cubic, t: Float): Pt {
        val m = 1 - t
        val a = m * m * m
        val b = 3 * m * m * t
        val d = 3 * m * t * t
        val e = t * t * t
        return Pt(a * c.p0.x + b * c.c1.x + d * c.c2.x + e * c.p3.x, a * c.p0.y + b * c.c1.y + d * c.c2.y + e * c.p3.y)
    }

    private fun cross(o: Pt, a: Pt, b: Pt): Double =
        (a.x - o.x).toDouble() * (b.y - o.y) - (a.y - o.y).toDouble() * (b.x - o.x)

    /** Proper crossing: each segment's endpoints lie strictly on opposite sides of the other. */
    private fun segmentsCross(a0: Pt, a1: Pt, b0: Pt, b1: Pt): Boolean {
        if (maxOf(a0.x, a1.x) < minOf(b0.x, b1.x) || maxOf(b0.x, b1.x) < minOf(a0.x, a1.x)) return false
        if (maxOf(a0.y, a1.y) < minOf(b0.y, b1.y) || maxOf(b0.y, b1.y) < minOf(a0.y, a1.y)) return false
        val d1 = cross(b0, b1, a0)
        val d2 = cross(b0, b1, a1)
        val d3 = cross(a0, a1, b0)
        val d4 = cross(a0, a1, b1)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }
}
