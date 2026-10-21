package net.hansen.ringthrough

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        @Suppress("DEPRECATION")
        android.util.Log.i("RingThrough", "PHONE_STATE $state ${intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)}")
        if (state != TelephonyManager.EXTRA_STATE_RINGING) {
            Escalator.stop()
            if (state == TelephonyManager.EXTRA_STATE_IDLE) Escalator.restore(context)
            return
        }
        // Android sends RINGING twice: once without the number, once with it (READ_CALL_LOG).
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
        val prefs = Prefs(context)
        val contact = prefs.match(number) ?: return
        if (Escalator.isAudible(context)) {
            prefs.lastEvent = "${now()} ${contact.name} called; phone already audible"
            return
        }
        prefs.lastEvent = "${now()} ${contact.name} called on silent; sound on in ${prefs.delayMs / 1000.0}s"
        Escalator.start(context.applicationContext, contact, prefs.delayMs, goAsync())
    }
}

fun now(): String = java.text.SimpleDateFormat("MMM d HH:mm:ss", java.util.Locale.US).format(java.util.Date())
