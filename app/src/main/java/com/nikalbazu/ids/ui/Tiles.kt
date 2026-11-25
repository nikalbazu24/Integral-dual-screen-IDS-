package com.nikalbazu.ids.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikalbazu.ids.core.Freq
import com.nikalbazu.ids.core.Grid as G
import com.nikalbazu.ids.core.Store
import com.nikalbazu.ids.core.Tile
import com.nikalbazu.ids.priv.Device
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

private val GAP = 14.dp

/** Estado del arrastre: se lee solo al dibujar (no recompone todo el Inicio en cada movimiento del dedo). */
private class DragState {
    var id by mutableStateOf<String?>(null)
    var resize by mutableStateOf(false)
    var origin: Tile? = null
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)
    var target by mutableStateOf<Tile?>(null)
    var preview by mutableStateOf<List<Tile>?>(null)
}

/** Inicio hecho de tarjetas que se mueven, cambian de tamaño, se quitan y se agregan. */
@Composable
fun TileBoard(onPop: (String) -> Unit) {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val editing by Store.editing.collectAsState()
    val tray by Store.tray.collectAsState()
    val drag = remember { DragState() }
    val dens = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gapPx = with(dens) { GAP.toPx() }
        val cw = (constraints.maxWidth - gapPx * (G.COLS - 1)) / G.COLS
        val ch = (constraints.maxHeight - gapPx * (G.ROWS - 1)) / G.ROWS
        fun pxOf(c: Tile) = IntOffset((c.x * (cw + gapPx)).roundToInt(), (c.y * (ch + gapPx)).roundToInt())
        fun sizeOf(c: Tile) = IntSize((c.w * cw + (c.w - 1) * gapPx).roundToInt(), (c.h * ch + (c.h - 1) * gapPx).roundToInt())

        // Cuadrícula de fondo mientras se edita
        if (editing) Canvas(Modifier.fillMaxSize()) {
            for (y in 0 until G.ROWS) for (x in 0 until G.COLS)
                drawRoundRect(Color.White.copy(alpha = 0.035f), Offset(x * (cw + gapPx), y * (ch + gapPx)), Size(cw, ch), CornerRadius(20f, 20f))
        }
        // Dónde va a quedar la tarjeta que se arrastra
        drag.target?.let { tg ->
            val ghost = drag.preview?.firstOrNull { it.id == tg.id } ?: tg
            val p = pxOf(ghost); val z = sizeOf(ghost)
            val ok = drag.preview != null
            Box(Modifier.offset { p }.size(with(dens) { z.width.toDp() }, with(dens) { z.height.toDp() })
                .border(2.dp, if (ok) t.tx.copy(alpha = 0.6f) else t.v[0], RoundedCornerShape(32.dp))
                .background((if (ok) t.tx else t.v[0]).copy(alpha = 0.06f), RoundedCornerShape(32.dp)))
        }

        val tiles = drag.preview ?: s.layout
        tiles.forEach { tile ->
            key(tile.id) {
                TileBox(tile, pxOf(tile), sizeOf(tile), editing, drag, cw, ch, gapPx, onPop)
            }
        }

        // Bandeja para agregar tarjetas
        AnimatedVisibility(tray, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).tap { Store.tray.value = false })
        }
        AnimatedVisibility(tray, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it }, exit = slideOutVertically(tween(200)) { it }) {
            TrayPanel(s.layout)
        }
    }
}

