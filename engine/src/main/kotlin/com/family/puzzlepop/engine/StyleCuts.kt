package com.family.puzzlepop.engine

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Cuts the non-legacy [PieceStyle]s (see [isLegacy]). The legacy CHUNKY/CLASSIC/WOBBLY path in
 * [PuzzleGenerator] is pinned bit-exact and never comes through here.
 *
 * Same topology as legacy: a (rows+1) x (cols+1) grid of corners, `horizontal[r][c]` running from corner
 * (r,c) to (r,c+1) and `vertical[r][c]` from (r,c) to (r+1,c). Border edges are a single straight cubic;
 * interior edges are always multi-cubic chains, so [PieceShape.isEdge] stays correct. Interior edges are
 * built horizontal row-major, then vertical row-major; styles that draw random numbers draw them in that
 * order.
 *
 * - [PieceStyle.RIBBON]: straight grid, uniform knobs in a (row+col) checkerboard. No RNG draws.
 * - [PieceStyle.WAVY_RIBBON]: every interior grid line is a sine wave rippling about once per 1.5 cells; uniform knobs ride on it in the
 *   same checkerboard. No RNG draws.
 * - [PieceStyle.FREEFORM]: jittered corners, one knob per edge with random side, position, height and head
 *   width; about 1 in 12 interior edges is a false (knob-free) edge. See [freeform] for the RNG order.
 * - [PieceStyle.HEARTS]: straight grid, every knob a heart ([KnobHead.HEART]), random side. See [hearts].
 * - [PieceStyle.PARTY]: straight grid, each knob randomly round, heart or star, random side. See [party].
 */
internal object StyleCuts {
    // Uniform tab geometry in edge-local units (u along the chord, v perpendicular, both fractions of chord length).
    private const val UNIFORM_NECK_HALF = 0.10
    private const val UNIFORM_HEAD_HALF = 0.17
    private const val UNIFORM_CENTRE = 0.5
    private const val RIBBON_HEIGHT = 0.26

    private const val WAVY_HEIGHT = 0.24

    /** Wave amplitude as a fraction of the cell size across the line (cellH for row lines, cellW for columns). */
    private const val WAVE_AMPLITUDE = 0.15

    /** Cells per half-period of the wave, so the ripple density follows the grid rather than the image. */
    private const val WAVE_CELLS_PER_HALF = 1.5

    /** Half-periods for a line spanning [cells] cells: an integer, so sin(PI * k * t) is zero at both borders. */
    private fun waveK(cells: Int): Int = max(2, Math.round(cells / WAVE_CELLS_PER_HALF).toInt())

    /**
     * Wave plus knob must stay within 0.45 x min(cellW, cellH) of the straight grid line (design section 9).
     * Knobs are capped against a slightly smaller budget; only very elongated cells ever hit the cap.
     */
    private const val WAVY_KNOB_BUDGET = 0.44

    private const val FLAT_SEGMENTS = 6

    /** Interior corner jitter, per axis, as a fraction of the cell size on that axis. */
    private const val FREEFORM_JITTER = 0.15

    /** Fraction of interior edges drawn knob-free (before the no-two-per-piece rule forces some back to knobs). */
    private const val FREEFORM_FLAT_PROBABILITY = 1.0 / 12

    /** Knob centre along the edge: 0.38 to 0.62, wider than CLASSIC's 0.40 to 0.60 (design section 2). */
    private const val FREEFORM_CENTRE_MIN = 0.38
    private const val FREEFORM_CENTRE_RANGE = 0.24

    /** Knob height, a fraction of the edge length: 0.16 to 0.27, further capped by the envelope and corner reach. */
    private const val FREEFORM_HEIGHT_MIN = 0.16
    private const val FREEFORM_HEIGHT_RANGE = 0.11

    /** Head edge stays this far inside the u band [0.2, 0.8] (the head is narrowed when the centre is off-middle). */
    private const val FREEFORM_BAND_MARGIN = 0.205

    /**
     * A knob may rise at most this fraction of the distance from its head to the nearer corner. If every edge
     * obeys it, knobs on two sides of a piece cannot meet near their shared corner; the margin below 1 absorbs
     * the skew from corner jitter.
     */
    private const val FREEFORM_CORNER_REACH = 0.65

    /** Head half width: 0.17 x (1 +- 0.15). */
    private const val FREEFORM_HEAD_JITTER = 0.15

