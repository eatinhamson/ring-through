package net.hansen.ringthrough

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager

/**
 * Waits [delay] while the call rings silently, then takes the phone off silent and makes
 * the call audible. Keeps the broadcast alive (goAsync, 60 s limit for background
 * broadcasts) until the call is answered, rejected, or goes to voicemail. When the call
 * ends, [restore] puts back the settings from before the sound went on.
 */
object Escalator {
    private const val MAX_HOLD_MS = 55_000L
    private val handler = Handler(Looper.getMainLooper())
    private var pending: BroadcastReceiver.PendingResult? = null
    private var ringtone: Ringtone? = null

    fun isAudible(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL &&
            audio.getStreamVolume(AudioManager.STREAM_RING) > 0
    }

    fun start(context: Context, contact: Contact, delay: Long, result: BroadcastReceiver.PendingResult) {
        stop()
        pending = result
        handler.postDelayed({ escalate(context, contact) }, delay)
        handler.postDelayed({ stop() }, MAX_HOLD_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        ringtone = null
        pending?.finish()
        pending = null
    }

    private fun escalate(context: Context, contact: Contact) {
        val prefs = Prefs(context)
        val tm = context.getSystemService(TelephonyManager::class.java)
        @Suppress("DEPRECATION")
        if (tm.callState != TelephonyManager.CALL_STATE_RINGING) {
            prefs.lastEvent = "${now()} ${contact.name}: call ended before the delay"
            stop()
            return
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val notes = mutableListOf<String>()
        val saved = intArrayOf(audio.ringerMode, nm.currentInterruptionFilter, -1)
        try {
            if (nm.isNotificationPolicyAccessGranted &&
                nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                notes += "DND off"
            }
            audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            notes += "ringer normal"
        } catch (e: SecurityException) {
            notes += "no Do Not Disturb access: ${e.message}"
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
        if (audio.getStreamVolume(AudioManager.STREAM_RING) == 0) {
            saved[2] = 0
            audio.setStreamVolume(AudioManager.STREAM_RING, max * 7 / 10, 0)
            notes += "ring volume ${max * 7 / 10}/$max"
        }
        prefs.saved = saved
        prefs.lastEvent = "${now()} ${contact.name}: sound on (${notes.joinToString(", ")})"
        // The system decides whether to play the ringtone when the call starts, and it can
        // start, stop or never play after the mode change. While the call rings, play the
        // ringtone ourselves only when no system ringtone plays.
        handler.postDelayed(object : Runnable {
            override fun run() {
                @Suppress("DEPRECATION")
                if (tm.callState != TelephonyManager.CALL_STATE_RINGING) return stop()
                val ours = ringtone?.isPlaying == true
                val system = playingRingtones(audio) - (if (ours) 1 else 0) > 0
                if (!system && !ours) {
                    ringtone = ringtone ?: newRingtone(context)
                    ringtone?.play()
                    prefs.lastEvent = "${now()} ${contact.name}: sound on (${notes.joinToString(", ")}, app ringtone)"
                } else if (system && ours) {
                    ringtone?.stop()
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    /** Puts back the settings from before the sound went on, unless the user changed them during the call. */
    fun restore(context: Context) {
        val prefs = Prefs(context)
        val (ringer, filter, volume) = prefs.saved ?: return
        prefs.saved = null
        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val notes = mutableListOf<String>()
        try {
            if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                if (volume >= 0) audio.setStreamVolume(AudioManager.STREAM_RING, volume, 0)
                audio.ringerMode = ringer
                notes += "ringer ${if (ringer == AudioManager.RINGER_MODE_SILENT) "silent" else "vibrate"}"
            }
            if (filter > NotificationManager.INTERRUPTION_FILTER_ALL &&
                nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm.setInterruptionFilter(filter)
                notes += "DND on"
            } else if (filter == NotificationManager.INTERRUPTION_FILTER_ALL && ringer != AudioManager.RINGER_MODE_NORMAL) {
                // When an app sets the ringer to silent, the system also turns Do Not Disturb on.
                // currentInterruptionFilter can still read the old value here, so do not check it.
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: SecurityException) {
            notes += "no Do Not Disturb access: ${e.message}"
        }
        prefs.lastEvent = "${prefs.lastEvent}; call ended, back to ${notes.ifEmpty { listOf("settings you chose") }.joinToString(", ")}"
    }

    private fun newRingtone(context: Context): Ringtone? {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        return RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
        }
    }

    // The player state has no public getter; toString() carries it as "state:started".
    // Stopped players stay in the list, so counting without the state over-reports.
    private fun playingRingtones(audio: AudioManager): Int =
        audio.activePlaybackConfigurations.count {
            it.audioAttributes.usage == AudioAttributes.USAGE_NOTIFICATION_RINGTONE &&
                it.toString().let { s -> "state:" !in s || "state:started" in s }
        }
}
