package com.clippy.forever

data class ClipRecord(
    val id: Long,
    val createdAt: Long,
    val kind: String,
    val text: String?,
    val imagePath: String?,
    val mimeType: String?,
    val fingerprint: String,
)
