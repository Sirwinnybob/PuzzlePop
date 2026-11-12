package com.family.puzzlepop.engine.game

import com.family.puzzlepop.engine.Box
import com.family.puzzlepop.engine.Pt
import com.family.puzzlepop.engine.PuzzleLayout
import com.family.puzzlepop.engine.SplitMix64
import com.family.puzzlepop.engine.options.RotationMode
import com.family.puzzlepop.engine.options.Snap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.min

/**
 * Mutable game rules over a [PuzzleLayout]. Internally uses flat arrays and a members-per-group map so
 * [moveGroup] is cheap enough to call on every touch frame; [state] builds an immutable snapshot on access.
 */
class PuzzleGame(val layout: PuzzleLayout, state: GameState) {
    private val n = layout.pieces.size
    private val rows = layout.grid.rows
    private val cols = layout.grid.cols

    private val xs = FloatArray(n)
    private val ys = FloatArray(n)
    private val groupOf = IntArray(n)
    private val locked = BooleanArray(n)
    private val homeX = FloatArray(n) { layout.pieces[it].bounds.left }
    private val homeY = FloatArray(n) { layout.pieces[it].bounds.top }
    private val z = ArrayList<Int>(n)
    private val members = HashMap<Int, ArrayList<Int>>()
    private val inTray = BooleanArray(n)
    private val trayOrder = ArrayList<Int>()
    private var elapsedMs: Long
    private var moves: Int
    private val rot = FloatArray(n)
    private val halfW = FloatArray(n) { layout.pieces[it].bounds.width / 2f }
    private val halfH = FloatArray(n) { layout.pieces[it].bounds.height / 2f }
    private var peeksUsed: Int
    private var lockedCount = 0

    init {
        require(
            state.positions.size == n && state.groupOf.size == n && state.locked.size == n && state.zOrder.size == n,
        ) { "GameState does not match layout of $n pieces" }
        require(state.rotations.isEmpty() || state.rotations.size == n) {
            "GameState has ${state.rotations.size} rotations for $n pieces (expected 0 or $n)"
        }
        for (i in state.rotations.indices) rot[i] = normalize(state.rotations[i])
        for (i in 0 until n) {
            xs[i] = state.positions[i].x
            ys[i] = state.positions[i].y
            groupOf[i] = state.groupOf[i]
            locked[i] = state.locked[i]
            if (locked[i]) lockedCount++
            members.getOrPut(groupOf[i]) { ArrayList() }.add(i)
        }
        z.addAll(state.zOrder)
        for (id in state.tray) {
            inTray[id] = true
            trayOrder.add(id)
        }
        elapsedMs = state.elapsedMs
        moves = state.moves
        peeksUsed = state.peeksUsed
    }

    /** An immutable snapshot of the current game. */
    val state: GameState
        get() = GameState(
            positions = List(n) { Pt(xs[it], ys[it]) },
            groupOf = groupOf.toList(),
            locked = locked.toList(),
            zOrder = z.toList(),
            elapsedMs = elapsedMs,
            moves = moves,
            tray = trayOrder.toList(),
            rotations = if (rot.all { it == 0f }) emptyList() else rot.toList(),
            peeksUsed = peeksUsed,
        )

    val placedCount: Int get() = lockedCount

    /** How many Peeks have been used (counts against a LIMITED peek allowance). */
    val peeksUsedCount: Int get() = peeksUsed

    fun recordPeek() {
        peeksUsed++
    }
    val isComplete: Boolean get() = lockedCount == n

    // Allocation-free reads for per-frame drawing and hit testing (unlike [state]).

    /** Current left of [pieceId]'s bounds on the table. */
    fun x(pieceId: Int): Float = xs[pieceId]

    /** Current top of [pieceId]'s bounds on the table. */
    fun y(pieceId: Int): Float = ys[pieceId]

    fun isLocked(pieceId: Int): Boolean = locked[pieceId]

    /** The group id of [pieceId] (group ids are piece ids, so they lie in 0 until piece count). */
    fun groupId(pieceId: Int): Int = groupOf[pieceId]

    /** Piece ids from bottom to top. A live view: do not hold it across mutations. */
    val zOrder: List<Int> get() = z

    /** Play time accumulated through [addElapsed]. */
    val elapsed: Long get() = elapsedMs

    /** Drops made so far. */
    val moveCount: Int get() = moves

    /** The pieces in the same group as [pieceId]. The returned list is a live view: do not hold it across mutations. */
    fun groupMembers(pieceId: Int): List<Int> = members.getValue(groupOf[pieceId])

    fun isInTray(pieceId: Int): Boolean = inTray[pieceId]

