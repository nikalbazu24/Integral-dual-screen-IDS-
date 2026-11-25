package com.nikalbazu.ids.priv

import android.app.ActivityOptions
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings as SysSettings
import com.nikalbazu.ids.core.ErrorReporter
import com.nikalbazu.ids.core.Freq
import com.nikalbazu.ids.core.GameProfile
import com.nikalbazu.ids.core.Store

/** Acciones sobre la consola. Las que requieren privilegios pasan por el ayudante. */
object Device {
    private lateinit var ctx: Context

    fun init(context: Context) { ctx = context.applicationContext }

    // Valores que usa el sistema de AYN en Settings.System
    private val FAN = mapOf("Silencio" to 1, "Auto" to 4, "Máx" to 5, "Personalizado" to 6)
    private val PERF = mapOf("Ahorro" to 0, "Equilibrado" to 1, "Rendimiento" to 2, "Máximo" to 2)

    fun setFan(mode: String) {
        Store.update { it.copy(fan = mode) }
        FAN[mode]?.let { Helper.send("fan $it") }
    }

    fun setPerf(mode: String) {
        Store.update { it.copy(perf = mode) }
        PERF[mode]?.let { Helper.send("perf $it") }
    }

    fun setHz(mode: String) {
        Store.update { it.copy(hz = mode) }
        Helper.send("hz " + if (mode.startsWith("60")) 60 else 120)
    }

    /** Lee los modos actuales que guarda el sistema de AYN. */
    fun readVendorModes() = runCatching {
        val r = ctx.contentResolver
        val fan = SysSettings.System.getString(r, "fan_mode")?.toIntOrNull()
        val perf = SysSettings.System.getString(r, "performance_mode")?.toIntOrNull()
        Store.update { s ->
            s.copy(
                fan = FAN.entries.firstOrNull { it.value == fan }?.key ?: s.fan,
                perf = when (perf) { 0 -> "Ahorro"; 1 -> "Equilibrado"; 2 -> "Rendimiento"; else -> s.perf },
            )
        }
    }

    // ---- Brillo y volumen (0-100) ----
    fun topBrightness(): Int = runCatching {
        SysSettings.System.getInt(ctx.contentResolver, SysSettings.System.SCREEN_BRIGHTNESS) * 100 / 255
    }.getOrDefault(50)

    fun setTopBrightness(pct: Int) = Helper.send("bright ${(pct.coerceIn(1, 100) * 255) / 100}")

    fun setBottomBrightness(pct: Int) {
        val bl = Store.tele.value.backlights
        val name = bl.getOrNull(1) ?: bl.firstOrNull() ?: return
        Helper.send("blight $name ${(pct.coerceIn(1, 100) * 255) / 100}")
        bottomPct = pct
    }
    var bottomPct = 50; private set

    fun volume(): Int {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    fun setVolume(pct: Int) = runCatching {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (pct.coerceIn(0, 100) * max) / 100, 0)
    }

    // ---- Modos de pantalla ----
    fun setScreens(mode: String) {
        Store.update { it.copy(mode = mode) }
        val bl = Store.tele.value.backlights
        val top = bl.getOrNull(0); val bottom = bl.getOrNull(1)
        when (mode) {
            "Dos" -> { top?.let { Helper.send("blight $it 180") }; bottom?.let { Helper.send("blight $it 160") } }
            "Arriba" -> bottom?.let { Helper.send("blight $it 0") }
            "Abajo" -> top?.let { Helper.send("blight $it 0") }
        }
    }

    // ---- Botones de AYN aprendidos ----
    val STOCK_BUTTONS = listOf("Bloqueo automático", "Estilo de control", "Alimentación directa", "Vibración", "Luz ambiental",
        "Botón extra 1", "Botón extra 2", "Botón extra 3")

    private fun readSetting(full: String): String? = runCatching {
        val ns = full.substringBefore(':'); val key = full.substringAfter(':')
        val r = ctx.contentResolver
        when (ns) {
            "system" -> SysSettings.System.getString(r, key)
            "global" -> SysSettings.Global.getString(r, key)
            else -> SysSettings.Secure.getString(r, key)
        }
    }.getOrNull()

    fun learnedOn(name: String): Boolean {
        val l = com.nikalbazu.ids.core.Community.all()[name] ?: return false
        return l.on.isNotEmpty() && l.on.all { (k, v) -> readSetting(k) == v }
    }

    private fun applyLearned(name: String, turnOn: Boolean) {
        val l = com.nikalbazu.ids.core.Community.all()[name] ?: return
        val target = if (turnOn) l.on else l.off
        Helper.send(*target.map { (k, v) -> "put ${k.substringBefore(':')} ${k.substringAfter(':')} $v" }.toTypedArray())
    }

