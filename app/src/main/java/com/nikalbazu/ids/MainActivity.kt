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
import com.nikalbazu.ids.priv.Device
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
import kotlinx.coroutines.launch

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
    val learningName by Store.learningSetting.collectAsState()
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var toastText by remember { mutableStateOf("") }
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
                        ToggleCard("Dormir el panel de AYN", "Lo pausa mientras IDS esté activo. Si IDS se cierra, vuelve solo.",
                            s.replaceStock, height = 120.dp) {
                            val on = !s.replaceStock
                            Store.update { it.copy(replaceStock = on) }
                            Helper.send(if (on) "freeze" else "thaw")
                        }
                        ToggleCard("Bloqueo total del panel de AYN", "Desactiva la app del panel de fábrica. Se reactiva sola si desinstalas o cierras IDS.",
                            s.blockStock, height = 120.dp) {
                            val on = !s.blockStock
                            Store.update { it.copy(blockStock = on) }
                            Helper.send(if (on) "block" else "unblock")
                        }
                        val detecting by Store.detectingStock.collectAsState()
                        Pill(if (detecting) "Ahora presiona el botón AYN…" else "Detectar panel de AYN", detecting, Modifier.fillMaxWidth(), 56.dp) {
                            Store.detectingStock.value = !detecting
                            if (!detecting) Helper.send("probe 1")
                        }
                        T("Detectado: " + (listOf("com.odin.dualscreen.assistant") + s.stockPackages).distinct().joinToString(), 15.sp, FontWeight.Bold, t.mu, maxLines = 3)
                        T("Quitar el panel de AYN para siempre", 22.sp, FontWeight.Black)
                        T("Desactiva el panel de fábrica y no vuelve aunque cierres o desinstales IDS. Primero usa “Detectar panel de AYN” para que encuentre el panel correcto. Se puede devolver cuando quieras.",
                            16.sp, FontWeight.Bold, t.mu, maxLines = 4)
                        var res by remember { mutableStateOf(Helper.readBlock()) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Pill(if (s.permStock) "Quitado ✓ (volver a aplicar)" else "Quitar para siempre", !s.permStock, Modifier.weight(1f), 58.dp) {
                                Store.update { it.copy(permStock = true, blockStock = true) }
                                Helper.send("blockperm")
                                scope.launch { delay(2500); res = Helper.readBlock() }
                            }
                            if (s.permStock) Pill("Devolver el panel", false, Modifier.weight(1f), 58.dp) {
                                Store.update { it.copy(permStock = false, blockStock = false) }
                                Helper.send("unblockperm")
                                res = emptyList()
                            }
                        }
                        if (res.isNotEmpty()) T(res.joinToString("\n") { (pkg, r) ->
                            when (r) { "ok" -> "✓ $pkg desactivado"; "fallo" -> "✗ $pkg no se dejó desactivar"; else -> "· $pkg no existe en esta consola" }
                        }, 15.sp, FontWeight.Bold, if (res.any { it.second == "ok" }) t.v[1] else t.v[0], maxLines = 6)
                    }
                }
                Card {
                    T("Aprender botones de AYN", 24.sp, FontWeight.Black)
                    T(if (learningName == null)
                        "Toca “Aprender” en un botón. Luego abre el panel de AYN (pantalla de abajo), toca ese mismo botón una vez y vuelve aquí a tocar “Ya lo toqué”."
                    else "Ahora toca “$learningName” en el panel de AYN, una sola vez, y después “Ya lo toqué”.", 17.sp, FontWeight.Bold, t.mu, maxLines = 4)
                    if (learningName != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pill("Ya lo toqué", true, Modifier.weight(1f), 58.dp) {
                            val name = learningName ?: return@Pill
                            Helper.send("snap 2")
                            scope.launch {
                                var waited = 0
                                while (Helper.snapAge(2) > 4000 && waited < 8000) { delay(400); waited += 400 }
                                val n = Device.finishLearning(name)
                                Store.learningSetting.value = null
                                if (s.replaceStock) Helper.send("freeze")
                                if (s.permStock) Helper.send("blockperm")
                                Store.toast(if (n > 0) "Listo: “$name” aprendido" else "No se detectó ningún cambio. Prueba de nuevo.")
                                toastText = if (n > 0) "“$name” aprendido ($n ajuste${if (n > 1) "s" else ""})" else "No se detectó cambio en “$name”. Intenta otra vez."
                            }
                        }
                        Pill("Cancelar", false, Modifier.width(200.dp), 58.dp) {
                            Store.learningSetting.value = null
                            if (s.replaceStock) Helper.send("freeze")
                            if (s.permStock) Helper.send("blockperm")
                        }
                    }
                    if (toastText.isNotEmpty()) T(toastText, 17.sp, FontWeight.Black, t.v[1])
                    if (s.learned.isNotEmpty()) Pill("Compartir lo aprendido con la comunidad", true, Modifier.fillMaxWidth(), 58.dp) {
                        com.nikalbazu.ids.core.Community.shareUrl()?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    }
                    T("Al compartir se abre GitHub con todo rellenado: solo toca “Submit new issue”. Se revisa, se agrega a la app y le llega a todos en la siguiente versión.",
                        15.sp, FontWeight.Bold, t.mu, maxLines = 3)
                    (Device.STOCK_BUTTONS + "Force landscape").forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val community by com.nikalbazu.ids.core.Community.buttons.collectAsState()
                            val mine = s.learned[name]
                            val learned = mine ?: community[name]
                            T(if (learned != null) "●" else "○", 22.sp, FontWeight.Black, if (learned != null) t.v[1] else t.mu)
                            Column(Modifier.weight(1f)) {
                                T(name, 19.sp, FontWeight.Black)
                                T(when {
                                    mine != null -> "Aprendido aquí · " + mine.on.keys.joinToString { it.substringAfter(':') }
                                    learned != null -> "Viene de la comunidad"
                                    else -> "Sin aprender"
                                }, 14.sp, FontWeight.Bold, t.mu, maxLines = 1)
                            }
                            Pill("Aprender", learningName == name, Modifier.width(170.dp), 48.dp, 16.sp) {
                                // Despierta el panel de fábrica y toma la primera foto de los ajustes
                                if (s.permStock) Helper.send("tempunblock")
                                Helper.send("thaw", "snap 1")
                                Store.learningSetting.value = name
                                toastText = if (s.permStock) "El panel de AYN volvió por un momento: presiona el botón AYN, toca “$name” y vuelve aquí." else ""
                            }
                            if (learned != null) Pill("Invertir", false, Modifier.width(140.dp), 48.dp, 16.sp) { Device.invert(name); Store.toast("“$name” invertido") }
                            if (mine != null) Pill("Olvidar", false, Modifier.width(140.dp), 48.dp, 16.sp) { Device.forget(name) }
                        }
                    }
                }
                Card(accent = 1) {
                    T("Revisión completa de la consola", 24.sp, FontWeight.Black)
                    T("Prueba cada función de verdad (ventilador, rendimiento, brillo de las dos pantallas, topes de CPU y GPU, luces), " +
                        "revisa si la consola la acepta y la deja como estaba. Demora unos 20 segundos. El resultado queda en Descargas › IDS › revision.txt, " +
                        "junto con las apps de AYN en Descargas › IDS › apk. Mándame esos archivos para conectar todo con lo que usa AYN.",
                        16.sp, FontWeight.Bold, t.mu, maxLines = 6)
                    var auditing by remember { mutableStateOf(false) }
                    val auditDone = remember(tick) { Helper.auditDone() }
                    Pill(if (auditing && !auditDone) "Revisando… no cierres la app" else "Hacer la revisión", !auditing || auditDone, Modifier.fillMaxWidth(), 58.dp) {
                        auditing = true
                        Helper.send("audit")
                    }
                    if (auditing && auditDone) {
                        val res = remember(tick) { Helper.auditSummary() }
                        T(res, 15.sp, FontWeight.Bold, if (res.contains("FALLA")) t.v[0] else t.v[1], maxLines = 24)
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
