package net.hansen.ringthrough

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.telephony.TelephonyManager

/**
 * Keeps the app process running so that Android does not cache or freeze it: a frozen
 * process gets the call broadcast late and its delay timer does not run.
 */
class RingService : Service() {
    private val receiver = CallReceiver()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("watch", "Watching for calls", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, "watch")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Ring Through is on")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        registerReceiver(receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), RECEIVER_EXPORTED)
        // If the process died during a call, it missed the call-ended broadcast: restore now.
        @Suppress("DEPRECATION")
        if (getSystemService(TelephonyManager::class.java).callState == TelephonyManager.CALL_STATE_IDLE) {
            Escalator.restore(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(receiver)
        Escalator.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        fun start(context: Context) =
            context.startForegroundService(Intent(context, RingService::class.java))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) RingService.start(context)
    }
}