@Composable
private fun TileBox(tile: Tile, pos: IntOffset, size: IntSize, editing: Boolean, drag: DragState, cw: Float, ch: Float, gapPx: Float, onPop: (String) -> Unit) {
    val t = LocalTok.current
    val dens = LocalDensity.current
    val info = G.INFO[tile.id] ?: return
    val col = t.v[info.color % t.v.size]
    val off = remember { Animatable(pos, IntOffset.VectorConverter) }
    val sz = remember { Animatable(size, IntSize.VectorConverter) }
    val isDragged = drag.id == tile.id
    var fresh by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Mientras se arrastra, sigue al dedo; al soltar, se desliza suave a su lugar
    LaunchedEffect(isDragged) {
        if (isDragged) snapshotFlow { drag.dx to drag.dy }.collect { (dx, dy) ->
            val o = drag.origin ?: return@collect
            val op = IntOffset((o.x * (cw + gapPx)).roundToInt(), (o.y * (ch + gapPx)).roundToInt())
            val os = IntSize((o.w * cw + (o.w - 1) * gapPx).roundToInt(), (o.h * ch + (o.h - 1) * gapPx).roundToInt())
            if (drag.resize) sz.snapTo(IntSize((os.width + dx).roundToInt().coerceAtLeast(cw.roundToInt()), (os.height + dy).roundToInt().coerceAtLeast((ch * 0.6f).roundToInt())))
            else off.snapTo(IntOffset(op.x + dx.roundToInt(), op.y + dy.roundToInt()))
        }
    }
    LaunchedEffect(pos, size, isDragged) {
        if (!isDragged) {
            launch { off.animateTo(pos, tween(240, easing = FastOutSlowInEasing)) }
            launch { sz.animateTo(size, tween(240, easing = FastOutSlowInEasing)) }
        }
    }
    LaunchedEffect(Unit) { fresh = false }

    fun cellFromDrag(o: Tile): Tile {
        val nx = (drag.dx / (cw + gapPx)); val ny = (drag.dy / (ch + gapPx))
        return if (drag.resize) o.copy(w = (o.w + nx).roundToInt().coerceIn(info.minW, G.COLS - o.x), h = (o.h + ny).roundToInt().coerceIn(info.minH, G.ROWS - o.y))
        else o.copy(x = (o.x + nx).roundToInt().coerceIn(0, G.COLS - o.w), y = (o.y + ny).roundToInt().coerceIn(0, G.ROWS - o.h))
    }
    fun start(resize: Boolean) {
        drag.origin = Store.settings.value.layout.firstOrNull { it.id == tile.id } ?: tile
        drag.resize = resize; drag.dx = 0f; drag.dy = 0f; drag.target = null; drag.preview = null
        drag.id = tile.id
    }
    fun moveBy(x: Float, y: Float) {
        drag.dx += x; drag.dy += y
        val o = drag.origin ?: return
        val tg = cellFromDrag(o)
        if (tg != drag.target) {
            drag.target = tg
            drag.preview = G.fit(Store.settings.value.layout.map { if (it.id == o.id) tg else it }, o.id)
        }
    }
    fun end() {
        val p = drag.preview
        if (p != null) Store.update { it.copy(layout = p) }
        else if (drag.target != null) Store.toast("No cabe ahí: prueba otro lugar")
        drag.id = null; drag.target = null; drag.preview = null; drag.origin = null
    }

    val dragMod = Modifier.pointerInput(editing) {
        if (editing) detectDragGestures(onDragStart = { start(false) }, onDragEnd = { end() }, onDragCancel = { end() }) { c, d -> c.consume(); moveBy(d.x, d.y) }
    }
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .layout { m, _ ->
                val z = sz.value
                val p = m.measure(Constraints.fixed(z.width.coerceAtLeast(1), z.height.coerceAtLeast(1)))
                layout(z.width, z.height) { p.place(0, 0) }
            }
            .offset { off.value }
            .zIndex(if (isDragged) 10f else 0f)
            .graphicsLayer {
                val k = if (isDragged && !drag.resize) 1.03f else 1f
                scaleX = k; scaleY = k
            }
            .then(if (tile.id == "temp") Modifier.background(t.bg, shape) else Modifier)
            .card(t, null, 32.dp)
            .then(if (tile.id == "temp") Modifier.background(tempColor(Store.tele.value.socC ?: 0f).copy(alpha = 0.10f), shape) else Modifier)
            .border(if (editing) 1.5.dp else 0.dp, if (editing) col.copy(alpha = if (isDragged) 1f else 0.5f) else Color.Transparent, shape)
            .clip(shape),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            TileContent(tile.id, maxWidth, maxHeight, editing, col, onPop)
        }
        if (editing) {
            Box(Modifier.matchParentSize().then(dragMod))
            // Quitar
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(40.dp).clip(CircleShape).background(t.v[0])
                .tap { Store.update { st -> st.copy(layout = st.layout.filter { it.id != tile.id }) }; Store.toast(info.name + " quitada · puedes volver a agregarla") },
                contentAlignment = Alignment.Center) { T("−", 24.sp, FontWeight.Black, Color.Black) }
            // Girar
            val canRotate = tile.w != tile.h && tile.h >= info.minW && tile.w >= info.minH
            if (canRotate) Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(44.dp).clip(CircleShape).background(t.track)
                .border(1.dp, t.tx.copy(alpha = 0.15f), CircleShape)
                .tap {
                    val r = tile.copy(w = tile.h, h = tile.w).let { it.copy(x = it.x.coerceAtMost(G.COLS - it.w), y = it.y.coerceAtMost(G.ROWS - it.h)) }
                    val p = G.fit(Store.settings.value.layout.map { if (it.id == tile.id) r else it }, tile.id)
                    if (p != null) Store.update { it.copy(layout = p) } else Store.toast("No hay espacio para girarla aquí")
                }, contentAlignment = Alignment.Center) { Icon(Ico.ROTATE, t.tx, 22.dp) }
            // Cambiar tamaño
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(44.dp).clip(CircleShape).background(t.tx)
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { start(true) }, onDragEnd = { end() }, onDragCancel = { end() }) { c, d -> c.consume(); moveBy(d.x, d.y) }
                }, contentAlignment = Alignment.Center) { Icon(Ico.RESIZE, t.bg, 22.dp) }
        }
    }
}

