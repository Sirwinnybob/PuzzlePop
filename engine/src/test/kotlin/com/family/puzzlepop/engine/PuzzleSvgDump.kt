package com.family.puzzlepop.engine

import org.junit.jupiter.api.Test
import java.io.File

/**
 * Debug helper (not an assertion test): writes a 6x8 puzzle SVG per style to engine/build/debug-svg/.
 * Env overrides: PUZZLE_SVG_DIR (output directory), PUZZLE_SVG_PIECES (target piece count).
 */
class PuzzleSvgDump {
    @Test
    fun dumpSvgs() {
        val dir = File(System.getenv("PUZZLE_SVG_DIR") ?: "build/debug-svg").apply { mkdirs() }
        val pieces = System.getenv("PUZZLE_SVG_PIECES")?.toIntOrNull() ?: 48
        for (style in PieceStyle.entries) {
            val layout = PuzzleGenerator.generate(PuzzleSpec(1600, 1200, pieces, style, 7L))
            val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="-10 -10 1620 1220" width="1620" height="1220">""")
            sb.append("""<rect x="-10" y="-10" width="1620" height="1220" fill="white"/>""")
            for (p in layout.pieces) {
                val o = p.outline
                sb.append("""<path fill="none" stroke="black" stroke-width="2" d="M ${o[0].p0.x} ${o[0].p0.y}""")
                for (c in o) sb.append(" C ${c.c1.x} ${c.c1.y} ${c.c2.x} ${c.c2.y} ${c.p3.x} ${c.p3.y}")
                sb.append(" Z\"/>")
            }
            sb.append("</svg>")
            File(dir, "${style.name.lowercase()}.svg").writeText(sb.toString())
        }
    }
}
