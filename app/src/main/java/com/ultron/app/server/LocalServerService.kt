package com.ultron.app.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.ultron.app.LocalServerConfig

class LocalServerService : Service() {
    private var server: LocalApiServer? = null
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("ultron_server", "ITRA Local Server", NotificationManager.IMPORTANCE_LOW))
        }
        startForeground(2205, notification())
        server = LocalApiServer(applicationContext)
        server?.start(LocalServerConfig.getApiPort(applicationContext), LocalServerConfig.isLocalhostOnly(applicationContext))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { server?.stop(); server = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun notification(): Notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "ultron_server").setContentTitle("ITRA local server").setContentText("Local integration server active").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build() else Notification.Builder(this).setContentTitle("ITRA local server").setContentText("Local integration server active").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build()
}