@Composable
private fun TrayPanel(layout: List<Tile>) {
    val t = LocalTok.current
    val used = layout.map { it.id }.toSet()
    val free = G.INFO.filterKeys { it !in used }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)).background(t.surf).tap { }
        .padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Agregar tarjeta", 28.sp, FontWeight.Black, modifier = Modifier.weight(1f))
            Pill("✕", false, Modifier.width(52.dp), 52.dp, 20.sp) { Store.tray.value = false }
        }
        if (free.isEmpty()) T("Ya están todas las tarjetas en el Inicio.", 18.sp, FontWeight.Bold, t.mu)
        else T("Toca una para ponerla en el primer espacio libre. Después la mueves y cambias su tamaño.", 16.sp, FontWeight.Bold, t.mu)
        val list = free.entries.toList()
        Grid(4, list.size, 12.dp) { i ->
            val (id, info) = list[i]
            val c = t.v[info.color % t.v.size]
            Row(Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(28.dp)).background(t.track)
                .tap {
                    val p = G.add(Store.settings.value.layout, id)
                    if (p != null) { Store.update { it.copy(layout = p) }; Store.tray.value = false; Store.toast(info.name + " agregada") }
                    else Store.toast("No hay espacio libre: quita otra tarjeta")
                }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.copy(alpha = 0.2f)).border(2.dp, c, CircleShape))
                Column { T(info.name, 18.sp, FontWeight.Black, maxLines = 1); T(info.hint, 13.sp, FontWeight.Bold, t.mu, maxLines = 1) }
            }
        }
    }
}

// ------------------------------------------------------------------
// Contenido: se arma con el espacio real de la tarjeta, nada se corta.
// ------------------------------------------------------------------

private data class Row2(val k: String, val v: String)