    /** Piece ids in the tray, in display order. A live read-only view: do not hold it across mutations. */
    val trayPieces: List<Int> get() = trayOrder

    /** True only for an unlocked, single-piece group that is not already in the tray. */
    fun canStoreInTray(pieceId: Int): Boolean =
        !locked[pieceId] && !inTray[pieceId] && members.getValue(groupOf[pieceId]).size == 1

    /** Appends [pieceId] to the tray; returns false (and does nothing) if [canStoreInTray] is false. */
    fun storeInTray(pieceId: Int): Boolean {
        if (!canStoreInTray(pieceId)) return false
        inTray[pieceId] = true
        trayOrder.add(pieceId)
        return true
    }

    /** Removes [pieceId] from the tray, places it at ([x], [y]) in table coordinates and raises it to the top. */
    fun takeFromTray(pieceId: Int, x: Float, y: Float) {
        if (!inTray[pieceId]) return
        inTray[pieceId] = false
        trayOrder.remove(pieceId)
        xs[pieceId] = x
        ys[pieceId] = y
        bringToFront(pieceId)
    }

    /** True if any member of [pieceId]'s unlocked group is within [radiusPx] of its home. */
    fun magnetCheck(pieceId: Int, radiusPx: Float): Boolean {
        if (locked[pieceId] || inTray[pieceId]) return false
        if (abs(angleDiff(rot[pieceId], 0f)) > ROT_TOL) return false
        return members.getValue(groupOf[pieceId]).any { hypot(xs[it] - homeX[it], ys[it] - homeY[it]) <= radiusPx }
    }

    /** Moves the whole group of [pieceId]. Locked groups and tray pieces do not move. */
    fun moveGroup(pieceId: Int, dx: Float, dy: Float) {
        if (locked[pieceId] || inTray[pieceId]) return
        for (m in members.getValue(groupOf[pieceId])) {
            xs[m] += dx
            ys[m] += dy
        }
    }

    /** Rotation of [pieceId] in degrees, in [0, 360). */
    fun rotation(pieceId: Int): Float = rot[pieceId]

    /**
     * Rotates the unlocked group of [pieceId] by [deltaDeg] about the centre of the bounding box of its
     * members' piece centres; each member's centre orbits that pivot. No-op for locked or tray pieces.
     */
    fun rotateGroup(pieceId: Int, deltaDeg: Float) {
        if (locked[pieceId] || inTray[pieceId]) return
        rotateMembers(members.getValue(groupOf[pieceId]), deltaDeg)
    }

    /**
     * Rotates the unlocked group of [pieceId] by [deltaDeg] about the fixed world point ([px], [py]) (a twist
     * keeps the pivot from the start so an L-shaped group doesn't creep). No-op for locked or tray pieces.
     */
    fun rotateGroupAbout(pieceId: Int, deltaDeg: Float, px: Float, py: Float) {
        if (locked[pieceId] || inTray[pieceId]) return
        rotateMembersAbout(members.getValue(groupOf[pieceId]), deltaDeg, px, py)
    }

