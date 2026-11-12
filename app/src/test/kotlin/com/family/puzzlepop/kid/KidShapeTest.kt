package com.family.puzzlepop.kid

import com.family.puzzlepop.engine.PieceStyle
import kotlin.random.Random
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class KidShapeTest {
    @Test
    fun fixedShapesResolveToTheirStyle() {
        val r = Random(1)
        assertEquals(PieceStyle.CHUNKY, KidShape.CHUNKY.resolve(r))
        assertEquals(PieceStyle.HEARTS, KidShape.HEARTS.resolve(r))
        assertEquals(PieceStyle.PARTY, KidShape.PARTY.resolve(r))
    }

    @Test
    fun surpriseOnlyPicksTheThreeKidStylesAndFavoursNone() {
        val counts = (0 until 300).map { KidShape.SURPRISE.resolve(Random(it)) }.groupingBy { it }.eachCount()
        assertEquals(setOf(PieceStyle.CHUNKY, PieceStyle.HEARTS, PieceStyle.PARTY), counts.keys)
        counts.forEach { (s, n) -> assertTrue("$s only $n times", n >= 60) }
    }

    @Test
    fun missingOrUnknownNameIsChunky() {
        assertEquals(KidShape.CHUNKY, kidShapeFromName(null))
        assertEquals(KidShape.CHUNKY, kidShapeFromName(""))
        assertEquals(KidShape.CHUNKY, kidShapeFromName("TRIANGLES"))
        assertEquals(KidShape.SURPRISE, kidShapeFromName("SURPRISE"))
        assertEquals(KidShape.HEARTS, kidShapeFromName("HEARTS"))
    }
}
