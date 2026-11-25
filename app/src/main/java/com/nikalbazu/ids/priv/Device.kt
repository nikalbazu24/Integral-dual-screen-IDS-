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

    /** Momento del último cambio hecho desde IDS (para no pisarlo con una lectura vieja). */
    @Volatile var lastSet = 0L

    fun setFan(mode: String) {
        lastSet = System.currentTimeMillis()
        Store.update { it.copy(fan = mode) }
        if (mode == "Inteligente") { smartLevel = -1; smartTick(Store.tele.value.socC) }
        else { Store.fanNow.value = ""; FAN[mode]?.let { Helper.send("ayn fan_mode $it") } }
    }

    // ---- Ventilador inteligente: sube solo cuando el chip se calienta ----
    // Nivel 0 = Silencio (bajo 55 °C), 1 = Auto (55–70 °C), 2 = Máx (sobre 70 °C).
    // Sube al instante; baja solo si se enfrió 4 °C bajo el umbral durante 20 s (para que no "bombee").
    private val SMART = listOf("Silencio" to 1, "Auto" to 4, "Máx" to 5)
    private var smartLevel = -1
    private var coolSince = 0L

    fun smartTick(c: Float?) {
        if (Store.settings.value.fan != "Inteligente" || c == null || c <= 0f) return
        val want = when { c >= 70f -> 2; c >= 55f -> 1; else -> 0 }
        val now = System.currentTimeMillis()
        val next = when {
            smartLevel < 0 || want > smartLevel -> { coolSince = 0L; want }
            want < smartLevel -> {
                val limit = if (smartLevel == 2) 66f else 51f
                if (c > limit) { coolSince = 0L; smartLevel }
                else { if (coolSince == 0L) coolSince = now; if (now - coolSince >= 20_000) { coolSince = 0L; smartLevel - 1 } else smartLevel }
            }
            else -> { coolSince = 0L; smartLevel }
        }
        if (next != smartLevel) {
            val first = smartLevel < 0
            smartLevel = next
            Helper.send("ayn fan_mode ${SMART[next].second}")
            if (!first) Store.log("Chip a ${c.toInt()} °C → ventilador en ${SMART[next].first}", "Ventilador")
        }
        Store.fanNow.value = SMART[smartLevel].first
    }

    fun setPerf(mode: String) {
        lastSet = System.currentTimeMillis()
        Store.update { it.copy(perf = mode) }
        PERF[mode]?.let { Helper.send("ayn performance_mode $it") }
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
                fan = if (s.fan == "Inteligente") s.fan else FAN.entries.firstOrNull { it.value == fan }?.key ?: s.fan,
                perf = when (perf) { 0 -> "Ahorro"; 1 -> "Equilibrado"; 2 -> "Rendimiento"; else -> s.perf },
            )
        }
    }

    // ---- Brillo y volumen (0-100) ----
    fun topBrightness(): Int = runCatching {
        SysSettings.System.getInt(ctx.contentResolver, SysSettings.System.SCREEN_BRIGHTNESS) * 100 / 255
    }.getOrDefault(50)

    fun setTopBrightness(pct: Int) = Helper.send("bright ${(pct.coerceIn(1, 100) * 255) / 100}")

    /** Brillo de la pantalla de abajo en % real (la app lo escala al máximo que acepta esa pantalla). */
    /** Brillo de la pantalla de abajo: el ajuste de AYN dual_screen_brightness_level (0-100). */
    fun setBottomBrightness(pct: Int) {
        Helper.send("ayn dual_screen_brightness_level ${pct.coerceIn(1, 100)}")
        bottomSet = pct
    }
    private var bottomSet = -1
    val bottomPct: Int get() = if (bottomSet > 0) bottomSet else Store.tele.value.ayn["dual_screen_brightness_level"]?.toIntOrNull() ?: 50
    private fun bottomBacklight(): String? { val bl = Store.tele.value.backlights; return bl.getOrNull(1) ?: bl.firstOrNull() }

    fun volume(stream: Int = AudioManager.STREAM_MUSIC): Int = runCatching {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        am.getStreamVolume(stream) * 100 / max
    }.getOrDefault(50)

    fun setVolume(pct: Int, stream: Int = AudioManager.STREAM_MUSIC) = runCatching {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(stream)
        am.setStreamVolume(stream, (pct.coerceIn(0, 100) * max + 50) / 100, 0)
    }

    // ---- Modos de pantalla ----
    fun setScreens(mode: String) {
        Store.update { it.copy(mode = mode) }
        val bl = Store.tele.value.backlights
        val top = bl.getOrNull(0); val bottom = bl.getOrNull(1)
        when (mode) {
            "Dos" -> { setBottomBrightness(bottomPct.coerceAtLeast(20)) }
            "Arriba" -> bottom?.let { Helper.send("blight $it 0") }
            "Abajo" -> top?.let { Helper.send("blight $it 0") }
        }
    }

    // ---- Botones de AYN aprendidos ----
    val STOCK_BUTTONS = listOf("Auto lock", "Estilo de control", "Vibración",
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
            // Si al tocarlo el botón se APAGÓ, la foto de "después" es el estado apagado: se dan vuelta.
            fun offness(m: Map<String, String>) = m.values.count { it == "0" || it.equals("false", true) || it.equals("off", true) }
            val inverted = offness(changed) > offness(off)
            val l = if (inverted) com.nikalbazu.ids.core.Learned(off, changed) else com.nikalbazu.ids.core.Learned(changed, off)
            Store.update { it.copy(learned = it.learned + (name to l)) }
            Store.log("Aprendido: $name (${changed.keys.joinToString { it.substringAfter(':') }})", "AYN")
        }
        return changed.size
    }

    fun forget(name: String) = Store.update { it.copy(learned = it.learned - name) }

    /** Por si un botón aprendido quedó al revés (dice encendido cuando está apagado). */
    fun invert(name: String) = Store.update { s ->
        val l = s.learned[name] ?: com.nikalbazu.ids.core.Community.all()[name] ?: return@update s
        s.copy(learned = s.learned + (name to com.nikalbazu.ids.core.Learned(l.off, l.on)))
    }.also { refreshStates() }

    // ---- Estado de los botones: se lee en segundo plano (nunca en la pantalla), así no hay tirones ----
    val states = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val pending = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private val stateIo = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "ids-states").apply { isDaemon = true } }
    private val WATCH by lazy { listOf("Wi‑Fi", "Bluetooth", "No molestar", "Carga 80 %", "Ratón virtual", "Gatillos L2/R2") + AYN_DIRECT.keys }

    fun refreshStates() = stateIo.execute {
        val now = System.currentTimeMillis()
        val names = (WATCH + STOCK_BUTTONS + com.nikalbazu.ids.core.Community.all().keys).distinct()
        states.value = names.associateWith { n ->
            val p = pending[n]
            if (p != null && now < p.second) p.first else { pending.remove(n); isOn(n) }
        }
    }

    // ---- Accesos rápidos ----
    /** Botones que son ajustes directos de AYN: nombre → (ajuste, valor encendido, valor apagado). */
    val AYN_DIRECT = mapOf(
        "Direct power supply" to Triple("is_charging_separation", "1", "0"),
        "Force landscape" to Triple("force_landscape", "1", "0"),
        "Luz ambiental" to Triple("joystick_light_enabled", "1,1", "0,0"),
        "Mantener pantalla" to Triple("keep_screen_on", "1", "0"),
    )

    fun isOn(name: String): Boolean = runCatching {
        AYN_DIRECT[name]?.let { (k, on, _) -> return Store.tele.value.ayn[k]?.let { it == on || (k == "joystick_light_enabled" && it.contains('1')) } ?: false }
        if (name in com.nikalbazu.ids.core.Community.all()) return learnedOn(name)
        when (name) {
            "Wi‑Fi" -> ctx.getSystemService(WifiManager::class.java).isWifiEnabled
            "Bluetooth" -> ctx.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true
            "No molestar" -> ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            else -> name in Store.settings.value.toggles
        }
    }.getOrDefault(false)

    fun toggle(name: String) {
        val on = !(states.value[name] ?: isOn(name))
        // Se muestra el cambio al instante; la lectura real lo confirma a los 4 s
        pending[name] = on to System.currentTimeMillis() + 4000
        states.value = states.value + (name to on)
        AYN_DIRECT[name]?.let { (k, onV, offV) ->
            if (k == "joystick_light_enabled" && on) applyLeds() else Helper.send("ayn $k ${if (on) onV else offV}")
            if (k == "joystick_light_enabled") Store.update { it.copy(effect = if (on) (if (it.effect == "Apagadas") "Fijo" else it.effect) else "Apagadas") }
            return
        }
        if (name in com.nikalbazu.ids.core.Community.all()) { applyLearned(name, on); return }
        if (name in STOCK_BUTTONS) { Store.toast("Enséñale este botón: pantalla de arriba › Aprender botones de AYN"); return }
        when (name) {
            "Wi‑Fi" -> Helper.send("wifi " + if (on) "on" else "off")
            "Bluetooth" -> Helper.send("bt " + if (on) "on" else "off")
            "No molestar" -> Helper.send("dnd " + if (on) "on" else "off")
            "Captura" -> { screenshot(); return }
            "Clean memory" -> { cleanMemory(); return }
            else -> Store.toast("Guardado · se conectará a la consola en una próxima versión")
        }
        Store.update { s -> s.copy(toggles = if (on) s.toggles + name else s.toggles - name) }
    }

    fun screenshot() { Helper.send("shot"); Store.toast("Captura guardada en Imágenes › IDS") }
    fun closeGame() { Store.foreground.value?.let { Helper.send("kill $it"); Store.toast("Juego cerrado") } }
    fun freeRam() = cleanMemory()

    /** Clean memory: cierra apps de fondo y avisa cuánta memoria quedó libre. */
    val cleaned = kotlinx.coroutines.flow.MutableStateFlow("")
    fun cleanMemory() {
        val am = ctx.getSystemService(android.app.ActivityManager::class.java)
        fun free(): Long = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        val before = free()
        Helper.send("killbg")
        Thread {
            Thread.sleep(1800)
            val after = free()
            val gb = after / 1_073_741_824f
            val gained = ((after - before).coerceAtLeast(0)) / 1_048_576
            cleaned.value = String.format(java.util.Locale("es"), "Listo · %.1f GB libres", gb)
            Store.toast(if (gained > 0) "Memoria liberada: $gained MB" else "Memoria ya estaba limpia")
            Thread.sleep(6000); cleaned.value = ""
        }.start()
    }

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
    private fun hex6(c: Long) = String.format("%06X", c and 0xFFFFFF)

    /** Luces de los sticks: se aplican con los mismos ajustes que usa AYN (también los ve la barra de notificaciones). */
    fun applyLeds() {
        val s = Store.settings.value
        val br = s.ledBright.coerceIn(1, 100)
        val l = hex6(s.ledL); val r = hex6(if (s.ledSame) s.ledL else s.ledR)
        when (s.effect) {
            "Apagadas" -> Helper.send("lights 0 000000 000000 0")
            "Respirar" -> Helper.send("fxb $l $br")
            "Arcoíris" -> Helper.send("fxr $br")
            "Según batería" -> {
                val p = Store.tele.value.batteryPct.coerceIn(0, 100)
                val c = String.format("%02X%02X28", 255 - p * 255 / 100, p * 255 / 100)
                Helper.send("lights 1 $c $c $br")
            }
            "Según temperatura" -> {
                val k = (((Store.tele.value.socC ?: 40f).coerceIn(30f, 85f) - 30f) / 55f * 255).toInt()
                val c = String.format("%02X%02X%02X", k, 200 - k * 200 / 255, 255 - k)
                Helper.send("lights 1 $c $c $br")
            }
            else -> Helper.send("lights 1 $l $r $br")
        }
    }

    /** Lee lo que tiene AYN guardado (color, brillo, encendido) para que IDS parta igual que la consola. */
    fun syncLedsFromSystem() {
        val a = Store.tele.value.ayn
        val cols = a["joystick_led_light_picker_color"]?.split(',')?.mapNotNull { c ->
            c.trim().removePrefix("#").takeLast(6).toLongOrNull(16)?.let { 0xFF000000 or it }
        } ?: return
        if (cols.isEmpty()) return
        val br = a["led_light_brightness_percent"]?.toFloatOrNull()?.let { (it * 100).toInt().coerceIn(1, 100) }
        val off = a["joystick_light_enabled"]?.split(',')?.all { it.trim() == "0" } == true
        Store.update { st ->
            st.copy(ledL = cols[0], ledR = cols.getOrElse(1) { cols[0] }, ledSame = cols.getOrElse(1) { cols[0] } == cols[0],
                ledBright = br ?: st.ledBright, effect = if (off) "Apagadas" else if (st.effect == "Apagadas") "Fijo" else st.effect)
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
