package com.knight.app

import android.content.Context
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

/** Downloads the small offline speech model once (about 40 MB) and keeps it in the app's private storage. */
object ModelStore {
    private const val URL_ZIP = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    fun dir(c: Context) = File(c.filesDir, "vosk-model")

    @Synchronized fun ensure(c: Context): File {
        val target = dir(c)
        if (File(target, "am/final.mdl").exists()) return target
        target.deleteRecursively(); target.mkdirs()
        val tmp = File(c.cacheDir, "model.zip")
        URL(URL_ZIP).openStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        ZipInputStream(tmp.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val rel = e.name.substringAfter('/')                       // drop the top folder name
                if (rel.isNotEmpty()) {
                    val f = File(target, rel)
                    if (!f.canonicalPath.startsWith(target.canonicalPath)) throw SecurityException("bad zip entry")
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                }
                e = z.nextEntry
            }
        }
        tmp.delete(); return target
    }
}
