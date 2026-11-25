# Ajustes reales de la AYN Thor (sacados de las apps de AYN y de la revisión del 25-11-2026)

Consola: AYN Thor, Android 13, build Thor_V1.0.0.377. Pantalla de arriba = display 0 (panel0-backlight, 1080x1920),
pantalla de abajo = "Screen-2", display 4 (panel1-backlight, 1080x1240). Los dos backlights tienen max 4095.

El sistema de AYN (com.odin.settings, proceso "system") escucha estos ajustes de Settings.System y los aplica solo
(triggers). Por eso basta con escribirlos; no hay que tocar sysfs.

| Función | Ajuste (Settings.System) | Valores |
|---|---|---|
| Ventilador | fan_mode | 0 apagado, 1 silencioso (Quiet), 4 inteligente (Smart), 5 deportivo (Sports), 6 personalizado |
| Rendimiento | performance_mode | 0 estándar, 1 medio, 2 alto |
| Brillo arriba | screen_brightness | 0-255 |
| Brillo abajo | dual_screen_brightness_level | 0-100 (el sistema lo convierte a brillo 0.68, etc.). Escribir el backlight crudo NO sirve: panel0 lo ignora y panel1 queda tenue |
| Volumen abajo | secondary_screen_volume_level | 0-15 |
| Carga directa ("Charging separation") | is_charging_separation | 0/1 (dispara /sys/class/qcom-battery/usb_charge_now) |
| Límite de carga | charging_limit_power_limit | |
| Force landscape | force_landscape | 0/1 (hoy está en 1) |
| Mantener pantalla | keep_screen_on | 0/1 |
| Luz de sticks encendida | joystick_light_enabled | "1,1" (izq,der) |
| Color de sticks | joystick_led_light_picker_color | "#ff0000ff,#ff0000ff" (izq,der; formato Android #AARRGGBB, hoy azul) |
| Brillo luces | led_light_brightness_percent | 0.0-1.0 |
| Luz de mandos externos | joystick_handle_light_enabled / joystick_handle_light_picker_color | |
| Auto-lock (modo foco) | screen_focus_lock, enable_system_key_focus_lock + reflexión WindowManager.setPinScreenMode | sin confirmar: se aprende con "Aprender" |
| Orientación ABXY | flip_button_layout, temp_abxy_layout_mode | |
| Vibración | vibrate_strength_value | 0.0-1.0 |

Las luces del sistema en /sys/class/leds (red, green, blue, flash, torch, switch) NO son los sticks: IDS no debe escribir ahí.
Servicios de AYN: PServerBinder, lights (ILightsManager), vendor.qti.hardware.lights.service, perf-hal, perfservice.
Barra de notificaciones (sysui_qs_tiles): ledlights, charge_limit, performancemode, fan, charging, triggerInputMode, controllerStyle...
Todas leen estos mismos ajustes.
