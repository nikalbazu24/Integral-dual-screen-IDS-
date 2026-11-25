package com.nikalbazu.ids

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as SysSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikalbazu.ids.core.ErrorReporter
import com.nikalbazu.ids.core.Store
import com.nikalbazu.ids.panel.Apps
import com.nikalbazu.ids.panel.PanelService
import com.nikalbazu.ids.priv.Helper
import com.nikalbazu.ids.priv.PServer
import com.nikalbazu.ids.ui.Card
import com.nikalbazu.ids.ui.LocalTok
import com.nikalbazu.ids.ui.Pill
import com.nikalbazu.ids.ui.T
import com.nikalbazu.ids.ui.Title
import com.nikalbazu.ids.ui.ToggleCard
import com.nikalbazu.ids.ui.buildTok
import kotlinx.coroutines.delay

/** Pantalla de arriba: preparación, permisos y ajustes avanzados. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
        PanelService.send(this)
        setContent { Setup() }
    }

    override fun onResume() {
        super.onResume()
        PanelService.send(this)
    }
}

@Composable
private fun Setup() {
    val ctx = LocalContext.current
    val s by Store.settings.collectAsState()
    val learning by Store.learningKey.collectAsState()
    val visible by Store.panelVisible.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
    val tok = remember(s.design, s.palette, s.font, s.techNumbers) { buildTok(ctx, s) }

    BoxWithConstraints(Modifier.fillMaxSize().background(tok.bg)) {
        val density = Density(constraints.maxWidth / 1500f, 1f)
        CompositionLocalProvider(LocalDensity provides density, LocalTok provides tok) {
            val t = tok
            val overlay = remember(tick) { SysSettings.canDrawOverlays(ctx) }
            val usage = remember(tick) { Apps.hasUsageAccess(ctx) }
            val helper = remember(tick) { Helper.statusText() }
            val pserver = remember(tick) { PServer.available }
            val reports = remember(tick) { ErrorReporter.pending().size }
            val diag = remember(tick) { Helper.readDiag() }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Title("Integral Dual Screen")
                        T("Versión ${BuildConfig.VERSION_NAME} · el panel vive en la pantalla de abajo", 20.sp, FontWeight.Bold, t.mu)
                    }
                    Pill(if (visible) "Ocultar panel" else "Mostrar panel abajo", true, Modifier.width(320.dp), 64.dp, 20.sp) {
                        PanelService.send(ctx, PanelService.ACTION_TOGGLE)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Card(Modifier.weight(1f)) {
                        T("1 · Preparación", 24.sp, FontWeight.Black)
                        Check("Mostrarse sobre otras apps", overlay, "Dar permiso") {
                            ctx.startActivity(Intent(SysSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        }
                        Check("Acceso a uso (perfiles por juego)", usage, "Dar permiso") {
                            ctx.startActivity(Intent(SysSettings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                        Check("Servicio interno de la Thor", pserver, null) {}
                        T("Conexión con el sistema: $helper", 18.sp, FontWeight.Bold, t.mu)
                    }
                    Card(Modifier.weight(1f)) {
                        T("2 · Botón AYN", 24.sp, FontWeight.Black)
                        T(if (s.aynKey.isBlank()) "Aún no se ha aprendido el botón." else "Botón guardado: ${s.aynKey}", 19.sp, FontWeight.Bold)
                        T("Toca “Aprender” y luego presiona una vez el botón AYN. Desde ahí, cada vez que lo presiones el panel aparece o se oculta, y vuelves a lo que estabas haciendo.",
                            17.sp, FontWeight.Bold, t.mu, maxLines = 5)
                        Pill(if (learning) "Presiona el botón AYN…" else "Aprender botón", learning, Modifier.fillMaxWidth(), 58.dp) {
                            Store.learningKey.value = !learning
                            Helper.ensureRunning()
                        }
                        ToggleCard("Reemplazar el panel de AYN", "Duerme el panel de fábrica mientras IDS esté activo. Si IDS se cierra, vuelve solo.",
                            s.replaceStock, height = 120.dp) {
                            val on = !s.replaceStock
                            Store.update { it.copy(replaceStock = on) }
                            Helper.send(if (on) "freeze" else "thaw")
                        }
                    }
                }
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("Diagnóstico de luces de los sticks", 24.sp, FontWeight.Black, modifier = Modifier.weight(1f))
                        Pill("Actualizar", false, Modifier.width(200.dp), 50.dp, 16.sp) { Helper.send("diag") }
                    }
                    T(diag.ifBlank { "Aún sin datos. Espera unos segundos con la conexión activa." }, 15.sp, FontWeight.Bold, t.mu, maxLines = 30)
                    T("Si cambias el color desde los ajustes de AYN y tocas Actualizar, aquí aparece qué ajuste cambió. Mándame una captura para conectarlo.",
                        15.sp, FontWeight.Bold, t.mu, maxLines = 3)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Card(Modifier.weight(1f)) {
                        T("3 · Pantalla de abajo", 24.sp, FontWeight.Black)
                        val displays = remember(tick) { ctx.getSystemService(DisplayManager::class.java).displays.toList() }
                        displays.forEach { d ->
                            Pill("${d.displayId} · ${d.name} (${d.mode.physicalWidth}×${d.mode.physicalHeight})",
                                s.bottomDisplay == d.displayId, Modifier.fillMaxWidth(), 52.dp, 16.sp) {
                                Store.update { it.copy(bottomDisplay = d.displayId) }
                            }
                        }
                        T("Si no eliges, se usa la primera pantalla que no sea la principal.", 16.sp, FontWeight.Bold, t.mu)
                    }
                    Card(Modifier.weight(1f)) {
                        T("4 · Ayuda y reportes", 24.sp, FontWeight.Black)
                        T(if (BuildConfig.REPORT_URL.isBlank()) "Los errores se guardan en la consola (envío automático sin configurar). Pendientes: $reports"
                            else "Los errores se envían solos. Pendientes: $reports", 17.sp, FontWeight.Bold, t.mu)
                        Pill("Ver versiones nuevas", false, Modifier.fillMaxWidth(), 56.dp) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.REPO}/releases")))
                        }
                        Pill("Reiniciar conexión con el sistema", false, Modifier.fillMaxWidth(), 56.dp) {
                            Helper.stop(); Helper.ensureRunning()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Check(name: String, ok: Boolean, action: String?, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        T(if (ok) "●" else "○", 24.sp, FontWeight.Black, if (ok) t.v[1] else t.v[0])
        T(name, 19.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
        if (!ok && action != null) Pill(action, false, Modifier.width(200.dp), 48.dp, 16.sp, onClick)
    }
}
