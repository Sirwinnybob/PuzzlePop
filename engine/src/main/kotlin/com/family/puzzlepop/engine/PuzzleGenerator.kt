package com.family.puzzlepop.engine

/**
 * Procedurally cuts an image into jigsaw pieces. Deterministic for a given [PuzzleSpec].
 *
 * Saved puzzles store only the spec (seed included) and regenerate their shapes on load, so output must
 * stay bit-exact per [PuzzleSpec.generatorVersion]. Any change to GridSizer, TabShapes, corner jitter or
 * the PRNG that alters output for an existing spec must bump generatorVersion and keep the old path.
 */
object PuzzleGenerator {
    private const val WOBBLY_CORNER_JITTER = 0.12
    private const val BOUNDS_PADDING = 1f
    private const val BOUNDS_STEPS = 16

    fun generate(spec: PuzzleSpec): PuzzleLayout = when (spec.generatorVersion) {
        1 -> generateV1(spec)
        else -> throw IllegalArgumentException(
            "Unsupported puzzle generator version ${spec.generatorVersion} (this app knows version 1)"
        )
    }

    /**
     * Version 1. Random numbers come only from `SplitMix64(spec.seed)`, consumed in a fixed order:
     * interior corners (row-major, WOBBLY only; 2 draws each), then horizontal interior edges
     * (row-major), then vertical interior edges (row-major); 6 draws per edge.
     * Non-legacy styles ([isLegacy] false) are cut by [StyleCuts], which documents its own draw order.
     */
    private fun generateV1(spec: PuzzleSpec): PuzzleLayout {
        if (!spec.style.isLegacy) return StyleCuts.generate(spec, GridSizer.choose(spec.targetCount, spec.imageWidth, spec.imageHeight))
        val grid = GridSizer.choose(spec.targetCount, spec.imageWidth, spec.imageHeight)
        val rows = grid.rows
        val cols = grid.cols
        val w = spec.imageWidth.toFloat()
        val h = spec.imageHeight.toFloat()
        val cellW = w / cols
        val cellH = h / rows
        val rng = SplitMix64(spec.seed)

        // Corners: (rows+1) x (cols+1). Border corners are never moved.
        val corners = Array(rows + 1) { r ->
            Array(cols + 1) { c ->
                val x = if (c == cols) w else c * cellW
                val y = if (r == rows) h else r * cellH
                val interior = r in 1 until rows && c in 1 until cols
                if (interior && spec.style == PieceStyle.WOBBLY) {
                    val jx = (rng.nextDouble() * 2 - 1) * WOBBLY_CORNER_JITTER * cellW
                    val jy = (rng.nextDouble() * 2 - 1) * WOBBLY_CORNER_JITTER * cellH
                    Pt((x + jx).toFloat(), (y + jy).toFloat())
                } else {
                    Pt(x, y)
                }
            }
        }

        fun edge(a: Pt, b: Pt, interior: Boolean): EdgeCurve {
            if (!interior) return TabShapes.straightEdge(a, b)
            val params = TabParams(
                sideSign = if (rng.nextBoolean()) 1f else -1f,
                centre01 = rng.nextDouble(),
                height01 = rng.nextDouble(),
                neckJitter01 = rng.nextDouble(),
                headJitter01 = rng.nextDouble(),
                wobble01 = rng.nextDouble(),
            )
            return TabShapes.tabEdge(a, b, spec.style, params)
        }

        // horizontal[r][c]: edge from corner (r,c) to (r,c+1), left to right.
        val horizontal = Array(rows + 1) { r ->
            Array(cols) { c -> edge(corners[r][c], corners[r][c + 1], r in 1 until rows) }
        }
        // vertical[r][c]: edge from corner (r,c) to (r+1,c), top to bottom.
        val vertical = Array(rows) { r ->
            Array(cols + 1) { c -> edge(corners[r][c], corners[r + 1][c], c in 1 until cols) }
        }

        val pieces = ArrayList<PieceShape>(rows * cols)
        for (r in 0 until rows) for (c in 0 until cols) {
            val sides = Sides(
                top = horizontal[r][c],
                right = vertical[r][c + 1],
                bottom = horizontal[r + 1][c].reversedCurve(),
                left = vertical[r][c].reversedCurve(),
            )
            pieces += PieceShape(r * cols + c, r, c, sides, boundsOf(sides))
        }
        return PuzzleLayout(spec, grid, cellW, cellH, pieces)
    }

    internal fun boundsOf(sides: Sides): Box {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (side in listOf(sides.top, sides.right, sides.bottom, sides.left)) {
            for (cubic in side) {
                for (i in 0..BOUNDS_STEPS) {
                    val t = i / BOUNDS_STEPS.toFloat()
                    val m = 1 - t
                    val a = m * m * m
                    val b = 3 * m * m * t
                    val c = 3 * m * t * t
                    val d = t * t * t
                    val x = a * cubic.p0.x + b * cubic.c1.x + c * cubic.c2.x + d * cubic.p3.x
                    val y = a * cubic.p0.y + b * cubic.c1.y + c * cubic.c2.y + d * cubic.p3.y
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return Box(minX - BOUNDS_PADDING, minY - BOUNDS_PADDING, maxX + BOUNDS_PADDING, maxY + BOUNDS_PADDING)
    }
}
