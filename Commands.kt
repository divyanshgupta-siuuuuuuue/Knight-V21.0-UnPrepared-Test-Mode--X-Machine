package com.knight.app

/** Turns a spoken sentence into one fixed job. Anything it does not recognise is handed to Knight AI in the app. */
data class Cmd(val op: String, val arg: String = "")

object Commands {
    private val wake = Regex("""\b(?:hey|hi|ok|okay|hello|yo)[, ]+(?:knight|night|nite|nate)\b[,. ]*(.*)$""", RegexOption.IGNORE_CASE)
    fun wakeRest(text: String): String? = wake.find(text)?.groupValues?.get(1)?.trim()

    fun parse(raw: String): Cmd? {
        val t = " " + raw.lowercase().replace(Regex("[.,!?]"), " ").replace(Regex("\\s+"), " ").trim() + " "
        Regex("""\bset (?:an? )?alarm (?:for|at) (\d{1,2})(?::(\d{2}))? ?(am|pm)?""").find(t)?.let { m ->
            var h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (m.groupValues[3] == "pm" && h < 12) h += 12; if (m.groupValues[3] == "am" && h == 12) h = 0
            return Cmd("alarm", "$h:$min")
        }
        Regex("""\bset (?:an? )?timer (?:for )?(\d+) (second|minute|hour)""").find(t)?.let { m ->
            val n = m.groupValues[1].toInt(); val s = when (m.groupValues[2]) { "hour" -> n * 3600; "minute" -> n * 60; else -> n }
            return Cmd("timer", s.toString())
        }
        Regex("""\b(?:turn|switch) (on|off) (?:the )?(?:flashlight|torch|flash light)""").find(t)?.let { return Cmd("torch", it.groupValues[1]) }
        if (Regex("""\b(flashlight|torch)\b""").containsMatchIn(t)) return Cmd("torch", if (t.contains(" off ")) "off" else "on")
        Regex("""\b(?:send (?:a )?(?:text|message) to|text|message) ([a-z0-9 ]+?) (?:saying|that says|say) (.+)""").find(t)?.let { return Cmd("text", it.groupValues[1].trim() + "|" + it.groupValues[2].trim()) }
        Regex("""\b(?:call|phone|ring) ([a-z0-9 ]{2,30})""").find(t)?.let { return Cmd("call", it.groupValues[1].trim()) }
        if (Regex("""\bvolume up|louder\b""").containsMatchIn(t)) return Cmd("volume", "up")
        if (Regex("""\bvolume down|quieter\b""").containsMatchIn(t)) return Cmd("volume", "down")
        if (Regex("""\b(mute|unmute)\b""").containsMatchIn(t)) return Cmd("volume", "mute")
        if (Regex("""\b(pause|play|resume) (?:the )?(?:music|song|video|media)\b|\bpause\b""").containsMatchIn(t)) return Cmd("media", "toggle")
        if (Regex("""\bnext (?:song|track)\b""").containsMatchIn(t)) return Cmd("media", "next")
        if (Regex("""\bgo home\b|\bhome screen\b""").containsMatchIn(t)) return Cmd("home")
        if (Regex("""\bgo back\b|\bback\b""").containsMatchIn(t)) return Cmd("back")
        if (Regex("""\brecent apps\b|\bapp switcher\b""").containsMatchIn(t)) return Cmd("recents")
        if (Regex("""\b(open|show|pull down) (?:the )?notifications?\b""").containsMatchIn(t)) return Cmd("notifications")
        if (Regex("""\bquick settings\b""").containsMatchIn(t)) return Cmd("quick_settings")
        if (Regex("""\bscreenshot\b|\bscreen shot\b""").containsMatchIn(t)) return Cmd("screenshot")
        if (Regex("""\block (?:the |my )?(?:phone|screen|device)\b""").containsMatchIn(t)) return Cmd("lock")
        if (Regex("""\bscroll down\b""").containsMatchIn(t)) return Cmd("scroll", "down")
        if (Regex("""\bscroll up\b""").containsMatchIn(t)) return Cmd("scroll", "up")
        Regex("""\b(?:tap|click|press) (?:on )?(?:the )?(.+)""").find(t)?.let { return Cmd("click", it.groupValues[1].trim()) }
        if (Regex("""\bread (?:the |my )?screen\b""").containsMatchIn(t)) return Cmd("read_screen")
        Regex("""\bopen (?:the )?(wifi|wi-fi|bluetooth|display|sound|settings)\b""").find(t)?.let { return Cmd("settings", it.groupValues[1]) }
        if (Regex("""\bwhat(?:'s| is) the time\b|\bwhat time is it\b""").containsMatchIn(t)) return Cmd("time")
        Regex("""\bopen ([a-z0-9 ]{2,30})""").find(t)?.let { return Cmd("open_app", it.groupValues[1].trim()) }
        return null
    }
}
