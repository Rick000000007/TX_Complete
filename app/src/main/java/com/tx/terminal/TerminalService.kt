package com.tx.terminal.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tx.terminal.R
import com.tx.terminal.TXApplication

class TerminalService : Service() {
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, TXApplication.CHANNEL_ID)
            .setContentTitle("TX Terminal")
            .setContentText("Session running in background")
            .setSmallIcon(android.R.drawable.ic_terminal) // Use system icon or add custom
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}

