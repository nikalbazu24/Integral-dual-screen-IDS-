package com.nikalbazu.ids.ui

import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikalbazu.ids.core.Store
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Panel completo de la pantalla de abajo. Se diseña sobre 1240 × 1080 y se escala a la pantalla real. */
@Composable
fun PanelRoot() {
    val ctx = LocalContext.current
    val s by Store.settings.collectAsState()
    val nav by Store.nav.collectAsState()
    val tok = remember(s.design, s.palette, s.font, s.techNumbers) { buildTok(ctx, s) }
    val fontScale = when (s.size) { "Pequeña" -> 0.92f; "Grande" -> 1.08f; else -> 1f }

    val visible by Store.panelVisible.collectAsState()
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val px = constraints.maxWidth.toFloat()
        val density = Density(px / 1240f, fontScale)
        CompositionLocalProvider(LocalDensity provides density, LocalTok provides tok) {
            // Al estar oculto, el contenido se retira por completo: no consume nada mientras juegas.
            AnimatedVisibility(
                visibleState = shown,
                enter = fadeIn(tween(220)) +
                    slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 7 } +
                    scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 0.95f),
                exit = fadeOut(tween(200)) +
                    slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it / 9 } +
                    scaleOut(tween(260), targetScale = 0.96f),
            ) {
                Column(Modifier.fillMaxSize().background(tok.bg).padding(horizontal = 32.dp, vertical = 20.dp)) {
                    LaunchedEffect(nav.sec, visible) { if (nav.sec != "inicio" || !visible) { Store.editing.value = false; Store.tray.value = false } }
                    StatusBar(nav.sec == "inicio")
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Crossfade(nav.sec, animationSpec = tween(180), label = "seccion") { sec ->
                            when (sec) {
                                "inicio" -> InicioScreen()
                                "control" -> ControlScreen()
                                "ct" -> ClusterTuneScreen()
                                "hw" -> HardwareScreen()
                                "juegos" -> JuegosScreen()
                                "herr" -> HerramientasScreen()
                                else -> PersonalizarScreen()
                            }
                        }
                        ToastBanner(Modifier.align(Alignment.TopCenter))
                    }
                    Spacer(Modifier.height(14.dp))
                    Dock(nav.sec)
                }
            }
        }
    }
}

@Composable
private fun StatusBar(inicio: Boolean) {
    val t = LocalTok.current
    val tele by Store.tele.collectAsState()
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    val es = Locale("es", "CL")
    val date = SimpleDateFormat("EEEE d 'de' MMMM", es).format(now).replaceFirstChar { it.uppercase() }
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        T(SimpleDateFormat("HH:mm", es).format(now), 40.sp, FontWeight.Black, num = true, maxLines = 1)
        Spacer(Modifier.width(14.dp))
        T(date, 24.sp, FontWeight.Bold, t.mu, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (inicio) {
            val editing by Store.editing.collectAsState()
            if (editing) {
                Pill("+ Agregar tarjeta", false, height = 50.dp, size = 18.sp) { Store.tray.value = true }
                Spacer(Modifier.width(10.dp))
                Pill("Restablecer", false, height = 50.dp, size = 18.sp) {
                    Store.update { it.copy(layout = com.nikalbazu.ids.core.Grid.DEFAULT) }; Store.toast("Inicio restablecido")
                }
                Spacer(Modifier.width(10.dp))
            }
            Pill(if (editing) "Listo" else "Editar inicio", editing, height = 50.dp, size = 18.sp) {
                Store.editing.value = !editing; Store.tray.value = false
            }
            Spacer(Modifier.width(16.dp))
        }
        if (!tele.helperAlive) {
            T("Sin conexión al sistema", 18.sp, FontWeight.Bold, t.v[0], maxLines = 1)
            Spacer(Modifier.width(16.dp))
        }
        val shape = RoundedCornerShape(25.dp)
        Row(
            Modifier.height(50.dp).clip(shape).background(t.track).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(34.dp).height(16.dp).clip(RoundedCornerShape(5.dp)).background(t.tx).padding(2.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)).background(t.bg).padding(1.dp)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(tele.batteryPct / 100f).background(if (tele.batteryPct <= 20) t.v[0] else t.v[1]))
                }
            }
            androidx.compose.animation.AnimatedVisibility(tele.charging, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                Icon(Ico.BOLT, t.v[1], 26.dp, 2.6f)
            }
            T("${tele.batteryPct} %", 24.sp, FontWeight.Black, num = true, maxLines = 1)
            val extra = when {
                tele.charging -> "cargando"
                tele.minutesLeft != null -> "${tele.minutesLeft!! / 60} h ${tele.minutesLeft!! % 60} min"
                else -> ""
            }
            if (extra.isNotEmpty()) T(extra, 18.sp, FontWeight.Bold, t.mu, maxLines = 1)
        }
    }
}

@Composable
private fun Dock(sec: String) {
    val t = LocalTok.current
    val items = listOf(Triple("inicio", "Inicio", Ico.INICIO), Triple("control", "Control", Ico.CONTROL), Triple("ct", "ClusterTune", Ico.CT),
        Triple("hw", "Hardware", Ico.HW), Triple("juegos", "Juegos", Ico.JUEGOS), Triple("herr", "Herramientas", Ico.HERR), Triple("pers", "Personalizar", Ico.PERS))
    Row(Modifier.fillMaxWidth().card(t, null, 44.dp).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (id, label, ico) ->
            val on = id == sec
            val shape = RoundedCornerShape(36.dp)
            val base = if (on) Modifier.shadow(12.dp, shape, ambientColor = t.sel.copy(alpha = 0.4f), spotColor = t.sel.copy(alpha = 0.5f)).background(t.sel, shape)
                else Modifier.background(t.track, shape)
            Column(Modifier.weight(1f).height(76.dp).then(base).clip(shape).tap { Store.nav { it.copy(sec = id) } },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                Icon(ico, if (on) t.selTx else t.tx)
                T(label, 15.sp, FontWeight.ExtraBold, if (on) t.selTx else t.tx, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ToastBanner(modifier: Modifier) {
    val t = LocalTok.current
    val msg by Store.message.collectAsState()
    LaunchedEffect(msg) { if (msg != null) { delay(2600); Store.message.value = null } }
    msg?.let {
        val shape = RoundedCornerShape(26.dp)
        Box(modifier.padding(top = 6.dp).shadow(18.dp, shape).clip(shape).background(t.sel).padding(horizontal = 26.dp, vertical = 14.dp)) {
            T(it, 19.sp, FontWeight.ExtraBold, t.selTx)
        }
    }
}

fun mediaKey(ctx: android.content.Context, code: Int) {
    val am = ctx.getSystemService(AudioManager::class.java)
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
}

val TRANSPARENT = Color.Transparent
