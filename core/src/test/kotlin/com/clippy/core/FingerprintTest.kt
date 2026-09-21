package com.clippy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintTest {
    @Test
    fun textFingerprintIsStable() {
        val first = Fingerprint.ofText("hello clippy")
        val second = Fingerprint.ofText("hello clippy")
        assertEquals(first, second)
        assertTrue(first.startsWith("text:"))
        assertEquals(64 + 5, first.length)
    }

    @Test
    fun differentTextHasDifferentFingerprint() {
        assertNotEquals(Fingerprint.ofText("alpha"), Fingerprint.ofText("beta"))
    }

    @Test
    fun imageFingerprintUsesBytes() {
        val a = Fingerprint.ofImage(byteArrayOf(1, 2, 3))
        val b = Fingerprint.ofImage(byteArrayOf(1, 2, 3))
        val c = Fingerprint.ofImage(byteArrayOf(9))
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertTrue(a.startsWith("image:"))
    }

    @Test
    fun previewTruncatesLongText() {
        val long = "x".repeat(300)
        val preview = Fingerprint.preview(long, 240)
        assertTrue(preview.endsWith("…"))
        assertEquals(241, preview.length)
    }

    @Test
    fun blankTextDetection() {
        assertTrue(Fingerprint.isBlankText("   "))
        assertTrue(Fingerprint.isBlankText(null))
        assertFalse(Fingerprint.isBlankText("copied"))
    }
}
