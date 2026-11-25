package com.nikalbazu.ids.core

import android.content.Context
import android.content.pm.PackageManager
import com.nikalbazu.ids.BuildConfig
import com.nikalbazu.ids.priv.Helper
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Actualizador automático: busca la última versión en GitHub Releases, la descarga y la instala
 * con el ayudante (sin preguntar) cuando el panel está cerrado.
 */
object Updater {
    /** Huella de la llave oficial del proyecto (signing/ids-release.p12). */
    const val OFFICIAL_SHA256 = "089B787295E36BBE38D93256F1D93680965A52D983483F3712A67FD720AFB9CC"

    val status = MutableStateFlow("Sin revisar todavía")
    val pending = MutableStateFlow<String?>(null)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "ids-update").apply { isDaemon = true } }
    private var lastCheck = 0L

    fun version(): String = BuildConfig.VERSION_NAME

    fun signature(ctx: Context): String = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val cert = info.signingInfo.apkContentsSigners.first().toByteArray()
        MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02X".format(it) }
    }.getOrDefault("")

    fun official(ctx: Context) = signature(ctx) == OFFICIAL_SHA256

    private fun newer(tag: String, cur: String): Boolean {
        val a = tag.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = cur.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** force = el usuario tocó "Buscar ahora". */
    fun check(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastCheck < 6 * 3600_000L || !Store.settings.value.autoUpdate)) return
        lastCheck = now
        io.execute {
            runCatching {
                status.value = "Buscando versión nueva…"
                val c = URL("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest").openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val j = JSONObject(c.inputStream.bufferedReader().readText())
                val tag = j.optString("tag_name")
                val assets = j.optJSONArray("assets")
                val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
                if (!newer(tag, version()) || apk == null) { status.value = "Tienes la última versión (v${version()})"; return@runCatching }
                status.value = "Descargando $tag…"
                val out = File(Helper.folder, "update.apk")
                val tmp = File(Helper.folder, "update.part")
                (URL(apk.getString("browser_download_url")).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 60_000
                }.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                tmp.renameTo(out)
                pending.value = tag
                status.value = "$tag lista: se instala sola al cerrar el panel"
                Store.toast("Actualización $tag descargada")
            }.onFailure { status.value = "No se pudo revisar: ${it.message ?: "sin conexión"}" }
        }
    }

    /** Se llama cuando el panel está cerrado: instala si hay algo descargado. */
    fun installIfReady() {
        val tag = pending.value ?: return
        if (!Helper.alive()) { status.value = "$tag descargada · falta la conexión con el sistema para instalarla"; return }
        pending.value = null
        status.value = "Instalando $tag…"
        Helper.send("selfupdate")
    }

    /** Resultado de la última instalación (si falló por la firma, hay que reinstalar a mano una vez). */
    fun lastInstallLog(): String = runCatching { File(Helper.folder, "update.log").readText().trim() }.getOrDefault("")
}
