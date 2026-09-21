package com.clippy.core

object CopyDetector {
    private val exact = setOf(
        "copy",
        "cut",
        "copied",
        "copy all",
        "copy link",
        "copy text",
        "copy image",
        "copy to clipboard",
        "copied to clipboard",
    )

    fun looksLikeCopyAction(vararg parts: String?): Boolean {
        val pieces = parts.mapNotNull { it?.trim() }.filter { it.isNotEmpty() }
        if (pieces.isEmpty()) return false
        if (pieces.any { it.lowercase() in exact }) return true
        val id = pieces.joinToString(" ").lowercase()
        if (id.contains("copyright") || id.contains("photocopy")) return false
        return id.contains("/copy") || id.contains(":id/copy") || id.endsWith("_copy") || id.endsWith("/cut")
    }

    fun looksLikeUiChrome(text: String): Boolean {
        val compact = text.trim()
        if (compact.length > 40) return false
        val lower = compact.lowercase().replace("\\s+".toRegex(), " ")
        val chrome = setOf(
            "wi-fi", "wi - fi", "wifi", "wi-fi off", "wi - fi off", "wi-fioff", "wi - fioff",
            "crop", "message", "phone", "clock", "bluetooth", "hotspot", "flashlight",
            "airplane", "silent", "vibrate", "mobile data",
        )
        if (lower in chrome) return true
        if (lower.contains("true5g") || lower.contains("jio")) return true
        return false
    }
}
