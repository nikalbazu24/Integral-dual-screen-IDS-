package com.nikalbazu.ids.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikalbazu.ids.core.Freq
import com.nikalbazu.ids.core.Store
import com.nikalbazu.ids.core.Tele
import com.nikalbazu.ids.priv.Device
import java.util.Locale

fun ghz(khz: Int?): String = if (khz == null || khz <= 0) "—" else String.format(Locale("es"), "%.2f", khz / 1_000_000f)
fun mhz(hz: Long?): String = if (hz == null || hz <= 0) "—" else "${hz / 1_000_000}"
fun deg(c: Float?): String = if (c == null || c <= 0f) "—" else "${c.toInt()}°"

data class Stat(val label: String, val value: String, val unit: String, val detail: String, val fraction: Float, val color: Int)

fun stats(t: Tele): List<Stat> {
    val cur = t.cpuCur["7"]; val hw = t.cpuHw["7"] ?: 3187200
    val gpu = t.gpuHz; val gpuHw = 680_000_000L
    val ram = t.ramUsedGb; val total = t.ramTotalGb.coerceAtLeast(1f)
    return listOf(
        Stat("CPU", ghz(cur).let { if (it == "—") it else it.dropLast(1) }, "GHz", "de ${ghz(hw)} GHz", (cur ?: 0) / hw.toFloat(), 2),
        Stat("GPU", mhz(gpu), "MHz", "de 680 MHz", ((gpu ?: 0L).toFloat() / gpuHw), 3),
        Stat("RAM", String.format(Locale("es"), "%.1f", ram), "GB", String.format(Locale("es"), "de %.0f GB", total), ram / total, 0),
        Stat("Consumo", t.watts?.let { String.format(Locale("es"), "%.1f", it) } ?: "—", "W", "de la batería", (t.watts ?: 0f) / 15f, 1),
    )
}

