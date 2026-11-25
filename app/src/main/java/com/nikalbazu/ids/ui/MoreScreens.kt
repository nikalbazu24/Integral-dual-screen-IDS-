package com.nikalbazu.ids.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikalbazu.ids.core.Freq
import com.nikalbazu.ids.core.GameProfile
import com.nikalbazu.ids.core.Store
import com.nikalbazu.ids.panel.AppEntry
import com.nikalbazu.ids.panel.Apps
import com.nikalbazu.ids.panel.PanelService
import com.nikalbazu.ids.priv.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
private fun rememberApps(): List<AppEntry> {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { Apps.list(ctx) } }
    return apps
}

// ------------------------------------------------------------ ClusterTune
@Composable
fun ClusterTuneScreen() {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val nav by Store.nav.collectAsState()
    val tele by Store.tele.collectAsState()
    val apps = rememberApps()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Title("ClusterTune")
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(t.track).padding(horizontal = 16.dp, vertical = 9.dp)) {
                T(if (tele.helperAlive) "conectado · sin root" else "sin conexión", 16.sp, FontWeight.ExtraBold, if (tele.helperAlive) t.v[1] else t.v[0])
            }
            Spacer(Modifier.weight(1f))
            T("Controla", 16.sp, FontWeight.ExtraBold, t.mu)
            SegRow(listOf("Esta app", "ClusterTune"), s.ctOwner, Modifier.width(320.dp), 46.dp, 16.sp) { o -> Store.update { it.copy(ctOwner = o) } }
        }
        if (s.ctOwner == "ClusterTune") {
            Card(accent = 2) {
                T("La app ClusterTune está a cargo de los topes de CPU y GPU.", 20.sp, FontWeight.Black)
                T("Así las dos apps no pelean por lo mismo. Aquí solo se muestran los datos.", 16.sp, FontWeight.Bold, t.mu)
                Pill("Abrir ClusterTune", true, height = 52.dp) { Device.openClusterTune() }
            }
        }
        Tabs(listOf("perf" to "Perfiles", "games" to "Por juego", "auto" to "Auto Tune", "autom" to "Automatización", "log" to "Historial"), nav.ctTab) { id ->
            Store.nav { it.copy(ctTab = id) }
        }
        when (nav.ctTab) {
            "perf" -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Stock", "Batería", "Silencioso", "Equilibrado", "Personalizado").forEach { name ->
                        val on = s.ct == name
                        val shape = RoundedCornerShape(30.dp)
                        Column(Modifier.fillMaxWidth().height(88.dp).then(if (on) Modifier.background(t.sel, shape) else Modifier.card(t, null, 30.dp))
                            .clip(shape).tap { if (name == "Personalizado") Store.update { it.copy(ct = name) } else Device.applyCaps(name) }
                            .padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
                            T(name, 21.sp, FontWeight.Black, if (on) t.selTx else t.tx)
                            T(Freq.HINT[name] ?: "", 15.sp, FontWeight.Bold, if (on) t.selTx.copy(alpha = 0.7f) else t.mu)
                        }
                    }
                }
                Card(Modifier.weight(3f)) {
                    T(s.ct, 30.sp, FontWeight.Black)
                    T("Solo pone un tope a la velocidad máxima. No cambia el voltaje ni el gobernador.", 16.sp, FontWeight.Bold, t.mu)
                    listOf(Triple("s", "CPU pequeños · 3× A510", 2016000), Triple("g", "CPU medianos · 4× A715/A710", 2803200),
                        Triple("p", "CPU principal · 1× X3", 3187200), Triple("gpu", "GPU · Adreno 740", 680000000)).forEach { (k, label, max) ->
                        val v = s.caps[k] ?: max
                        val isGpu = k == "gpu"
                        val step = if (isGpu) 25_000_000 else 100_000
                        val text = if (isGpu) "$label · ${v / 1_000_000} MHz" else "$label · ${ghz(v)} GHz"
                        StepBar(text, v / max.toFloat(), t.v[3].copy(alpha = 0.4f), height = 56.dp,
                            onMinus = { Device.applyCaps("Personalizado", s.caps + (k to (v - step).coerceAtLeast(max / 4))) },
                            onPlus = { Device.applyCaps("Personalizado", s.caps + (k to (v + step).coerceAtMost(max))) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Importar", false, Modifier.weight(1f), 52.dp) { Store.toast("Importar perfiles llegará en la próxima versión") }
                        Pill("Abrir ClusterTune", false, Modifier.weight(1f), 52.dp) { Device.openClusterTune() }
                        Pill("Volver a Stock", true, Modifier.weight(1f), 52.dp) { Device.applyCaps("Stock") }
                    }
                }
            }
            "games" -> Card {
                T("El perfil cambia solo al abrir cada juego. Marca tus juegos como favoritos en la pestaña Juegos.", 17.sp, FontWeight.Bold, t.mu)
                val list = apps.filter { it.pkg in s.favorites || it.pkg in s.games.keys }
                if (list.isEmpty()) T("Aún no tienes juegos favoritos.", 19.sp, FontWeight.Black)
                list.forEach { app ->
                    val cur = s.games[app.pkg]?.ct ?: "Ninguno"
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(app.label, 20.sp, FontWeight.Black, modifier = Modifier.width(230.dp), maxLines = 1)
                        SegRow(listOf("Ninguno", "Stock", "Batería", "Silencioso", "Equilibrado"), cur, Modifier.weight(1f), 50.dp, 15.sp) { o ->
                            Store.update { st -> st.copy(games = st.games + (app.pkg to (st.games[app.pkg] ?: GameProfile()).copy(ct = o))) }
                        }
                    }
                }
            }
            "auto" -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ToggleCard("Auto Tune (experimental)", "Bajará los topes mientras el juego cumpla su meta de FPS. Nunca sube del perfil. La medición de FPS llega en la próxima versión.",
                    s.autoTune, height = 130.dp) { Store.update { it.copy(autoTune = !it.autoTune) } }
                Card {
                    T("Meta de FPS por juego", 17.sp, FontWeight.ExtraBold, t.mu)
                    apps.filter { it.pkg in s.favorites || it.pkg in s.games.keys }.forEach { app ->
                        val cur = (s.games[app.pkg]?.fps ?: 60).toString()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            T(app.label, 20.sp, FontWeight.Black, modifier = Modifier.width(300.dp), maxLines = 1)
                            SegRow(listOf("30", "45", "60", "90", "120"), cur, Modifier.weight(1f), 50.dp, 16.sp) { o ->
                                Store.update { st -> st.copy(games = st.games + (app.pkg to (st.games[app.pkg] ?: GameProfile()).copy(fps = o.toInt()))) }
                            }
                        }
                    }
                }
            }
            "autom" -> {
                val items = listOf(
                    Triple("boot", "Aplicar al encender", "Carga tu perfil después de reiniciar"),
                    Triple("sleep", "Perfil al dormir", "Usa Batería con la pantalla apagada"),
                    Triple("minf", "Proteger velocidad mínima", "No se tocan las velocidades mínimas"),
                    Triple("tile", "Acceso en ajustes rápidos", "Agrega el botón “Panel IDS” en la barra de Android"),
                    Triple("edge", "Gesto en el borde", "Próxima versión"),
                    Triple("hud", "Indicador sobre el juego", "Próxima versión"),
                )
                Grid(2, items.size) { i ->
                    val (k, name, hint) = items[i]
                    ToggleCard(name, hint, k in s.automation, Modifier.weight(1f), 120.dp) {
                        Store.update { st -> st.copy(automation = if (k in st.automation) st.automation - k else st.automation + k) }
                    }
                }
            }
            else -> {
                val hist by Store.history.collectAsState()
                Card {
                    T("Cambios automáticos", 17.sp, FontWeight.ExtraBold, t.mu)
                    if (hist.isEmpty()) T("Todavía no hay cambios registrados.", 19.sp, FontWeight.Black)
                    hist.take(12).forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            T(e.time, 18.sp, FontWeight.Bold, t.mu, Modifier.width(70.dp))
                            T(e.text, 19.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                            Box(Modifier.clip(RoundedCornerShape(17.dp)).background(t.track).padding(horizontal = 14.dp, vertical = 6.dp)) {
                                T(e.tag, 15.sp, FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ Hardware
@Composable
fun HardwareScreen() {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val nav by Store.nav.collectAsState()
    val tele by Store.tele.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Hardware")
        Tabs(listOf("led" to "Luces", "fan" to "Ventilador", "ctl" to "Controles", "bat" to "Batería"), nav.hwTab) { id -> Store.nav { it.copy(hwTab = id) } }
        when (nav.hwTab) {
            "led" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(Modifier.weight(2f).height(440.dp)) {
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("l" to "Stick izquierdo", "r" to "Stick derecho").forEach { (k, name) ->
                                val color = Color(if (k == "l") s.ledL else s.ledR)
                                val active = s.ledSame || nav.ledSide == k
                                Column(Modifier.tap { Store.nav { it.copy(ledSide = k) } }, horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Box(Modifier.size(150.dp).border(4.dp, if (active) t.sel else Color.Transparent, CircleShape).padding(8.dp)
                                        .border(12.dp, color, CircleShape).padding(12.dp).clip(CircleShape).background(t.track))
                                    T(name, 19.sp, FontWeight.Black)
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Pill((if (s.ledSame) "●  " else "○  ") + "Mismo color en los dos", s.ledSame, Modifier.fillMaxWidth(), 52.dp) {
                            Store.update { it.copy(ledSame = !it.ledSame, ledR = if (!it.ledSame) it.ledL else it.ledR) }
                        }
                    }
                    Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Card {
                            T("Color · " + if (s.ledSame) "los dos sticks" else if (nav.ledSide == "l") "stick izquierdo" else "stick derecho", 18.sp, FontWeight.ExtraBold, t.mu)
                            val colors = listOf(0xFF7CC4F0, 0xFFF59AB0, 0xFFFF8A65, 0xFFFFC44D, 0xFFB6E35A, 0xFF5FD4BD,
                                0xFF5B7CFF, 0xFFA77BFF, 0xFFFF4D4D, 0xFFFFFFFF, 0xFFFF9F1C, 0xFF3A3940)
                            Grid(6, colors.size, 12.dp) { i ->
                                val hex = colors[i]
                                val cur = if (nav.ledSide == "l" || s.ledSame) s.ledL else s.ledR
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(62.dp).border(3.dp, if (hex == cur) t.sel else Color.Transparent, CircleShape).padding(6.dp)
                                        .clip(CircleShape).background(Color(hex)).tap {
                                            Store.update { st ->
                                                if (st.ledSame) st.copy(ledL = hex, ledR = hex)
                                                else if (nav.ledSide == "l") st.copy(ledL = hex) else st.copy(ledR = hex)
                                            }
                                        })
                                }
                            }
                        }
                        Card {
                            T("Efecto", 18.sp, FontWeight.ExtraBold, t.mu)
                            val fx = listOf("Fijo", "Respirar", "Arcoíris", "Según batería", "Según temperatura", "Apagadas")
                            Grid(3, fx.size, 8.dp) { i -> Pill(fx[i], s.effect == fx[i], Modifier.weight(1f), 52.dp, 15.sp) { Store.update { it.copy(effect = fx[i]) } } }
                        }
                        StepBar("Brillo de las luces · ${s.ledBright}", s.ledBright / 100f, t.v[4].copy(alpha = 0.45f),
                            onMinus = { Store.update { it.copy(ledBright = (it.ledBright - 10).coerceAtLeast(0)) } },
                            onPlus = { Store.update { it.copy(ledBright = (it.ledBright + 10).coerceAtMost(100)) } })
                    }
                }
                T("Las luces se guardan, pero aún no se envían a los sticks: falta confirmar cómo las controla la Thor. Luces detectadas: " +
                    (tele.leds.joinToString().ifBlank { "ninguna todavía" }), 16.sp, FontWeight.Bold, t.mu)
            }
            "fan" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(Triple("Velocidad", tele.fanRpm?.toString() ?: "—", "rpm") to ((tele.fanRpm ?: 0) / 6000f),
                        Triple("Chip", deg(tele.socC), "°C") to ((tele.socC ?: 0f) / 90f),
                        Triple("Batería", deg(tele.batteryC), "°C") to ((tele.batteryC ?: 0f) / 60f)).forEachIndexed { i, (info, frac) ->
                        Row(Modifier.weight(1f).card(t, listOf(2, 4, 1)[i]).padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Ring(info.second, info.third, frac, t.v[listOf(2, 4, 1)[i]], 118.dp, 12.dp, 26.sp, 14.sp, t.track)
                            T(info.first, 21.sp, FontWeight.Black)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().card(t).padding(16.dp)) {
                    SegRow(listOf("Silencio", "Auto", "Máx", "Personalizado"), s.fan, height = 64.dp, size = 19.sp) { Device.setFan(it) }
                }
                Card {
                    val custom = s.fan == "Personalizado"
                    T("Curva del ventilador · " + if (custom) "toca + o − en cada temperatura" else "elige Personalizado para editarla", 18.sp, FontWeight.ExtraBold, t.mu)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf(40, 50, 60, 70, 80).forEachIndexed { i, temp ->
                            val v = s.curve.getOrElse(i) { 50 }
                            fun set(d: Int) = Store.update { st -> st.copy(fan = "Personalizado", curve = st.curve.toMutableList().also { it[i] = (it[i] + d).coerceIn(0, 100) }) }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("+", false, Modifier.fillMaxWidth(), 44.dp, 22.sp) { set(5) }
                                Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(26.dp)).background(t.track), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().fillMaxHeight(v / 100f).clip(RoundedCornerShape(26.dp)).background(t.v[2].copy(alpha = 0.45f)))
                                    T("$v %", 22.sp, FontWeight.Black, modifier = Modifier.padding(bottom = 12.dp), num = true)
                                }
                                Pill("−", false, Modifier.fillMaxWidth(), 44.dp, 22.sp) { set(-5) }
                                T("$temp °C", 18.sp, FontWeight.ExtraBold)
                            }
                        }
                    }
                    T("El modo se aplica en la consola. La curva propia se enviará cuando confirmemos dónde la guarda AYN.", 15.sp, FontWeight.Bold, t.mu)
                }
            }
            "ctl" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth().card(t).padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("Gatillos L2/R2", 20.sp, FontWeight.Black, modifier = Modifier.width(260.dp))
                    SegRow(listOf("Analógico", "Digital"), s.trig, Modifier.weight(1f), 56.dp) { o -> Store.update { it.copy(trig = o) } }
                }
                StepBar("Vibración · ${s.vib} %", s.vib / 100f, t.v[3].copy(alpha = 0.45f),
                    onMinus = { Store.update { it.copy(vib = (it.vib - 10).coerceAtLeast(0)) } },
                    onPlus = { Store.update { it.copy(vib = (it.vib + 10).coerceAtMost(100)) } })
                StepBar("Zona muerta de sticks · ${s.deadZone} %", s.deadZone / 25f, t.v[3].copy(alpha = 0.45f),
                    onMinus = { Store.update { it.copy(deadZone = (it.deadZone - 1).coerceAtLeast(0)) } },
                    onPlus = { Store.update { it.copy(deadZone = (it.deadZone + 1).coerceAtMost(25)) } })
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToggleCard("Intercambiar A/B y X/Y", "Distribución tipo Nintendo", "swapAB" in s.toggles, Modifier.weight(1f), 110.dp) { Device.toggle("swapAB") }
                    ToggleCard("Giroscopio como stick", "Para apuntar moviendo la consola", "gyro" in s.toggles, Modifier.weight(1f), 110.dp) { Device.toggle("gyro") }
                }
                T("Estos ajustes se guardan y se conectarán a la consola en próximas versiones.", 16.sp, FontWeight.Bold, t.mu)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth().card(t, 1).padding(horizontal = 26.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Ring("${tele.batteryPct}", "%", tele.batteryPct / 100f, t.v[1], 140.dp, 14.dp, 40.sp, 18.sp)
                    val left = tele.minutesLeft?.let { "${it / 60} h ${it % 60} min restantes" } ?: if (tele.charging) "Cargando" else "Calculando…"
                    T("$left · consumo ${tele.watts?.let { String.format(java.util.Locale("es"), "%.1f W", it) } ?: "—"} · ${deg(tele.batteryC)}C", 19.sp, FontWeight.Bold)
                }
                val items = listOf(
                    Triple("Carga 80 %", "Limitar carga al 80 %", "Cuida la batería si juegas enchufado"),
                    Triple("low", "Ahorro automático al 20 %", "Pasa a 60 Hz y perfil Batería"),
                    Triple("warn", "Aviso de batería baja", "En la pantalla de abajo, sin cortar el juego"),
                    Triple("night", "Carga nocturna", "Carga lento de noche para cuidar la batería"),
                )
                Grid(2, items.size) { i ->
                    val (k, name, hint) = items[i]
                    val on = if (k == "Carga 80 %") k in s.toggles else k in s.battery
                    ToggleCard(name, hint, on, Modifier.weight(1f), 120.dp) {
                        if (k == "Carga 80 %") Device.toggle(k)
                        else Store.update { st -> st.copy(battery = if (k in st.battery) st.battery - k else st.battery + k) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ Juegos
@Composable
fun JuegosScreen() {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val nav by Store.nav.collectAsState()
    val apps = rememberApps()
    val favs = apps.filter { it.pkg in s.favorites }
    val selected = apps.firstOrNull { it.pkg == nav.selectedGame } ?: favs.firstOrNull() ?: apps.firstOrNull()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Juegos")
        if (favs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            favs.take(8).forEach { a ->
                Column(Modifier.weight(1f).tap { Device.launch(a.pkg, 0) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(a, 88)
                    T(a.label, 15.sp, FontWeight.ExtraBold, maxLines = 1)
                }
            }
            repeat(8 - favs.take(8).size) { Spacer(Modifier.weight(1f)) }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(apps, key = { it.pkg }) { a ->
                    val on = a.pkg == selected?.pkg
                    val shape = RoundedCornerShape(26.dp)
                    Row(Modifier.fillMaxWidth().height(78.dp).then(if (on) Modifier.background(t.sel, shape) else Modifier.background(t.track, shape))
                        .clip(shape).tap { Store.nav { it.copy(selectedGame = a.pkg) } }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AppIcon(a, 52)
                        T(a.label, 19.sp, FontWeight.Black, if (on) t.selTx else t.tx, Modifier.weight(1f), maxLines = 1)
                        if (a.pkg in s.favorites) T("★", 22.sp, FontWeight.Black, if (on) t.selTx else t.v[4])
                    }
                }
            }
            if (selected != null) {
                val p = s.games[selected.pkg] ?: GameProfile()
                fun set(block: (GameProfile) -> GameProfile) = Store.update { st -> st.copy(games = st.games + (selected.pkg to block(st.games[selected.pkg] ?: GameProfile()))) }
                Card(Modifier.weight(3f).fillMaxHeight().verticalScroll(rememberScrollState()), gap = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AppIcon(selected, 64)
                        T(selected.label, 28.sp, FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Abrir arriba", true, Modifier.weight(1f), 50.dp) { Device.launch(selected.pkg, 0) }
                        Pill("Abrir abajo", false, Modifier.weight(1f), 50.dp) {
                            Device.launch(selected.pkg, PanelService.instance?.bottomDisplayId() ?: 0)
                        }
                        Pill(if (selected.pkg in s.favorites) "★ Favorito" else "☆ Favorito", selected.pkg in s.favorites, Modifier.weight(1f), 50.dp) {
                            Store.update { st -> st.copy(favorites = if (selected.pkg in st.favorites) st.favorites - selected.pkg else st.favorites + selected.pkg) }
                        }
                    }
                    T("Refresco", 16.sp, FontWeight.ExtraBold, t.mu)
                    SegRow(listOf("60 Hz", "120 Hz"), p.hz, height = 48.dp, size = 16.sp) { o -> set { it.copy(hz = o) } }
                    T("Rendimiento", 16.sp, FontWeight.ExtraBold, t.mu)
                    SegRow(listOf("Ahorro", "Equilibrado", "Rendimiento"), p.perf, height = 48.dp, size = 16.sp) { o -> set { it.copy(perf = o) } }
                    T("Ventilador", 16.sp, FontWeight.ExtraBold, t.mu)
                    SegRow(listOf("Silencio", "Auto", "Máx"), p.fan, height = 48.dp, size = 16.sp) { o -> set { it.copy(fan = o) } }
                    T("Pantalla de abajo al jugar", 16.sp, FontWeight.ExtraBold, t.mu)
                    SegRow(listOf("Inicio", "Notas", "Táctil"), p.pane, height = 48.dp, size = 16.sp) { o -> set { it.copy(pane = o) } }
                    T("CPU/GPU de este juego: ${p.ct} (se cambia en ClusterTune › Por juego)", 16.sp, FontWeight.Bold, t.mu)
                }
            }
        }
    }
}

@Composable
private fun AppIcon(a: AppEntry, size: Int) {
    val t = LocalTok.current
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(t.track), contentAlignment = Alignment.Center) {
        a.icon?.let { Image(it, contentDescription = a.label, modifier = Modifier.fillMaxSize()) } ?: T(a.label.take(1), (size / 2.4f).sp, FontWeight.Black)
    }
}

// ------------------------------------------------------------ Herramientas
@Composable
fun HerramientasScreen() {
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    val nav by Store.nav.collectAsState()
    val endsAt by Store.timerEndsAt.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) { while (endsAt > 0) { now = System.currentTimeMillis(); delay(1000) } }
    var notes by remember { mutableStateOf(s.notes) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Herramientas")
        val tools = listOf(
            Triple("Captura", "De la pantalla de arriba") { Device.screenshot() },
            Triple("Grabar 30 s", "Próxima versión") { Store.toast("Grabar llegará en la próxima versión") },
            Triple("Cerrar juego", "Sin salir al inicio") { Device.closeGame() },
            Triple("Liberar RAM", "Cierra apps de fondo") { Device.freeRam() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            tools.forEach { (name, hint, action) ->
                Column(Modifier.weight(1f).height(130.dp).card(t).tap(action).padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
                    T(name, 23.sp, FontWeight.Black); T(hint, 15.sp, FontWeight.Bold, t.mu)
                }
            }
        }
        Row(Modifier.height(250.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f).fillMaxHeight(), accent = 3) {
                T("Temporizador de sesión", 20.sp, FontWeight.ExtraBold)
                val remain = if (endsAt > 0) ((endsAt - now) / 1000).coerceAtLeast(0) else s.timerMin * 60L
                T(String.format("%02d:%02d:%02d", remain / 3600, (remain % 3600) / 60, remain % 60), 72.sp, FontWeight.Black, num = true, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { m ->
                        Pill(if (m < 60) "$m min" else "${m / 60} h", s.timerMin == m && endsAt == 0L, Modifier.weight(1f), 48.dp, 16.sp) {
                            Store.update { it.copy(timerMin = m) }
                        }
                    }
                    Pill(if (endsAt > 0) "Detener" else "Iniciar", true, Modifier.weight(1.2f), 48.dp, 16.sp) {
                        Store.timerEndsAt.value = if (endsAt > 0) 0 else System.currentTimeMillis() + s.timerMin * 60_000L
                    }
                }
            }
            Card(Modifier.weight(1f).fillMaxHeight(), accent = 2) {
                T("Panel táctil", 20.sp, FontWeight.ExtraBold)
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(t.track), contentAlignment = Alignment.Center) {
                    T("Mover el cursor de arriba llegará en la próxima versión", 18.sp, FontWeight.Bold, t.mu)
                }
            }
        }
        Card(accent = 4) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Notas rápidas", 20.sp, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Pill(if (nav.editingNotes) "Guardar" else "Editar", nav.editingNotes, height = 46.dp, size = 16.sp) {
                    val editing = !nav.editingNotes
                    if (!editing) Store.update { it.copy(notes = notes) }
                    Store.nav { it.copy(editingNotes = editing) }
                    PanelService.instance?.setFocusable(editing)
                }
            }
            BasicTextField(
                value = notes,
                onValueChange = { notes = it },
                readOnly = !nav.editingNotes,
                textStyle = TextStyle(color = t.tx, fontSize = 20.sp, fontFamily = t.font, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(t.tx),
                modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(24.dp)).background(t.track).padding(16.dp),
                decorationBox = { inner ->
                    if (notes.isEmpty()) T("Trucos, rutas, contraseñas de juego…", 20.sp, FontWeight.Bold, t.mu)
                    inner()
                },
            )
        }
    }
}

