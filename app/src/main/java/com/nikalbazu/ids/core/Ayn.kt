package com.nikalbazu.ids.core

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ajustes de AYN leídos directo del sistema (sin el ayudante) y avisados al instante cuando cambian,
 * también si se cambian desde la barra de notificaciones o desde el panel de AYN.
 */
object Ayn {
    val KEYS = listOf(
        "fan_mode", "performance_mode", "is_charging_separation", "force_landscape", "keep_screen_on",
        "screen_focus_lock", "enable_system_key_focus_lock", "joystick_light_enabled", "joystick_led_light_picker_color",
        "led_light_brightness_percent", "dual_screen_brightness_level", "screen_brightness",
    )
    val values = MutableStateFlow<Map<String, String>>(emptyMap())
    private var started = false

    fun start(ctx: Context) {
        if (started) return
        started = true
        val r = ctx.applicationContext.contentResolver
        val thread = HandlerThread("ids-ayn").apply { start() }
        val handler = Handler(thread.looper)
        fun read() = runCatching {
            values.value = KEYS.mapNotNull { k -> Settings.System.getString(r, k)?.let { k to it } }.toMap()
        }
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { read() }
        }
        KEYS.forEach { k -> runCatching { r.registerContentObserver(Settings.System.getUriFor(k), false, obs) } }
        handler.post { read() }
    }
}