@Composable
fun InicioScreen() {
    val s by Store.settings.collectAsState()
    val w = s.widgets
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if ("now" in w) NowStrip()
        if ("temp" in w || "consola" in w) Row(Modifier.height(330.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if ("temp" in w) TempCard(Modifier.weight(1f).fillMaxHeight())
            if ("consola" in w) ConsolaCard(Modifier.weight(1f).fillMaxHeight())
        }
        if ("ct" in w || "stats" in w) Row(Modifier.height(330.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if ("ct" in w) CtCard(Modifier.weight(1f).fillMaxHeight())
            if ("stats" in w) StatsGrid(Modifier.weight(1f).fillMaxHeight())
        }
        if ("sliders" in w) SlidersCard()
        if ("hw" in w) HwShortcuts()
        if ("quick" in w || "music" in w) Row(Modifier.height(140.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if ("quick" in w) QuickRow(Modifier.weight(3f).fillMaxHeight())
            if ("music" in w) MusicCard(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun NowStrip() {
    val t = LocalTok.current
    val fg by Store.foreground.collectAsState()
    Row(Modifier.fillMaxWidth().card(t).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val name = fg?.let { Device.label(it) } ?: "Ningún juego abierto"
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(t.tile[0]), contentAlignment = Alignment.Center) {
            T(name.take(1), 28.sp, FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            T(name, 22.sp, FontWeight.Black, maxLines = 1)
            T(if (fg != null && Store.settings.value.games.containsKey(fg)) "Perfil propio activo" else "Sin perfil propio", 16.sp, FontWeight.Bold, t.mu)
        }
        Pill("Cerrar juego", false, height = 44.dp, size = 16.sp) { Device.closeGame() }
    }
}

@Composable
private fun TempCard(modifier: Modifier) {
    val t = LocalTok.current
    val tele by Store.tele.collectAsState()
    val s by Store.settings.collectAsState()
    val c = tele.socC ?: 0f
    val state = when { c <= 0f -> "Leyendo…"; c < 55 -> "Normal · bajo 55 °C"; c < 70 -> "Tibia"; else -> "Caliente" }
    val color = when { c < 55 -> t.v[1]; c < 70 -> t.v[4]; else -> t.v[0] }
    Row(modifier.card(t, 4).padding(horizontal = 26.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Ring(deg(tele.socC), "chip", c / 90f, color, 220.dp, 12.dp, 64.sp, 18.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Temperatura", 24.sp, FontWeight.Black)
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(t.track).padding(horizontal = 14.dp, vertical = 7.dp)) {
                T("●  $state", 16.sp, FontWeight.Black, color)
            }
            listOf(
                "Batería" to (tele.batteryC?.let { "${it.toInt()} °C" } ?: "—"),
                "Ventilador" to ((tele.fanRpm?.let { "$it rpm · " } ?: "") + s.fan),
                "Máxima hoy" to (if (tele.maxTodayC > 0) "${tele.maxTodayC.toInt()} °C" else "—"),
            ).forEach { (k, v) ->
                Row { T(k, 18.sp, FontWeight.Bold, t.mu, Modifier.weight(1f)); T(v, 18.sp, FontWeight.Black) }
            }
            Pill("Ventilador ›", true, Modifier.fillMaxWidth(), 46.dp, 16.sp) { Store.nav { it.copy(sec = "hw", hwTab = "fan") } }
        }
    }
}

@Composable
private fun ConsolaCard(modifier: Modifier) {
    val s by Store.settings.collectAsState()
    Card(modifier, gap = 10.dp) {
        T("Consola", 22.sp, FontWeight.Black)
        LabeledSeg("Pantallas", listOf("Dos", "Arriba", "Abajo"), s.mode, 118.dp, 56.dp) { Device.setScreens(it) }
        LabeledSeg("Refresco", listOf("60 Hz", "120 Hz"), s.hz, 118.dp, 56.dp) { Device.setHz(it) }
        LabeledSeg("Rendimiento", listOf("Ahorro", "Equilibrado", "Rendimiento"), s.perf, 118.dp, 56.dp) { Device.setPerf(it) }
        LabeledSeg("Ventilador", listOf("Silencio", "Auto", "Máx"), s.fan, 118.dp, 56.dp) { Device.setFan(it) }
    }
}

@Composable
private fun CtCard(modifier: Modifier) {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    Card(modifier, accent = 3, gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("ClusterTune", 22.sp, FontWeight.Black, modifier = Modifier.weight(1f))
            Pill("Abrir ›", true, height = 44.dp, size = 16.sp) { Store.nav { it.copy(sec = "ct") } }
        }
        T(s.ct, 34.sp, FontWeight.Black, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            val p = s.caps["p"] ?: Freq.STOCK.getValue("p"); val g = s.caps["gpu"] ?: Freq.STOCK.getValue("gpu")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ring(ghz(p), "GHz", p / 3187200f, t.v[3], 74.dp, 10.dp, 18.sp, 11.sp); T("Tope\nCPU", 15.sp, FontWeight.ExtraBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ring("${g / 1_000_000}", "MHz", g / 680_000_000f, t.v[2], 74.dp, 10.dp, 18.sp, 11.sp); T("Tope\nGPU", 15.sp, FontWeight.ExtraBold)
            }
        }
        SegRow(listOf("Stock", "Batería", "Silencioso", "Equilibrado"), s.ct, height = 50.dp, size = 15.sp) { Device.applyCaps(it) }
        T(if (s.ctOwner == "Esta app") "Auto Tune: " + (if (s.autoTune) "activo" else "apagado") else "Controlado por la app ClusterTune", 17.sp, FontWeight.Bold)
    }
}

@Composable
private fun StatsGrid(modifier: Modifier) {
    val t = LocalTok.current
    val tele by Store.tele.collectAsState()
    val list = stats(tele)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (r in 0..1) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (c in 0..1) {
                val st = list[r * 2 + c]
                Row(Modifier.weight(1f).fillMaxHeight().card(t, st.color, 30.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ring(st.value, st.unit, st.fraction, t.v[st.color], 98.dp, 12.dp, 22.sp, 13.sp, t.track)
                    Column { T(st.label, 20.sp, FontWeight.Black); T(st.detail, 14.sp, FontWeight.Bold, t.mu) }
                }
            }
        }
    }
}

@Composable
fun SlidersCard() {
    val t = LocalTok.current
    var top by remember { mutableIntStateOf(Device.topBrightness()) }
    var bottom by remember { mutableIntStateOf(Device.bottomPct) }
    var vol by remember { mutableIntStateOf(Device.volume()) }
    Row(Modifier.fillMaxWidth().card(t).padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StepBar("Brillo arriba · $top", top / 100f, t.v[2].copy(alpha = if (t.light) 0.35f else 0.4f), Modifier.weight(1f),
            onMinus = { top = (top - 10).coerceAtLeast(1); Device.setTopBrightness(top) },
            onPlus = { top = (top + 10).coerceAtMost(100); Device.setTopBrightness(top) },
            onSlide = { top = (it * 100).toInt().coerceIn(1, 100) }, onRelease = { Device.setTopBrightness(top) })
        StepBar("Brillo abajo · $bottom", bottom / 100f, t.v[2].copy(alpha = if (t.light) 0.35f else 0.4f), Modifier.weight(1f),
            onMinus = { bottom = (bottom - 10).coerceAtLeast(1); Device.setBottomBrightness(bottom) },
            onPlus = { bottom = (bottom + 10).coerceAtMost(100); Device.setBottomBrightness(bottom) },
            onSlide = { bottom = (it * 100).toInt().coerceIn(1, 100) }, onRelease = { Device.setBottomBrightness(bottom) })
        StepBar("Volumen · $vol", vol / 100f, t.v[2].copy(alpha = if (t.light) 0.35f else 0.4f), Modifier.weight(1f),
            onMinus = { vol = (vol - 10).coerceAtLeast(0); Device.setVolume(vol) },
            onPlus = { vol = (vol + 10).coerceAtMost(100); Device.setVolume(vol) },
            onSlide = { val n = (it * 100).toInt(); if (n != vol) { vol = n; Device.setVolume(vol) } })
    }
}

@Composable
private fun HwShortcuts() {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val tele by Store.tele.collectAsState()
    Row(Modifier.height(112.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.weight(1f).fillMaxHeight().card(t).tap { Store.nav { it.copy(sec = "hw", hwTab = "led") } }.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(androidx.compose.ui.graphics.Color(s.ledL)))
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(androidx.compose.ui.graphics.Color(s.ledR)))
            Column(Modifier.weight(1f)) { T("Luces de los sticks", 20.sp, FontWeight.Black); T("${s.effect} · brillo ${s.ledBright} %", 16.sp, FontWeight.Bold, t.mu) }
            T("›", 24.sp, FontWeight.Black)
        }
        Row(Modifier.weight(1f).fillMaxHeight().card(t).tap { Store.nav { it.copy(sec = "hw", hwTab = "fan") } }.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            T(tele.fanRpm?.toString() ?: "—", 36.sp, FontWeight.Black, num = true)
            Column(Modifier.weight(1f)) { T("Ventilador · ${s.fan}", 20.sp, FontWeight.Black); T("rpm · chip a ${deg(tele.socC)}", 16.sp, FontWeight.Bold, t.mu) }
            T("›", 24.sp, FontWeight.Black)
        }
    }
}

@Composable
private fun QuickRow(modifier: Modifier) {
    val t = LocalTok.current
    var tick by remember { mutableIntStateOf(0) }
    Row(modifier.card(t).padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Wi‑Fi", "Bluetooth", "No molestar", "Carga 80 %", "Ratón virtual", "Captura").forEach { name ->
            val on = remember(tick) { Device.isOn(name) }
            Column(Modifier.weight(1f).tap { Device.toggle(name); tick++ }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(34.dp)).background(if (on) t.sel else t.track), contentAlignment = Alignment.Center) {
                    T(if (on) "●" else "○", 24.sp, FontWeight.Black, if (on) t.selTx else t.tx)
                }
                T(name, 14.sp, FontWeight.ExtraBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MusicCard(modifier: Modifier) {
    val ctx = LocalContext.current
    Card(modifier, padding = PaddingValues(18.dp), gap = 10.dp) {
        T("Música", 19.sp, FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("«", false, Modifier.weight(1f), 48.dp) { mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            Pill("II", true, Modifier.weight(1f), 48.dp) { mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
            Pill("»", false, Modifier.weight(1f), 48.dp) { mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT) }
        }
    }
}

@Composable
fun ControlScreen() {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Control")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(Triple("Dos", "Dos pantallas", 3), Triple("Arriba", "Solo arriba", 1), Triple("Abajo", "Solo abajo", 2)).forEach { (id, name, mask) ->
                val on = s.mode == id
                val shape = RoundedCornerShape(34.dp)
                val mod = if (on) Modifier.background(t.sel, shape) else Modifier.card(t, null, 34.dp)
                Column(Modifier.weight(1f).height(160.dp).then(mod).clip(shape).tap { Device.setScreens(id) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val idle = if (on) androidx.compose.ui.graphics.Color(0xFFC9C5CF) else t.track
                    Box(Modifier.width(84.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (mask and 1 != 0) t.v[0] else idle))
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(66.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (mask and 2 != 0) t.v[0] else idle))
                    Spacer(Modifier.height(12.dp))
                    T(name, 22.sp, FontWeight.Black, if (on) t.selTx else t.tx)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Pill("Intercambiar apps", false, Modifier.weight(1f), 84.dp, 19.sp) { Store.toast("Intercambiar apps llegará en la próxima versión") }
            Pill("Traer app aquí", false, Modifier.weight(1f), 84.dp, 19.sp) { Store.toast("Traer app llegará en la próxima versión") }
            Pill("Apagar pantalla arriba", false, Modifier.weight(1f), 84.dp, 19.sp) { Device.setScreens("Abajo") }
        }
        val names = listOf("Wi‑Fi", "Bluetooth", "No molestar", "Captura", "Carga 80 %", "Ratón virtual", "Gatillos L2/R2", "Luces RGB")
        Grid(4, names.size) { i ->
            val on = remember(tick) { Device.isOn(names[i]) }
            ToggleCard(names[i], "", on, Modifier.weight(1f)) { Device.toggle(names[i]); tick++ }
        }
        T("Wi‑Fi, Bluetooth, No molestar y Captura ya funcionan. El resto se conectará en próximas versiones.", 17.sp, FontWeight.Bold, t.mu)
    }
}
