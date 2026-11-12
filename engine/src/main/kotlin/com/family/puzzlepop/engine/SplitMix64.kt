package com.family.puzzlepop.engine

/**
 * Tiny deterministic PRNG (SplitMix64). The engine owns its algorithm so that saved puzzles, which store
 * only a seed, regenerate bit-exactly regardless of Kotlin/JDK upgrades (kotlin.random.Random's algorithm
 * is explicitly not guaranteed stable).
 */
internal class SplitMix64(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1) with 24 bits of mantissa. */
    fun nextFloat(): Float = (nextLong() ushr 40).toInt() / 16777216f

    /** Uniform in [0, 1) with 53 bits of mantissa. */
    fun nextDouble(): Double = (nextLong() ushr 11) / 9007199254740992.0

    fun nextBoolean(): Boolean = nextLong() < 0

    /** Uniform in [0, bound); bound must be positive. Uses a high-bits multiply (negligible bias for small bounds). */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 33) * bound ushr 31).toInt()
    }
}
