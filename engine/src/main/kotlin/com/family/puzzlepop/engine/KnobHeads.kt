package com.family.puzzlepop.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The head drawn on a knob (and its matching hole) by [knobEdge]. Order matters: PARTY picks by ordinal. */
internal enum class KnobHead { ROUND, HEART, STAR }

/**
 * A centred knob from [a] to [b]: a straight baseline to the left neck point, the [head] chain, and a straight
 * baseline from the right neck point to [b]. [sideSign] +1 puts the knob on the edge-local +v side
 * (perp = (-dy, dx)), -1 on the other. [scale] (at most 1) shrinks the whole head about the neck centre
 * (u 0.5, v 0), for cells too elongated for a full-size head to fit the 0.45 x cell envelope.
 *
 * Every head fits the knob envelope (u in [0.2, 0.8], v <= [KnobHeads.MAX_V]) and keeps its neck narrower than
 * its head, so pieces lock. The endpoints are exactly [a] and [b], and segments share endpoints exactly.
 */
internal fun knobEdge(a: Pt, b: Pt, head: KnobHead, sideSign: Float, scale: Double = 1.0): EdgeCurve {
    val chain = KnobHeads.chain(head)
    val s = sideSign.toDouble()
    val dx = (b.x - a.x).toDouble()
    val dy = (b.y - a.y).toDouble()
    // perp = (-dy, dx) has the chord's length, so v is a fraction of chord length.
    fun map(u0: Double, v0: Double): Pt {
        val u = 0.5 + (u0 - 0.5) * scale
        val v = v0 * scale * s
        return Pt((a.x + dx * u - dy * v).toFloat(), (a.y + dy * u + dx * v).toFloat())
    }
    val neckL = map(chain.first()[0], chain.first()[1])
    val neckR = map(chain.last()[6], chain.last()[7])
    val out = ArrayList<Cubic>(chain.size + 2)
    out += TabShapes.straightEdge(a, neckL)
    for (seg in chain) {
        // Shared segment endpoints are reused, so the chain is exactly continuous.
        val p3 = if (seg === chain.last()) neckR else map(seg[6], seg[7])
        out += Cubic(out.last().p3, map(seg[2], seg[3]), map(seg[4], seg[5]), p3)
    }
    out += TabShapes.straightEdge(neckR, b)
    return out
}

/**
 * Head outlines in edge-local (u, v), v pointing out of the knob, as cubics `[u0, v0, u1, v1, u2, v2, u3, v3]`
 * from the left neck point on the baseline to the right neck point. Each head is drawn for its left half up to
 * its top-centre point (u 0.5) and mirrored, so every head is symmetric.
 */
internal object KnobHeads {
    /** Highest v any head reaches (the knob envelope allows 0.32). */
    const val MAX_V = 0.315

    // ROUND: the classic round knob, Ribbon's uniform tab (neck 0.10, head 0.17, height 0.26).
    private const val ROUND_NECK = 0.10
    private const val ROUND_HEAD = 0.17
    private const val ROUND_HEIGHT = 0.26

    // HEART: a short stem that opens into a heart, point at the neck, two lobes outward.
    private const val HEART_NECK = 0.065 // neck half width
    private const val HEART_STEM = 0.035 // the neck rises straight this far, then the heart's sides open out
    private const val HEART_HALF = 0.175 // half width at the widest point
    private const val HEART_WIDE_V = 0.215 // where it is widest: high up, like a drawn heart
    private const val HEART_LOBE_U = 0.088 // lobe tops sit this far either side of the middle
    private const val HEART_TOP = MAX_V // lobe tops
    private const val HEART_DIP = 0.24 // the notch between the lobes

    // STAR: an upright five-point star with rounded tips, on a short neck between its two feet. The feet are a
    // little shorter than the other points, which buys height for a bigger star.
    private const val STAR_R = 0.157 // outer radius (in v; u is stretched by STAR_STRETCH)
    private const val STAR_FEET = 0.95 // foot radius as a fraction of STAR_R
    private const val STAR_INNER = 0.50 // inner radius as a fraction of STAR_R: chubby, friendly arms
    private const val STAR_STRETCH = 1.18 // slight horizontal stretch so the star is as wide as the other heads
    private const val STAR_CENTRE = MAX_V - STAR_R // centre v, so the top point touches MAX_V
    private const val STAR_NECK = 0.055 // neck half width
    private const val STAR_TIP = 0.028 // tip rounding: handle length at each outer point
    private const val STAR_FOOT_TIP = 0.014 // the feet have short inner arms, so they get a smaller rounding
    private const val STAR_ARM = 0.35 // handle at each inner corner, as a fraction of the arm

    private val ROUND_CHAIN = mirrored(roundLeft())
    private val HEART_CHAIN = mirrored(heartLeft())
    private val STAR_CHAIN = mirrored(starLeft())

    fun chain(head: KnobHead): List<DoubleArray> = when (head) {
        KnobHead.ROUND -> ROUND_CHAIN
        KnobHead.HEART -> HEART_CHAIN
        KnobHead.STAR -> STAR_CHAIN
    }

