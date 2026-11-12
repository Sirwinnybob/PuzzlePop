package com.family.puzzlepop.play

import com.family.puzzlepop.data.PuzzleRepository
import com.family.puzzlepop.engine.Box
import com.family.puzzlepop.engine.PuzzleGenerator
import com.family.puzzlepop.engine.PuzzleLayout
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.game.PuzzleGame
import com.family.puzzlepop.engine.options.PlayOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** The margin around the board is at least this fraction of the board's size on each side (the stage 1–3 table). */
internal const val TABLE_MARGIN_FRACTION = 0.6f

/** Grown-up mode: and at least this many piece cells on each side, so small puzzles have room to sort pieces. */
internal const val TABLE_MARGIN_CELLS = 3.5f

/**
 * The play table: the board (0,0)-(W,H) with a margin on every side of 60% of the board's size or, in
 * grown-up mode, [TABLE_MARGIN_CELLS] piece cells if that is more (small puzzles: a 4 x 3 board gets 3.5
 * cells instead of 2.4 / 1.8). Kid mode keeps 60%: the kid's camera always shows the whole table, and a wider
 * margin would shrink the board for a toddler. The canvas grows this to fill the screen ([tableToFill]).
 */
fun tableFor(layout: PuzzleLayout, kid: Boolean = false): Box =
    tableBox(layout.spec.imageWidth.toFloat(), layout.spec.imageHeight.toFloat(), layout.cellWidth, layout.cellHeight, kid)

/** [tableFor] as a pure function of the board and cell sizes. Never smaller than the 60% table. */
internal fun tableBox(boardW: Float, boardH: Float, cellW: Float, cellH: Float, kid: Boolean): Box {
    var mx = TABLE_MARGIN_FRACTION * boardW
    var my = TABLE_MARGIN_FRACTION * boardH
    if (!kid) {
        mx = max(mx, TABLE_MARGIN_CELLS * cellW)
        my = max(my, TABLE_MARGIN_CELLS * cellH)
    }
    return Box(-mx, -my, boardW + mx, boardH + my)
}

/**
 * The table as the canvas uses it: [base] grown to take in every point of [extra] (loose pieces' centres,
 * as left, top, right, bottom; a save can hold pieces outside a smaller table), then widened or heightened
 * about its centre to the aspect ([areaW] / [areaH]) of the screen area it is fitted into, so a fitted camera
 * shows no screen space that pieces can't be dragged to. It never shrinks [base].
 */
internal fun tableToFill(base: Box, extra: FloatArray?, areaW: Float, areaH: Float): Box {
    var l = base.left
    var t = base.top
    var r = base.right
    var b = base.bottom
    if (extra != null) {
        l = minOf(l, extra[0]); t = minOf(t, extra[1]); r = maxOf(r, extra[2]); b = maxOf(b, extra[3])
    }
    if (areaW > 0f && areaH > 0f) {
        val w = r - l
        val h = b - t
        val aspect = areaW / areaH
        if (w / h < aspect) {
            val grow = (h * aspect - w) / 2
            l -= grow; r += grow
        } else {
            val grow = (w / aspect - h) / 2
            t -= grow; b += grow
        }
    }
    return Box(l, t, r, b)
}

/**
 * Generates [spec]'s pieces, scatters them on the table (or, with [startInTray], puts them all in the tray)
 * and saves the new puzzle. Returns its id. Kid creation passes [kid] and options with no tray and no rotation.
 */
suspend fun createPuzzle(
    puzzles: PuzzleRepository,
    photoId: String,
    spec: PuzzleSpec,
    options: PlayOptions,
    kid: Boolean = false,
): String {
    val state = withContext(Dispatchers.Default) {
        val layout = PuzzleGenerator.generate(spec)
        PuzzleGame.newGame(layout, tableFor(layout, kid), spec.seed, options.startInTray, options.rotation).state
    }
    return puzzles.create(photoId, spec, state, options)
}