    private fun rotateMembers(g: List<Int>, deltaDeg: Float) {
        if (deltaDeg == 0f) return
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in g) {
            val cx = xs[p] + halfW[p]
            val cy = ys[p] + halfH[p]
            if (cx < minX) minX = cx
            if (cx > maxX) maxX = cx
            if (cy < minY) minY = cy
            if (cy > maxY) maxY = cy
        }
        rotateMembersAbout(g, deltaDeg, (minX + maxX) / 2f, (minY + maxY) / 2f)
    }

    private fun rotateMembersAbout(g: List<Int>, deltaDeg: Float, px: Float, py: Float) {
        if (deltaDeg == 0f) return
        val rad = Math.toRadians(deltaDeg.toDouble())
        val c = cos(rad)
        val s = sin(rad)
        for (p in g) {
            val dx = xs[p] + halfW[p] - px
            val dy = ys[p] + halfH[p] - py
            xs[p] = (px + dx * c - dy * s).toFloat() - halfW[p]
            ys[p] = (py + dx * s + dy * c).toFloat() - halfH[p]
            rot[p] = normalize(rot[p] + deltaDeg)
        }
    }

    /** Sets every loose (unlocked, not in tray) group upright. Returns true if anything changed. */
    fun uprightAllForKid(): Boolean {
        var changed = false
        for (g in members.values.toList()) {
            val p = g[0]
            if (locked[p] || inTray[p] || rot[p] == 0f) continue
            rotateMembers(g, -rot[p])
            for (m in g) rot[m] = 0f
            changed = true
        }
        return changed
    }

    /** Raises the whole group of [pieceId] to the top of the z-order. */
    fun bringToFront(pieceId: Int) {
        if (locked[pieceId] || inTray[pieceId]) return
        val g = members.getValue(groupOf[pieceId])
        if (g.size == 1) {
            if (z.lastOrNull() != pieceId) {
                z.remove(pieceId)
                z.add(pieceId)
            }
            return
        }
        val set = g.toHashSet()
        val moved = z.filter { it in set }
        z.removeAll(set)
        z.addAll(moved)
    }

    fun addElapsed(ms: Long) {
        elapsedMs += ms
    }

    /**
     * Resolves a drop of the group containing [pieceId]: locks it when any member is within [tolerancePx]
     * of home, otherwise joins it to adjacent matching groups.
     */
    fun drop(pieceId: Int, tolerancePx: Float): DropResult {
        if (locked[pieceId] || inTray[pieceId]) return DropResult.NONE
        moves++
        val gid = groupOf[pieceId]

        // Lock (snapping every member exactly home) if any member is within tolerance of its home.
        val upright = abs(angleDiff(rot[pieceId], 0f)) <= ROT_TOL
        val nearHome = upright && members.getValue(gid).any { hypot(xs[it] - homeX[it], ys[it] - homeY[it]) <= tolerancePx }
        if (nearHome) {
            lockGroup(gid)
            return DropResult.LOCKED
        }

        // Join: repeat until no adjacent unlocked group lines up.
        var joined = false
        while (true) {
            val hit = findJoin(gid, tolerancePx) ?: break
            val (m, nb) = hit
            val ngid = groupOf[nb]
            // The neighbour group adopts the dropped group's exact rotation (about its own pivot).
            val r = rot[m]
            val turn = angleDiff(r, rot[nb])
            if (turn != 0f) {
                val ng = members.getValue(ngid)
                rotateMembers(ng, turn)
                for (p in ng) rot[p] = r
            }
            val (hx, hy) = rotated(homeCx(nb) - homeCx(m), homeCy(nb) - homeCy(m), r)
            val dx = (xs[m] + halfW[m] + hx) - (xs[nb] + halfW[nb])
            val dy = (ys[m] + halfH[m] + hy) - (ys[nb] + halfH[nb])
            // The first join snaps the dropped group; later joins move the neighbour group instead so
            // earlier joins stay exact.
            if (!joined) {
                for (p in members.getValue(gid)) {
                    xs[p] -= dx
                    ys[p] -= dy
                }
            } else {
                for (p in members.getValue(ngid)) {
                    xs[p] += dx
                    ys[p] += dy
                }
            }
            val into = members.getValue(gid)
            for (p in members.remove(ngid)!!) {
                groupOf[p] = gid
                into.add(p)
            }
            joined = true
        }
        if (!joined) return DropResult.NONE
        bringToFront(pieceId)
        return DropResult.JOINED
    }

    /** Finds a (member, neighbour) pair across the group boundary whose offset matches the home offset. */
    private fun findJoin(gid: Int, tol: Float): Pair<Int, Int>? {
        for (m in members.getValue(gid)) {
            val r = m / cols
            val c = m % cols
            for (k in 0 until 4) {
                val nr = r + DR[k]
                val nc = c + DC[k]
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue
                val nb = nr * cols + nc
                if (groupOf[nb] == gid || locked[nb] || inTray[nb]) continue
                if (abs(angleDiff(rot[m], rot[nb])) > ROT_TOL) continue
                val (hx, hy) = rotated(homeCx(nb) - homeCx(m), homeCy(nb) - homeCy(m), rot[m])
                val ex = (xs[nb] + halfW[nb]) - (xs[m] + halfW[m]) - hx
                val ey = (ys[nb] + halfH[nb]) - (ys[m] + halfH[m]) - hy
                if (hypot(ex, ey) <= tol) return m to nb
            }
        }
        return null
    }

    private fun lockGroup(gid: Int) {
        val g = members.getValue(gid)
        for (p in g) {
            xs[p] = homeX[p]
            ys[p] = homeY[p]
            rot[p] = 0f
            locked[p] = true
        }
        lockedCount += g.size
        // Locked pieces drop to the bottom, keeping their relative order.
        val set = g.toHashSet()
        val moved = z.filter { it in set }
        z.removeAll(set)
        z.addAll(0, moved)
    }

    private fun homeCx(p: Int) = homeX[p] + halfW[p]
    private fun homeCy(p: Int) = homeY[p] + halfH[p]

    private fun rotated(x: Float, y: Float, deg: Float): Pair<Float, Float> {
        if (deg == 0f) return x to y
        val rad = Math.toRadians(deg.toDouble())
        val c = cos(rad)
        val s = sin(rad)
        return (x * c - y * s).toFloat() to (x * s + y * c).toFloat()
    }

    companion object {
        private const val ROT_TOL = 10f

        private fun normalize(deg: Float): Float {
            var a = deg % 360f
            if (a < 0f) a += 360f
            return if (a >= 360f) 0f else a
        }

        /** Signed difference a - b folded into [-180, 180]. */
        private fun angleDiff(a: Float, b: Float): Float {
            var d = (a - b) % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }

        private val DR = intArrayOf(-1, 1, 0, 0)
        private val DC = intArrayOf(0, 0, -1, 1)

        /** Starts a new game with every piece scattered uniformly on [table] outside the board rectangle. */
        fun newGame(
            layout: PuzzleLayout,
            table: Box,
            seed: Long,
            startInTray: Boolean = false,
            rotation: RotationMode = RotationMode.OFF,
        ): PuzzleGame {
            val rng = SplitMix64(seed)
            val boardW = layout.spec.imageWidth.toFloat()
            val boardH = layout.spec.imageHeight.toFloat()
            val n = layout.pieces.size
            val positions = ArrayList<Pt>(n)
            for (piece in layout.pieces) {
                val w = piece.bounds.width
                val h = piece.bounds.height
                // Rectangles of feasible top-left positions: left, right, above, below the board.
                val regions = listOf(
                    Box(table.left, table.top, min(-w, table.right - w), table.bottom - h),
                    Box(maxOf(boardW, table.left), table.top, table.right - w, table.bottom - h),
                    Box(table.left, table.top, table.right - w, min(-h, table.bottom - h)),
                    Box(table.left, maxOf(boardH, table.top), table.right - w, table.bottom - h),
                ).filter { it.right >= it.left && it.bottom >= it.top }
                require(regions.isNotEmpty()) { "table has no room outside the board for piece ${piece.id}" }
                val areas = regions.map { (it.width.toDouble() + 1.0) * (it.height.toDouble() + 1.0) }
                var pick = rng.nextDouble() * areas.sum()
                var idx = 0
                while (idx < regions.size - 1 && pick >= areas[idx]) {
                    pick -= areas[idx]
                    idx++
                }
                val r = regions[idx]
                positions += Pt(
                    r.left + rng.nextFloat() * r.width,
                    r.top + rng.nextFloat() * r.height,
                )
            }
            val order = (0 until n).toMutableList()
            for (i in n - 1 downTo 1) { // Fisher-Yates with the engine's own PRNG
                val j = rng.nextInt(i + 1)
                val t = order[i]; order[i] = order[j]; order[j] = t
            }
            val angles = if (rotation == RotationMode.OFF) emptyList() else {
                val arng = SplitMix64(seed xor 0x5EED)
                List(n) {
                    if (rotation == RotationMode.QUARTER) arng.nextInt(4) * 90f else arng.nextFloat() * 360f
                }
            }
            return PuzzleGame(
                layout,
                GameState(
                    rotations = angles,
                    positions = positions,
                    groupOf = List(n) { it },
                    locked = List(n) { false },
                    zOrder = order,
                    tray = if (startInTray) shuffledIds(n, seed xor 0x7A7L) else emptyList(),
                ),
            )
        }

        /** Ids 0 until [n] in a deterministic shuffled order (Fisher-Yates with the engine's PRNG). */
        private fun shuffledIds(n: Int, seed: Long): List<Int> {
            val rng = SplitMix64(seed)
            val ids = (0 until n).toMutableList()
            for (i in n - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val t = ids[i]; ids[i] = ids[j]; ids[j] = t
            }
            return ids
        }

        /** Kid-mode snap distance: half the smaller cell dimension. */
        fun kidTolerance(layout: PuzzleLayout): Float = 0.5f * min(layout.cellWidth, layout.cellHeight)

        /** Kid-mode magnet radius: 0.30 of the smaller cell dimension. */
        fun kidMagnetRadius(layout: PuzzleLayout): Float = 0.30f * min(layout.cellWidth, layout.cellHeight)

        /** Snap distance for a [Snap] setting: its factor times the smaller cell dimension. */
        fun tolerance(layout: PuzzleLayout, snap: Snap): Float = snap.factor * min(layout.cellWidth, layout.cellHeight)

        /** Snap distance: a quarter of the smaller cell dimension. */
        fun defaultTolerance(layout: PuzzleLayout): Float = 0.25f * min(layout.cellWidth, layout.cellHeight)
    }
}
