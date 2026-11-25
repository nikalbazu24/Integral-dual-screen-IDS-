package com.nikalbazu.ids.core

import android.content.Context
import android.os.Build
import com.nikalbazu.ids.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Reporte de errores automático.
 * Si la app se cae, guarda el detalle y lo envía apenas vuelve a abrirse
 * a la dirección configurada al compilar (IDS_REPORT_URL).
 * Si no hay dirección, los reportes quedan guardados para compartirlos a mano.
 */
object ErrorReporter {
    private lateinit var dir: File

    fun install(ctx: Context) {
        dir = File(ctx.filesDir, "reports").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { save("crash", e, "hilo ${t.name}") }
            previous?.uncaughtException(t, e)
        }
        sendPending()
    }

    /** Errores que no cierran la app pero conviene conocer. */
    fun report(where: String, e: Throwable) {
        runCatching { save("error", e, where) }
        sendPending()
    }

    fun pending(): List<File> = if (::dir.isInitialized) dir.listFiles()?.sortedBy { it.name } ?: emptyList() else emptyList()

    private fun save(kind: String, e: Throwable, where: String) {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val o = JSONObject()
            .put("app", "Integral Dual Screen")
            .put("tipo", kind)
            .put("version", BuildConfig.VERSION_NAME)
            .put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("android", Build.VERSION.RELEASE)
            .put("donde", where)
            .put("fecha", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            .put("error", e.toString())
            .put("detalle", sw.toString().take(12000))
        File(dir, "r_${System.currentTimeMillis()}.json").writeText(o.toString())
    }

    private fun sendPending() {
        val url = BuildConfig.REPORT_URL
        if (url.isBlank() || !::dir.isInitialized) return
        thread(name = "ids-reports") {
            pending().forEach { f ->
                val ok = runCatching {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    c.outputStream.use { it.write(f.readBytes()) }
                    val code = c.responseCode
                    c.disconnect()
                    code in 200..399
                }.getOrDefault(false)
                if (ok) f.delete()
            }
        }
    }
}
