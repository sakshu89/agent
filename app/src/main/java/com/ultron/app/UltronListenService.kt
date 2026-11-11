package com.ultron.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground service that keeps ITRA's microphone loop alive for hands-free mode,
 * so listening continues while other apps (YouTube, WhatsApp...) are on screen.
 * The listening itself is done by [AssistantController].
 */
class UltronListenService : Service() {
    companion object {
        const val ACTION_STOP = "com.ultron.app.STOP_HANDS_FREE"
        private const val CHANNEL_ID = "ultron_hands_free"
        private const val NOTIFICATION_ID = 4107
    }

    override fun onCreate() {
        super.onCreate()
        AssistantController.init(applicationContext)
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (t: Throwable) {
            LogStore.e("SERVICE", "Could not start the foreground listener", t)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AssistantController.setHandsFree(false)
            stopSelf()
            return START_NOT_STICKY
        }
        // Restarted by the system after the process was killed: pick hands-free back up.
        if (intent == null) {
            AssistantController.resumeIfEnabled()
            if (!AssistantController.handsFree) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        LogStore.i("SERVICE", "Hands-free listener stopped.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "ITRA hands-free", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flags
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, UltronListenService::class.java).setAction(ACTION_STOP), flags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ITRA is listening")
            .setContentText("Say \"Ultron ...\" followed by a command")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
