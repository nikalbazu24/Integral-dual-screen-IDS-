package com.nikalbazu.ids.priv

import android.content.Context
import android.os.Process
import com.nikalbazu.ids.core.ErrorReporter
import com.nikalbazu.ids.core.Store
import java.io.File

/**
 * Comunicación con el ayudante con privilegios (assets/ids_helper.sh).
 * La app deja órdenes en archivos "cmd_*" y el ayudante deja los datos en "tele.txt".
 */
object Helper {
    private lateinit var dir: File
    private lateinit var ctx: Context
    private var lastLaunch = 0L
    var method: String = "ninguno"; private set

    fun init(context: Context) {
        ctx = context.applicationContext
        dir = File(ctx.getExternalFilesDir(null), "h").apply { mkdirs() }
    }

    private val rootPath get() = "/data/media/0/Android/data/${ctx.packageName}/files/h/ids_helper.sh"

    /** Telemetría reciente = ayudante vivo. */
    fun alive(): Boolean {
        val f = File(dir, "tele.txt")
        return f.exists() && System.currentTimeMillis() - f.lastModified() < 6000
    }

    fun ensureRunning() {
        if (alive()) return
        val now = System.currentTimeMillis()
        if (now - lastLaunch < 8000) return
        lastLaunch = now
        try {
            val script = File(dir, "ids_helper.sh")
            val content = ctx.assets.open("ids_helper.sh").use { it.readBytes() }
            if (!script.exists() || !script.readBytes().contentEquals(content)) script.writeBytes(content)
            val cmd = "setsid sh $rootPath ${Process.myUid()} </dev/null >/dev/null 2>&1 &"
            method = when {
                PServer.launch(cmd) -> "servicio de la Thor"
                PServer.launchWithSu(cmd) -> "root"
                else -> "ninguno"
            }
        } catch (e: Throwable) {
            ErrorReporter.report("Helper.ensureRunning", e)
        }
    }

    fun send(vararg lines: String) {
        if (lines.isEmpty()) return
        ensureRunning()
        runCatching {
            val name = "${System.nanoTime()}"
            val tmp = File(dir, "tmp_$name")
            tmp.writeText(lines.joinToString("\n", postfix = "\n"))
            tmp.renameTo(File(dir, "cmd_$name"))
        }.onFailure { ErrorReporter.report("Helper.send", it) }
    }

    fun stop() = send("thaw", "stop")

    fun readTelemetry(): Map<String, String> = runCatching {
        File(dir, "tele.txt").readLines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1).trim()
        }.toMap()
    }.getOrDefault(emptyMap())

    /** Última tecla física vista por el ayudante: (código, marca única). */
    fun lastKey(): Pair<String, String>? = runCatching {
        val parts = File(dir, "key.evt").readText().trim().split(" ")
        if (parts.size >= 2) parts[0] to parts[1] else null
    }.getOrNull()

    fun statusText(): String = when {
        alive() -> "Conectado (${method.takeIf { it != "ninguno" } ?: "activo"})"
        PServer.available -> "Servicio de la Thor disponible, iniciando…"
        else -> "Sin acceso al servicio de la Thor"
    }.also { Store.tele.value = Store.tele.value.copy(helperAlive = alive()) }
}
