package com.clippy.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyDetectorTest {
    @Test
    fun detectsCopyButton() {
        assertTrue(CopyDetector.looksLikeCopyAction("Copy"))
        assertTrue(CopyDetector.looksLikeCopyAction("copied to clipboard"))
        assertTrue(CopyDetector.looksLikeCopyAction("com.foo:id/copy"))
        assertTrue(CopyDetector.looksLikeCopyAction("Cut"))
        assertTrue(CopyDetector.looksLikeCopyAction("Copy link"))
    }

    @Test
    fun ignoresUnrelatedUi() {
        assertFalse(CopyDetector.looksLikeCopyAction("Copyright 2026"))
        assertFalse(CopyDetector.looksLikeCopyAction("Share"))
        assertFalse(CopyDetector.looksLikeCopyAction("Wi - Fi"))
        assertFalse(CopyDetector.looksLikeCopyAction("Crop"))
        assertFalse(CopyDetector.looksLikeCopyAction("Message"))
        assertFalse(CopyDetector.looksLikeCopyAction("Jio True5G - Jio"))
        assertFalse(CopyDetector.looksLikeCopyAction(null, "", "   "))
    }

    @Test
    fun flagsStatusBarJunk() {
        assertTrue(CopyDetector.looksLikeUiChrome("Wi - Fi"))
        assertTrue(CopyDetector.looksLikeUiChrome("Crop"))
        assertTrue(CopyDetector.looksLikeUiChrome("Jio True5G - Jio"))
        assertTrue(CopyDetector.looksLikeUiChrome("Message"))
        assertTrue(CopyDetector.looksLikeUiChrome("Wi - FiOff"))
        assertFalse(CopyDetector.looksLikeUiChrome("Meeting notes for Friday"))
    }
}
