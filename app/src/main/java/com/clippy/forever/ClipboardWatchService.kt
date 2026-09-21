package com.clippy.forever

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ClipboardWatchService : Service() {
    private val clipboard by lazy {
        getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }

    private val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
        onClipboardChanged()
    }

    override fun onCreate() {
        super.onCreate()
        SaveNotifier.ensureChannel(this)
        startAsForeground()
        clipboard.addPrimaryClipChangedListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!WatchPrefs.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(listener)
        super.onDestroy()
        if (WatchPrefs.isEnabled(this) && !userRequestedStop) {
            start(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onClipboardChanged() {
        if (ClippyApp.ignoreNextClipboardChange) {
            ClippyApp.ignoreNextClipboardChange = false
            return
        }
        if (ClippyApp.mainVisible) return
        val status = ClipboardCapture(this).captureCurrent()
        if (status != CaptureStatus.EMPTY) return
        bringCaptureToFront()
    }

    private fun bringCaptureToFront() {
        val capture = Intent(this, CaptureActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
            .putExtra(CaptureActivity.EXTRA_SILENT, true)
        try {
            startActivity(capture)
        } catch (_: Exception) {
            // Without overlay permission Android blocks this from the background.
        }
    }

    private fun startAsForeground() {
        val tap = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, SaveNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clipboard)
            .setContentTitle(getString(R.string.notify_title))
            .setContentText(getString(R.string.notify_body))
            .setContentIntent(tap)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            if (!WatchPrefs.isEnabled(context)) return
            userRequestedStop = false
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ClipboardWatchService::class.java),
                )
            } catch (_: Exception) {
                // Android may block FGS start until the user opens the app.
            }
        }

        fun stop(context: Context) {
            userRequestedStop = true
            context.stopService(Intent(context, ClipboardWatchService::class.java))
        }

        @Volatile
        var userRequestedStop: Boolean = false
    }
}
