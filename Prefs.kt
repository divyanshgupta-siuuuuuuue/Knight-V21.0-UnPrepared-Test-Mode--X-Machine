package com.knight.app

import android.content.Context
import org.json.JSONObject

/** What the person has switched on. Everything starts off. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("knight", Context.MODE_PRIVATE)
    fun get(c: Context, k: String): Boolean = sp(c).getBoolean(k, false)
    fun set(c: Context, k: String, v: Boolean) { sp(c).edit().putBoolean(k, v).apply() }
    fun json(c: Context): JSONObject = JSONObject()
        .put("background", get(c, "background"))
        .put("autostart", false)
        .put("control", get(c, "control"))
        .put("screenshots", get(c, "screenshots"))
}