@Composable
private fun TileContent(id: String, W0: Dp, H0: Dp, editing: Boolean, col: Color, onPop: (String) -> Unit) {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val tele by Store.tele.collectAsState()
    val info = G.INFO[id] ?: return
    val strip = H0 < 160.dp
    val padH = if (strip) 20.dp else 22.dp; val padV = if (strip) 14.dp else 18.dp
    val W = W0 - padH * 2; val H = H0 - padV * 2 - 36.dp
    val nav = { sec: String, hw: String? -> if (!editing) Store.nav { if (hw != null) it.copy(sec = sec, hwTab = hw) else it.copy(sec = sec) } }

    Column(Modifier.fillMaxSize().padding(horizontal = padH, vertical = padV), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var title = info.name
        var sub = ""
        var headerTap: (() -> Unit)? = null
        when (id) {
            "ct" -> title = "ClusterTune · " + (if (Device.ctLinked(s)) com.nikalbazu.ids.core.ClusterTune.state.collectAsState().value.profile ?: "—" else s.ct)
            "brillo", "volumen" -> { if (W0 < 120.dp) title = if (id == "brillo") "Brillo" else "Vol."; sub = if (W0 >= 260.dp) "Por pantalla ›" else ""; headerTap = { if (!editing) onPop(if (id == "brillo") "brillo" else "volumen") } }
            "juego" -> title = Store.foreground.collectAsState().value?.let { Device.label(it) } ?: "Ningún juego abierto"
            "reloj" -> title = ""
        }
        if (title.isNotEmpty() || editing) Row(Modifier.height(26.dp).padding(end = if (editing) 44.dp else 0.dp).then(if (headerTap != null) Modifier.tap(headerTap) else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(col))
            T(title, if (strip) 17.sp else 20.sp, FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1)
            if (sub.isNotEmpty() && !editing) T(sub, 14.sp, FontWeight.ExtraBold, t.mu, maxLines = 1)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (id) {
                "temp" -> {
                    val c = tele.socC ?: 0f
                    val fanNow by Store.fanNow.collectAsState()
                    RingRows(deg(tele.socC), "chip", c / 90f, tempColor(c), listOf(
                        Row2("Batería", tele.batteryC?.let { "${it.toInt()} °C" } ?: "—"),
                        Row2("Ventilador", if (s.fan == "Inteligente" && fanNow.isNotBlank()) "Intel. ($fanNow)" else s.fan),
                        Row2("Máxima hoy", if (tele.maxTodayC > 0) "${tele.maxTodayC.toInt()} °C" else "—")), W, H) { nav("hw", "fan") }
                }
                "consola" -> ConsolaBody(W, H)
                "ct" -> CtBody(W, H, editing)
                "stats" -> StatsBody(W, H)
                "brillo" -> {
                    var v by remember { mutableIntStateOf(Device.topBrightness()) }
                    Slide(v, t.v[4].copy(alpha = 0.4f), H0 > W0 * 1.2f, { v = it }) {
                        Device.setTopBrightness(v); if (Store.settings.value.brightLinked) Device.setBottomBrightness(v)
                    }
                }
                "volumen" -> {
                    var v by remember { mutableIntStateOf(Device.volume()) }
                    Slide(v, t.v[2].copy(alpha = 0.4f), H0 > W0 * 1.2f, { if (it != v) { v = it; Device.setVolume(it) } }) {}
                }
                "accesos" -> AccessBody(W, H)
                "luces" -> RingRows("${s.ledBright}%", "intensidad", s.ledBright / 100f, Color(if (s.effect == "Apagadas") 0xFF55535C else s.ledL),
                    listOf(Row2("Efecto", s.effect), Row2("Color", if (s.ledSame) "Los dos iguales" else "Distinto")), W, H) { nav("hw", "led") }
                "fan" -> {
                    val fanNow by Store.fanNow.collectAsState()
                    RingRows(tele.fanRpm?.toString() ?: "—", "rpm", (tele.fanRpm ?: 0) / 6000f, col,
                        listOf(Row2("Modo", if (s.fan == "Inteligente" && fanNow.isNotBlank()) "Intel. ($fanNow)" else s.fan), Row2("Chip", deg(tele.socC))), W, H) { nav("hw", "fan") }
                }
                "bateria" -> RingRows("${tele.batteryPct}%", if (tele.charging) "cargando" else "batería", tele.batteryPct / 100f, if (tele.batteryPct <= 20) t.v[0] else t.v[1],
                    listOf(Row2("Quedan", tele.minutesLeft?.let { "${it / 60} h ${it % 60} min" } ?: "—"), Row2("Temperatura", tele.batteryC?.let { "${it.toInt()} °C" } ?: "—")), W, H) { nav("hw", "bat") }
                "reloj" -> {
                    var now by remember { mutableStateOf(java.util.Date()) }
                    LaunchedEffect(Unit) { while (true) { now = java.util.Date(); kotlinx.coroutines.delay(10_000) } }
                    val size = minOf(W.value / 3f, (H0.value - padV.value * 2) * 0.8f, 150f).coerceAtLeast(22f)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        T(java.text.SimpleDateFormat("HH:mm", java.util.Locale("es", "CL")).format(now), size.sp, FontWeight.Black, num = true, maxLines = 1)
                    }
                }
                "musica" -> {
                    val ctx = LocalContext.current
                    val all = listOf("«" to KeyEvent.KEYCODE_MEDIA_PREVIOUS, "II" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "»" to KeyEvent.KEYCODE_MEDIA_NEXT)
                    val list = if (W < 190.dp) listOf(all[1]) else all
                    ChipFill(list.size, W, H, 56.dp, prefer = listOf(list.size to 1, 1 to list.size)) { i, _ ->
                        Pill(list[i].first, list[i].first == "II", Modifier.fillMaxSize(), height = Dp.Unspecified, size = 22.sp) { mediaKey(ctx, list[i].second) }
                    }
                }
                "juego" -> {
                    val items = listOf("Cerrar juego", "Ajustes del juego")
                    ChipFill(2, W, H, 130.dp) { i, _ ->
                        Pill(items[i], i == 1, Modifier.fillMaxSize(), height = Dp.Unspecified, size = 16.sp) {
                            if (i == 0) Device.closeGame() else nav("juegos", null)
                        }
                    }
                }
            }
        }
    }
}

