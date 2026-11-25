package com.nikalbazu.ids.ui

import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

/** Colores de temperatura: frío azul, normal verde, caliente naranjo, muy caliente rojo. */
fun tempColor(c: Float): Color = when {
    c <= 0f -> Color(0xFF8A8792)
    c < 35f -> Color(0xFF5AB4F0)
    c < 55f -> Color(0xFF5CD68A)
    c < 70f -> Color(0xFFF5A04A)
    else -> Color(0xFFF0605A)
}

fun tempState(c: Float): String = when {
    c <= 0f -> "Leyendo…"
    c < 35f -> "Fría · bajo 35 °C"
    c < 55f -> "Normal · 35 a 55 °C"
    c < 70f -> "Caliente · 55 a 70 °C"
    else -> "Muy caliente · sobre 70 °C"
}

@Composable
fun InicioScreen() {
    var pop by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        TileBoard { pop = it }
        AnimatedVisibility(pop != null, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.94f)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).tap { pop = null }, contentAlignment = Alignment.Center) {
                when (pop) {
                    "brillo" -> BrightPopup { pop = null }
                    "volumen" -> VolumePopup { pop = null }
                    else -> {}
                }
            }
        }
    }
}

/** Columna lateral: título, barra que se desliza y botón "Por pantalla". */
@Composable
private fun SideBar(title: String, value: Int, fill: Color, width: Dp, onSlide: (Float) -> Unit, onRelease: () -> Unit, onOpen: () -> Unit) {
    val t = LocalTok.current
    Column(Modifier.width(width).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T(title, 17.sp, FontWeight.Black, maxLines = 1)
        VBar("$value", value / 100f, fill, Modifier.weight(1f).fillMaxWidth(), onSlide, onRelease)
        Pill("Por pantalla", false, Modifier.fillMaxWidth(), 56.dp, 14.sp, onOpen)
    }
}

@Composable
private fun BrightBar(onOpen: () -> Unit) {
    val t = LocalTok.current
    var v by remember { mutableIntStateOf(Device.topBrightness()) }
    SideBar("Brillo", v, t.v[4].copy(alpha = 0.4f), 82.dp,
        onSlide = { v = (it * 100).toInt().coerceIn(1, 100) },
        onRelease = {
            Device.setTopBrightness(v)
            if (Store.settings.value.brightLinked) Device.setBottomBrightness(v)
        }, onOpen = onOpen)
}

@Composable
private fun VolumeBar(onOpen: () -> Unit) {
    val t = LocalTok.current
    var v by remember { mutableIntStateOf(Device.volume()) }
    SideBar("Volumen", v, t.v[2].copy(alpha = 0.4f), 80.dp,
        onSlide = { val n = (it * 100).toInt(); if (n != v) { v = n; Device.setVolume(n) } },
        onRelease = {}, onOpen = onOpen)
}

/** Ventanita con barras por pantalla o por tipo de sonido. */
@Composable
private fun PopupFrame(title: String, hint: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTok.current
    Column(Modifier.width(620.dp).card(t, null, 36.dp).tap { }.padding(horizontal = 28.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(title, 26.sp, FontWeight.Black, modifier = Modifier.weight(1f))
            Pill("✕", false, Modifier.width(52.dp), 52.dp, 20.sp, onClose)
        }
        T(hint, 16.sp, FontWeight.Bold, t.mu, maxLines = 2)
        content()
    }
}