    /** Total budget for jitter plus knob, as a fraction of the smaller cell side (design section 9 allows 0.45). */
    private const val FREEFORM_BUDGET = 0.44

    /** HEARTS and PARTY: a head may reach this fraction of the smaller cell side (design section 9 allows 0.45). */
    private const val HEAD_BUDGET = 0.44

    fun generate(spec: PuzzleSpec, grid: Grid): PuzzleLayout = when (spec.style) {
        PieceStyle.RIBBON -> ribbon(spec, grid)
        PieceStyle.WAVY_RIBBON -> wavyRibbon(spec, grid)
        PieceStyle.FREEFORM -> freeform(spec, grid)
        PieceStyle.HEARTS -> hearts(spec, grid)
        PieceStyle.PARTY -> party(spec, grid)
        PieceStyle.CHUNKY, PieceStyle.CLASSIC, PieceStyle.WOBBLY ->
            throw IllegalArgumentException("${spec.style} is a legacy style, cut by PuzzleGenerator")
    }

    /**
     * Knob direction for the interior edge whose piece below (horizontal edges) or to the right (vertical
     * edges) is (r,c). Even (row+col) pieces get knobs out at top and bottom and holes left and right; odd
     * pieces the opposite. The edge-local +v side is down for left-to-right edges and left for top-to-bottom
     * edges, so both cases reduce to: -1 (up / right) when r+c is even.
     */
    private fun checkerSign(r: Int, c: Int): Float = if ((r + c) % 2 == 0) -1f else 1f

    /** RIBBON: no RNG draws. */
    private fun ribbon(spec: PuzzleSpec, grid: Grid): PuzzleLayout {
        val corners = straightCorners(spec, grid)
        return buildPieces(spec, grid, corners) { a, b, _, r, c -> uniformTab(a, b, checkerSign(r, c)) }
    }

    /**
     * WAVY_RIBBON: no RNG draws.
     *
     * Interior row line r is displaced by `dy_r(x) = s_r * A * cellH * sin(PI * k * x / W)` and interior column
     * line c by `dx_c(y) = s_c * A * cellW * sin(PI * k * y / H)`, with A = [WAVE_AMPLITUDE], k = [waveK] (about one half-period per 1.5 cells: `cols` for rows, `rows` for columns) and
     * the sign s alternating by line index (+1 for even, -1 for odd), so neighbouring lines swell and pinch.
     * Border lines stay straight. Interior corner (r,c) moves to `(x_c + dx_c(y_r), y_r + dy_r(x_c))`. Each edge
     * is a uniform tab on the chord between its moved corners, whose baseline also follows the wave between
     * the corners; knobs point along the chord's normal.
     */
    private fun wavyRibbon(spec: PuzzleSpec, grid: Grid): PuzzleLayout {
        val w = spec.imageWidth.toDouble()
        val h = spec.imageHeight.toDouble()
        val cellW = w / grid.cols
        val cellH = h / grid.rows
        val minCell = min(cellW, cellH)
        val kRow = waveK(grid.cols)
        val kCol = waveK(grid.rows)

        fun lineSign(i: Int) = if (i % 2 == 0) 1.0 else -1.0
        // Zero on border lines, so border corners and edges stay exactly on the image rectangle.
        fun rowWave(r: Int, x: Double): Double =
            if (r == 0 || r == grid.rows) 0.0 else lineSign(r) * WAVE_AMPLITUDE * cellH * sin(PI * kRow * x / w)
        fun colWave(c: Int, y: Double): Double =
            if (c == 0 || c == grid.cols) 0.0 else lineSign(c) * WAVE_AMPLITUDE * cellW * sin(PI * kCol * y / h)

        val straight = straightCorners(spec, grid)
        val corners = Array(grid.rows + 1) { r ->
            Array(grid.cols + 1) { c ->
                val s = straight[r][c]
                if (r == 0 || r == grid.rows || c == 0 || c == grid.cols) s
                else Pt((s.x + colWave(c, r * cellH)).toFloat(), (s.y + rowWave(r, c * cellW)).toFloat())
            }
        }

        return buildPieces(spec, grid, corners) { a, b, horizontal, r, c ->
            val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
            // A row line's knobs ride on a wave of up to A * cellH; columns on A * cellW.
            val waveMax = WAVE_AMPLITUDE * (if (horizontal) cellH else cellW)
            val height = min(WAVY_HEIGHT, (WAVY_KNOB_BUDGET * minCell - waveMax) / len)
            // How far the wave bends away from the straight chord between the two corners, at u along the edge.
            val sag: (Double) -> Double = if (horizontal) {
                val x0 = c * cellW
                val w0 = rowWave(r, x0)
                val w1 = rowWave(r, x0 + cellW).takeIf { c + 1 < grid.cols } ?: 0.0
                { u -> rowWave(r, x0 + u * cellW) - ((1 - u) * w0 + u * w1) }
            } else {
                val y0 = r * cellH
                val w0 = colWave(c, y0)
                val w1 = colWave(c, y0 + cellH).takeIf { r + 1 < grid.rows } ?: 0.0
                { u -> colWave(c, y0 + u * cellH) - ((1 - u) * w0 + u * w1) }
            }
            // Turn the sag (a y shift for rows, an x shift for columns) into edge-local v: project onto perp = (-dy, dx).
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            val len2 = dx * dx + dy * dy
            val baseline: (Double) -> Double =
                if (horizontal) { u -> sag(u) * dx / len2 } else { u -> -sag(u) * dy / len2 }
            uniformTab(a, b, checkerSign(r, c), height, baseline)
        }
    }

