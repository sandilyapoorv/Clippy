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
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import com.clippy.core.CopyDetector
import com.clippy.core.Fingerprint

class ClipboardAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val clipboard by lazy { getSystemService(CLIPBOARD_SERVICE) as ClipboardManager }
    private var lastSelection: String = ""
    private var lastSavedAt = 0L
    private var lastSavedText: String = ""
    private var capturing = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (ClippyApp.ignoreNextClipboardChange) {
            ClippyApp.ignoreNextClipboardChange = false
            return@OnPrimaryClipChangedListener
        }
        handler.post { captureCopy(forceOverlay = true) }
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        clipboard.addPrimaryClipChangedListener(clipListener)
        connected = this
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
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            -> cacheSelection(event)
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            -> {
                if (isCopyEvent(event)) {
                    handler.post { captureCopy(forceOverlay = true) }
                }
            }
        }
    }

    private fun isCopyEvent(event: AccessibilityEvent): Boolean {
        val source = event.source
        try {
            return CopyDetector.looksLikeCopyAction(
                event.contentDescription?.toString(),
                event.text?.joinToString(" "),
                event.className?.toString(),
                source?.contentDescription?.toString(),
                source?.text?.toString(),
                source?.viewIdResourceName,
            )
        } finally {
            source?.recycle()
        }
    }

    private fun cacheSelection(event: AccessibilityEvent) {
        val fromEvent = selectedText(event)
        if (!fromEvent.isNullOrBlank()) {
            lastSelection = fromEvent.trim()
            return
        }
        val fromTree = selectedFromActiveWindow()
        if (!fromTree.isNullOrBlank()) {
            lastSelection = fromTree.trim()
        }
    }

    private fun selectedText(event: AccessibilityEvent): String? {
        val node = event.source
        try {
            val nodeText = sliceSelection(node)
            if (!nodeText.isNullOrBlank()) return nodeText
            val joined = event.text?.joinToString("")?.trim()
            return joined?.takeIf { it.isNotEmpty() }
        } finally {
            node?.recycle()
        }
    }

    private fun selectedFromActiveWindow(): String? {
        val root = rootInActiveWindow ?: return null
        try {
            return findSelected(root)
        } finally {
            root.recycle()
        }
    }

    private fun findSelected(node: AccessibilityNodeInfo): String? {
        val own = sliceSelection(node)
        if (!own.isNullOrBlank()) return own
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findSelected(child)
            child.recycle()
            if (!found.isNullOrBlank()) return found
        }
        return null
    }

    private fun sliceSelection(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val text = node.text?.toString() ?: return null
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        if (start >= 0 && end > start && end <= text.length) {
            return text.substring(start, end)
        }
        if (node.isSelected && text.isNotBlank()) return text
        return null
    }

    private fun captureCopy(forceOverlay: Boolean) {
        if (capturing) return
        capturing = true
        val candidates = listOfNotNull(
            lastSelection.takeIf { it.isNotBlank() },
            selectedFromActiveWindow(),
        )
        for (text in candidates) {
            if (saveText(text)) {
                capturing = false
                return
            }
        }
        val clipStatus = ClipboardCapture(this).captureCurrent()
        if (clipStatus != CaptureStatus.EMPTY) {
            capturing = false
            return
        }
        if (forceOverlay) {
            peekOverlayAndSave()
        } else {
            capturing = false
        }
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
            x = 0
            y = 0
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
                if (status == CaptureStatus.EMPTY) {
                    field.onTextContextMenuItem(android.R.id.paste)
                    val pasted = field.text?.toString()
                    if (!Fingerprint.isBlankText(pasted)) {
                        saveText(pasted!!.trim())
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

    private fun saveText(text: String): Boolean {
        if (Fingerprint.isBlankText(text)) return false
        val now = System.currentTimeMillis()
        if (text == lastSavedText && now - lastSavedAt < 1_000) return true
        val status = ClipboardCapture(this).captureSharedText(text)
        if (status != CaptureStatus.EMPTY) {
            lastSavedText = text
            lastSavedAt = now
            return true
        }
        return false
    }

    companion object {
        @Volatile
        var connected: ClipboardAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            if (connected != null) return true
            val manager = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
            val expected = "${context.packageName}/${ClipboardAccessibilityService::class.java.name}"
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.id.equals(expected, ignoreCase = true) || it.id.contains("ClipboardAccessibilityService") }
        }
    }
}
