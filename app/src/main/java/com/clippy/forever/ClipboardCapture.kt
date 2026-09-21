package com.clippy.forever

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.clippy.core.CopyDetector
import com.clippy.core.Fingerprint

enum class CaptureStatus { SAVED, DUPLICATE, EMPTY }

class ClipboardCapture(private val context: Context) {
    private val store = (context.applicationContext as ClippyApp).store
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun captureCurrent(): CaptureStatus {
        val clip = clipboard.primaryClip ?: return CaptureStatus.EMPTY
        return captureClip(clip)
    }

    fun captureClip(clip: ClipData): CaptureStatus {
        var last = CaptureStatus.EMPTY
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            val text = item.coerceToText(context)?.toString()
            val uri = item.uri
            val imageMime = imageMime(clip, uri)
            last = when {
                uri != null && imageMime != null -> saveImage(uri, imageMime)
                !Fingerprint.isBlankText(text) -> saveText(text!!)
                uri != null -> saveImage(uri, context.contentResolver.getType(uri) ?: "image/*")
                else -> CaptureStatus.EMPTY
            }
            if (last == CaptureStatus.SAVED) return last
        }
        return last
    }

    fun captureSharedText(text: String): CaptureStatus = saveText(text)

    fun captureSharedUri(uri: Uri, mimeType: String?): CaptureStatus = saveImage(uri, mimeType)

    fun copyBack(record: ClipRecord) {
        val clip = if (record.kind == "IMAGE" && record.imagePath != null) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                java.io.File(record.imagePath),
            )
            ClipData.newUri(context.contentResolver, "Clippy image", uri)
        } else {
            ClipData.newPlainText("Clippy text", record.text.orEmpty())
        }
        clipboard.setPrimaryClip(clip)
        ClippyApp.ignoreNextClipboardChange = true
    }

    private fun saveText(text: String): CaptureStatus {
        if (CopyDetector.looksLikeUiChrome(text)) return CaptureStatus.EMPTY
        ClipInbox.addText(context, text)
        val result = store.insertText(text, Fingerprint.ofText(text))
        return when {
            result.failed -> CaptureStatus.SAVED
            result.duplicate -> CaptureStatus.DUPLICATE
            else -> CaptureStatus.SAVED
        }
    }

    private fun saveImage(uri: Uri, mimeType: String?): CaptureStatus {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return CaptureStatus.EMPTY
        if (bytes.isEmpty()) return CaptureStatus.EMPTY
        val mime = mimeType ?: context.contentResolver.getType(uri) ?: "image/*"
        val result = store.insertImage(bytes, mime, Fingerprint.ofImage(bytes))
        return when {
            result.failed -> CaptureStatus.EMPTY
            result.duplicate -> CaptureStatus.DUPLICATE
            else -> CaptureStatus.SAVED
        }
    }

    private fun imageMime(clip: ClipData, uri: Uri?): String? {
        if (uri == null) return null
        val fromDescription = (0 until clip.description.mimeTypeCount)
            .map { clip.description.getMimeType(it) }
            .firstOrNull { it.startsWith("image/") }
        if (fromDescription != null) return fromDescription
        val fromUri = context.contentResolver.getType(uri)
        return fromUri?.takeIf { it.startsWith("image/") }
    }
}
