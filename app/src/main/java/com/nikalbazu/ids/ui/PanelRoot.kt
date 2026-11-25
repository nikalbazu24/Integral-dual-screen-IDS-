package com.nikalbazu.ids.ui

import android.media.AudioManager
import android.view.KeyEvent
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

    BoxWithConstraints(Modifier.fillMaxSize().background(tok.bg)) {
        val px = constraints.maxWidth.toFloat()
        val density = Density(px / 1240f, fontScale)
        CompositionLocalProvider(LocalDensity provides density, LocalTok provides tok) {
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
                StatusBar()
                Spacer(Modifier.height(14.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (nav.sec) {
                        "inicio" -> InicioScreen()
                        "control" -> ControlScreen()
                        "ct" -> ClusterTuneScreen()
                        "hw" -> HardwareScreen()
                        "juegos" -> JuegosScreen()
                        "herr" -> HerramientasScreen()
                        else -> PersonalizarScreen()
                    }
                    ToastBanner(Modifier.align(Alignment.TopCenter))
                }
                Spacer(Modifier.height(14.dp))
                Dock(nav.sec)
            }
        }
    }
}

@Composable
private fun StatusBar() {
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
    val items = listOf("inicio" to "Inicio", "control" to "Control", "ct" to "ClusterTune", "hw" to "Hardware",
        "juegos" to "Juegos", "herr" to "Herramientas", "pers" to "Personalizar")
    Row(Modifier.fillMaxWidth().card(t, null, 44.dp).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (id, label) ->
            Pill(label, id == sec, Modifier.weight(1f), 76.dp, 18.sp) { Store.nav { it.copy(sec = id) } }
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