    /** Diferencia entre las dos fotos de ajustes: lo que cambió al tocar el botón en el panel de AYN. */
    fun finishLearning(name: String): Int {
        val before = Helper.readSnap(1); val after = Helper.readSnap(2)
        val noisy = Regex("brightness|volume|time|timestamp|last_|_count|boot|uptime|alarm|screen_off", RegexOption.IGNORE_CASE)
        val changed = after.filter { (k, v) -> before[k] != v && !noisy.containsMatchIn(k) && v.isNotBlank() && !v.contains(' ') }
        if (changed.isNotEmpty()) {
            val off = changed.mapValues { (k, _) -> before[k] ?: "0" }
            Store.update { it.copy(learned = it.learned + (name to com.nikalbazu.ids.core.Learned(changed, off))) }
            Store.log("Aprendido: $name (${changed.keys.joinToString { it.substringAfter(':') }})", "AYN")
        }
        return changed.size
    }

    fun forget(name: String) = Store.update { it.copy(learned = it.learned - name) }

    // ---- Accesos rápidos ----
    fun isOn(name: String): Boolean = runCatching {
        if (name in com.nikalbazu.ids.core.Community.all()) return learnedOn(name)
        when (name) {
            "Wi‑Fi" -> ctx.getSystemService(WifiManager::class.java).isWifiEnabled
            "Bluetooth" -> ctx.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true
            "No molestar" -> ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            else -> name in Store.settings.value.toggles
        }
    }.getOrDefault(false)

    fun toggle(name: String) {
        val on = !isOn(name)
        if (name in com.nikalbazu.ids.core.Community.all()) { applyLearned(name, on); return }
        if (name in STOCK_BUTTONS) { Store.toast("Enséñale este botón: pantalla de arriba › Aprender botones de AYN"); return }
        when (name) {
            "Wi‑Fi" -> Helper.send("wifi " + if (on) "on" else "off")
            "Bluetooth" -> Helper.send("bt " + if (on) "on" else "off")
            "No molestar" -> Helper.send("dnd " + if (on) "on" else "off")
            "Captura" -> { screenshot(); return }
            else -> Store.toast("Guardado · se conectará a la consola en una próxima versión")
        }
        Store.update { s -> s.copy(toggles = if (on) s.toggles + name else s.toggles - name) }
    }

    fun screenshot() { Helper.send("shot"); Store.toast("Captura guardada en Imágenes › IDS") }
    fun closeGame() { Store.foreground.value?.let { Helper.send("kill $it"); Store.toast("Juego cerrado") } }
    fun freeRam() { Helper.send("killbg"); Store.toast("Memoria liberada") }

    // ---- ClusterTune ----
    fun applyCaps(name: String, custom: Map<String, Int>? = null) {
        val caps = custom ?: Freq.PROFILES[name] ?: return
        Store.update { it.copy(ct = name, caps = caps) }
        if (Store.settings.value.ctOwner != "Esta app") return
        if (name == "Stock") {
            Helper.send("capreset 0", "capreset 3", "capreset 7", "gpucap ${Freq.STOCK["gpu"]}")
        } else {
            Helper.send("cap 0 ${caps["s"]}", "cap 3 ${caps["g"]}", "cap 7 ${caps["p"]}", "gpucap ${caps["gpu"]}")
        }
    }

    fun openClusterTune() = runCatching {
        ctx.packageManager.getLaunchIntentForPackage("com.aure.clustertune")?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(it, ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle())
        } ?: Store.toast("ClusterTune no está instalado")
    }

    // ---- Perfil por juego ----
    fun applyGameProfile(pkg: String, p: GameProfile) {
        setHz(p.hz); setPerf(p.perf); setFan(p.fan)
        if (p.ct != "Ninguno") applyCaps(p.ct)
        Store.log("Abriste ${label(pkg)} → perfil propio", "Por juego")
    }

    private val labels = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    // ---- Luces de los sticks ----
    private fun rgb(c: Long) = Triple(((c shr 16) and 0xFF).toInt(), ((c shr 8) and 0xFF).toInt(), (c and 0xFF).toInt())

    /** Aplica color, brillo y efecto a los anillos de los sticks. */
    fun applyLeds() {
        val s = Store.settings.value
        val br = (s.ledBright.coerceIn(0, 100) * 255) / 100
        val (lr, lg, lb) = rgb(s.ledL)
        val (rr, rg, rb) = rgb(if (s.ledSame) s.ledL else s.ledR)
        when (s.effect) {
            "Apagadas" -> Helper.send("led all 0 0 0 0")
            "Respirar" -> Helper.send("fxb $lr $lg $lb $br")
            "Arcoíris" -> Helper.send("fxr $br")
            "Según batería" -> {
                val p = Store.tele.value.batteryPct.coerceIn(0, 100)
                Helper.send("led all ${255 - p * 255 / 100} ${p * 255 / 100} 40 $br")
            }
            "Según temperatura" -> {
                val c = (Store.tele.value.socC ?: 40f).coerceIn(30f, 85f)
                val k = ((c - 30f) / 55f * 255).toInt()
                Helper.send("led all $k ${200 - k * 200 / 255} ${255 - k} $br")
            }
            else -> Helper.send("led l $lr $lg $lb $br", "led r $rr $rg $rb $br")
        }
    }

    fun launch(pkg: String, displayId: Int) {
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            ctx.startActivity(i, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
        } catch (e: Throwable) {
            ErrorReporter.report("Device.launch", e)
            Store.toast("No se pudo abrir la app")
        }
    }
}
