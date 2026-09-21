package com.clippy.core

import java.security.MessageDigest

object Fingerprint {
    fun ofText(text: String): String = "text:" + sha256(text.toByteArray(Charsets.UTF_8))

    fun ofImage(bytes: ByteArray): String = "image:" + sha256(bytes)

    fun preview(text: String, maxChars: Int = 240): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed
        return trimmed.take(maxChars).trimEnd() + "…"
    }

    fun isBlankText(text: String?): Boolean = text == null || text.trim().isEmpty()

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
