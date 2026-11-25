package com.nikalbazu.ids.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Configuración por juego (se aplica sola al abrir la app). */
data class GameProfile(
    val hz: String = "120 Hz",
    val perf: String = "Equilibrado",
    val fan: String = "Inteligente",
    val ct: String = "Ninguno",
    val fps: Int = 60,
    val pane: String = "Inicio",
)

/** Todo lo que el usuario elige y debe recordarse. */
data class Settings(
    val design: String = "Negro puro",
    val palette: String = "Prisma",
    val font: String = "Nunito",
    val size: String = "Normal",
    val techNumbers: Boolean = false,
    val widgets: Set<String> = setOf("temp", "consola", "ct", "stats", "sliders"),
    /** Tarjetas del Inicio (posición y tamaño en la cuadrícula de 12 × 6). */
    val layout: List<Tile> = Grid.DEFAULT,
    val mode: String = "Dos",
    val hz: String = "120 Hz",
    val perf: String = "Equilibrado",
    val fan: String = "Inteligente",
    val ct: String = "Stock",
    /** Brillo: mover las dos pantallas juntas con la barra general. */
    val brightLinked: Boolean = true,
    val caps: Map<String, Int> = Freq.STOCK,
    val ctOwner: String = "Auto",
    val autoTune: Boolean = false,
    val automation: Set<String> = setOf("boot", "minf"),
    val toggles: Set<String> = emptySet(),
    val ledL: Long = 0xFF7CC4F0,
    val ledR: Long = 0xFFF59AB0,
    val ledSame: Boolean = false,
    val effect: String = "Fijo",
    val ledBright: Int = 60,
    val curve: List<Int> = listOf(20, 35, 55, 75, 100),
    val trig: String = "Analógico",
    val vib: Int = 60,
    val deadZone: Int = 10,
    val battery: Set<String> = setOf("warn"),
    val notes: String = "",
    val aynKey: String = "",
    val replaceStock: Boolean = false,
    val bottomDisplay: Int = -1,
    val favorites: List<String> = emptyList(),
    val games: Map<String, GameProfile> = emptyMap(),
    val timerMin: Int = 60,
    /** Botones de AYN aprendidos: nombre → (ajuste → valor encendido / apagado). */
    val learned: Map<String, Learned> = emptyMap(),
    /** Paquetes que muestran el panel de fábrica (detectados al presionar el botón AYN). */
    val stockPackages: List<String> = emptyList(),
    /** Bloqueo total: desactiva esas apps mientras IDS esté instalado. */
    val blockStock: Boolean = false,
    /** Panel de AYN quitado para siempre (no se revierte al cerrar IDS). */
    val permStock: Boolean = false,
)

data class Learned(val on: Map<String, String>, val off: Map<String, String>)

/** Navegación del panel (no se guarda). */
data class Nav(
    val sec: String = "inicio",
    val ctTab: String = "perf",
    val hwTab: String = "led",
    val selectedGame: String? = null,
    val ledSide: String = "l",
    val editingNotes: Boolean = false,
)

data class LogEntry(val time: String, val text: String, val tag: String)

/** Datos en vivo de la consola. */
data class Tele(
    val socC: Float? = null,
    val batteryC: Float? = null,
    val batteryPct: Int = 0,
    val charging: Boolean = false,
    val watts: Float? = null,
    val minutesLeft: Int? = null,
    val cpuCur: Map<String, Int> = emptyMap(),
    val cpuMax: Map<String, Int> = emptyMap(),
    val cpuHw: Map<String, Int> = emptyMap(),
    val gpuHz: Long? = null,
    val gpuMaxHz: Long? = null,
    val gpuLoad: Int? = null,
    val fanRpm: Int? = null,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val backlights: List<String> = emptyList(),
    /** Brillo real de cada pantalla en % (leído del sistema). */
    val blPct: Map<String, Int> = emptyMap(),
    val leds: List<String> = emptyList(),
    val helperAlive: Boolean = false,
    val maxTodayC: Float = 0f,
)

