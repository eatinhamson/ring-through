package net.hansen.ringthrough

import android.content.Context
import android.telephony.PhoneNumberUtils

data class Contact(val name: String, val number: String)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ring_through", Context.MODE_PRIVATE)

    var contacts: List<Contact>
        get() = sp.getStringSet("contacts", emptySet())!!
            .map { it.split('\t', limit = 2) }
            .filter { it.size == 2 }
            .map { Contact(it[0], it[1]) }
            .sortedBy { it.name }
        set(value) {
            sp.edit().putStringSet("contacts", value.map { "${it.name}\t${it.number}" }.toSet()).apply()
        }

    /** Seconds your carrier rings before voicemail. */
    var ringLengthSec: Int
        get() = sp.getInt("ring_length_sec", 25)
        set(value) = sp.edit().putInt("ring_length_sec", value).apply()

    /** Share of the ring length that stays silent before the phone turns the sound on. */
    var silentPercent: Int
        get() = sp.getInt("silent_percent", 50)
        set(value) = sp.edit().putInt("silent_percent", value).apply()

    val delayMs: Long get() = ringLengthSec * 1000L * silentPercent / 100

    var lastEvent: String
        get() = sp.getString("last_event", "none yet")!!
        set(value) = sp.edit().putString("last_event", value).apply()

    /** Ringer mode, Do Not Disturb filter and ring volume (-1 = unchanged) from before the sound went on. */
    var saved: IntArray?
        get() = sp.getString("saved", null)?.split(',')?.map { it.toInt() }?.toIntArray()
        set(value) = sp.edit().putString("saved", value?.joinToString(",")).apply()

    fun match(number: String): Contact? =
        contacts.firstOrNull { PhoneNumberUtils.compare(it.number, number) }
}
