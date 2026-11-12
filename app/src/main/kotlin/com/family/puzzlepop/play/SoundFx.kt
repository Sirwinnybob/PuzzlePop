package com.family.puzzlepop.play

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.family.puzzlepop.R

/** The play screen's short sound effects, preloaded into a [SoundPool]. Call [release] when done. */
class SoundFx(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val snap = pool.load(context, R.raw.snap, 1)
    private val join = pool.load(context, R.raw.join, 1)
    private val finish = pool.load(context, R.raw.finish, 1)
    private val fanfare = pool.load(context, R.raw.fanfare, 1)
    private val tray = pool.load(context, R.raw.tray, 1)
    private val hint = pool.load(context, R.raw.hint, 1)

    fun snap() = play(snap)
    fun join() = play(join)
    fun finish() = play(finish)
    fun fanfare() = play(fanfare)
    fun tray() = play(tray)
    fun hint() = play(hint)

    fun release() = pool.release()

    // SoundPool.play is asynchronous and cheap; a sound that hasn't finished loading is silently skipped.
    private fun play(id: Int) {
        pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    private companion object {
        const val VOLUME = 0.9f
    }
}
