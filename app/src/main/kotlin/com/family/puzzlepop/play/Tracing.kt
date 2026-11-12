package com.family.puzzlepop.play

import android.os.Trace

/**
 * A named section in system traces (Perfetto / atrace). Nearly free when no trace is being recorded, so
 * these stay in release builds; the manifest marks the app profileable so they can be captured there.
 */
internal inline fun <T> traced(name: String, block: () -> T): T {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}
