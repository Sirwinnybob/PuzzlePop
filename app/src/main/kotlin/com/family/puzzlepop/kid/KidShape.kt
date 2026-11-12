package com.family.puzzlepop.kid

import com.family.puzzlepop.engine.PieceStyle
import kotlin.random.Random

/** The grown-up's choice of piece shape for kid puzzles. Kids never see these names. */
enum class KidShape { CHUNKY, HEARTS, PARTY, SURPRISE }

private val SURPRISE_POOL = listOf(PieceStyle.CHUNKY, PieceStyle.HEARTS, PieceStyle.PARTY)

/** The style to cut a new kid puzzle in. SURPRISE draws from [random], so pass a fresh one, not the puzzle seed. */
fun KidShape.resolve(random: Random): PieceStyle = when (this) {
    KidShape.CHUNKY -> PieceStyle.CHUNKY
    KidShape.HEARTS -> PieceStyle.HEARTS
    KidShape.PARTY -> PieceStyle.PARTY
    KidShape.SURPRISE -> SURPRISE_POOL[random.nextInt(SURPRISE_POOL.size)]
}

/** Parses a stored name; a missing or unknown value is CHUNKY. */
fun kidShapeFromName(s: String?): KidShape = KidShape.entries.firstOrNull { it.name == s } ?: KidShape.CHUNKY
