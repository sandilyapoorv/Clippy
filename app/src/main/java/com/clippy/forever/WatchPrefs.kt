package com.clippy.forever

import android.content.Context

object WatchPrefs {
    private const val PREFS = "clippy"
    private const val KEY_ENABLED = "watch_enabled"
    private const val KEY_ASKED_BATTERY = "asked_battery"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun shouldAskBattery(context: Context): Boolean {
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASKED_BATTERY, false)
    }

    fun markBatteryAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ASKED_BATTERY, true)
            .apply()
    }
}
