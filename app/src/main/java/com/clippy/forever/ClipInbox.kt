package com.clippy.forever

import android.content.Context
import com.clippy.core.Fingerprint

/**
 * Crash-safe text queue. Accessibility can be killed before SQLite finishes;
 * SharedPreferences.commit() flushes to disk immediately.
 */
object ClipInbox {
    private const val PREFS = "clippy_inbox"
    private const val KEY = "pending"
    private const val SEP = "\u0001"

    @Synchronized
    fun addText(context: Context, text: String) {
        if (Fingerprint.isBlankText(text)) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, "").orEmpty()
        val line = System.currentTimeMillis().toString() + "\t" + text.replace(SEP, " ")
        prefs.edit().putString(KEY, if (existing.isBlank()) line else existing + SEP + line).commit()
    }

    @Synchronized
    fun drain(context: Context, store: ClipStore) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val blob = prefs.getString(KEY, "") ?: return
        if (blob.isBlank()) return
        prefs.edit().remove(KEY).commit()
        blob.split(SEP).forEach { line ->
            val tab = line.indexOf('\t')
            val text = if (tab >= 0) line.substring(tab + 1) else line
            if (!Fingerprint.isBlankText(text)) {
                store.insertText(text, Fingerprint.ofText(text))
            }
        }
    }
}
