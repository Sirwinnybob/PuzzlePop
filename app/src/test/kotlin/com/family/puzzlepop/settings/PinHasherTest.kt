package com.family.puzzlepop.settings

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun sameSaltSameHash() {
        val salt = PinHasher.newSalt()
        assertArrayEquals(PinHasher.hash("1234", salt), PinHasher.hash("1234", salt))
    }

    @Test
    fun differentSaltDifferentHash() {
        val a = PinHasher.hash("1234", PinHasher.newSalt())
        val b = PinHasher.hash("1234", PinHasher.newSalt())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun hashIsNotPin() {
        val pin = "4821"
        val hash = PinHasher.hash(pin, PinHasher.newSalt())
        assertTrue(hash.size == 32)
        val ascii = pin.toByteArray(Charsets.US_ASCII)
        val contains = (0..hash.size - ascii.size).any { i ->
            ascii.indices.all { hash[i + it] == ascii[it] }
        }
        assertFalse(contains)
    }
}
