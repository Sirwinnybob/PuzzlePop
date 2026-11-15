package com.family.puzzlepop.kid

import com.family.puzzlepop.settings.KidSettings
import com.family.puzzlepop.settings.Usage
import java.time.Instant
import java.time.ZoneId

/**
 * Tracks play time against the sitting and daily limits. Pure Kotlin + java.time so it is JVM testable.
 * Call [tick] regularly; only time while `playing` counts, and each tick adds at most [MAX_TICK_MS].
 */
class PlayTimeKeeper(
    private val settings: KidSettings,
    usage: Usage,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    sealed interface Status {
        data object Playing : Status
        data class Warning(val remainingMs: Long) : Status
        data object FinishThenBreak : Status
        data class OnBreak(val endsAtEpochMs: Long) : Status
        data object DoneForToday : Status
    }

    private var dayKey = usage.dayKey
    private var dailyUsedMs = usage.dailyUsedMs
    private var sittingUsedMs = usage.sittingUsedMs
    private var breakEndsAt = usage.breakEndsAtEpochMs
    private var doneForToday = usage.doneForToday
    private var lastStop = usage.lastStopEpochMs
    private var wasPlaying = false

    private var lastTick: Long? = null
    private var piecesLeft = Int.MAX_VALUE
    private var finishing = false
    private var finishExtraMs = 0L

    val usage: Usage
        get() = Usage(dayKey, dailyUsedMs, sittingUsedMs, breakEndsAt, doneForToday, lastStop)

    fun piecesLeftChanged(left: Int) {
        piecesLeft = left
    }

    fun addTime(minutes: Int) {
        val extra = minutes * MIN_MS
        // Exactly [minutes] more than the limit allows right now: used time is first brought down to the
        // limit (an overrun does not count), then reduced by the bonus (it may go negative).
        settings.dailyLimitMin?.let { dailyUsedMs = minOf(dailyUsedMs, it * MIN_MS) - extra }
        settings.sittingLimitMin?.let { sittingUsedMs = minOf(sittingUsedMs, it * MIN_MS) - extra }
        doneForToday = false
        breakEndsAt = null
        finishing = false
        finishExtraMs = 0
    }

    /** Kid play stopped at [nowEpochMs]; a long enough gap before the next play starts a new sitting. */
    fun playStopped(nowEpochMs: Long) {
        lastStop = nowEpochMs
    }

    fun endBreak() {
        resetSitting()
    }

    private fun resetSitting() {
        sittingUsedMs = 0
        breakEndsAt = null
        finishing = false
        finishExtraMs = 0
    }

    fun tick(nowEpochMs: Long, playing: Boolean): Status {
        var delta = lastTick?.let { (nowEpochMs - it).coerceIn(0, MAX_TICK_MS) } ?: 0L
        lastTick = nowEpochMs

        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate().toString()
        if (today != dayKey) {
            dayKey = today
            dailyUsedMs = 0
            sittingUsedMs = 0
            doneForToday = false
            finishing = false
            finishExtraMs = 0
            delta = 0
        }

        // A limit that was turned off or raised ends "done for today" and any running break.
        val dailyCap = settings.dailyLimitMin?.let { it * MIN_MS }
        if (doneForToday && (dailyCap == null || dailyUsedMs < dailyCap)) doneForToday = false
        if (settings.sittingLimitMin == null && breakEndsAt != null) resetSitting()

        if (doneForToday) return Status.DoneForToday

        breakEndsAt?.let { end ->
            if (nowEpochMs < end) return Status.OnBreak(end)
            resetSitting()
            delta = 0
        }

        if (playing && !wasPlaying) {
            // Play resumes. No break or done-for-today is active here (both returned above).
            val stop = lastStop
            if (stop != null && nowEpochMs - stop >= settings.breakMin * MIN_MS) resetSitting()
            lastStop = null
        }
        wasPlaying = playing

        if (playing) {
            dailyUsedMs += delta
            sittingUsedMs += delta
            if (finishing) finishExtraMs += delta
        }

        val dailyLeft = settings.dailyLimitMin?.let { it * MIN_MS - dailyUsedMs }
        val sittingLeft = settings.sittingLimitMin?.let { it * MIN_MS - sittingUsedMs }

        if (dailyLeft != null && dailyLeft <= 0) {
            if (mayFinish()) return Status.FinishThenBreak
            doneForToday = true
            return Status.DoneForToday
        }
        if (sittingLeft != null && sittingLeft <= 0) {
            if (mayFinish()) return Status.FinishThenBreak
            val end = nowEpochMs + settings.breakMin * MIN_MS
            breakEndsAt = end
            finishing = false
            finishExtraMs = 0
            return Status.OnBreak(end)
        }

        val remaining = listOfNotNull(dailyLeft, sittingLeft).minOrNull() ?: return Status.Playing
        return if (remaining <= WARNING_MS) Status.Warning(remaining) else Status.Playing
    }

    /** True while a nearly finished puzzle may be completed after the limit; latches until the cap or completion. */
    private fun mayFinish(): Boolean {
        // Only the current puzzle counts: a new (bigger) one never extends the finish window.
        val eligible = piecesLeft in 1..settings.finishAllowance
        val ok = eligible && finishExtraMs < FINISH_CAP_MS
        finishing = ok
        return ok
    }

    private companion object {
        const val MIN_MS = 60_000L
        const val MAX_TICK_MS = 5_000L
        const val WARNING_MS = 2 * MIN_MS
        const val FINISH_CAP_MS = 3 * MIN_MS
    }
}