@Composable
private fun PopBar(name: String, value: Int, fill: Color, onSlide: (Float) -> Unit, onRelease: () -> Unit) {
    Column(Modifier.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VBar("$value", value / 100f, fill, Modifier.fillMaxWidth().height(300.dp), onSlide, onRelease)
        T(name, 16.sp, FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun BrightPopup(onClose: () -> Unit) {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    var top by remember { mutableIntStateOf(Device.topBrightness()) }
    var bottom by remember { mutableIntStateOf(Device.bottomPct) }
    PopupFrame("Brillo por pantalla", "La barra del costado mueve " + (if (s.brightLinked) "las dos pantallas juntas." else "solo la pantalla de arriba."), onClose) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally)) {
            PopBar("Pantalla arriba", top, t.v[4].copy(alpha = 0.45f), { top = (it * 100).toInt().coerceIn(1, 100) }) { Device.setTopBrightness(top) }
            PopBar("Pantalla abajo", bottom, t.v[4].copy(alpha = 0.45f), { bottom = (it * 100).toInt().coerceIn(1, 100) }) { Device.setBottomBrightness(bottom) }
        }
        Pill(if (s.brightLinked) "Mover juntas: sí" else "Mover juntas: no", s.brightLinked, Modifier.fillMaxWidth(), 54.dp, 16.sp) {
            Store.update { it.copy(brightLinked = !it.brightLinked) }
        }
    }
}

