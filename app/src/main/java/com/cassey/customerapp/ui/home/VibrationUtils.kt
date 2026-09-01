package com.cassey.customerapp.ui.home

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

private const val VIBRATE_CHANNEL_ID = "ridego_silent_vibrate"

@RequiresApi(Build.VERSION_CODES.O)
fun vibrateDevice(context: Context, durationMs: Long = 600) {
    vibrateDirect(context, durationMs)
    vibrateViaNotification(context, durationMs)
}

@RequiresApi(Build.VERSION_CODES.O)
private fun vibrateDirect(context: Context, durationMs: Long) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= 33) {
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                .build()
            vibrator.vibrate(effect, attributes)
        } else {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            vibrator.vibrate(effect, audioAttributes)
        }
    } catch (e: Exception) {
        // Vibration fallback failed.
    }
}

private fun vibrateViaNotification(context: Context, durationMs: Long) {
    try {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(VIBRATE_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                VIBRATE_CHANNEL_ID,
                "Ride Alerts",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, durationMs)
                setSound(null, null)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, VIBRATE_CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Ride update")
            .setContentText("Your booking status changed.")
            .setVibrate(longArrayOf(0, durationMs))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    } catch (e: Exception) {
        // Notification fallback failed.
    }
}