    /** Adds the right half: the left half reversed and reflected in u = 0.5. */
    private fun mirrored(left: List<DoubleArray>): List<DoubleArray> =
        left + left.asReversed().map { s ->
            doubleArrayOf(1 - s[6], s[7], 1 - s[4], s[5], 1 - s[2], s[3], 1 - s[0], s[1])
        }

    private fun seg(vararg p: Double) = p

    private fun roundLeft(): List<DoubleArray> {
        val n = ROUND_NECK
        val w = ROUND_HEAD
        val h = ROUND_HEIGHT
        return listOf(
            seg(0.5 - n, 0.0, 0.5 - n, 0.25 * h, 0.5 - w, 0.30 * h, 0.5 - w, 0.65 * h),
            seg(0.5 - w, 0.65 * h, 0.5 - w, 0.90 * h, 0.5 - 0.5 * w, h, 0.5, h),
        )
    }

    private fun heartLeft(): List<DoubleArray> {
        val k = 0.5523 // quarter-circle handle ratio
        val neckU = 0.5 - HEART_NECK
        val wideU = 0.5 - HEART_HALF
        val lobeU = 0.5 - HEART_LOBE_U
        val lobeRiseV = HEART_TOP - HEART_WIDE_V
        return listOf(
            // Straight stem up from the baseline.
            seg(neckU, 0.0, neckU, HEART_STEM / 3, neckU, 2 * HEART_STEM / 3, neckU, HEART_STEM),
            // The heart's lower side: opens out diagonally, bulging, to its widest point (vertical tangent there).
            seg(neckU, HEART_STEM, neckU - 0.06, HEART_STEM + 0.05, wideU, HEART_WIDE_V - 0.085, wideU, HEART_WIDE_V),
            // Outer lobe: a rounded quarter turn up to the lobe top (horizontal tangent).
            seg(wideU, HEART_WIDE_V, wideU, HEART_WIDE_V + k * lobeRiseV, lobeU - k * (HEART_HALF - HEART_LOBE_U), HEART_TOP, lobeU, HEART_TOP),
            // Inner lobe: over and down into the notch, arriving vertically (a cusp with its mirror).
            seg(lobeU, HEART_TOP, lobeU + 0.6 * HEART_LOBE_U, HEART_TOP, 0.5, HEART_DIP + 0.06, 0.5, HEART_DIP),
        )
    }

    private fun starLeft(): List<DoubleArray> {
        val inner = STAR_INNER * STAR_R
        fun at(deg: Double, r: Double): DoubleArray {
            val t = deg * PI / 180
            return doubleArrayOf(0.5 + STAR_STRETCH * r * cos(t), STAR_CENTRE + r * sin(t))
        }
        // Travel direction at an outer point when walking clockwise (left foot -> apex), as a unit vector.
        fun travel(deg: Double): DoubleArray {
            val t = deg * PI / 180
            val du = STAR_STRETCH * sin(t)
            val dv = -cos(t)
            val len = hypot(du, dv)
            return doubleArrayOf(du / len, dv / len)
        }
        fun lerp(p: DoubleArray, q: DoubleArray, f: Double) = doubleArrayOf(p[0] + (q[0] - p[0]) * f, p[1] + (q[1] - p[1]) * f)

        val foot = at(234.0, STAR_FEET * STAR_R)
        val bottomInner = at(270.0, inner)
        // The neck rises straight up and meets the foot's inner arm.
        val neckU = 0.5 - STAR_NECK
        val f = (neckU - foot[0]) / (bottomInner[0] - foot[0])
        val join = lerp(foot, bottomInner, f)

        val out = ArrayList<DoubleArray>()
        out += seg(neckU, 0.0, neckU, join[1] / 3, neckU, 2 * join[1] / 3, join[0], join[1])
        // Walk clockwise: foot (234), inner 198, side point (162), inner 126, then the apex (90).
        var from = join
        for ((outerDeg, radius, tip) in listOf(Triple(234.0, STAR_FEET * STAR_R, STAR_FOOT_TIP), Triple(162.0, STAR_R, STAR_TIP))) {
            val o = at(outerDeg, radius)
            val tv = travel(outerDeg)
            // Into the outer point: straight-ish arm, rounded at the tip.
            val c1 = lerp(from, o, STAR_ARM)
            out += seg(from[0], from[1], c1[0], c1[1], o[0] - tip * tv[0], o[1] - tip * tv[1], o[0], o[1])
            // Out of it to the next inner corner, 36 degrees on.
            val i = at(outerDeg - 36, inner)
            val c2 = lerp(i, o, STAR_ARM)
            out += seg(o[0], o[1], o[0] + tip * tv[0], o[1] + tip * tv[1], c2[0], c2[1], i[0], i[1])
            from = i
        }
        // Up to the apex, arriving horizontally.
        val apex = at(90.0, STAR_R)
        val c1 = lerp(from, apex, STAR_ARM)
        out += seg(from[0], from[1], c1[0], c1[1], 0.5 - STAR_TIP * STAR_STRETCH, apex[1], 0.5, apex[1])
        return out
    }
}
