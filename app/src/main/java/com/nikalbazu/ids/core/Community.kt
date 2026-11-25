package com.nikalbazu.ids.core

import android.content.Context
import android.net.Uri
import android.os.Build
import com.nikalbazu.ids.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Botones de AYN compartidos por la comunidad.
 * Vienen dentro de la app (assets/ayn_buttons.json) y se actualizan solos desde GitHub,
 * así quien descargue IDS no tiene que enseñarle nada.
 */
object Community {
    val buttons = MutableStateFlow<Map<String, Learned>>(emptyMap())
    private const val RAW = "https://raw.githubusercontent.com/${BuildConfig.REPO}/main/app/src/main/assets/ayn_buttons.json"

    fun init(ctx: Context) {
        val cache = File(ctx.filesDir, "ayn_buttons.json")
        val text = if (cache.exists()) cache.readText() else ctx.assets.open("ayn_buttons.json").use { it.readBytes().decodeToString() }
        parse(text)?.let { buttons.value = it }
        thread(name = "ids-community") {
            runCatching {
                val c = URL(RAW).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val body = c.inputStream.use { it.readBytes().decodeToString() }
                parse(body)?.let { buttons.value = it; cache.writeText(body) }
            }
        }
    }

    private fun parse(text: String): Map<String, Learned>? = runCatching {
        val b = JSONObject(text).getJSONObject("buttons")
        fun JSONObject.m(): Map<String, String> = keys().asSequence().associateWith { optString(it) }
        b.keys().asSequence().associateWith { k ->
            val o = b.getJSONObject(k)
            Learned(o.getJSONObject("on").m(), o.getJSONObject("off").m())
        }
    }.getOrNull()

    /** Lo aprendido por esta persona tiene prioridad sobre lo de la comunidad. */
    fun all(): Map<String, Learned> = buttons.value + Store.settings.value.learned

    /** Enlace para compartir lo aprendido: abre un aviso en GitHub ya rellenado. */
    fun shareUrl(): String? {
        val mine = Store.settings.value.learned
        if (mine.isEmpty()) return null
        val b = JSONObject()
        mine.forEach { (name, l) -> b.put(name, JSONObject().put("on", JSONObject(l.on as Map<*, *>)).put("off", JSONObject(l.off as Map<*, *>))) }
        val json = JSONObject()
            .put("firmware", Build.DISPLAY)
            .put("modelo", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("app", BuildConfig.VERSION_NAME)
            .put("buttons", b)
            .toString(2)
        val body = "Botones de AYN aprendidos en mi consola.\n\n```json\n$json\n```\n"
        return "https://github.com/${BuildConfig.REPO}/issues/new?labels=botones-ayn" +
            "&title=" + Uri.encode("Botones de AYN: " + mine.keys.joinToString()) +
            "&body=" + Uri.encode(body)
    }
}