// ------------------------------------------------------------ Personalizar
@Composable
fun PersonalizarScreen() {
    val ctx = LocalContext.current
    val t = LocalTok.current
    val s by Store.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Personalizar")
        Card {
            T("Qué se muestra en Inicio", 20.sp, FontWeight.Black)
            val w = listOf("now" to "Juego actual", "temp" to "Temperatura", "consola" to "Consola", "ct" to "ClusterTune", "stats" to "Estado",
                "sliders" to "Brillo y volumen", "hw" to "Luces y ventilador", "quick" to "Accesos rápidos", "music" to "Música")
            Grid(3, w.size, 10.dp) { i ->
                val (k, name) = w[i]
                val on = k in s.widgets
                Pill((if (on) "●  " else "○  ") + name, on, Modifier.weight(1f), 72.dp) {
                    Store.update { st -> st.copy(widgets = if (k in st.widgets) st.widgets - k else st.widgets + k) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(Triple("Negro puro", "Ahorra batería · tarjetas con contorno y brillo", false),
                Triple("Aurora claro", "Tonos pastel del primer boceto", true)).forEach { (name, hint, light) ->
                val on = s.design == name
                val shape = RoundedCornerShape(32.dp)
                Row(Modifier.weight(1f).height(120.dp).clip(shape)
                    .background(if (light) Color(0xFFF4F1EC) else Color(0xFF111115), shape)
                    .border(if (on) 3.dp else 1.dp, if (on) t.sel else t.line, shape)
                    .tap { Store.update { it.copy(design = name) } }.padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val fg = if (light) Color(0xFF2B2A33) else Color(0xFFF4F1EC)
                    T(if (on) "●" else "○", 26.sp, FontWeight.Black, fg)
                    Column { T(name, 22.sp, FontWeight.Black, fg); T(hint, 15.sp, FontWeight.Bold, fg.copy(alpha = 0.7f)) }
                }
            }
        }
        Card {
            T("Letra", 20.sp, FontWeight.Black)
            val names = FONTS.keys.toList()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                names.forEach { n ->
                    val on = s.font == n
                    val fam = remember(n) { fontFamily(ctx, n) }
                    val shape = RoundedCornerShape(26.dp)
                    Column(Modifier.weight(1f).height(110.dp).clip(shape).background(if (on) t.sel else t.track, shape)
                        .tap { Store.update { it.copy(font = n) } }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        androidx.compose.foundation.text.BasicText("Aa", style = TextStyle(color = if (on) t.selTx else t.tx, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, fontFamily = fam))
                        androidx.compose.foundation.text.BasicText(n, maxLines = 1, style = TextStyle(color = if (on) t.selTx else t.tx, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = fam))
                    }
                }
            }
            LabeledSeg("Tamaño", listOf("Pequeña", "Normal", "Grande"), s.size, 170.dp, 50.dp) { o -> Store.update { it.copy(size = o) } }
            LabeledSeg("Números grandes", listOf("Igual que el texto", "Fuente técnica"), if (s.techNumbers) "Fuente técnica" else "Igual que el texto", 170.dp, 50.dp) { o ->
                Store.update { it.copy(techNumbers = o == "Fuente técnica") }
            }
        }
        Card {
            T("Paleta de colores · " + if (s.design == "Aurora claro") "las paletas se usan en Negro puro" else "solo cambia contornos, anillos y botones", 18.sp, FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PALETTES.forEach { (name, pair) ->
                    val on = s.palette == name
                    val shape = RoundedCornerShape(30.dp)
                    Column(Modifier.weight(1f).height(120.dp).clip(shape).background(Color.Black, shape)
                        .border(if (on) 3.dp else 1.dp, if (on) pair.second else Color(0xFF2A2930), shape)
                        .tap { Store.update { it.copy(palette = name) } }.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 1, 3).forEach { i -> Box(Modifier.size(32.dp).border(3.dp, pair.first[i], CircleShape)) }
                        }
                        T(name, 20.sp, FontWeight.Black, Color(0xFFF4F1EC))
                    }
                }
            }
        }
    }
}
