package com.clippy.forever

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import com.clippy.core.CopyDetector
import com.clippy.core.Fingerprint

class ClipboardAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val clipboard by lazy { getSystemService(CLIPBOARD_SERVICE) as ClipboardManager }
    private var capturing = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (ClippyApp.ignoreNextClipboardChange) {
            ClippyApp.ignoreNextClipboardChange = false
            return@OnPrimaryClipChangedListener
        }
        handler.post { saveClipboardOnly() }
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        clipboard.addPrimaryClipChangedListener(clipListener)
        connected = this
        ClipboardWatchService.start(this)
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        if (connected === this) connected = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() == packageName) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED,
            -> {
                if (isCopyButton(event)) {
                    handler.post { saveClipboardOnly() }
                }
            }
        }
    }

    private fun isCopyButton(event: AccessibilityEvent): Boolean {
        val source = event.source
        try {
            return CopyDetector.looksLikeCopyAction(
                event.contentDescription?.toString(),
                source?.contentDescription?.toString(),
                source?.text?.toString(),
                source?.viewIdResourceName,
            )
        } finally {
            source?.recycle()
        }
    }

    private fun saveClipboardOnly() {
        if (capturing) return
        capturing = true
        val status = ClipboardCapture(this).captureCurrent()
        if (status == CaptureStatus.SAVED) {
            val preview = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            SaveNotifier.noteLastSaved(this, preview.ifBlank { "clipboard" })
            capturing = false
            return
        }
        if (status == CaptureStatus.DUPLICATE) {
            capturing = false
            return
        }
        peekOverlayAndSave()
    }

    private fun peekOverlayAndSave() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val field = EditText(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            alpha = 0f
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            windowManager.addView(field, params)
        } catch (_: Exception) {
            capturing = false
            return
        }
        field.post {
            try {
                field.requestFocus()
                val status = ClipboardCapture(this).captureCurrent()
                if (status == CaptureStatus.SAVED) {
                    val preview = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
                    SaveNotifier.noteLastSaved(this, preview.ifBlank { "clipboard" })
                } else if (status == CaptureStatus.EMPTY) {
                    field.onTextContextMenuItem(android.R.id.paste)
                    val pasted = field.text?.toString()?.trim().orEmpty()
                    if (!Fingerprint.isBlankText(pasted) && !CopyDetector.looksLikeUiChrome(pasted)) {
                        ClipboardCapture(this).captureSharedText(pasted)
                        SaveNotifier.noteLastSaved(this, pasted)
                    }
                }
            } finally {
                try {
                    windowManager.removeView(field)
                } catch (_: Exception) {
                }
                capturing = false
            }
        }
    }

    companion object {
        @Volatile
        var connected: ClipboardAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            if (connected != null) return true
            val manager = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.id.contains("ClipboardAccessibilityService") }
        }
    }
}