object Freq {
    // Topes de fábrica del Snapdragon 8 Gen 2 (kHz para CPU, Hz para GPU)
    val STOCK = mapOf("s" to 2016000, "g" to 2803200, "p" to 3187200, "gpu" to 680000000)
    val PROFILES: Map<String, Map<String, Int>> = mapOf(
        "Stock" to STOCK,
        "Batería" to mapOf("s" to 1497600, "g" to 1612800, "p" to 1708800, "gpu" to 400000000),
        "Silencioso" to mapOf("s" to 1593600, "g" to 1900800, "p" to 2112000, "gpu" to 450000000),
        "Equilibrado" to mapOf("s" to 1785600, "g" to 2188800, "p" to 2400000, "gpu" to 525000000),
    )
    val HINT = mapOf(
        "Stock" to "Velocidad normal de la consola",
        "Batería" to "Dura más, se calienta menos",
        "Silencioso" to "Ventilador tranquilo",
        "Equilibrado" to "Buen punto medio",
        "Personalizado" to "Tus propios topes",
    )
}

/** Nombres antiguos de botones de AYN → nombres que usa AYN. */
val RENAMED = mapOf("Bloqueo automático" to "Auto lock", "Alimentación directa" to "Direct power supply")

object Store {
    private lateinit var appContext: Context
    val settings = MutableStateFlow(Settings())
    val nav = MutableStateFlow(Nav())
    val tele = MutableStateFlow(Tele())
    val history = MutableStateFlow<List<LogEntry>>(emptyList())
    val panelVisible = MutableStateFlow(false)
    val learningKey = MutableStateFlow(false)
    val foreground = MutableStateFlow<String?>(null)
    val timerEndsAt = MutableStateFlow(0L)
    val message = MutableStateFlow<String?>(null)
    /** Botón de AYN que se está aprendiendo ahora mismo (null = ninguno). */
    val learningSetting = MutableStateFlow<String?>(null)
    val detectingStock = MutableStateFlow(false)
    /** Modo que eligió el ventilador inteligente ahora mismo ("" si no está en Inteligente). */
    val fanNow = MutableStateFlow("")
    /** Modo edición del Inicio y bandeja para agregar tarjetas. */
    val editing = MutableStateFlow(false)
    val tray = MutableStateFlow(false)

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        val raw = prefs().getString("settings", null)
        if (raw != null) runCatching { settings.value = decode(JSONObject(raw)) }
        val hist = prefs().getString("history", null)
        if (hist != null) runCatching {
            val arr = JSONArray(hist)
            history.value = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LogEntry(o.optString("t"), o.optString("x"), o.optString("g"))
            }
        }
    }

    private fun prefs() = appContext.getSharedPreferences("ids", Context.MODE_PRIVATE)

    private val saver = Executors.newSingleThreadExecutor { r -> Thread(r, "ids-store").apply { isDaemon = true } }

    /** Cambia la configuración al instante y la guarda en segundo plano. */
    fun update(block: (Settings) -> Settings) {
        val next = synchronized(this) { block(settings.value).also { settings.value = it } }
        saver.execute {
            if (settings.value === next) prefs().edit().putString("settings", encode(next).toString()).apply()
        }
    }

    fun nav(block: (Nav) -> Nav) { nav.value = block(nav.value) }

    fun log(text: String, tag: String) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val list = (listOf(LogEntry(time, text, tag)) + history.value).take(60)
        history.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.time).put("x", it.text).put("g", it.tag)) }
        prefs().edit().putString("history", arr.toString()).apply()
    }

    fun toast(text: String) { message.value = text }

    // ---- JSON ----
    private fun encode(s: Settings): JSONObject = JSONObject().apply {
        put("design", s.design); put("palette", s.palette); put("font", s.font); put("size", s.size)
        put("techNumbers", s.techNumbers); put("layout", JSONArray().also { a -> s.layout.forEach { t -> a.put(JSONObject().put("id", t.id).put("x", t.x).put("y", t.y).put("w", t.w).put("h", t.h)) } }); put("widgets", JSONArray(s.widgets.toList()))
        put("mode", s.mode); put("hz", s.hz); put("perf", s.perf); put("fan2", s.fan); put("brightLinked", s.brightLinked); put("ct", s.ct)
        put("caps", JSONObject(s.caps as Map<*, *>)); put("ctOwner2", s.ctOwner); put("autoTune", s.autoTune)
        put("automation", JSONArray(s.automation.toList())); put("toggles", JSONArray(s.toggles.toList()))
        put("ledL", s.ledL); put("ledR", s.ledR); put("ledSame", s.ledSame); put("effect", s.effect)
        put("ledBright", s.ledBright); put("curve", JSONArray(s.curve)); put("trig", s.trig); put("vib", s.vib)
        put("deadZone", s.deadZone); put("battery", JSONArray(s.battery.toList())); put("notes", s.notes)
        put("aynKey", s.aynKey); put("replaceStock", s.replaceStock); put("bottomDisplay", s.bottomDisplay)
        put("favorites", JSONArray(s.favorites)); put("timerMin", s.timerMin)
        val g = JSONObject()
        s.games.forEach { (pkg, p) ->
            g.put(pkg, JSONObject().put("hz", p.hz).put("perf", p.perf).put("fan", p.fan).put("ct", p.ct)
                .put("fps", p.fps).put("pane", p.pane))
        }
        put("games", g)
        val l = JSONObject()
        s.learned.forEach { (name, v) ->
            l.put(name, JSONObject().put("on", JSONObject(v.on as Map<*, *>)).put("off", JSONObject(v.off as Map<*, *>)))
        }
        put("learned", l)
        put("stockPackages", JSONArray(s.stockPackages)); put("blockStock", s.blockStock); put("permStock", s.permStock)
    }

    private fun JSONObject.strings(key: String): List<String>? = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }
    private fun JSONObject.ints(key: String): List<Int>? = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optInt(it) } }

    private fun decode(o: JSONObject): Settings {
        val d = Settings()
        val caps = o.optJSONObject("caps")?.let { c -> c.keys().asSequence().associateWith { c.optInt(it) } } ?: d.caps
        val games = o.optJSONObject("games")?.let { g ->
            g.keys().asSequence().associateWith { k ->
                val p = g.getJSONObject(k)
                GameProfile(p.optString("hz", "120 Hz"), p.optString("perf", "Equilibrado"), p.optString("fan", "Inteligente"),
                    p.optString("ct", "Ninguno"), p.optInt("fps", 60), p.optString("pane", "Inicio"))
            }
        } ?: emptyMap()
        fun JSONObject.strMap(): Map<String, String> = keys().asSequence().associateWith { optString(it) }
        val learned = o.optJSONObject("learned")?.let { l ->
            l.keys().asSequence().associateWith { k ->
                val v = l.getJSONObject(k)
                Learned(v.optJSONObject("on")?.strMap() ?: emptyMap(), v.optJSONObject("off")?.strMap() ?: emptyMap())
            }
        } ?: emptyMap()
        return Settings(
            design = o.optString("design", d.design), palette = o.optString("palette", d.palette),
            font = o.optString("font", d.font), size = o.optString("size", d.size),
            techNumbers = o.optBoolean("techNumbers", d.techNumbers),
            widgets = o.strings("widgets")?.toSet() ?: d.widgets,
            layout = o.optJSONArray("layout")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { t ->
                Tile(t.optString("id"), t.optInt("x"), t.optInt("y"), t.optInt("w", 1), t.optInt("h", 1)).takeIf { it.id in Grid.INFO && it.inside() } } } }
                ?.takeIf { it.isNotEmpty() } ?: d.layout,
            mode = o.optString("mode", d.mode), hz = o.optString("hz", d.hz), perf = o.optString("perf", d.perf),
            fan = o.optString("fan2", d.fan), brightLinked = o.optBoolean("brightLinked", d.brightLinked), ct = o.optString("ct", d.ct), caps = caps,
            ctOwner = o.optString("ctOwner2", d.ctOwner), autoTune = o.optBoolean("autoTune", d.autoTune),
            automation = o.strings("automation")?.toSet() ?: d.automation,
            toggles = o.strings("toggles")?.toSet() ?: d.toggles,
            ledL = o.optLong("ledL", d.ledL), ledR = o.optLong("ledR", d.ledR), ledSame = o.optBoolean("ledSame", d.ledSame),
            effect = o.optString("effect", d.effect), ledBright = o.optInt("ledBright", d.ledBright),
            curve = o.ints("curve") ?: d.curve, trig = o.optString("trig", d.trig), vib = o.optInt("vib", d.vib),
            deadZone = o.optInt("deadZone", d.deadZone), battery = o.strings("battery")?.toSet() ?: d.battery,
            notes = o.optString("notes", d.notes), aynKey = o.optString("aynKey", d.aynKey),
            replaceStock = o.optBoolean("replaceStock", d.replaceStock), bottomDisplay = o.optInt("bottomDisplay", d.bottomDisplay),
            favorites = o.strings("favorites") ?: d.favorites, games = games, timerMin = o.optInt("timerMin", d.timerMin),
            learned = learned.mapKeys { (k, _) -> RENAMED[k] ?: k },
            stockPackages = o.strings("stockPackages") ?: d.stockPackages,
            blockStock = o.optBoolean("blockStock", d.blockStock), permStock = o.optBoolean("permStock", d.permStock),
        )
    }
}