/** Anillo con filas de datos al lado o debajo, según quepa. */
@Composable
private fun RingRows(value: String, unit: String, fraction: Float, color: Color, rows: List<Row2>, W: Dp, H: Dp, onTap: () -> Unit) {
    val t = LocalTok.current
    val w = W.value; val h = H.value
    var ring: Float; var row = true; var n: Int
    if (h < 100) {
        ring = minOf(h, 58f); n = minOf(rows.size, floor((h + 4) / 26).toInt().coerceAtLeast(0)); if (w - ring - 16 < 130) n = 0
    } else {
        val rRow = minOf(h, 200f, w - 16 - 150)
        if (rRow >= 90) { ring = rRow; n = minOf(rows.size, floor((h + 6) / 28).toInt()) }
        else {
            row = false; ring = minOf(w, 200f, h - 26)
            n = minOf(rows.size, floor((h - ring - 8) / 26).toInt().coerceAtLeast(0))
            if (ring > h * 0.75f && rows.isNotEmpty()) { ring = minOf(w, floor(h * 0.7f)); n = minOf(rows.size, floor((h - ring - 8) / 26).toInt().coerceAtLeast(0)) }
        }
    }
    val showRing = ring >= 44
    val vSize = (ring * 0.27f).coerceAtLeast(15f).sp
    val ringView = @Composable {
        if (showRing) Ring(value, if (ring >= 80) unit else "", fraction, color, ring.dp, (ring / 14f).coerceIn(6f, 14f).dp, vSize, 13.sp)
        else T(value, 26.sp, FontWeight.Black, color, num = true, maxLines = 1)
    }
    val rowsView = @Composable { mod: Modifier ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            rows.take(n).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(r.k, 16.sp, FontWeight.Bold, t.mu, maxLines = 1); Spacer(Modifier.weight(1f)); T(r.v, 16.sp, FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
    if (row) Row(Modifier.fillMaxSize().tap(onTap), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        ringView(); if (n > 0) rowsView(Modifier.weight(1f))
    } else Column(Modifier.fillMaxSize().tap(onTap), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        ringView(); if (n > 0) rowsView(Modifier.fillMaxWidth())
    }
}

/**
 * Reparte n botones para llenar todo el espacio: prueba varias grillas (4×1, 2×2, 1×4…)
 * y usa la que deja botones más grandes y cómodos.
 */
@Composable
private fun ChipFill(n: Int, W: Dp, H: Dp, minW: Dp, prefer: List<Pair<Int, Int>>? = null, cell: @Composable (Int, Dp) -> Unit) {
    if (n <= 0) return
    val cands = prefer ?: listOf(4 to 1, 2 to 2, 1 to 4, 3 to 2, 2 to 1, 1 to 2, 3 to 1, 1 to 3, 1 to 1)
    var best: Triple<Int, Int, Float>? = null; var bestScore = -1f; var bestN = 1
    for ((cols, rows) in cands) {
        val k = minOf(n, cols * rows)
        if (cols > k && rows == 1) continue
        val cw = (W.value - (cols - 1) * 10) / cols; val chh = (H.value - (rows - 1) * 10) / rows
        if (cw < minW.value || chh < 40) continue
        val score = k * 1000 + minOf(cw / 2.4f, chh)
        if (score > bestScore) { bestScore = score; best = Triple(cols, rows, chh); bestN = k }
    }
    val (cols, rows, chh) = best ?: Triple(1, 1, H.value.coerceAtLeast(40f))
    val rowsUsed = (bestN + cols - 1) / cols
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (r in 0 until rowsUsed) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in 0 until cols) {
                val i = r * cols + c
                Box(Modifier.weight(1f).fillMaxHeight()) { if (i < bestN) cell(i, chh.dp) }
            }
        }
    }
}