@Composable
private fun VolumePopup(onClose: () -> Unit) {
    val t = LocalTok.current
    val streams = listOf("Multimedia" to AudioManager.STREAM_MUSIC, "Notificaciones" to AudioManager.STREAM_NOTIFICATION, "Sistema" to AudioManager.STREAM_SYSTEM)
    PopupFrame("Volumen por tipo de sonido", "La consola tiene un solo parlante: aquí ajustas cada tipo de sonido por separado.", onClose) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally)) {
            streams.forEachIndexed { i, (name, st) ->
                var v by remember { mutableIntStateOf(Device.volume(st)) }
                PopBar(name, v, t.v[listOf(2, 3, 1)[i]].copy(alpha = 0.45f), { val n = (it * 100).toInt(); if (n != v) { v = n; Device.setVolume(n, st) } }) {}
            }
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
    val fanNow by Store.fanNow.collectAsState()
    val c = tele.socC ?: 0f
    val color by animateColorAsState(tempColor(c), tween(600), label = "temp")
    val shape = RoundedCornerShape(36.dp)
    val fanText = (tele.fanRpm?.let { "$it rpm · " } ?: "") + if (s.fan == "Inteligente" && fanNow.isNotBlank()) "Inteligente ($fanNow)" else s.fan
    Column(
        modifier.card(t, null, 36.dp).background(color.copy(alpha = 0.10f), shape).border(1.5.dp, color.copy(alpha = 0.55f), shape)
            .tap { Store.nav { it.copy(sec = "hw", hwTab = "fan") } }.padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Ring(deg(tele.socC), "chip", c / 90f, color, 196.dp, 13.dp, 58.sp, 17.sp)
        Box(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(t.track).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            T("●  " + tempState(c), 15.sp, FontWeight.Black, color, maxLines = 1)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Batería" to (tele.batteryC?.let { "${it.toInt()} °C" } ?: "—"),
                "Ventilador" to fanText,
                "Máxima hoy" to (if (tele.maxTodayC > 0) "${tele.maxTodayC.toInt()} °C" else "—"),
            ).forEach { (k, v) ->
                Row { T(k, 16.sp, FontWeight.Bold, t.mu, Modifier.weight(1f)); T(v, 16.sp, FontWeight.Black, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ConsolaCard(modifier: Modifier) {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    Column(modifier.card(t, null, 36.dp).padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.SpaceBetween) {
        T("Consola", 22.sp, FontWeight.Black)
        listOf(
            Triple("Pantallas", listOf("Dos", "Arriba", "Abajo"), s.mode) to { o: String -> Device.setScreens(o) },
            Triple("Refresco", listOf("60 Hz", "120 Hz"), s.hz) to { o: String -> Device.setHz(o) },
            Triple("Rendimiento", listOf("Ahorro", "Equilibrado", "Rendimiento"), s.perf) to { o: String -> Device.setPerf(o) },
            Triple("Ventilador", listOf("Silencio", "Inteligente", "Máx"), s.fan) to { o: String -> Device.setFan(o) },
        ).forEach { (row, pick) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T(row.first, 15.sp, FontWeight.ExtraBold, t.mu)
                SegRow(row.second, row.third, Modifier.fillMaxWidth(), 48.dp, 15.sp, pick)
            }
        }
    }
}

@Composable
private fun CtCard(modifier: Modifier) {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val tele by Store.tele.collectAsState()
    // Los anillos muestran el tope REAL que tiene la consola ahora (no lo que se pidió)
    val wantP = s.caps["p"] ?: Freq.STOCK.getValue("p"); val wantG = s.caps["gpu"] ?: Freq.STOCK.getValue("gpu")
    val p = tele.cpuMax["7"] ?: wantP
    val g = tele.gpuMaxHz?.toInt()?.takeIf { it > 0 } ?: wantG
    val applied = tele.cpuMax["7"] == null || kotlin.math.abs(p - wantP) < 120_000
    val cts by com.nikalbazu.ids.core.ClusterTune.state.collectAsState()
    val linked = Device.ctLinked(s)
    val own = !linked
    Column(modifier.card(t, 3, 36.dp).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T("ClusterTune · perfil activo", 16.sp, FontWeight.ExtraBold, t.mu, maxLines = 1)
                T(if (linked) cts.profile ?: "ClusterTune" else s.ct, 34.sp, FontWeight.Black, maxLines = 1)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (linked) {
                    Pill("Elegir perfil", true, Modifier.width(288.dp), 42.dp, 16.sp) { Device.clusterTune("picker") }
                    Pill("Auto Tune · ajuste rápido", false, Modifier.width(288.dp), 42.dp, 16.sp) { Device.clusterTune("tuner") }
                } else listOf(listOf("Stock", "Batería"), listOf("Silencioso", "Equilibrado")).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { n -> Pill(n, s.ct == n, Modifier.width(140.dp), 42.dp, 16.sp) { Device.applyCaps(n) } }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Más ajustes ›", true, Modifier.width(200.dp), 42.dp, 15.sp) { if (linked) Device.openClusterTune() else Store.nav { it.copy(sec = "ct") } }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Ring(ghz(p), "GHz · CPU", p / 3187200f, t.v[3], 100.dp, 11.dp, 22.sp, 11.sp)
                    Ring("${g / 1_000_000}", "MHz · GPU", g / 680_000_000f, t.v[2], 100.dp, 11.dp, 22.sp, 11.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(19.dp)).background(t.track).tap { if (linked) Device.clusterTune("tuner") },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val atOn = if (linked) cts.autoTune else s.autoTune
            T("●  ", 15.sp, FontWeight.Black, if (atOn) t.v[1] else t.mu)
            T(if (!applied && own) "Aviso · la consola tiene otro tope (lo cambió AYN o la app ClusterTune)" else "Auto Tune · " + when {
                linked -> if (cts.autoTune) "activo en ClusterTune" else "apagado en ClusterTune"
                s.autoTune -> "activo, ajusta los topes según el juego"
                else -> "apagado"
            }, 15.sp, FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

/** Accesos con ícono: Direct power supply, Force landscape, Auto lock y Clean memory. */
@Composable
private fun AccessRow() {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val community by com.nikalbazu.ids.core.Community.buttons.collectAsState()
    val cleaned by Device.cleaned.collectAsState()
    val states by Device.states.collectAsState()
    LaunchedEffect(Unit) { Device.refreshStates() }
    val items = listOf(
        listOf("Direct power supply", Ico.POWER, "Usa el cargador, no la batería", 4),
        listOf("Force landscape", Ico.LAND, "Pantalla siempre acostada", 2),
        listOf("Auto lock", Ico.AIM, "Para juegos de disparo", 0),
        listOf("Clean memory", Ico.RAM, "Cierra apps de fondo", 1),
    )
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (nameA, icoA, hintA, colA) ->
            val name = nameA as String; val ico = icoA as String; val col = t.v[(colA as Int) % t.v.size]
            val action = name == "Clean memory"
            val on = !action && (states[name] ?: false)
            val needsLearn = name in Device.STOCK_BUTTONS && name !in com.nikalbazu.ids.core.Community.all()
            val hint = when {
                action -> cleaned.ifBlank { hintA as String }
                needsLearn -> "Enséñalo una vez arriba"
                on -> "Activado"
                else -> hintA as String
            }
            val shape = RoundedCornerShape(32.dp)
            val base = if (on) Modifier.shadow(12.dp, shape, ambientColor = t.sel.copy(alpha = 0.4f), spotColor = t.sel.copy(alpha = 0.5f)).background(t.sel, shape)
                else Modifier.background(t.track, shape)
            Row(Modifier.weight(1f).fillMaxHeight().then(base).clip(shape).tap { Device.toggle(name) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).background(if (on) Color.Black.copy(alpha = 0.12f) else col.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center) { Icon(ico, if (on) t.selTx else col, 24.dp) }
                Column(Modifier.weight(1f)) {
                    T(name, 15.sp, FontWeight.Black, if (on) t.selTx else t.tx, maxLines = 1)
                    T(hint, 12.sp, FontWeight.Bold, if (on) t.selTx.copy(alpha = 0.75f) else t.mu, maxLines = 1)
                }
            }
        }
    }
}

/** Franja horizontal pequeña: CPU, GPU, RAM y consumo. */
@Composable
private fun StatsStrip() {
    val t = LocalTok.current
    val tele by Store.tele.collectAsState()
    val list = stats(tele)
    Row(Modifier.fillMaxWidth().height(92.dp).card(t, null, 30.dp).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        list.forEach { st ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ring("", "", st.fraction, t.v[st.color], 62.dp, 8.dp, track = t.track)
                Column {
                    T(st.label, 14.sp, FontWeight.ExtraBold, t.mu, maxLines = 1)
                    Row(verticalAlignment = Alignment.Bottom) {
                        T(st.value, 22.sp, FontWeight.Black, num = true, maxLines = 1)
                        T(" " + st.unit, 14.sp, FontWeight.ExtraBold, t.mu, maxLines = 1)
                    }
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
    val states by Device.states.collectAsState()
    Row(modifier.card(t).padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Wi‑Fi", "Bluetooth", "No molestar", "Carga 80 %", "Ratón virtual", "Captura").forEach { name ->
            val on = states[name] ?: false
            Column(Modifier.weight(1f).tap { Device.toggle(name) }, horizontalAlignment = Alignment.CenterHorizontally,
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
        val community by com.nikalbazu.ids.core.Community.buttons.collectAsState()
        val learned = community.keys + s.learned.keys
        val names = listOf("Wi‑Fi", "Bluetooth", "No molestar", "Captura") + Device.AYN_DIRECT.keys +
            Device.STOCK_BUTTONS.take(3) + Device.STOCK_BUTTONS.drop(3).filter { it in learned } +
            listOf("Clean memory", "Carga 80 %", "Ratón virtual")
        val states by Device.states.collectAsState()
        LaunchedEffect(Unit) { Device.refreshStates() }
        Grid(4, names.size) { i ->
            val n = names[i]
            val on = states[n] ?: false
            val hint = when {
                n in Device.AYN_DIRECT -> "Ajuste de AYN"
                n in learned -> "Botón de AYN"
                n in Device.STOCK_BUTTONS -> "Sin aprender"
                else -> ""
            }
            ToggleCard(n, hint, on, Modifier.weight(1f)) { Device.toggle(n) }
        }
        T("Los botones de AYN se enseñan una vez en la pantalla de arriba (Aprender botones de AYN) y desde ahí funcionan aquí.", 17.sp, FontWeight.Bold, t.mu)
    }
}
