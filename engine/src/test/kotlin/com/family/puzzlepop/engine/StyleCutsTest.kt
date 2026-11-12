package com.family.puzzlepop.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class StyleCutsTest {

    companion object {
        @JvmStatic
        fun ribbonSpecs(): List<PuzzleSpec> =
            CutChecks.SPECS_FOR(PieceStyle.RIBBON) + CutChecks.SPECS_FOR(PieceStyle.WAVY_RIBBON)

        @JvmStatic
        fun freeformSpecs(): List<PuzzleSpec> = CutChecks.SPECS_FOR(PieceStyle.FREEFORM)

        @JvmStatic
        fun heartsAndPartySpecs(): List<PuzzleSpec> =
            CutChecks.SPECS_FOR(PieceStyle.HEARTS) + CutChecks.SPECS_FOR(PieceStyle.PARTY)
    }

    @Test
    fun enumIsAppendedInOrder() {
        assertEquals(
            listOf("CHUNKY", "CLASSIC", "WOBBLY", "RIBBON", "WAVY_RIBBON", "FREEFORM", "HEARTS", "PARTY"),
            PieceStyle.entries.map { it.name },
        )
        assertEquals(listOf(PieceStyle.CHUNKY, PieceStyle.CLASSIC, PieceStyle.WOBBLY), PieceStyle.entries.filter { it.isLegacy })
    }

    /** Even (row+col): top and bottom knobs out, left and right holes. Odd: the opposite. */
    @Test
    fun ribbonPattern() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 50, PieceStyle.RIBBON, 1L))
        val g = l.grid
        var checked = 0
        for (p in l.pieces) {
            val centre = Pt((p.col + 0.5f) * l.cellWidth, (p.row + 0.5f) * l.cellHeight)
            val even = (p.row + p.col) % 2 == 0
            fun check(side: EdgeCurve, interior: Boolean, wantOut: Boolean, name: String) {
                if (!interior) return
                assertEquals(wantOut, bulgesOut(side, centre), "piece (${p.row},${p.col}) $name")
                checked++
            }
            check(p.sides.top, p.row > 0, even, "top")
            check(p.sides.bottom, p.row < g.rows - 1, even, "bottom")
            check(p.sides.left, p.col > 0, !even, "left")
            check(p.sides.right, p.col < g.cols - 1, !even, "right")
        }
        assertEquals(2 * (2 * g.rows * g.cols - g.rows - g.cols), checked)

        val other = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 50, PieceStyle.RIBBON, 99L))
        assertEquals(l.pieces.map { it.outline }, other.pieces.map { it.outline })
    }

    /** The mid-chain point of a side (its knob apex) sits farther from the piece centre than the side's chord. */
    private fun bulgesOut(side: EdgeCurve, centre: Pt): Boolean {
        val apex = side[side.size / 2].p0
        val mid = Pt((side.first().p0.x + side.last().p3.x) / 2, (side.first().p0.y + side.last().p3.y) / 2)
        val offset = (apex.x - mid.x) * (mid.x - centre.x) + (apex.y - mid.y) * (mid.y - centre.y)
        return offset > 0
    }

    @Test
    fun wavyRibbonIgnoresSeed() {
        val a = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 100, PieceStyle.WAVY_RIBBON, 1L))
        val b = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 100, PieceStyle.WAVY_RIBBON, 2L))
        assertEquals(a.pieces.map { it.outline }, b.pieces.map { it.outline })
    }

    @Test
    fun wavyRibbonBordersStraight() {
        for (spec in CutChecks.SPECS_FOR(PieceStyle.WAVY_RIBBON)) {
            val l = PuzzleGenerator.generate(spec)
            val w = spec.imageWidth.toFloat()
            val h = spec.imageHeight.toFloat()
            fun EdgeCurve.points() = single().let { listOf(it.p0, it.c1, it.c2, it.p3) }
            for (p in l.pieces) {
                if (p.row == 0) assertTrue(p.sides.top.points().all { it.y == 0f }, "top ${p.id}")
                if (p.row == l.grid.rows - 1) assertTrue(p.sides.bottom.points().all { it.y == h }, "bottom ${p.id}")
                if (p.col == 0) assertTrue(p.sides.left.points().all { it.x == 0f }, "left ${p.id}")
                if (p.col == l.grid.cols - 1) assertTrue(p.sides.right.points().all { it.x == w }, "right ${p.id}")
            }
        }
    }

    @Test
    fun wavyRibbonActuallyWaves() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 36, PieceStyle.WAVY_RIBBON, 1L))
        var sum = 0.0
        var n = 0
        for (r in 1 until l.grid.rows) for (c in 1 until l.grid.cols) {
            val corner = l.piece(r, c).sides.top.first().p0
            sum += hypot(corner.x - c * l.cellWidth, corner.y - r * l.cellHeight)
            n++
        }
        val mean = sum / n
        assertTrue(mean >= 0.07 * minOf(l.cellWidth, l.cellHeight), "mean interior corner shift $mean")
    }

    @ParameterizedTest
    @MethodSource("ribbonSpecs")
    fun newStylesPassGeometryChecks(spec: PuzzleSpec) {
        val l = PuzzleGenerator.generate(spec)
        CutChecks.assertSharedEdgesExact(l)
        CutChecks.assertEnvelope(l, baselineSlack = if (spec.style == PieceStyle.WAVY_RIBBON) CutChecks.WAVY_BASELINE_SLACK else 0f)
        CutChecks.assertNoSelfCrossing(l)
        CutChecks.assertEdgeCountIsBorder(l)
    }

    /** 16:9 photos at 10-12 pieces give cells up to 1.41:1, where an uncapped knob plus wave leaves the envelope. */
    @Test
    fun wavyRibbonElongatedCellsStayInEnvelope() {
        for ((w, h) in listOf(4000 to 2250, 2250 to 4000)) for (n in listOf(10, 12)) {
            val l = PuzzleGenerator.generate(PuzzleSpec(w, h, n, PieceStyle.WAVY_RIBBON, 1L))
            CutChecks.assertEnvelope(l, baselineSlack = CutChecks.WAVY_BASELINE_SLACK)
            CutChecks.assertNoSelfCrossing(l)
        }
    }

    @Test
    fun wavyRibbonGolden() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 50, PieceStyle.WAVY_RIBBON, 42L))
        assertEquals(Grid(6, 8), l.grid)
        val b = l.pieces[7].bounds
        assertEquals(3329.606f, b.left, 0.01f)
        assertEquals(-1f, b.top, 0.01f)
        assertEquals(4001f, b.right, 0.01f)
        assertEquals(501f, b.bottom, 0.01f)
    }

    /** Pins RIBBON's shape so saved Ribbon puzzles are never silently reshaped. */
    @Test
    fun ribbonGolden() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 50, PieceStyle.RIBBON, 42L))
        assertEquals(Grid(6, 8), l.grid)
        val edge = l.pieces[7].bounds
        assertEquals(3369f, edge.left, 0.01f)
        assertEquals(-1f, edge.top, 0.01f)
        assertEquals(4001f, edge.right, 0.01f)
        assertEquals(501f, edge.bottom, 0.01f)
        val inner = l.pieces[20].bounds
        assertEquals(1999f, inner.left, 0.01f)
        assertEquals(869f, inner.top, 0.01f)
        assertEquals(2501f, inner.right, 0.01f)
        assertEquals(1631f, inner.bottom, 0.01f)
    }

    @Test
    fun wavy1000IsFast() {
        val spec = PuzzleSpec(4000, 3000, 1000, PieceStyle.WAVY_RIBBON, 1L)
        PuzzleGenerator.generate(spec) // warm-up
        val start = System.nanoTime()
        PuzzleGenerator.generate(spec)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("1000-piece WAVY_RIBBON generate: $ms ms")
        assertTrue(ms < 500, "took $ms ms")
    }

    /** Flat interior edges must stay multi-cubic so they never count as border sides. */
    @Test
    fun flatChainIsSixStraightCubics() {
        val a = Pt(10f, 20f)
        val b = Pt(130f, 50f)
        val chain = StyleCuts.flatChain(a, b)
        assertEquals(6, chain.size)
        assertEquals(a, chain.first().p0)
        assertEquals(b, chain.last().p3)
        for (i in 0 until chain.size - 1) assertEquals(chain[i].p3, chain[i + 1].p0)
        for (pt in chain.flatMap { listOf(it.c1, it.c2, it.p3) }) {
            assertEquals(0f, (pt.x - a.x) * (b.y - a.y) - (pt.y - a.y) * (b.x - a.x), 0.5f, "$pt off the chord")
        }
    }

    // ---- FREEFORM ----

    @ParameterizedTest
    @MethodSource("freeformSpecs")
    fun freeformPassesGeometryChecks(spec: PuzzleSpec) {
        val l = PuzzleGenerator.generate(spec)
        CutChecks.assertSharedEdgesExact(l)
        CutChecks.assertEnvelope(l)
        CutChecks.assertNoSelfCrossing(l)
        CutChecks.assertEdgeCountIsBorder(l)
    }

    /** SPECS_FOR is near-square cells; 16:9 photos give elongated ones, wide and tall, at few and many pieces. */
    @Test
    fun freeformElongatedCellsStayInEnvelope() {
        for ((w, h) in listOf(4000 to 2250, 2250 to 4000)) for (n in listOf(10, 300)) for (seed in 1L..3L) {
            val l = PuzzleGenerator.generate(PuzzleSpec(w, h, n, PieceStyle.FREEFORM, seed))
            CutChecks.assertSharedEdgesExact(l)
            CutChecks.assertEnvelope(l)
            CutChecks.assertNoSelfCrossing(l)
            CutChecks.assertEdgeCountIsBorder(l)
        }
    }

    /** An interior edge is false (knob-free) when it stays on its chord. */
    private fun isFalse(side: EdgeCurve): Boolean {
        val a = side.first().p0
        val b = side.last().p3
        val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        return CutChecks.samples(side).all { pt ->
            abs(((pt.x - a.x) * (b.y - a.y) - (pt.y - a.y) * (b.x - a.x)) / len) < 0.01 * len
        }
    }

    @Test
    fun freeformHasFalseEdges() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 1000, PieceStyle.FREEFORM, 1L))
        val g = l.grid
        var interior = 0
        var flat = 0
        for (p in l.pieces) {
            val sides = listOf(p.sides.top, p.sides.right, p.sides.bottom, p.sides.left)
            val flatSides = sides.count { it.size > 1 && isFalse(it) }
            assertTrue(flatSides < 2, "piece ${p.id} has $flatSides false sides")
            if (p.col < g.cols - 1) { interior++; if (isFalse(p.sides.right)) flat++ }
            if (p.row < g.rows - 1) { interior++; if (isFalse(p.sides.bottom)) flat++ }
        }
        val fraction = flat.toDouble() / interior
        assertTrue(fraction in 0.04..0.14, "false edge fraction $fraction")
    }

    @Test
    fun freeformFalseEdgesAreNotBorder() {
        CutChecks.assertEdgeCountIsBorder(PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 1000, PieceStyle.FREEFORM, 7L)))
    }

    @Test
    fun freeformCornersUneven() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 1000, PieceStyle.FREEFORM, 1L))
        val cell = min(l.cellWidth, l.cellHeight)
        var sum = 0.0
        var n = 0
        for (r in 1 until l.grid.rows) for (c in 1 until l.grid.cols) {
            val corner = l.piece(r, c).sides.top.first().p0
            sum += hypot((corner.x - c * l.cellWidth).toDouble(), (corner.y - r * l.cellHeight).toDouble())
            n++
        }
        assertTrue(sum / n >= 0.10 * cell, "mean corner displacement ${sum / n} vs cell $cell")
    }

    @Test
    fun freeformGolden() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 50, PieceStyle.FREEFORM, 42L))
        assertEquals(Grid(6, 8), l.grid)
        val b = l.pieces[7].bounds
        assertEquals(3418.44f, b.left, 0.01f)
        assertEquals(-1f, b.top, 0.01f)
        assertEquals(4001f, b.right, 0.01f)
        assertEquals(504.00f, b.bottom, 0.01f)
    }

    @Test
    fun freeformDeterministic() {
        val spec = PuzzleSpec(4000, 3000, 200, PieceStyle.FREEFORM, 5L)
        assertEquals(PuzzleGenerator.generate(spec).pieces.map { it.outline }, PuzzleGenerator.generate(spec).pieces.map { it.outline })
        val other = PuzzleGenerator.generate(spec.copy(seed = 6L))
        assertTrue(PuzzleGenerator.generate(spec).pieces.map { it.outline } != other.pieces.map { it.outline })
    }

    /** Spread (max - min) of the knob apex position u along the chord, over interior non-flat edges. */
    private fun knobCentreSpread(l: PuzzleLayout): Double {
        val us = ArrayList<Double>()
        for (p in l.pieces) for (side in listOf(p.sides.right, p.sides.bottom)) {
            if (side.size == 1 || isFalse(side)) continue
            val a = side.first().p0
            val b = side.last().p3
            val apex = side[side.size / 2].p0
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            us += ((apex.x - a.x) * dx + (apex.y - a.y) * dy) / (dx * dx + dy * dy)
        }
        return us.max() - us.min()
    }

    @Test
    fun freeformKnobsVaryMoreThanClassic() {
        val freeform = knobCentreSpread(PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 1000, PieceStyle.FREEFORM, 1L)))
        val classic = knobCentreSpread(PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 1000, PieceStyle.CLASSIC, 1L)))
        assertTrue(freeform > classic, "freeform spread $freeform vs classic $classic")
    }

    // ---- HEARTS and PARTY ----

    @ParameterizedTest
    @MethodSource("heartsAndPartySpecs")
    fun heartsAndPartyPassGeometryChecks(spec: PuzzleSpec) {
        val l = PuzzleGenerator.generate(spec)
        CutChecks.assertSharedEdgesExact(l)
        CutChecks.assertEnvelope(l)
        CutChecks.assertNoSelfCrossing(l)
        CutChecks.assertEdgeCountIsBorder(l)
    }

    /** 16:9 photos give elongated cells, where a full-size head would leave the 0.45 x cell envelope. */
    @Test
    fun heartsAndPartyElongatedCellsStayInEnvelope() {
        for (style in listOf(PieceStyle.HEARTS, PieceStyle.PARTY)) {
            for ((w, h) in listOf(4000 to 2250, 2250 to 4000, 4000 to 1500)) for (n in listOf(10, 12, 300)) {
                val l = PuzzleGenerator.generate(PuzzleSpec(w, h, n, style, 1L))
                CutChecks.assertEnvelope(l)
                CutChecks.assertNoSelfCrossing(l)
            }
        }
    }

    /** Sampled (u, v) of an edge from (0,0) to (100,0): u along it, v = y / 100 (the knob side for sideSign 1). */
    private fun uv(edge: EdgeCurve): List<Pair<Double, Double>> =
        CutChecks.samples(edge).map { it.x / 100.0 to it.y / 100.0 }

    /** Indices of the peaks of [values] that rise and then fall by more than [tol] (ignores float noise on straight runs). */
    private fun localMaxima(values: List<Double>, tol: Double = 1e-3): List<Int> {
        val peaks = ArrayList<Int>()
        var rising = false
        var best = 0
        var low = 0
        for (i in values.indices) {
            val v = values[i]
            if (rising) {
                if (v > values[best]) best = i else if (values[best] - v > tol) { peaks += best; rising = false; low = i }
            } else {
                if (v < values[low]) low = i else if (v - values[low] > tol) { rising = true; best = i }
            }
        }
        return peaks
    }

    /** Width (max u - min u) of the outline where it crosses the level v = [level]. */
    private fun widthAt(pts: List<Pair<Double, Double>>, level: Double): Double {
        val us = ArrayList<Double>()
        for (i in 0 until pts.size - 1) {
            val (u0, v0) = pts[i]
            val (u1, v1) = pts[i + 1]
            if ((v0 - level) * (v1 - level) <= 0 && v0 != v1) us += u0 + (u1 - u0) * (level - v0) / (v1 - v0)
        }
        return us.max() - us.min()
    }

    @Test
    fun heartHeadShape() {
        val pts = uv(knobEdge(Pt(0f, 0f), Pt(100f, 0f), KnobHead.HEART, 1f))
        val vs = pts.map { it.second }
        val maxima = localMaxima(vs)
        assertEquals(2, maxima.size, "lobe tops at $maxima")
        val (left, right) = maxima
        val dip = vs.subList(left, right + 1).min()
        assertTrue(dip < minOf(vs[left], vs[right]) - 0.04, "dip $dip between lobes ${vs[left]} and ${vs[right]}")
        assertTrue(pts[left].first < 0.5 && pts[right].first > 0.5, "lobes either side of the middle")
        val head = pts.filter { it.second > 0.005 }.let { h -> h.maxOf { it.first } - h.minOf { it.first } }
        val neck = widthAt(pts, 0.02)
        assertTrue(neck < 0.6 * head, "neck $neck vs head $head")
        assertTrue(neck >= 0.10, "neck $neck too thin to see")
    }

    @Test
    fun starHeadShape() {
        val pts = uv(knobEdge(Pt(0f, 0f), Pt(100f, 0f), KnobHead.STAR, 1f))
        val head = pts.filter { it.second > 0.005 }
        assertTrue(localMaxima(head.map { it.second }).size >= 3, "v maxima")
        // Five points: the apex, plus a side point and a foot on each side, which stick out sideways.
        assertEquals(4, localMaxima(head.map { abs(it.first - 0.5) }).size, "sideways points")
        val width = head.maxOf { it.first } - head.minOf { it.first }
        assertTrue(widthAt(pts, 0.02) < 0.6 * width, "neck narrower than head")
    }

    @Test
    fun knobHeadsFlipWithSide() {
        for (head in KnobHead.entries) {
            val up = knobEdge(Pt(0f, 0f), Pt(100f, 0f), head, 1f)
            val down = knobEdge(Pt(0f, 0f), Pt(100f, 0f), head, -1f)
            assertTrue(up.size > 1)
            assertEquals(up.map { it.p3.x }, down.map { it.p3.x })
            assertEquals(up.map { it.p3.y }, down.map { -it.p3.y + 0f })
        }
    }

    /** Head type from chain length: each head has its own cubic count. */
    private fun headOf(side: EdgeCurve): KnobHead =
        KnobHead.entries.single { knobEdge(Pt(0f, 0f), Pt(100f, 0f), it, 1f).size == side.size }

    @Test
    fun partyMix() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 1000, PieceStyle.PARTY, 1L))
        val counts = IntArray(3)
        for (p in l.pieces) {
            if (p.col < l.grid.cols - 1) counts[headOf(p.sides.right).ordinal]++
            if (p.row < l.grid.rows - 1) counts[headOf(p.sides.bottom).ordinal]++
        }
        val total = counts.sum().toDouble()
        for (head in KnobHead.entries) {
            val f = counts[head.ordinal] / total
            assertTrue(f in 0.25..0.42, "$head fraction $f")
        }
    }

    @Test
    fun heartsAreAllHearts() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 100, PieceStyle.HEARTS, 1L))
        for (p in l.pieces) {
            if (p.col < l.grid.cols - 1) assertEquals(KnobHead.HEART, headOf(p.sides.right))
            if (p.row < l.grid.rows - 1) assertEquals(KnobHead.HEART, headOf(p.sides.bottom))
        }
    }

    /** Piece 7 (top-right corner) pins its bottom knob; piece 20 (interior) pins knobs on all four sides. */
    @Test
    fun heartsGolden() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 50, PieceStyle.HEARTS, 42L))
        assertEquals(Grid(6, 8), l.grid)
        assertBox(Box(3499f, -1f, 4001f, 658.5f), l.pieces[7].bounds)
        assertBox(Box(1841.5f, 999f, 2501f, 1658.5f), l.pieces[20].bounds)
    }

    @Test
    fun partyGolden() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 50, PieceStyle.PARTY, 42L))
        assertEquals(Grid(6, 8), l.grid)
        assertBox(Box(3499f, -1f, 4001f, 631f), l.pieces[7].bounds)
        assertBox(Box(1999f, 869f, 2658.5f, 1631f), l.pieces[20].bounds)
    }

    private fun assertBox(want: Box, got: Box) {
        assertEquals(want.left, got.left, 0.01f, "left of $got")
        assertEquals(want.top, got.top, 0.01f, "top of $got")
        assertEquals(want.right, got.right, 0.01f, "right of $got")
        assertEquals(want.bottom, got.bottom, 0.01f, "bottom of $got")
    }
}
