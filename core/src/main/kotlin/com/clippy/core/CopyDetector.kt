package com.clippy.core

object CopyDetector {
    private val copyWords = setOf("copy", "copied", "cut", "clipboard")
    private val skip = setOf("copyright", "photocopy")

    fun looksLikeCopyAction(vararg parts: String?): Boolean {
        val label = parts.filterNotNull().joinToString(" ").lowercase().trim()
        if (label.isEmpty()) return false
        if (skip.any { label.contains(it) }) return false
        if (label.contains("copy to clipboard") || label.contains("copied to clipboard")) return true
        if (label.contains("/copy") || label.contains(":id/copy") || label.contains("_copy")) return true
        val tokens = label.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return tokens.any { it in copyWords }
    }
}
