package com.knight.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.Locale

/** Keeps "Hey Knight" listening in the background (offline) and runs what you ask, as long as you allowed it. */
class KnightListenService : Service(), RecognitionListener {
    companion object {
        const val ACTION_STOP = "com.knight.app.STOP"
        const val CHANNEL = "knight_listen"
        fun start(c: Context) { val i = Intent(c, KnightListenService::class.java); if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i) }
        fun stop(c: Context) { c.stopService(Intent(c, KnightListenService::class.java)) }
    }
    private var model: Model? = null
    private var speech: SpeechService? = null
    private var tts: TextToSpeech? = null
    @Volatile private var speaking = false
    @Volatile private var awakeUntil = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { s -> if (s == TextToSpeech.SUCCESS) { tts?.language = Locale.getDefault(); tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { speaking = true }
            override fun onDone(id: String?) { speaking = false }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { speaking = false }
        }) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val n = notification("Getting ready…")
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
        Thread {
            try {
                val dir = ModelStore.ensure(this)
                model = Model(dir.absolutePath)
                val rec = Recognizer(model, 16000.0f)
                speech = SpeechService(rec, 16000.0f).also { it.startListening(this) }
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification("Listening for “Hey Knight”"))
            } catch (e: Exception) {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification("Could not start listening: ${e.message}"))
            }
        }.start()
        return START_STICKY
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hey Knight", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, KnightListenService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Knight").setContentText(text)
            .setContentIntent(open).addAction(0, "Stop listening", stop).setOngoing(true).build()
    }

    private fun say(t: String) { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "k") }

    override fun onResult(hypothesis: String?) {
        if (speaking || hypothesis == null) return
        val text = try { JSONObject(hypothesis).optString("text").trim() } catch (e: Exception) { "" }
        if (text.isEmpty()) return
        val rest = Commands.wakeRest(text)
        when {
            rest != null && rest.isNotEmpty() -> handle(rest)
            rest != null -> { awakeUntil = System.currentTimeMillis() + 8000; say("Yes?") }
            System.currentTimeMillis() < awakeUntil -> { awakeUntil = 0; handle(text) }
        }
    }
    private fun handle(text: String) {
        val cmd = Commands.parse(text)
        if (cmd == null) {                                  // not a phone job: hand it to Knight AI in the app
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("handoff", text)
            try { startActivity(i) } catch (e: Exception) { say("Open Knight to ask that.") }
            return
        }
        say(CommandRunner.run(this, cmd.op, cmd.arg))
    }
    override fun onPartialResult(hypothesis: String?) {}
    override fun onFinalResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) { try { speech?.startListening(this) } catch (e: Exception) {} }
    override fun onTimeout() { try { speech?.startListening(this) } catch (e: Exception) {} }

    override fun onDestroy() {
        try { speech?.stop(); speech?.shutdown() } catch (e: Exception) {}
        try { tts?.shutdown() } catch (e: Exception) {}
        super.onDestroy()
    }
}
