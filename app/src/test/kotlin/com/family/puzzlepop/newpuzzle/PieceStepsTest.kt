package com.family.puzzlepop.newpuzzle

import org.junit.Assert.assertEquals
import org.junit.Test

class PieceStepsTest {
    @Test fun endsMapToEnds() {
        assertEquals(0f, PieceSteps.fraction(0), 0f)
        assertEquals(1f, PieceSteps.fraction(PieceSteps.lastIndex), 0f)
        assertEquals(0, PieceSteps.indexAt(0f))
        assertEquals(PieceSteps.lastIndex, PieceSteps.indexAt(1f))
    }

    @Test fun everyStepRoundTrips() {
        for (i in 0..PieceSteps.lastIndex) assertEquals(i, PieceSteps.indexAt(PieceSteps.fraction(i)))
    }

    @Test fun snapsToNearestStepAndPinsOutside() {
        val half = PieceSteps.fraction(1) / 2
        assertEquals(0, PieceSteps.indexAt(half * 0.9f))
        assertEquals(1, PieceSteps.indexAt(half * 1.1f))
        assertEquals(0, PieceSteps.indexAt(-0.3f))
        assertEquals(PieceSteps.lastIndex, PieceSteps.indexAt(1.7f))
        assertEquals(0, PieceSteps.indexAt(Float.NaN))
    }

    @Test fun defaultAndPresetCountsAreSteps() {
        assert(PieceSteps.indexOf(PieceSteps.DEFAULT_COUNT) >= 0)
        for (c in listOf(12, 36, 100, 300, 750)) assert(PieceSteps.indexOf(c) >= 0) { "missing $c" }
    }
}
