package com.nikalbazu.ids.panel

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings as SysSettings
import com.nikalbazu.ids.MainActivity
import com.nikalbazu.ids.R
import com.nikalbazu.ids.core.ErrorReporter
import com.nikalbazu.ids.core.Store
import com.nikalbazu.ids.priv.Device
import com.nikalbazu.ids.priv.Helper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Servicio que mantiene el panel, escucha el botón AYN y lee los datos de la consola. */
class PanelService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var host: OverlayHost? = null
    private var lastKeyStamp: String? = null
    private var lastFg: String? = null
    private var beforeSleepCt: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // Al abrir la consola el sistema puede revivir el panel de fábrica: lo volvemos a dormir.
            if (i.action == Intent.ACTION_SCREEN_ON && Store.settings.value.replaceStock) Helper.send("freeze")
            if ("sleep" !in Store.settings.value.automation) return
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    beforeSleepCt = Store.settings.value.ct
                    Device.applyCaps("Batería"); Store.log("Pantalla apagada → perfil Batería", "Dormir")
                }
                Intent.ACTION_SCREEN_ON -> beforeSleepCt?.let { Device.applyCaps(it); beforeSleepCt = null }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForeground(1, notification())
        host = OverlayHost(this)
        Helper.ensureRunning()
        com.nikalbazu.ids.core.Ayn.start(this)
        Device.readVendorModes()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        })
        Store.settings.value.stockPackages.forEach { Helper.send("stockadd $it") }
        if (Store.settings.value.replaceStock) Helper.send("freeze")
        if (Store.settings.value.blockStock) Helper.send("block")
        lastKeyStamp = Helper.lastKey()?.second
        Device.applyLeds()
        Helper.send("diag")
        loops()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_SHOW -> show()
            ACTION_HIDE -> host?.hide()
        }
        return START_STICKY
    }

    private fun show() {
        if (!SysSettings.canDrawOverlays(this)) { Store.toast("Falta el permiso para mostrarse sobre otras apps"); return }
        runCatching { host?.show() }.onFailure { ErrorReporter.report("PanelService.show", it) }
    }

    private fun toggle() {
        if (!SysSettings.canDrawOverlays(this)) return
        runCatching { host?.toggle() }.onFailure { ErrorReporter.report("PanelService.toggle", it) }
    }

    fun setFocusable(f: Boolean) = host?.setFocusable(f)
    fun bottomDisplayId(): Int = host?.displayId ?: -1

    private fun loops() {
        // Cualquier cambio de AYN (barra, panel de AYN) se refleja al instante en los botones
        scope.launch { com.nikalbazu.ids.core.Ayn.values.collect { Device.refreshStates() } }
        // Botón AYN (lo que anota el ayudante)
        scope.launch {
            while (isActive) {
                val k = withContext(Dispatchers.IO) { Helper.lastKey() }
                if (k != null && k.second != lastKeyStamp) {
                    lastKeyStamp = k.second
                    if (Store.learningKey.value) {
                        Store.update { it.copy(aynKey = k.first) }
                        Store.learningKey.value = false
                        Store.toast("Botón guardado: ${k.first}")
                    } else if (Store.learningSetting.value != null) {
                        // Aprendiendo un botón: el botón AYN abre el panel de fábrica, IDS no se mete
                    } else if (k.first == Store.settings.value.aynKey && k.first.isNotBlank()) {
                        toggle()
                        val st = Store.settings.value
                        if (st.replaceStock || st.blockStock || Store.detectingStock.value) detectStock()
                    }
                }
                delay(120)
            }
        }
        // Datos en vivo
        scope.launch {
            while (isActive) {
                runCatching { withContext(Dispatchers.IO) { refreshTelemetry() } }
                    .onFailure { ErrorReporter.report("telemetría", it) }
                tickTimer()
                Device.smartTick(Store.tele.value.socC)
                if (Store.panelVisible.value && ledTick % 2 == 0) Device.refreshStates()
                ledTick++
                val fx = Store.settings.value.effect
                if ((fx == "Según temperatura" && ledTick % 5 == 0) || (fx == "Según batería" && ledTick % 60 == 0)) Device.applyLeds()
                delay(1000)
            }
        }
        // App en primer plano → perfil por juego
        scope.launch {
            while (isActive) {
                val fg = withContext(Dispatchers.IO) { Apps.foreground(this@PanelService) }
                if (fg != null && fg != lastFg && fg != packageName) {
                    lastFg = fg
                    Store.foreground.value = fg
                    Store.settings.value.games[fg]?.let { Device.applyGameProfile(fg, it) }
                }
                delay(2000)
            }
        }
    }

    private var lastBaseline = 0L

    /** Mira qué ventanas aparecieron justo después del botón AYN: esas son del panel de fábrica. */
    private fun detectStock() {
        scope.launch {
            delay(700)
            Helper.send("probe 2")
            var waited = 0
            while (Helper.probeAge(2) > 2500 && waited < 4000) { delay(250); waited += 250 }
            val before = withContext(Dispatchers.IO) { Helper.readProbe(1) }.toSet()
            val after = withContext(Dispatchers.IO) { Helper.readProbe(2) }
            val skip = setOf(packageName, "android", "com.android.systemui")
            val keywords = Regex("odin|ayn|tcc|dualscreen", RegexOption.IGNORE_CASE)
            val found = after.filter { (d, pkg) ->
                d != "proc" && pkg !in skip && d != "0" && ((d to pkg) !in before || keywords.containsMatchIn(pkg))
            }.map { it.second } + after.filter { it.first == "proc" && keywords.containsMatchIn(it.second) }.map { it.second.substringBefore(':') }
            val fresh = found.distinct().filter { it !in Store.settings.value.stockPackages && Regex("^[a-zA-Z0-9._]+$").matches(it) }
            if (fresh.isNotEmpty()) {
                Store.update { it.copy(stockPackages = (it.stockPackages + fresh).distinct()) }
                fresh.forEach { Helper.send("stockadd $it") }
                if (Store.settings.value.replaceStock) Helper.send("freeze")
                if (Store.settings.value.blockStock) Helper.send("block")
                Store.log("Panel de AYN detectado: ${fresh.joinToString()}", "AYN")
            }
            if (Store.detectingStock.value) {
                Store.detectingStock.value = false
                Store.toast(if (fresh.isEmpty()) "No apareció nada nuevo. Prueba otra vez." else "Detectado: ${fresh.joinToString()}")
            }
            Helper.send("probe 1")
        }
    }

    private fun refreshTelemetry() {
        val st0 = Store.settings.value
        if ((st0.replaceStock || st0.blockStock || Store.detectingStock.value) && !Store.panelVisible.value &&
            System.currentTimeMillis() - lastBaseline > 15_000) {
            lastBaseline = System.currentTimeMillis(); Helper.send("probe 1")
        }
        Helper.ensureRunning()
        val m = Helper.readTelemetry()
        val bm = getSystemService(BatteryManager::class.java)
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val tempTenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val mv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val uah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        // Algunas consolas informan mA en vez de µA
        val amps = if (abs(ua) > 20000) abs(ua) / 1_000_000f else abs(ua) / 1000f
        val watts = if (mv > 0 && amps > 0f) amps * mv / 1000f else null
        val ampHours = if (uah > 0) uah / 1_000_000f else null
        val minutes = if (!charging && ampHours != null && amps > 0.05f) (ampHours / amps * 60).toInt() else null

        val mi = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        val gb = 1024f * 1024f * 1024f

        fun i(k: String) = m[k]?.toIntOrNull()
        fun l(k: String) = m[k]?.toLongOrNull()
        val soc = i("soc")?.takeIf { it > 0 }?.let { if (it > 1000) it / 1000f else it.toFloat() }
        val prev = Store.tele.value
        Store.tele.value = prev.copy(
            socC = soc ?: prev.socC,
            batteryC = tempTenths / 10f,
            batteryPct = pct,
            charging = charging,
            watts = watts,
            minutesLeft = minutes,
            cpuCur = listOf("0", "3", "7").mapNotNull { p -> i("cur$p")?.let { p to it } }.toMap(),
            cpuMax = listOf("0", "3", "7").mapNotNull { p -> i("max$p")?.let { p to it } }.toMap(),
            cpuHw = listOf("0", "3", "7").mapNotNull { p -> i("hw$p")?.let { p to it } }.toMap(),
            gpuHz = l("gpu"), gpuMaxHz = l("gpumax"), gpuLoad = i("gpuload"),
            fanRpm = i("fan"),
            ramUsedGb = (mi.totalMem - mi.availMem) / gb, ramTotalGb = mi.totalMem / gb,
            backlights = m["bl"].orEmpty().split(',').filter { it.isNotBlank() },
            blPct = m.filterKeys { it.startsWith("blp_") }.mapNotNull { (k, v) -> v.toIntOrNull()?.let { k.removePrefix("blp_") to it } }.toMap(),
            leds = m["leds"].orEmpty().split(',').filter { it.isNotBlank() },
            helperAlive = Helper.alive(),
            maxTodayC = maxOf(prev.maxTodayC, soc ?: 0f),
        )
        // Lo que se cambie desde la barra de notificaciones o desde AYN también se ve en IDS
        com.nikalbazu.ids.core.ClusterTune.refresh(this, Helper.folder)
        val ayn = com.nikalbazu.ids.core.Ayn.values.value
        if (ayn.isNotEmpty()) {
            if (!ledsSynced) { ledsSynced = true; Device.syncLedsFromSystem() }
            val fanName = mapOf("0" to "Apagado", "1" to "Silencio", "4" to "Auto", "5" to "Máx", "6" to "Personalizado")[ayn["fan_mode"]]
            val perfName = mapOf("0" to "Ahorro", "1" to "Equilibrado", "2" to "Rendimiento")[ayn["performance_mode"]]
            val cur = Store.settings.value
            if (System.currentTimeMillis() - Device.lastSet > 3000 && ((fanName != null && cur.fan != "Inteligente" && fanName != cur.fan) || (perfName != null && perfName != cur.perf)))
                Store.update { it.copy(fan = if (it.fan == "Inteligente" || fanName == null) it.fan else fanName, perf = perfName ?: it.perf) }
        }
        // Ahorro automático al 20 %
        val st = Store.settings.value
        if ("low" in st.battery && !charging && pct in 1..20 && !lowApplied) {
            lowApplied = true
            Device.setHz("60 Hz"); Device.applyCaps("Batería")
            Store.log("Batería al $pct % → ahorro automático", "Batería")
            Store.toast("Batería baja: se activó el ahorro")
        }
        if (pct > 25 || charging) lowApplied = false
    }
    private var lowApplied = false
    private var ledsSynced = false
    private var ledTick = 0

    private fun tickTimer() {
        val end = Store.timerEndsAt.value
        if (end > 0 && System.currentTimeMillis() >= end) {
            Store.timerEndsAt.value = 0
            Store.toast("¡Se acabó el tiempo de juego!")
            runCatching {
                getSystemService(Vibrator::class.java).vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
            }
        }
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("panel", "Panel IDS", NotificationManager.IMPORTANCE_MIN))
        val toggle = PendingIntent.getService(this, 1,
            Intent(this, PanelService::class.java).setAction(ACTION_TOGGLE), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "panel")
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Integral Dual Screen activo")
            .setContentText("Toca para mostrar u ocultar el panel")
            .setContentIntent(toggle)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_tile), "Ajustes", open).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(screenReceiver) }
        if (Store.settings.value.replaceStock) Helper.send("thaw")
        host?.destroy()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "ids.TOGGLE"
        const val ACTION_SHOW = "ids.SHOW"
        const val ACTION_HIDE = "ids.HIDE"
        @Volatile var instance: PanelService? = null

        fun send(ctx: Context, action: String? = null) {
            val i = Intent(ctx, PanelService::class.java)
            if (action != null) i.action = action
            ctx.startForegroundService(i)
        }
    }
}
