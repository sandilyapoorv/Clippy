package com.clippy.forever

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object SaveNotifier {
    const val CHANNEL_ID = "clippy_save"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val tap = PendingIntent.getActivity(
            context,
            1,
            Intent(context, CaptureActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clipboard)
            .setContentTitle(context.getString(R.string.notify_title))
            .setContentText(context.getString(R.string.notify_body))
            .setContentIntent(tap)
            .setOngoing(true)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(context).notify(42, notification)
    }

    fun noteLastSaved(context: Context, preview: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val tap = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snippet = preview.trim().replace("\n", " ").take(80)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clipboard)
            .setContentTitle(context.getString(R.string.notify_title))
            .setContentText(context.getString(R.string.last_saved, snippet))
            .setContentIntent(tap)
            .setOngoing(true)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(context).notify(42, notification)
    }
}
