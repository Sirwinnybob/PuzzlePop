package com.family.puzzlepop.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.math.abs

class PuzzleGeneratorTest {

    private fun spec(style: PieceStyle, target: Int = 48, seed: Long = 42L, w: Int = 4000, h: Int = 3000) =
        PuzzleSpec(w, h, target, style, seed)

    private fun near(a: Float, b: Float, tol: Float = 0.01f) = abs(a - b) <= tol

    private fun nearPt(a: Pt, b: Pt) = near(a.x, b.x) && near(a.y, b.y)

    private fun interiorSides(layout: PuzzleLayout): List<EdgeCurve> {
        val g = layout.grid
        val out = mutableListOf<EdgeCurve>()
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            if (c < g.cols - 1) out += layout.piece(r, c).sides.right
            if (r < g.rows - 1) out += layout.piece(r, c).sides.bottom
        }
        return out
    }

    @ParameterizedTest
    @EnumSource(PieceStyle::class)
    fun sameSeedSamePuzzle(style: PieceStyle) {
        assertEquals(PuzzleGenerator.generate(spec(style)), PuzzleGenerator.generate(spec(style)))
        // CHUNKY, RIBBON and WAVY_RIBBON draw no random numbers, so their shapes ignore the seed.
        if (style !in setOf(PieceStyle.CHUNKY, PieceStyle.RIBBON, PieceStyle.WAVY_RIBBON)) {
            val a = PuzzleGenerator.generate(spec(style, seed = 1L))
            val b = PuzzleGenerator.generate(spec(style, seed = 2L))
            assertNotEquals(interiorSides(a), interiorSides(b))
        }
    }

    @ParameterizedTest
    @EnumSource(PieceStyle::class)
    fun pieceCountMatchesGrid(style: PieceStyle) {
        val s = spec(style)
        val layout = PuzzleGenerator.generate(s)
        assertEquals(GridSizer.choose(s.targetCount, s.imageWidth, s.imageHeight).count, layout.pieces.size)
        assertEquals((0 until layout.grid.count).toList(), layout.pieces.map { it.id })
        layout.pieces.forEachIndexed { i, p ->
            assertEquals(i / layout.grid.cols, p.row)
            assertEquals(i % layout.grid.cols, p.col)
            assertEquals(p, layout.piece(p.row, p.col))
        }
    }

    @ParameterizedTest
    @EnumSource(PieceStyle::class)
    fun outlinesAreClosed(style: PieceStyle) {
        for (piece in PuzzleGenerator.generate(spec(style)).pieces) {
            val o = piece.outline
            for (i in 0 until o.size - 1) assertTrue(nearPt(o[i].p3, o[i + 1].p0), "piece ${piece.id} gap at $i")
            assertTrue(nearPt(o.last().p3, o.first().p0), "piece ${piece.id} not closed")
        }
    }

    @ParameterizedTest
    @EnumSource(PieceStyle::class)
    fun neighboursFit(style: PieceStyle) {
        val layout = PuzzleGenerator.generate(spec(style))
        val g = layout.grid
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            if (c < g.cols - 1) {
                assertEquals(layout.piece(r, c).sides.right, layout.piece(r, c + 1).sides.left.reversedCurve())
            }
            if (r < g.rows - 1) {
                assertEquals(layout.piece(r, c).sides.bottom, layout.piece(r + 1, c).sides.top.reversedCurve())
            }
        }
    }

    @ParameterizedTest
    @EnumSource(PieceStyle::class)
    fun borderSidesAreStraight(style: PieceStyle) {
        val s = spec(style)
        val layout = PuzzleGenerator.generate(s)
        val g = layout.grid
        fun EdgeCurve.points() = flatMap { listOf(it.p0, it.c1, it.c2, it.p3) }
        for (p in layout.pieces) {
            if (p.row == 0) assertTrue(p.sides.top.points().all { near(it.y, 0f) })
            if (p.row == g.rows - 1) assertTrue(p.sides.bottom.points().all { near(it.y, s.imageHeight.toFloat()) })
            if (p.col == 0) assertTrue(p.sides.left.points().all { near(it.x, 0f) })
            if (p.col == g.cols - 1) assertTrue(p.sides.right.points().all { near(it.x, s.imageWidth.toFloat()) })
            assertEquals(p.row == 0 || p.col == 0 || p.row == g.rows - 1 || p.col == g.cols - 1, p.isEdge)
        }
    }

    @ParameterizedTest
    @EnumSource(PieceStyle::class)
    fun boundsContainOutline(style: PieceStyle) {
        for (p in PuzzleGenerator.generate(spec(style)).pieces) {
            for (cubic in p.outline) {
                for (i in 0..32) {
                    val t = i / 32f
                    val m = 1 - t
                    val x = m * m * m * cubic.p0.x + 3 * m * m * t * cubic.c1.x + 3 * m * t * t * cubic.c2.x + t * t * t * cubic.p3.x
                    val y = m * m * m * cubic.p0.y + 3 * m * m * t * cubic.c1.y + 3 * m * t * t * cubic.c2.y + t * t * t * cubic.p3.y
                    assertTrue(x >= p.bounds.left && x <= p.bounds.right && y >= p.bounds.top && y <= p.bounds.bottom,
                        "piece ${p.id} sample ($x,$y) outside ${p.bounds}")
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(PieceStyle::class)
    fun fullRangeGenerates(style: PieceStyle) {
        for (target in listOf(10, 50, 300, 1000)) {
            val layout = PuzzleGenerator.generate(spec(style, target = target))
            assertEquals(GridSizer.choose(target, 4000, 3000).count, layout.pieces.size)
        }
        if (style == PieceStyle.CLASSIC) {
            val big = spec(style, target = 1000)
            PuzzleGenerator.generate(big) // warm-up
            val start = System.nanoTime()
            PuzzleGenerator.generate(big)
            val ms = (System.nanoTime() - start) / 1_000_000
            println("1000-piece CLASSIC generate: $ms ms")
            assertTrue(ms < 1000, "took $ms ms")
        }
    }

    /** Pins generator v1 output. If this fails, you changed shapes for existing saves: bump generatorVersion instead. */
    @Test
    fun generatorV1IsPinned() {
        val l = PuzzleGenerator.generate(PuzzleSpec(4000, 3000, 50, PieceStyle.CLASSIC, 42L))
        assertEquals(Grid(6, 8), l.grid)
        assertEquals(48, l.pieces.size)
        val b = l.pieces[7].bounds
        assertEquals(3368.84f, b.left, 0.01f)
        assertEquals(-1f, b.top, 0.01f)
        assertEquals(4001f, b.right, 0.01f)
        assertEquals(501f, b.bottom, 0.01f)
        val right = l.pieces[9].sides.right
        assertEquals(6, right.size)
        val first = right[0]
        assertEquals(1000f, first.p0.x, 0.01f); assertEquals(500f, first.p0.y, 0.01f)
        assertEquals(1000f, first.p3.x, 0.01f); assertEquals(693.27f, first.p3.y, 0.01f)
        val flank = right[1]
        assertEquals(967.51f, flank.c1.x, 0.01f); assertEquals(693.27f, flank.c1.y, 0.01f)
        assertEquals(961.01f, flank.c2.x, 0.01f); assertEquals(650.65f, flank.c2.y, 0.01f)
        assertEquals(915.53f, flank.p3.x, 0.01f); assertEquals(650.65f, flank.p3.y, 0.01f)
    }

    @Test
    fun unknownGeneratorVersionIsRejected() {
        val ex = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            PuzzleGenerator.generate(PuzzleSpec(400, 300, 12, PieceStyle.CLASSIC, 1L, generatorVersion = 99))
        }
        assertTrue(ex.message!!.contains("99"))
    }

    @Test
    fun splitMix64MatchesReferenceVectors() {
        // Reference outputs for seed 0 from the SplitMix64 specification.
        val r = SplitMix64(0L)
        assertEquals(-0x1ddf57c684e23251L, r.nextLong()) // 0xE220A8397B1DCDAF
        assertEquals(0x6e789e6aa1b965f4L, r.nextLong())
        val f = SplitMix64(7L)
        repeat(1000) {
            val x = f.nextFloat(); assertTrue(x >= 0f && x < 1f)
            val n = f.nextInt(10); assertTrue(n in 0..9)
        }
    }
}
