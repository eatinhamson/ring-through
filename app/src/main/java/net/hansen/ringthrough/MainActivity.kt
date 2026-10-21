package net.hansen.ringthrough

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        RingService.start(this)
        render()
    }

    private fun render() {
        root.removeAllViews()
        text("Ring Through", 24f)
        text("When a chosen contact calls and the phone is on silent, vibrate or Do Not Disturb, " +
            "it rings silently for part of the ring, then turns the sound on. When the call ends, it puts silent back.")

        section("Setup")
        val phoneOk = granted(Manifest.permission.READ_PHONE_STATE) && granted(Manifest.permission.READ_CALL_LOG)
        val dndOk = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        step(phoneOk, "See who is calling") {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG), 1)
        }
        step(dndOk, "Turn off silent and Do Not Disturb") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        step(granted(Manifest.permission.POST_NOTIFICATIONS), "Show the \"Ring Through is on\" notification") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
        }
        step(batteryOk, "Run without battery limits") {
            @Suppress("BatteryLife")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        section("Contacts")
        if (prefs.contacts.isEmpty()) text("No contacts yet.")
        prefs.contacts.forEach { c ->
            row("${c.name}  ${c.number}", "Remove") {
                prefs.contacts = prefs.contacts - c
                render()
            }
        }
        button("Add contact") {
            startActivityForResult(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI), 2)
        }

        section("Timing")
        val length = number("Ring length before voicemail (seconds)", prefs.ringLengthSec)
        val percent = number("Silent part of the ring (%)", prefs.silentPercent)
        button("Save") {
            prefs.ringLengthSec = length.text.toString().toIntOrNull()?.coerceIn(5, 50) ?: prefs.ringLengthSec
            prefs.silentPercent = percent.text.toString().toIntOrNull()?.coerceIn(0, 95) ?: prefs.silentPercent
            render()
        }
        text("The sound turns on after ${prefs.delayMs / 1000.0} s of ringing.")

        section("Last event")
        text(prefs.lastEvent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != 2 || resultCode != RESULT_OK) return
        contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use {
            if (it.moveToFirst()) {
                prefs.contacts = prefs.contacts + Contact(it.getString(0), it.getString(1))
            }
        }
        render()
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun text(s: String, size: Float = 16f) = root.addView(TextView(this).apply {
        text = s; textSize = size; setPadding(0, 8, 0, 8)
    })

    private fun section(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 20f; setPadding(0, 40, 0, 8)
    })

    private fun button(label: String, onClick: () -> Unit) = root.addView(Button(this).apply {
        text = label; setOnClickListener { onClick() }
    })

    private fun step(done: Boolean, label: String, onClick: () -> Unit) =
        if (done) text("✓ $label") else button(label, onClick)

    private fun row(label: String, action: String, onClick: () -> Unit) {
        root.addView(LinearLayout(this).apply {
            addView(TextView(context).apply {
                text = label; textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(context).apply { text = action; setOnClickListener { onClick() } })
        })
    }

    private fun number(label: String, value: Int): EditText {
        text(label)
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString())
            root.addView(this)
        }
    }
}