@Composable
private fun ConsolaBody(W: Dp, H: Dp) {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val all = listOf(
        Triple("Pantallas", listOf("Dos", "Arriba", "Abajo"), s.mode) to { o: String -> Device.setScreens(o) },
        Triple("Refresco", listOf("60 Hz", "120 Hz"), s.hz) to { o: String -> Device.setHz(o) },
        Triple("Rendimiento", listOf("Ahorro", "Equilibrado", "Rendimiento"), s.perf) to { o: String -> Device.setPerf(o) },
        Triple("Ventilador", listOf("Silencio", "Inteligente", "Máx"), s.fan) to { o: String -> Device.setFan(o) },
    )
    val n = ((H.value + 6) / 50).toInt().coerceIn(1, 4)
    val label = W.value >= 110 + 3 * 96
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        all.take(n).forEach { (row, pick) ->
            val (name, opts, cur) = row
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (label) T(name, 15.sp, FontWeight.ExtraBold, t.mu, Modifier.width(110.dp), maxLines = 1)
                val fit = (W.value - (if (label) 118 else 0)) / opts.size >= 86
                if (fit) opts.forEach { o -> Pill(o, o == cur, Modifier.weight(1f).fillMaxHeight(), Dp.Unspecified, 15.sp) { pick(o) } }
                else Pill((if (label) "" else "$name: ") + cur, true, Modifier.weight(1f).fillMaxHeight(), Dp.Unspecified, 15.sp) {
                    pick(opts[(opts.indexOf(cur) + 1) % opts.size])
                }
            }
        }
    }
}

@Composable
private fun CtBody(W: Dp, H: Dp, editing: Boolean) {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val tele by Store.tele.collectAsState()
    val cts by com.nikalbazu.ids.core.ClusterTune.state.collectAsState()
    val linked = Device.ctLinked(s)
    val p = tele.cpuMax["7"] ?: s.caps["p"] ?: Freq.STOCK.getValue("p")
    val g = tele.gpuMaxHz?.toInt()?.takeIf { it > 0 } ?: s.caps["gpu"] ?: Freq.STOCK.getValue("gpu")
    val at = if (linked) cts.autoTune else s.autoTune
    val names = if (linked) listOf("Elegir perfil", "Auto Tune") else listOf("Stock", "Batería", "Silencioso", "Equilibrado")
    val onChip = { i: Int -> if (linked) Device.clusterTune(if (i == 0) "picker" else "tuner") else Device.applyCaps(names[i]) }
    val sel = { i: Int -> if (linked) i == 1 && at else s.ct == names[i] }
    val rows = listOf(Row2("GPU", "${g / 1_000_000} MHz"), Row2("Auto Tune", if (at) "activo" else "apagado"))
    if (H.value < 100 && W.value >= 700) {
        // Franja ancha: anillo + botones en la misma línea
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ring(ghz(p), "", p / 3187200f, t.v[3], minOf(H.value, 58f).dp, 7.dp, 15.sp, 11.sp)
            names.forEachIndexed { i, nm -> Pill(nm, sel(i), Modifier.weight(1f).fillMaxHeight(), Dp.Unspecified, 15.sp) { if (!editing) onChip(i) } }
        }
        return
    }
    val chipH = if (H.value >= 150) 52f else 0f
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            RingRows(ghz(p), "GHz · CPU", p / 3187200f, t.v[3], rows, W, (H.value - chipH).dp) { if (!editing) { if (linked) Device.openClusterTune() else Store.nav { it.copy(sec = "ct") } } }
        }
        if (chipH > 0) Box(Modifier.fillMaxWidth().height(44.dp)) {
            ChipFill(names.size, W, 44.dp, 100.dp, prefer = listOf(names.size to 1, 2 to 1, 1 to 1)) { i, _ ->
                Pill(names[i], sel(i), Modifier.fillMaxSize(), Dp.Unspecified, 15.sp) { onChip(i) }
            }
        }
    }
}