    /**
     * FREEFORM. Random numbers come only from `SplitMix64(spec.seed)`, drawn in this order:
     * 1. interior corners in row-major order, 2 draws each (x then y): jitter of +-[FREEFORM_JITTER] x cell
     *    on that axis;
     * 2. interior horizontal edges row-major, then interior vertical edges row-major, 5 draws each, always
     *    all 5 even for a flat edge: side (boolean), isFlat (`nextDouble() < 1/12`), centre01, height01,
     *    headJitter01.
     *
     * A flat edge becomes a [flatChain], unless the piece on either side already has a flat side (decided in
     * generation order), in which case it gets a knob after all, so no piece has two false sides.
     * Knob height is a fraction of the (jittered) edge length, capped so the corner jitter across the line
     * plus the knob stays within [FREEFORM_BUDGET] x min(cellW, cellH) of the straight grid line.
     */
    private fun freeform(spec: PuzzleSpec, grid: Grid): PuzzleLayout {
        val rng = SplitMix64(spec.seed)
        val cellW = spec.imageWidth.toDouble() / grid.cols
        val cellH = spec.imageHeight.toDouble() / grid.rows
        val minCell = min(cellW, cellH)

        val corners = straightCorners(spec, grid)
        for (r in 1 until grid.rows) for (c in 1 until grid.cols) {
            val s = corners[r][c]
            val jx = (rng.nextDouble() * 2 - 1) * FREEFORM_JITTER * cellW
            val jy = (rng.nextDouble() * 2 - 1) * FREEFORM_JITTER * cellH
            corners[r][c] = Pt((s.x + jx).toFloat(), (s.y + jy).toFloat())
        }

        val flatSides = IntArray(grid.rows * grid.cols)
        return buildPieces(spec, grid, corners) { a, b, horizontal, r, c ->
            val sideSign = if (rng.nextBoolean()) 1f else -1f
            val wantsFlat = rng.nextDouble() < FREEFORM_FLAT_PROBABILITY
            val centre01 = rng.nextDouble()
            val height01 = rng.nextDouble()
            val headJitter01 = rng.nextDouble()

            val first = if (horizontal) (r - 1) * grid.cols + c else r * grid.cols + (c - 1)
            val second = r * grid.cols + c
            if (wantsFlat && flatSides[first] == 0 && flatSides[second] == 0) {
                flatSides[first]++
                flatSides[second]++
                flatChain(a, b)
            } else {
                val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
                val across = if (horizontal) cellH else cellW
                val centre = FREEFORM_CENTRE_MIN + FREEFORM_CENTRE_RANGE * centre01
                val headWanted = UNIFORM_HEAD_HALF * (1 + FREEFORM_HEAD_JITTER * (2 * headJitter01 - 1))
                val head = minOf(headWanted, centre - FREEFORM_BAND_MARGIN, 1 - centre - FREEFORM_BAND_MARGIN)
                val cornerReach = FREEFORM_CORNER_REACH * (minOf(centre, 1 - centre) - head)
                val height = minOf(
                    cornerReach,
                    FREEFORM_HEIGHT_MIN + FREEFORM_HEIGHT_RANGE * height01,
                    (FREEFORM_BUDGET * minCell - FREEFORM_JITTER * across) / len,
                )
                tabChain(a, b, sideSign, centre, height, UNIFORM_NECK_HALF, head)
            }
        }
    }

