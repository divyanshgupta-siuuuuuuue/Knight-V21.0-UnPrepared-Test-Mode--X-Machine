package com.knight.app

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import android.accessibilityservice.AccessibilityService
import java.text.DateFormat
import java.util.Date

/** The only things Knight can do to your phone. Each needs "Let Knight control my phone" to be on, and some need an Android permission. */
object CommandRunner {
    private fun launch(c: Context, i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i) }
    private fun has(c: Context, p: String) = c.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    private val a11yNeeded = "That needs Accessibility access. Open Knight and press Accessibility settings."

    fun run(c: Context, op: String, arg: String): String {
        if (op == "time") return "It is " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) + "."
        if (!Prefs.get(c, "control")) return "Phone control is off. Turn on Let Knight control my phone in Knight setup."
        return try {
            when (op) {
                "open_app" -> openApp(c, arg)
                "open_url" -> if (Regex("^https?://\\S+$").matches(arg)) { launch(c, Intent(Intent.ACTION_VIEW, Uri.parse(arg))); "Opened it." } else "That is not a web address I can open."
                "call" -> call(c, arg)
                "text" -> text(c, arg)
                "alarm" -> { val p = arg.split(":"); launch(c, Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, p[0].toInt()).putExtra(AlarmClock.EXTRA_MINUTES, p[1].toInt()).putExtra(AlarmClock.EXTRA_SKIP_UI, true)); "Alarm set for ${p[0]}:${p[1].padStart(2, '0')}." }
                "timer" -> { launch(c, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, arg.toInt()).putExtra(AlarmClock.EXTRA_SKIP_UI, true)); "Timer started." }
                "torch" -> torch(c, arg == "on")
                "volume" -> volume(c, arg)
                "media" -> media(c, arg)
                "settings" -> settings(c, arg)
                "home" -> a11y(c) { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
                "back" -> a11y(c) { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
                "recents" -> a11y(c) { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) }
                "notifications" -> a11y(c) { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }
                "quick_settings" -> a11y(c) { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) }
                "lock" -> if (android.os.Build.VERSION.SDK_INT >= 28) a11y(c) { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) } else "Locking needs Android 9 or newer."
                "screenshot" -> if (!Prefs.get(c, "screenshots")) "Screenshots are off. Turn them on in Knight setup first."
                    else if (android.os.Build.VERSION.SDK_INT >= 28) a11y(c) { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) } else "Screenshots need Android 9 or newer."
                "scroll" -> a11y(c) { it.scroll(arg == "down") }
                "click" -> a11y(c) { it.clickText(arg) }
                "read_screen" -> KnightAccessibilityService.instance?.screenText()?.ifBlank { "I could not read anything on this screen." } ?: a11yNeeded
                else -> "I cannot do that."
            }
        } catch (e: Exception) { "That did not work: ${e.message}" }
    }

    private fun a11y(c: Context, f: (KnightAccessibilityService) -> Boolean): String {
        val s = KnightAccessibilityService.instance ?: return a11yNeeded
        return if (f(s)) "Done." else "I could not do that on this screen."
    }

    private fun openApp(c: Context, name: String): String {
        val pm = c.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val want = name.lowercase().trim()
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == want } ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(want) }
            ?: return "I could not find an app called $name."
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "I could not open $name."
        launch(c, i); return "Opening ${hit.loadLabel(pm)}."
    }

    private fun number(c: Context, who: String): String? {
        if (Regex("^[+0-9 ()-]{5,}$").matches(who)) return who.replace(" ", "")
        if (!has(c, android.Manifest.permission.READ_CONTACTS)) return null
        c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { cur ->
            while (cur.moveToNext()) if (cur.getString(1).orEmpty().lowercase().contains(who.lowercase())) return cur.getString(0)
        }
        return null
    }
    private fun call(c: Context, who: String): String {
        val n = number(c, who) ?: return if (has(c, android.Manifest.permission.READ_CONTACTS)) "I could not find $who in your contacts." else "Allow contacts in Knight setup so I can find $who."
        val action = if (has(c, android.Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        launch(c, Intent(action, Uri.parse("tel:" + Uri.encode(n)))); return "Calling $who."
    }
    private fun text(c: Context, arg: String): String {
        val (who, body) = arg.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val n = number(c, who) ?: return "I could not find $who."
        launch(c, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(n))).putExtra("sms_body", body)); return "Your message to $who is ready. Tap send."   // you always press send
    }
    private fun torch(c: Context, on: Boolean): String {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "This phone has no flashlight."
        cm.setTorchMode(id, on); return if (on) "Flashlight on." else "Flashlight off."
    }
    private fun volume(c: Context, dir: String): String {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, when (dir) { "up" -> AudioManager.ADJUST_RAISE; "down" -> AudioManager.ADJUST_LOWER; else -> AudioManager.ADJUST_TOGGLE_MUTE }, AudioManager.FLAG_SHOW_UI)
        return "Volume $dir."
    }
    private fun media(c: Context, what: String): String {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val code = if (what == "next") KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)); return "Done."
    }
    private fun settings(c: Context, what: String): String {
        val a = when (what) { "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS; "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS; "display" -> Settings.ACTION_DISPLAY_SETTINGS; "sound" -> Settings.ACTION_SOUND_SETTINGS; else -> Settings.ACTION_SETTINGS }
        launch(c, Intent(a)); return "Opened $what settings."
    }
}
