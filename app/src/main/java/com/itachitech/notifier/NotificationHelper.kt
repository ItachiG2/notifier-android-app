package com.itachitech.notifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun getServiceNotification(contentText: String, @DrawableRes iconResId: Int): Notification {
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("Notifier Service")
            .setContentText(contentText)
            .setSmallIcon(iconResId)
            .setOngoing(true)
            .build()
    }

    fun showIncomingCallNotification(title: String, message: String) {
        createIncomingCallChannel()

        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra("message", message)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, NotifierService::class.java).apply {
            action = "ACTION_DISMISS_CALL"
        }
        val dismissPendingIntent = PendingIntent.getService(
            context, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_sync_24)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(0, "Dismiss", dismissPendingIntent)

        // Only add the full-screen intent if the permission has been granted.
        if (canUseFullScreenIntent()) {
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun canUseFullScreenIntent(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notificationManager.canUseFullScreenIntent()
        } else {
            true // Permission is granted by default on older versions.
        }
    }

    fun showMissedCallNotification(message: String) {
        createMissedCallChannel()

        val notificationBuilder = NotificationCompat.Builder(context, MISSED_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_sync_24)
            .setContentTitle("Missed Call")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notificationManager.notify(MISSED_CALL_NOTIFICATION_ID, notificationBuilder.build())
    }

    fun cancelIncomingCallNotification() {
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)
    }

    fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Notifier Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createIncomingCallChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                INCOMING_CALL_CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for showing incoming call notifications."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createMissedCallChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MISSED_CALL_CHANNEL_ID,
                "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel for showing missed call notifications."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val SERVICE_NOTIFICATION_ID = 1
        const val SERVICE_CHANNEL_ID = "NotifierServiceChannel"
        private const val INCOMING_CALL_NOTIFICATION_ID = 2
        private const val MISSED_CALL_NOTIFICATION_ID = 3
        private const val INCOMING_CALL_CHANNEL_ID = "IncomingCallChannel"
        private const val MISSED_CALL_CHANNEL_ID = "MissedCallChannel"
    }
}