@Composable
private fun StatsBody(W: Dp, H: Dp) {
    val t = LocalTok.current
    val tele by Store.tele.collectAsState()
    val list = stats(tele)
    val cols = ((W.value + 10) / 160).toInt().coerceAtLeast(1)
    val rows = ((H.value + 10) / 64).toInt().coerceAtLeast(1)
    val n = minOf(4, cols * rows); val c = minOf(cols, n); val r = (n + c - 1) / c
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        for (ri in 0 until r) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (ci in 0 until c) {
                val i = ri * c + ci
                Box(Modifier.weight(1f)) {
                    if (i < n) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val st = list[i]
                        Ring("", "", st.fraction, t.v[st.color], 54.dp, 7.dp, track = t.track)
                        Column {
                            T(st.label, 13.sp, FontWeight.ExtraBold, t.mu, maxLines = 1)
                            T("${st.value} ${st.unit}", 20.sp, FontWeight.Black, num = true, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessBody(W: Dp, H: Dp) {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val states by Device.states.collectAsState()
    val cleaned by Device.cleaned.collectAsState()
    LaunchedEffect(Unit) { Device.refreshStates() }
    val items = listOf(
        listOf("Direct power supply", Ico.POWER, "Usa el cargador, no la batería", 4),
        listOf("Force landscape", Ico.LAND, "Pantalla siempre acostada", 2),
        listOf("Auto lock", Ico.AIM, "Para juegos de disparo", 0),
        listOf("Clean memory", Ico.RAM, "Cierra apps de fondo", 1),
    )
    ChipFill(items.size, W, H, 120.dp) { i, cellH ->
        val name = items[i][0] as String; val ico = items[i][1] as String; val hint0 = items[i][2] as String
        val c = t.v[(items[i][3] as Int) % t.v.size]
        val action = name == "Clean memory"
        val on = !action && (states[name] ?: false)
        val needsLearn = name in Device.STOCK_BUTTONS && name !in com.nikalbazu.ids.core.Community.all()
        val hint = when { action -> cleaned.ifBlank { hint0 }; needsLearn -> "Enséñalo una vez arriba"; on -> "Activado"; else -> hint0 }
        val shape = RoundedCornerShape(30.dp)
        val big = cellH > 90.dp
        Row(Modifier.fillMaxSize().clip(shape).background(if (on) t.sel else t.track).tap { Device.toggle(name) }.padding(horizontal = if (big) 20.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ic = if (big) 56.dp else 40.dp
            Box(Modifier.size(ic).clip(CircleShape).background(if (on) Color.Black.copy(alpha = 0.12f) else c.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(ico, if (on) t.selTx else c, ic * 0.55f)
            }
            Column(Modifier.weight(1f)) {
                T(name, if (big) 20.sp else 15.sp, FontWeight.Black, if (on) t.selTx else t.tx, maxLines = 1)
                if (cellH > 52.dp) T(hint, if (big) 15.sp else 12.sp, FontWeight.Bold, if (on) t.selTx.copy(alpha = 0.75f) else t.mu, maxLines = 1)
            }
        }
    }
}

/** Barra que se desliza: vertical si la tarjeta es alta, horizontal si es ancha. */
@Composable
private fun Slide(value: Int, fill: Color, vertical: Boolean, onSlide: (Int) -> Unit, onRelease: () -> Unit) {
    val t = LocalTok.current
    val slide by rememberUpdatedState(onSlide); val release by rememberUpdatedState(onRelease)
    val shape = RoundedCornerShape(40.dp)
    Box(
        Modifier.fillMaxSize().clip(shape).background(t.track)
            .pointerInput(vertical) {
                if (vertical) androidx.compose.foundation.gestures.detectVerticalDragGestures(onDragEnd = { release() }, onDragCancel = { release() }) { c, _ ->
                    c.consume(); slide(((1f - c.position.y / size.height) * 100).roundToInt().coerceIn(1, 100))
                } else detectHorizontalDragGestures(onDragEnd = { release() }, onDragCancel = { release() }) { c, _ ->
                    c.consume(); slide(((c.position.x / size.width) * 100).roundToInt().coerceIn(1, 100))
                }
            }
            .pointerInput(vertical) {
                detectTapGestures { p ->
                    slide((if (vertical) (1f - p.y / size.height) * 100 else p.x / size.width * 100).roundToInt().coerceIn(1, 100)); release()
                }
            },
        contentAlignment = if (vertical) Alignment.BottomCenter else Alignment.CenterStart,
    ) {
        Box(if (vertical) Modifier.fillMaxWidth().fillMaxHeight(value / 100f).clip(shape).background(fill)
            else Modifier.fillMaxHeight().fillMaxWidth(value / 100f).clip(shape).background(fill))
        Box(Modifier.matchParentSize().padding(14.dp), contentAlignment = if (vertical) Alignment.BottomCenter else Alignment.Center) {
            T("$value", 26.sp, FontWeight.Black, num = true, maxLines = 1, align = TextAlign.Center)
        }
    }
}