    /**
     * HEARTS. Random numbers come only from `SplitMix64(spec.seed)`: interior horizontal edges row-major, then
     * interior vertical edges row-major, 1 draw each: side (`nextBoolean()`, true = +v side). Corners are not
     * jittered.
     */
    private fun hearts(spec: PuzzleSpec, grid: Grid): PuzzleLayout {
        val rng = SplitMix64(spec.seed)
        val fit = headFit(spec, grid)
        return buildPieces(spec, grid, straightCorners(spec, grid)) { a, b, _, _, _ ->
            val sideSign = if (rng.nextBoolean()) 1f else -1f
            knobEdge(a, b, KnobHead.HEART, sideSign, fit(a, b))
        }
    }

    /**
     * PARTY. Random numbers come only from `SplitMix64(spec.seed)`: interior horizontal edges row-major, then
     * interior vertical edges row-major, 2 draws each: side (`nextBoolean()`, true = +v side), then head
     * `KnobHead.entries[(nextDouble() * 3).toInt()]` (ROUND, HEART, STAR). Corners are not jittered.
     */
    private fun party(spec: PuzzleSpec, grid: Grid): PuzzleLayout {
        val rng = SplitMix64(spec.seed)
        val fit = headFit(spec, grid)
        return buildPieces(spec, grid, straightCorners(spec, grid)) { a, b, _, _, _ ->
            val sideSign = if (rng.nextBoolean()) 1f else -1f
            val head = KnobHead.entries[(rng.nextDouble() * KnobHead.entries.size).toInt()]
            knobEdge(a, b, head, sideSign, fit(a, b))
        }
    }

    /**
     * Head scale for an edge from a to b: 1, unless the cell is so elongated that a full-height head
     * ([KnobHeads.MAX_V] x edge length) would leave [HEAD_BUDGET] x min(cellW, cellH) of the grid line.
     */
    private fun headFit(spec: PuzzleSpec, grid: Grid): (Pt, Pt) -> Double {
        val minCell = min(spec.imageWidth.toDouble() / grid.cols, spec.imageHeight.toDouble() / grid.rows)
        return { a, b ->
            val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
            min(1.0, HEAD_BUDGET * minCell / (KnobHeads.MAX_V * len))
        }
    }

    /** Corner (r,c) at (c * cellW, r * cellH), with the far border exactly at the image width and height. */
    private fun straightCorners(spec: PuzzleSpec, grid: Grid): Array<Array<Pt>> {
        val w = spec.imageWidth.toFloat()
        val h = spec.imageHeight.toFloat()
        val cellW = w / grid.cols
        val cellH = h / grid.rows
        return Array(grid.rows + 1) { r ->
            Array(grid.cols + 1) { c -> Pt(if (c == grid.cols) w else c * cellW, if (r == grid.rows) h else r * cellH) }
        }
    }

    /**
     * Builds every edge from [corners] and assembles the pieces. Border edges are straight single cubics;
     * [interiorEdge] makes each interior edge, called horizontal row-major then vertical row-major with
     * `(a, b, horizontal, r, c)`, where (r,c) is the piece below a horizontal edge or right of a vertical one.
     */
    private fun buildPieces(
        spec: PuzzleSpec,
        grid: Grid,
        corners: Array<Array<Pt>>,
        interiorEdge: (a: Pt, b: Pt, horizontal: Boolean, r: Int, c: Int) -> EdgeCurve,
    ): PuzzleLayout {
        val rows = grid.rows
        val cols = grid.cols
        val horizontal = Array(rows + 1) { r ->
            Array(cols) { c ->
                val a = corners[r][c]
                val b = corners[r][c + 1]
                if (r in 1 until rows) interiorEdge(a, b, true, r, c) else TabShapes.straightEdge(a, b)
            }
        }
        val vertical = Array(rows) { r ->
            Array(cols + 1) { c ->
                val a = corners[r][c]
                val b = corners[r + 1][c]
                if (c in 1 until cols) interiorEdge(a, b, false, r, c) else TabShapes.straightEdge(a, b)
            }
        }
        val pieces = ArrayList<PieceShape>(rows * cols)
        for (r in 0 until rows) for (c in 0 until cols) {
            val sides = Sides(
                top = horizontal[r][c],
                right = vertical[r][c + 1],
                bottom = horizontal[r + 1][c].reversedCurve(),
                left = vertical[r][c].reversedCurve(),
            )
            pieces += PieceShape(r * cols + c, r, c, sides, PuzzleGenerator.boundsOf(sides))
        }
        val cellW = spec.imageWidth.toFloat() / cols
        val cellH = spec.imageHeight.toFloat() / rows
        return PuzzleLayout(spec, grid, cellW, cellH, pieces)
    }

