package com.family.puzzlepop.kid

import com.family.puzzlepop.settings.KidSettings
import com.family.puzzlepop.settings.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PlayTimeKeeperTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val min = 60_000L
    private val day0 = LocalDateTime.of(2026, 11, 2, 10, 0).atZone(zone).toInstant().toEpochMilli()
    private val dayKey = "2026-11-02"

    private fun usage() = Usage(dayKey, 0, 0, null, false)
    private fun keeper(s: KidSettings = KidSettings(), u: Usage = usage()) = PlayTimeKeeper(s, u, zone)

    /** Ticks every 5 s from [from] for [ms]; returns last status and final time. */
    private fun PlayTimeKeeper.play(from: Long, ms: Long, playing: Boolean = true): Pair<PlayTimeKeeper.Status, Long> {
        var t = from
        var st = tick(t, playing)
        while (t < from + ms) {
            t += 5_000
            st = tick(t, playing)
        }
        return st to t
    }

    @Test
    fun countsOnlyWhilePlaying() {
        val k = keeper()
        k.tick(day0, true)
        k.play(day0, 60_000, playing = false)
        assertEquals(0L, k.usage.sittingUsedMs)
        k.play(day0 + 60_000, 30_000, playing = true)
        assertEquals(30_000L, k.usage.sittingUsedMs)
        assertEquals(30_000L, k.usage.dailyUsedMs)
    }

    @Test
    fun largeGapIsCappedAtFiveSeconds() {
        val k = keeper()
        k.tick(day0, true)
        k.tick(day0 + 10 * min, true)
        assertEquals(5_000L, k.usage.sittingUsedMs)
    }

    @Test
    fun warningAtTwoMinutes() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = null))
        val (st, _) = k.play(day0, 18 * min)
        assertEquals(PlayTimeKeeper.Status.Warning(120_000), st)
    }

    @Test
    fun sittingLimitStartsBreak() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = null))
        k.piecesLeftChanged(10)
        val (st, now) = k.play(day0, 20 * min)
        assertEquals(PlayTimeKeeper.Status.OnBreak(now + 30 * min), st)
    }

    @Test
    fun finishAllowance() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = null))
        k.piecesLeftChanged(4)
        val (st, now) = k.play(day0, 20 * min)
        assertEquals(PlayTimeKeeper.Status.FinishThenBreak, st)
        k.piecesLeftChanged(0)
        // break end is anchored to the tick that noticed the puzzle finishing
        assertEquals(PlayTimeKeeper.Status.OnBreak(now + 5_000 + 30 * min), k.tick(now + 5_000, true))
    }

    @Test
    fun finishAllowanceSafetyCap() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = null))
        k.piecesLeftChanged(4)
        val (_, now) = k.play(day0, 20 * min)
        val (mid, now2) = k.play(now, 2 * min)
        assertEquals(PlayTimeKeeper.Status.FinishThenBreak, mid)
        val (end, _) = k.play(now2, 90_000)
        assertTrue(end is PlayTimeKeeper.Status.OnBreak)
    }

    @Test
    fun breakEndsThenNewSitting() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = null))
        k.piecesLeftChanged(10)
        val (st, now) = k.play(day0, 20 * min)
        val ends = (st as PlayTimeKeeper.Status.OnBreak).endsAtEpochMs
        assertTrue(k.tick(ends - 1, false) is PlayTimeKeeper.Status.OnBreak)
        assertEquals(PlayTimeKeeper.Status.Playing, k.tick(ends, true))
        assertEquals(0L, k.usage.sittingUsedMs)
        assertTrue(now < ends)
    }

    @Test
    fun restartKeepsBreak() {
        val s = KidSettings(sittingLimitMin = 20, dailyLimitMin = null)
        val k = keeper(s)
        k.piecesLeftChanged(10)
        val (st, now) = k.play(day0, 20 * min)
        val rebuilt = keeper(s, k.usage)
        assertEquals(st, rebuilt.tick(now + 10 * min, true))
        assertEquals(k.usage, rebuilt.usage)
    }

    @Test
    fun dailyLimitDoneForToday() {
        val k = keeper(KidSettings(sittingLimitMin = null, dailyLimitMin = 60))
        k.piecesLeftChanged(20)
        val (st, now) = k.play(day0, 60 * min)
        assertEquals(PlayTimeKeeper.Status.DoneForToday, st)
        assertTrue(k.usage.doneForToday)
        assertEquals(PlayTimeKeeper.Status.DoneForToday, rebuiltStatus(k, now + 1_000))
    }

    private fun rebuiltStatus(k: PlayTimeKeeper, now: Long) =
        keeper(KidSettings(sittingLimitMin = null, dailyLimitMin = 60), k.usage).tick(now, true)

    @Test
    fun dailyBeatsSittingWhenBothReached() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = 20))
        k.piecesLeftChanged(20)
        val (st, _) = k.play(day0, 20 * min)
        assertEquals(PlayTimeKeeper.Status.DoneForToday, st)
    }

    @Test
    fun midnightResets() {
        val k = keeper(KidSettings(sittingLimitMin = null, dailyLimitMin = 60))
        k.piecesLeftChanged(20)
        val late = LocalDateTime.of(2026, 11, 2, 23, 59).atZone(zone).toInstant().toEpochMilli()
        k.tick(late - 1000, false)
        // fast-forward counters: rebuild from a near-limit usage on the same day
        val k2 = keeper(KidSettings(sittingLimitMin = null, dailyLimitMin = 60), Usage(dayKey, 60 * min, 0, null, false))
        assertEquals(PlayTimeKeeper.Status.DoneForToday, k2.tick(late, true))
        val next = LocalDateTime.of(2026, 11, 3, 0, 1).atZone(zone).toInstant().toEpochMilli()
        assertEquals(PlayTimeKeeper.Status.Playing, k2.tick(next, true))
        assertEquals(0L, k2.usage.dailyUsedMs)
        assertEquals("2026-11-03", k2.usage.dayKey)
        assertEquals(false, k2.usage.doneForToday)
    }

    @Test
    fun limitsOff() {
        val k = keeper(KidSettings(sittingLimitMin = null, dailyLimitMin = null))
        k.piecesLeftChanged(20)
        val (st, _) = k.play(day0, 3 * 60 * min)
        assertEquals(PlayTimeKeeper.Status.Playing, st)
    }

    @Test
    fun addTimeAndEndBreak() {
        val s = KidSettings(sittingLimitMin = 20, dailyLimitMin = null)
        val k = keeper(s)
        k.piecesLeftChanged(10)
        val (st, now) = k.play(day0, 20 * min)
        assertTrue(st is PlayTimeKeeper.Status.OnBreak)
        k.addTime(10)
        assertEquals(PlayTimeKeeper.Status.Playing, k.tick(now + 5_000, true))
        // 10 minutes remaining: warning only inside the last 2
        val (st2, now2) = k.play(now + 5_000, 8 * min)
        assertTrue(st2 is PlayTimeKeeper.Status.Warning)
        val (st3, _) = k.play(now2, 2 * min)
        assertTrue(st3 is PlayTimeKeeper.Status.OnBreak)
        k.endBreak()
        assertEquals(PlayTimeKeeper.Status.Playing, k.tick(now2 + 2 * min + 10_000, true))
        assertEquals(null, k.usage.breakEndsAtEpochMs)
    }

    @Test
    fun addTimeAddsFullAmount() {
        val k = keeper(KidSettings(sittingLimitMin = 5, dailyLimitMin = null))
        k.piecesLeftChanged(10)
        val (st, now) = k.play(day0, 5 * min)
        assertTrue(st is PlayTimeKeeper.Status.OnBreak)
        k.addTime(10)
        assertEquals(PlayTimeKeeper.Status.Playing, k.tick(now + 5_000, true))
        val (st2, now2) = k.play(now + 5_000, 8 * min)
        assertTrue(st2 is PlayTimeKeeper.Status.Warning)
        val (st3, _) = k.play(now2, 2 * min)
        assertTrue(st3 is PlayTimeKeeper.Status.OnBreak)
    }

    @Test
    fun newPuzzleNeverExtendsFinish() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = null))
        k.piecesLeftChanged(2)
        val (st, now) = k.play(day0, 20 * min)
        assertEquals(PlayTimeKeeper.Status.FinishThenBreak, st)
        k.piecesLeftChanged(12) // a new puzzle
        assertTrue(k.tick(now + 5_000, true) is PlayTimeKeeper.Status.OnBreak)
    }

    @Test
    fun finishedPuzzleBreaksWhenReportedZero() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = null))
        k.piecesLeftChanged(1)
        val (_, now) = k.play(day0, 20 * min)
        k.piecesLeftChanged(0)
        assertTrue(k.tick(now + 5_000, true) is PlayTimeKeeper.Status.OnBreak)
    }

    @Test
    fun turningDailyLimitOffEndsDoneForToday() {
        val done = Usage(dayKey, 60 * min, 0, null, true)
        assertEquals(PlayTimeKeeper.Status.DoneForToday, keeper(KidSettings(dailyLimitMin = 60), done).tick(day0, false))
        val st = keeper(KidSettings(dailyLimitMin = null, sittingLimitMin = null), done).tick(day0, false)
        assertEquals(PlayTimeKeeper.Status.Playing, st)
    }

    @Test
    fun raisingDailyLimitResumesPlay() {
        val done = Usage(dayKey, 60 * min, 0, null, true)
        val st = keeper(KidSettings(dailyLimitMin = 120, sittingLimitMin = null), done).tick(day0, false)
        assertEquals(PlayTimeKeeper.Status.Playing, st)
    }

    @Test
    fun turningSittingLimitOffEndsBreak() {
        val onBreak = Usage(dayKey, 20 * min, 20 * min, day0 + 10 * min, false)
        assertTrue(keeper(KidSettings(), onBreak).tick(day0, false) is PlayTimeKeeper.Status.OnBreak)
        val st = keeper(KidSettings(sittingLimitMin = null), onBreak).tick(day0, false)
        assertEquals(PlayTimeKeeper.Status.Playing, st)
    }

    @Test
    fun longGapStartsNewSitting() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = null, breakMin = 30))
        val (_, t) = k.play(day0, 15 * min)
        k.playStopped(t)
        k.tick(t + 1_000, false)
        k.tick(t + 40 * min, true)
        k.tick(t + 40 * min + 5_000, true)
        assertTrue(k.usage.sittingUsedMs <= 10_000L) // new sitting: only the two capped ticks just played
        assertTrue(k.usage.dailyUsedMs > 15 * min)
        assertEquals(null, k.usage.lastStopEpochMs)
    }

    @Test
    fun shortGapKeepsSitting() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = null, breakMin = 30))
        val (_, t) = k.play(day0, 15 * min)
        k.playStopped(t)
        k.tick(t + 10 * min, false)
        k.tick(t + 10 * min + 5_000, true)
        assertTrue(k.usage.sittingUsedMs >= 15 * min)
    }

    @Test
    fun gapDoesNotEndBreakEarly() {
        val k = keeper(KidSettings(sittingLimitMin = 20, dailyLimitMin = null, breakMin = 30))
        k.piecesLeftChanged(100)
        val (st, t) = k.play(day0, 21 * min)
        assertTrue(st is PlayTimeKeeper.Status.OnBreak)
        k.playStopped(t - 60 * min) // an older stop, gap far longer than the break
        val st2 = k.tick(t + 5 * min, true)
        assertTrue(st2 is PlayTimeKeeper.Status.OnBreak)
    }
}
