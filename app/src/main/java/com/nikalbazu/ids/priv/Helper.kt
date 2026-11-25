package com.nikalbazu.ids.priv

import android.content.Context
import android.os.Process
import com.nikalbazu.ids.core.ErrorReporter
import com.nikalbazu.ids.core.Store
import java.io.File
import java.util.concurrent.Executors

/**
 * Comunicación con el ayudante con privilegios (assets/ids_helper.sh).
 * La app deja órdenes en archivos "cmd_*" y el ayudante deja los datos en "tele.txt".
 */
object Helper {
    private lateinit var dir: File
    val folder: File get() = dir
    private lateinit var ctx: Context
    private var lastLaunch = 0L
    var method: String = "ninguno"; private set
    private const val VERSION = "11"
    /** Todo lo que toca archivos va en segundo plano para que el panel nunca se trabe. */
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "ids-helper").apply { isDaemon = true } }

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
        if (alive() && readTelemetry()["v"] == VERSION) return
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
        val text = lines.joinToString("\n", postfix = "\n")
        io.execute {
            ensureRunning()
            runCatching {
                val name = "${System.nanoTime()}"
                val tmp = File(dir, "tmp_$name")
                tmp.writeText(text)
                tmp.renameTo(File(dir, "cmd_$name"))
            }.onFailure { ErrorReporter.report("Helper.send", it) }
        }
    }

    /** Ajustes del sistema guardados por la orden "snap": "espacio:clave" → valor. */
    fun readSnap(n: Int): Map<String, String> = runCatching {
        File(dir, "snap_$n.txt").readLines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()
    }.getOrDefault(emptyMap())

    fun snapAge(n: Int): Long = File(dir, "snap_$n.txt").let { if (it.exists()) System.currentTimeMillis() - it.lastModified() else Long.MAX_VALUE }

    /** Ventanas visibles por pantalla ("pantalla paquete") y procesos de AYN ("proc nombre"). */
    fun readProbe(n: Int): List<Pair<String, String>> = runCatching {
        File(dir, "probe_$n.txt").readLines().mapNotNull { l ->
            val p = l.trim().split(" ")
            if (p.size >= 2) p[0] to p[1] else null
        }
    }.getOrDefault(emptyList())

    fun probeAge(n: Int): Long = File(dir, "probe_$n.txt").let { if (it.exists()) System.currentTimeMillis() - it.lastModified() else Long.MAX_VALUE }

    /** Resultado de "Quitar para siempre": paquete → ok / fallo / noexiste. */
    fun readBlock(): List<Pair<String, String>> = runCatching {
        File(dir, "block.txt").readLines().mapNotNull { l -> l.trim().split(" ").takeIf { it.size == 2 }?.let { it[0] to it[1] } }
    }.getOrDefault(emptyList())

    fun auditDone(): Boolean = File(dir, "audit.ok").let { it.exists() && System.currentTimeMillis() - it.lastModified() < 30 * 60_000 }

    /** Las líneas OK / FALLA de la revisión. */
    fun auditSummary(): String = runCatching {
        File(dir, "revision.txt").readLines().filter { it.startsWith("OK") || it.startsWith("FALLA") }.joinToString("\n")
    }.getOrDefault("").ifBlank { "Listo. Abre Descargas › IDS › revision.txt" }

    fun readRestore(): String? = runCatching { File(dir, "restore.txt").readText().trim() }.getOrNull()

    fun readDiag(): String = runCatching { File(dir, "diag.txt").readText() }.getOrDefault("")

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
