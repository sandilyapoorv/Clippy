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
    }

    @Test
    fun ignoresUnrelatedUi() {
        assertFalse(CopyDetector.looksLikeCopyAction("Copyright 2026"))
        assertFalse(CopyDetector.looksLikeCopyAction("Share"))
        assertFalse(CopyDetector.looksLikeCopyAction(null, "", "   "))
    }
}