    /** The CHUNKY-like uniform tab (neck 0.10, head 0.17, centred), [height] tall, on the [sideSign] side. */
    private fun uniformTab(
        a: Pt,
        b: Pt,
        sideSign: Float,
        height: Double = RIBBON_HEIGHT,
        baseline: ((Double) -> Double)? = null,
    ): EdgeCurve = tabChain(a, b, sideSign, UNIFORM_CENTRE, height, UNIFORM_NECK_HALF, UNIFORM_HEAD_HALF, baseline)

    /**
     * Six-cubic tab from [a] to [b], mirroring [TabShapes.tabEdge] (baseline, left flank, head left, head right,
     * right flank, baseline) without touching its pinned code. [baseline] adds an edge-local v offset at u
     * (it must vanish at u = 0 and 1), like WOBBLY's wobble term; the endpoints are always exactly [a] and [b].
     */
    private fun tabChain(
        a: Pt,
        b: Pt,
        sideSign: Float,
        centre: Double,
        height: Double,
        neckHalf: Double,
        headHalf: Double,
        baseline: ((Double) -> Double)? = null,
    ): EdgeCurve {
        val s = sideSign.toDouble()
        val h = height
        val cu = centre
        val n = neckHalf
        val w = headHalf
        fun uv(u: Double, v: Double) = doubleArrayOf(u, v * s)
        val seg = arrayOf(
            arrayOf(uv(0.0, 0.0), uv((cu - n) / 3, 0.0), uv(2 * (cu - n) / 3, 0.0), uv(cu - n, 0.0)),
            arrayOf(uv(cu - n, 0.0), uv(cu - n, 0.25 * h), uv(cu - w, 0.30 * h), uv(cu - w, 0.65 * h)),
            arrayOf(uv(cu - w, 0.65 * h), uv(cu - w, 0.90 * h), uv(cu - 0.5 * w, h), uv(cu, h)),
            arrayOf(uv(cu, h), uv(cu + 0.5 * w, h), uv(cu + w, 0.90 * h), uv(cu + w, 0.65 * h)),
            arrayOf(uv(cu + w, 0.65 * h), uv(cu + w, 0.30 * h), uv(cu + n, 0.25 * h), uv(cu + n, 0.0)),
            arrayOf(uv(cu + n, 0.0), uv(cu + n + (1 - cu - n) / 3, 0.0), uv(cu + n + 2 * (1 - cu - n) / 3, 0.0), uv(1.0, 0.0)),
        )
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        // perp = (-dy, dx) has the chord's length, so v is a fraction of chord length.
        fun map(p: DoubleArray): Pt {
            val u = p[0]
            val v = p[1] + (baseline?.invoke(u) ?: 0.0)
            return Pt((a.x + dx * u - dy * v).toFloat(), (a.y + dy * u + dx * v).toFloat())
        }
        val out = ArrayList<Cubic>(seg.size)
        for ((i, s4) in seg.withIndex()) {
            // Shared segment endpoints are reused, so the chain is exactly continuous.
            val p0 = if (i == 0) a else out[i - 1].p3
            val p3 = if (i == seg.size - 1) b else map(s4[3])
            out += Cubic(p0, map(s4[1]), map(s4[2]), p3)
        }
        return out
    }

    /** A knob-free interior edge: [FLAT_SEGMENTS] straight cubics, so it never reads as a border side. */
    internal fun flatChain(a: Pt, b: Pt): EdgeCurve {
        val pts = List(FLAT_SEGMENTS + 1) { i ->
            when (i) {
                0 -> a
                FLAT_SEGMENTS -> b
                else -> Pt(a.x + (b.x - a.x) * i / FLAT_SEGMENTS, a.y + (b.y - a.y) * i / FLAT_SEGMENTS)
            }
        }
        return (0 until FLAT_SEGMENTS).flatMap { TabShapes.straightEdge(pts[it], pts[it + 1]) }
    }
}
