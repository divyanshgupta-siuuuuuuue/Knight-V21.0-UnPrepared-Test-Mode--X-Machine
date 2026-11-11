package com.knight.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/** The Knight website inside the app, plus a small, checked bridge to the phone. Only your own Knight site may use it. */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private val base = BuildConfig.KNIGHT_URL.trimEnd('/')

    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url.toString()
                if (u.startsWith(base)) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (e: Exception) {}   // other sites open in the browser, never inside the bridge
                return true
            }
            override fun onPageFinished(v: WebView, url: String) { intent?.getStringExtra("handoff")?.let { handoff(it); intent.removeExtra("handoff") } }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.origin.toString().startsWith(base)) request.grant(request.resources) else request.deny()
            }
        }
        web.addJavascriptInterface(Bridge(), "KnightAndroid")
        web.loadUrl("$base/#/app")
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); intent.getStringExtra("handoff")?.let { handoff(it) } }
    private fun handoff(text: String) { web.post { web.evaluateJavascript("if(window.handleSpoken)handleSpoken(" + JSONObject.quote(text) + ");", null) } }
    @Deprecated("Deprecated in Java") override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }

    inner class Bridge {
        private fun state() = JSONObject().put("prefs", Prefs.json(this@MainActivity)).put("platform", "android").put("version", BuildConfig.VERSION_NAME)
            .put("accessibility", KnightAccessibilityService.instance != null)
            .put("mic", if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == 0) "granted" else "prompt")
            .put("overlay", Settings.canDrawOverlays(this@MainActivity))
        @JavascriptInterface fun getState(): String = state().toString()
        @JavascriptInterface fun setPref(k: String, v: String): String {
            if (k in listOf("background", "control", "screenshots")) {
                Prefs.set(this@MainActivity, k, v == "true")
                if (k == "background") runOnUiThread { if (v == "true") { requestAll(); KnightListenService.start(this@MainActivity) } else KnightListenService.stop(this@MainActivity) }
            }
            return Prefs.json(this@MainActivity).let { JSONObject().put("background", it.getBoolean("background")).put("autostart", false).put("control", it.getBoolean("control")).put("screenshots", it.getBoolean("screenshots")) }.toString()
        }
        @JavascriptInterface fun askMic(): String { runOnUiThread { requestAll() }; return "prompt" }
        @JavascriptInterface fun run(op: String, arg: String): String = CommandRunner.run(this@MainActivity, op, arg)
        @JavascriptInterface fun openAccessibilitySettings() { runOnUiThread { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        @JavascriptInterface fun openOverlaySettings() { runOnUiThread { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } }
        @JavascriptInterface fun requestAll() { runOnUiThread { askPerms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)) } }
    }
}
