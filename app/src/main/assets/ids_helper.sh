#!/system/bin/sh
# Integral Dual Screen - ayudante con privilegios.
# Lo inicia el servicio interno de la Thor (PServerBinder) o "su".
# Solo ejecuta las órdenes de la lista de abajo; cualquier otra se ignora.
# Si la app deja de estar viva, devuelve todo a la normalidad y se cierra.

D=/data/media/0/Android/data/com.nikalbazu.ids/files/h
U="$1"
PKG=com.nikalbazu.ids
STOCK=com.odin.dualscreen.assistant
VERSION=11

[ -d "$D" ] || exit 1
if [ -f "$D/helper.pid" ]; then
  old=$(cat "$D/helper.pid")
  [ -n "$old" ] && [ "$old" != "$$" ] && kill "$old" 2>/dev/null
fi
echo $$ > "$D/helper.pid"

own() { chown "$U:$U" "$1" 2>/dev/null; chmod 666 "$1" 2>/dev/null; }
num() { case "$1" in ''|*[!0-9]*) return 1;; esac; return 0; }

# Panel de fábrica: se duerme mientras IDS lo reemplace (también si el sistema lo reinicia al abrir la consola)
# Lista de paquetes del panel de fábrica: el conocido + los que la app detecte al presionar el botón AYN
stock_list() { echo "$STOCK"; [ -f "$D/stock.list" ] && cat "$D/stock.list"; }
stock_pids() { for k in $(stock_list | sort -u); do pgrep -f "$k" 2>/dev/null; done; }
okpkg() { case "$1" in ''|*[!a-zA-Z0-9._]*|android|com.android.systemui|$PKG) return 1;; esac; return 0; }
block() { touch "$D/block.on"; for k in $(stock_list | sort -u); do okpkg "$k" && pm disable-user --user 0 "$k" >/dev/null 2>&1; done; }
unblock() { rm -f "$D/block.on"; for k in $(stock_list | sort -u); do okpkg "$k" && pm enable "$k" >/dev/null 2>&1; done; }
# Quitar para siempre: igual que el bloqueo, pero NO se revierte si IDS se cierra. Anota el resultado de cada paquete.
blockperm() {
  touch "$D/block.on" "$D/perm.on"; : > "$D/block.tmp"
  for k in $(stock_list | sort -u); do
    okpkg "$k" || continue
    am force-stop "$k" >/dev/null 2>&1
    pm disable-user --user 0 "$k" >/dev/null 2>&1
    if pm list packages -d 2>/dev/null | grep -qx "package:$k"; then echo "$k ok" >> "$D/block.tmp"
    elif pm list packages 2>/dev/null | grep -qx "package:$k"; then echo "$k fallo" >> "$D/block.tmp"
    else echo "$k noexiste" >> "$D/block.tmp"; fi
  done
  mv "$D/block.tmp" "$D/block.txt"; own "$D/block.txt"
}
unblockperm() { rm -f "$D/perm.on"; unblock; rm -f "$D/block.txt"; }
probe() {
  dumpsys window windows 2>/dev/null | awk '
    /Window #/ { if (pkg != "" && disp != "") print disp, pkg; pkg = ""; disp = "" }
    { for (i = 1; i <= NF; i++) {
        if ($i ~ /^mDisplayId=/) { split($i, a, "="); disp = a[2] }
        if ($i ~ /^package=/) { split($i, b, "="); pkg = b[2] } } }
    END { if (pkg != "" && disp != "") print disp, pkg }' | sort -u > "$D/probe.tmp"
  ps -A -o NAME 2>/dev/null | grep -iE "odin|ayn|tcc|dual" | sed 's/^/proc /' >> "$D/probe.tmp"
  mv "$D/probe.tmp" "$D/probe_$1.txt"; own "$D/probe_$1.txt"
}
thaw() { rm -f "$D/freeze.on"; for p in $(stock_pids); do kill -CONT "$p" 2>/dev/null; done; }
freeze() { touch "$D/freeze.on"; for p in $(stock_pids); do kill -STOP "$p" 2>/dev/null; done; }
keep_frozen() { [ -f "$D/freeze.on" ] && for p in $(stock_pids); do kill -STOP "$p" 2>/dev/null; done; }

cleanup() {
  for p in $(stock_pids); do kill -CONT "$p" 2>/dev/null; done
  [ -f "$D/block.on" ] && [ ! -f "$D/perm.on" ] && unblock
  fx_stop
  [ -n "$KW" ] && kill "$KW" 2>/dev/null
  pkill -f "getevent -ql" 2>/dev/null
  rm -f "$D/helper.pid"
  exit 0
}

# Lista de sensores de temperatura del chip (se arma una vez)
ZONES=""
for z in /sys/class/thermal/thermal_zone*; do
  t=$(cat "$z/type" 2>/dev/null)
  case "$t" in
    *cpu*|*gpu*|*CPU*|*GPU*|*cpuss*|*gpuss*) ZONES="$ZONES $z/temp";;
  esac
done

# Luces de los sticks: busca luces RGB del sistema (anillos de los joysticks)
LEDS=""
for l in /sys/class/leds/*; do
  n=$(basename "$l" | tr 'A-Z' 'a-z')
  case "$n" in *joy*|*stick*|*rgb*|*ring*|*l_led*|*r_led*|*left*|*right*) LEDS="$LEDS $l";; esac
done

led_set() { # lado r g b brillo
  side="$1"; r="$2"; g="$3"; b="$4"; br="$5"
  for l in $LEDS; do
    n=$(basename "$l" | tr 'A-Z' 'a-z')
    case "$side" in
      l) case "$n" in *right*|*_r|*_r_*|*r:*|*-r|*r_led*) continue;; esac;;
      r) case "$n" in *left*|*_l|*_l_*|*l:*|*-l|*l_led*) continue;; esac;;
    esac
    if [ -f "$l/multi_intensity" ]; then
      out=""
      for c in $(cat "$l/multi_index" 2>/dev/null); do
        case "$c" in red) out="$out $r";; green) out="$out $g";; blue) out="$out $b";; *) out="$out 0";; esac
      done
      [ -z "$out" ] && out="$r $g $b"
      echo $out > "$l/multi_intensity" 2>/dev/null
      echo "$br" > "$l/brightness" 2>/dev/null
    else
      case "$n" in
        *red*) echo $(( r * br / 255 )) > "$l/brightness" 2>/dev/null;;
        *green*) echo $(( g * br / 255 )) > "$l/brightness" 2>/dev/null;;
        *blue*) echo $(( b * br / 255 )) > "$l/brightness" 2>/dev/null;;
        *) echo "$br" > "$l/brightness" 2>/dev/null;;
      esac
    fi
  done
}

# Luces de los sticks: las aplica el sistema de AYN al cambiar estos ajustes
lights_set() { # encendido colorIzq colorDer brillo%
  if [ "$1" = 0 ]; then settings put system joystick_light_enabled "0,0"; return; fi
  settings put system joystick_led_light_picker_color "#ff$2,#ff$3"
  settings put system led_light_brightness_percent "$(awk "BEGIN{printf \"%.2f\", $4/100}")"
  [ "$(settings get system joystick_light_enabled)" = "1,1" ] || settings put system joystick_light_enabled "1,1"
}

FXP=""
fx_stop() { [ -n "$FXP" ] && kill "$FXP" 2>/dev/null; FXP=""; }

diag() {
  {
    echo "# luces: $LEDS"
    for l in $LEDS; do echo "$(basename $l): $(ls $l | tr '\n' ' ')"; done
    echo "# ajustes del sistema relacionados"
    settings list system 2>/dev/null | grep -iE 'led|light|rgb|joy|stick|lamp|ring'
    settings list global 2>/dev/null | grep -iE 'led|light|rgb|joy|stick|lamp|ring'
    settings list secure 2>/dev/null | grep -iE 'led|rgb|joy|stick|lamp'
  } > "$D/diag.tmp" 2>/dev/null
  mv "$D/diag.tmp" "$D/diag.txt"; own "$D/diag.txt"
}

soc_temp() {
  best=0
  for f in $ZONES; do
    v=$(cat "$f" 2>/dev/null)
    num "$v" || continue
    [ "$v" -gt "$best" ] && [ "$v" -lt 150000 ] && best=$v
  done
  echo $best
}

telemetry() {
  {
    echo "v=$VERSION"
    echo "t=$(date +%s)"
    echo "soc=$(soc_temp)"
    for p in 0 3 7; do
      c=/sys/devices/system/cpu/cpufreq/policy$p
      echo "cur$p=$(cat $c/scaling_cur_freq 2>/dev/null)"
      echo "max$p=$(cat $c/scaling_max_freq 2>/dev/null)"
      echo "hw$p=$(cat $c/cpuinfo_max_freq 2>/dev/null)"
    done
    echo "gpu=$(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null)"
    echo "gpumax=$(cat /sys/class/kgsl/kgsl-3d0/max_gpuclk 2>/dev/null)"
    echo "gpuload=$(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null | tr -d ' %')"
    for h in /sys/class/hwmon/hwmon*; do
      [ -f "$h/fan1_input" ] && echo "fan=$(cat $h/fan1_input 2>/dev/null)"
    done
    echo "bl=$(ls /sys/class/backlight 2>/dev/null | tr '\n' ',')"
    for b in /sys/class/backlight/*; do
      [ -d "$b" ] || continue
      m=$(cat "$b/max_brightness" 2>/dev/null); v=$(cat "$b/brightness" 2>/dev/null)
      num "$m" && num "$v" && [ "$m" -gt 0 ] && echo "blp_$(basename $b)=$(( v * 100 / m ))"
    done
    echo "leds=$(ls /sys/class/leds 2>/dev/null | tr '\n' ',')"
    echo "stock=$(pidof $STOCK)"
    CT=/data/data/com.aure.clustertune/files/datastore
    for f in android_tuner_settings android_tuner; do
      if [ -f "$CT/$f.preferences_pb" ] && [ "$CT/$f.preferences_pb" -nt "$D/ct_$f.pb" ]; then
        cp "$CT/$f.preferences_pb" "$D/ct_$f.pb" && own "$D/ct_$f.pb"
      fi
    done
  } > "$D/tele.tmp"
  mv "$D/tele.tmp" "$D/tele.txt"
  own "$D/tele.txt"
}

# Revisión completa: prueba cada función, la lee de vuelta y la deja como estaba. Todo queda en Descargas/IDS.
R=/data/media/0/Download/IDS
chk() { # nombre esperado obtenido
  if [ "$2" = "$3" ]; then echo "OK   $1 ($3)"; else echo "FALLA $1: se pidió '$2' y quedó '$3'"; fi
}
audit() {
  mkdir -p "$R/apk"
  {
    echo "# Revisión IDS v$VERSION · $(date)"
    echo "uid=$(id) modelo=$(getprop ro.product.model) build=$(getprop ro.build.display.id) android=$(getprop ro.build.version.release)"
    echo; echo "## Pruebas"
    for k in fan_mode performance_mode; do
      old=$(settings get system $k)
      if [ $k = fan_mode ]; then new=4; [ "$old" = "4" ] && new=1; else new=1; [ "$old" = "1" ] && new=0; fi
      settings put system $k $new; sleep 1; chk "ajuste $k" "$new" "$(settings get system $k)"; settings put system $k "$old"
    done
    old=$(settings get system screen_brightness); settings put system screen_brightness 77; sleep 1
    chk "brillo arriba (screen_brightness)" 77 "$(settings get system screen_brightness)"; settings put system screen_brightness "$old"
    for b in /sys/class/backlight/*; do
      [ -d "$b" ] || continue
      m=$(cat $b/max_brightness 2>/dev/null); o=$(cat $b/brightness 2>/dev/null); want=$(( m / 2 ))
      echo "$want" > $b/brightness 2>/dev/null; sleep 1
      echo "     backlight $(basename $b): max=$m antes=$o actual_brightness=$(cat $b/actual_brightness 2>/dev/null)"
      chk "backlight $(basename $b) a la mitad" "$want" "$(cat $b/brightness 2>/dev/null)"; echo "$o" > $b/brightness 2>/dev/null
    done
    for p in 0 3 7; do
      c=/sys/devices/system/cpu/cpufreq/policy$p; [ -d $c ] || continue
      old=$(cat $c/scaling_max_freq); lst=$(cat $c/scaling_available_frequencies 2>/dev/null)
      want=$(echo $lst | tr ' ' '\n' | sort -n | awk '{a[NR]=$1} END {print a[int(NR/2)+1]}')
      echo "     cpu policy$p: frecuencias: $lst"
      [ -n "$want" ] && { echo "$want" > $c/scaling_max_freq 2>/dev/null; sleep 2; chk "tope CPU policy$p (a los 2 s)" "$want" "$(cat $c/scaling_max_freq)"; echo "$old" > $c/scaling_max_freq; }
    done
    g=/sys/class/kgsl/kgsl-3d0
    old=$(cat $g/max_gpuclk 2>/dev/null); echo "     gpu: disponibles=$(cat $g/gpu_available_frequencies 2>/dev/null) max_gpuclk=$old"
    want=$(cat $g/gpu_available_frequencies 2>/dev/null | tr ' ' '\n' | sort -n | awk '{a[NR]=$1} END {print a[int(NR/2)+1]}')
    [ -n "$want" ] && { echo "$want" > $g/max_gpuclk 2>/dev/null; sleep 2; chk "tope GPU max_gpuclk" "$want" "$(cat $g/max_gpuclk)"; echo "$old" > $g/max_gpuclk; }
    ls -d /sys/class/devfreq/*gpu* /sys/class/devfreq/*kgsl* 2>/dev/null | while read d; do echo "     devfreq $d: max_freq=$(cat $d/max_freq 2>/dev/null) disponibles=$(cat $d/available_frequencies 2>/dev/null)"; done
    w=$(settings get global wifi_on); echo "     wifi_on=$w bluetooth_on=$(settings get global bluetooth_on) zen_mode=$(settings get global zen_mode)"
    echo; echo "## Luces (/sys/class/leds)"
    for l in /sys/class/leds/*; do echo "$(basename $l): max=$(cat $l/max_brightness 2>/dev/null) brillo=$(cat $l/brightness 2>/dev/null) archivos=$(ls $l | tr '\n' ' ')"; done
    echo; echo "## Pantallas"; dumpsys display 2>/dev/null | grep -E "mDisplayId=|mBrightness|mUniqueId|DisplayDeviceInfo\{" | head -40
    echo; echo "## Paquetes de AYN / Odin"; pm list packages -f 2>/dev/null | grep -iE "odin|ayn|thor|dual|pserver"
    echo "## Desactivados"; pm list packages -d 2>/dev/null
    echo; echo "## Servicios del sistema (AYN)"; service list 2>/dev/null | grep -iE "odin|ayn|pserver|fan|led|light|perf|game"
    echo; echo "## Procesos"; ps -A -o PID,USER,NAME 2>/dev/null | grep -iE "odin|ayn|fan|led|light|perf|dual"
    echo; echo "## Ajustes system"; settings list system; echo "## Ajustes global"; settings list global; echo "## Ajustes secure"; settings list secure
    echo; echo "## Propiedades AYN"; getprop | grep -iE "odin|ayn|fan|led|perf|persist.vendor.*(mode|light)"
  } > "$R/revision.txt" 2>&1
  for k in $(pm list packages 2>/dev/null | sed 's/package://' | grep -iE "odin|ayn|thor"); do
    okpkg "$k" || continue
    pm path "$k" 2>/dev/null | sed 's/package://' | while read f; do cp "$f" "$R/apk/${k}_$(basename $f)" 2>/dev/null; done
  done
  chmod -R 777 "$R" 2>/dev/null
  cp "$R/revision.txt" "$D/revision.txt"; own "$D/revision.txt"
  am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Download/IDS/revision.txt" >/dev/null 2>&1
  echo done > "$D/audit.ok"; own "$D/audit.ok"
}

run() {
  v="$1"; a="$2"; b="$3"; c="$4"; d="$5"; e="$6"
  case "$v" in
    fan) num "$a" && settings put system fan_mode "$a";;
    perf) num "$a" && settings put system performance_mode "$a";;
    hz) num "$a" && settings put system peak_refresh_rate "$a.0" && settings put system min_refresh_rate "$a.0";;
    bright) num "$a" && settings put system screen_brightness "$a";;
    blight)
      case "$a" in ''|*/*|*..*) return;; esac
      num "$b" || return
      [ "$b" -gt 100 ] && b=100
      m=$(cat "/sys/class/backlight/$a/max_brightness" 2>/dev/null); num "$m" || m=255
      echo $(( b * m / 100 )) > "/sys/class/backlight/$a/brightness";;
    restoreall)
      rm -f "$D/perm.on" "$D/block.on" "$D/block.txt"
      : > "$D/restore.tmp"
      for k in $( (stock_list; pm list packages -d 2>/dev/null | sed 's/package://' | grep -iE "odin|ayn|thor|dual") | sort -u); do
        okpkg "$k" || continue
        pm enable "$k" >/dev/null 2>&1 && echo "$k" >> "$D/restore.tmp"
      done
      mv "$D/restore.tmp" "$D/restore.txt"; own "$D/restore.txt";;
    tempunblock) for k in $(stock_list | sort -u); do okpkg "$k" && pm enable "$k" >/dev/null 2>&1; done;;
    audit) rm -f "$D/audit.ok"; audit </dev/null >/dev/null 2>&1 &;;
    cap)
      case "$a" in 0|3|7) ;; *) return;; esac
      num "$b" && echo "$b" > "/sys/devices/system/cpu/cpufreq/policy$a/scaling_max_freq";;
    capreset)
      case "$a" in 0|3|7) ;; *) return;; esac
      cat "/sys/devices/system/cpu/cpufreq/policy$a/cpuinfo_max_freq" > "/sys/devices/system/cpu/cpufreq/policy$a/scaling_max_freq";;
    gpucap) num "$a" && echo "$a" > /sys/class/kgsl/kgsl-3d0/max_gpuclk;;
    wifi) case "$a" in on) svc wifi enable;; off) svc wifi disable;; esac;;
    bt) case "$a" in on) svc bluetooth enable;; off) svc bluetooth disable;; esac;;
    dnd) case "$a" in on) cmd notification set_dnd priority;; off) cmd notification set_dnd off;; esac;;
    shot)
      mkdir -p /data/media/0/Pictures/IDS
      f="/data/media/0/Pictures/IDS/captura_$(date +%Y%m%d_%H%M%S).png"
      screencap -p "$f" && chmod 666 "$f"
      am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Pictures/IDS/$(basename "$f")" >/dev/null 2>&1;;
    kill)
      case "$a" in *[!a-zA-Z0-9._]*|'') return;; esac
      [ "$a" = "$PKG" ] || am force-stop "$a";;
    killbg) am kill-all;;
    led)
      case "$a" in l|r|all) ;; *) return;; esac
      num "$b" && num "$c" && num "$d" && num "$e" && { fx_stop; led_set "$a" "$b" "$c" "$d" "$e"; };;
    ayn)
      case "$a" in
        fan_mode|performance_mode|is_charging_separation|force_landscape|keep_screen_on|joystick_light_enabled|joystick_led_light_picker_color|led_light_brightness_percent|dual_screen_brightness_level|secondary_screen_volume_level|screen_focus_lock|enable_system_key_focus_lock|vibrate_strength_value|flip_button_layout|charging_limit_power_limit) ;;
        *) return;;
      esac
      case "$b" in ''|*[!A-Za-z0-9_.,#-]*) return;; esac
      [ "$a" = force_landscape ] && [ "$b" = 0 ] && wm set-ignore-orientation-request false >/dev/null 2>&1
      settings put system "$a" "$b";;
    lights)
      # lights encendido(1/0) colorIzq colorDer brillo(0-100)  · colores RRGGBB
      case "$b$c" in *[!0-9A-Fa-f]*) return;; esac
      num "$a" && num "$d" || return
      fx_stop; lights_set "$a" "$b" "$c" "$d";;
    fxb)
      # Respirar: solo cambia la intensidad del color (no apaga ni toca el brillo general)
      case "$a" in *[!0-9A-Fa-f]*|'') return;; esac
      fx_stop; lights_set 1 "$a" "$a" "$b"
      r=$((16#${a%????})); g=$((16#$(echo $a | cut -c3-4))); bl=$((16#${a#????}))
      ( while true; do for k in 100 85 68 52 38 28 38 52 68 85; do
          settings put system joystick_led_light_picker_color "$(printf '#ff%02X%02X%02X,#ff%02X%02X%02X' $((r*k/100)) $((g*k/100)) $((bl*k/100)) $((r*k/100)) $((g*k/100)) $((bl*k/100)))"
          sleep 0.3; done; done ) &
      FXP=$!;;
    fxr)
      num "$a" || return
      fx_stop; lights_set 1 FF0000 FF0000 "$a"
      ( while true; do for col in FF0000 FF6E00 FFDC00 5AFF00 00FFA0 00A0FF 3C00FF C800FF FF008C; do
          settings put system joystick_led_light_picker_color "#ff$col,#ff$col"; sleep 0.5; done; done ) &
      FXP=$!;;
    dbright)
      # Brillo de la pantalla de abajo como lo hace AYN: DisplayManager.setBrightness(pantalla, 0..1)
      num "$a" && num "$b" || return
      case "$c" in ''|*[!0-9.]*) return;; esac
      service call display "$a" i32 "$b" f "$c" >/dev/null 2>&1;;
    ctopen)
      case "$a" in
        picker) am start -n com.aure.clustertune/.TileControlActivity -a com.aure.clustertune.action.SHOW_PROFILE_PICKER_OVERLAY -f 0x18000000 >/dev/null 2>&1;;
        tuner) am start -n com.aure.clustertune/.TileControlActivity -a com.aure.clustertune.action.SHOW_COMPACT_TUNER_OVERLAY -f 0x18000000 >/dev/null 2>&1;;
      esac;;
    fxoff) fx_stop;;
    diag) diag;;
    probe) case "$a" in 1|2) probe "$a";; esac;;
    stockadd) okpkg "$a" && { grep -qx "$a" "$D/stock.list" 2>/dev/null || echo "$a" >> "$D/stock.list"; } && [ -f "$D/freeze.on" ] && freeze;;
    stockclear) rm -f "$D/stock.list";;
    block) block;;
    blockperm) blockperm;;
    unblockperm) unblockperm;;
    land)
      case "$a" in
        on) settings put system accelerometer_rotation 0; settings put system user_rotation 0; wm set-ignore-orientation-request true >/dev/null 2>&1;;
        off) wm set-ignore-orientation-request false >/dev/null 2>&1; settings put system accelerometer_rotation 1;;
      esac;;
    unblock) unblock;;
    snap)
      case "$a" in 1|2) ;; *) return;; esac
      { settings list system | sed 's/^/system:/'
        settings list global | sed 's/^/global:/'
        settings list secure | sed 's/^/secure:/'; } > "$D/snap.tmp" 2>/dev/null
      mv "$D/snap.tmp" "$D/snap_$a.txt"; own "$D/snap_$a.txt";;
    put)
      case "$a" in system|global|secure) ;; *) return;; esac
      case "$b" in ''|*[!A-Za-z0-9_.]*) return;; esac
      case "$c" in ''|*[!A-Za-z0-9_.,:-]*) return;; esac
      settings put "$a" "$b" "$c";;
    freeze) freeze;;
    thaw) thaw;;
    stop) cleanup;;
  esac
}

# Vigilante de botones: anota la última tecla presionada (sirve para el botón AYN)
( getevent -ql 2>/dev/null | while read -r dev typ code val; do
    [ "$typ" = "EV_KEY" ] || continue
    [ "$val" = "DOWN" ] || continue
    echo "$code $(date +%s)$RANDOM" > "$D/key.tmp"
    mv "$D/key.tmp" "$D/key.evt"
    own "$D/key.evt"
  done ) &
KW=$!

miss=0
n=0
while true; do
  if [ $((n % 7)) -eq 0 ]; then
    if pidof $PKG >/dev/null 2>&1; then miss=0; else miss=$((miss+1)); fi
    [ $miss -ge 20 ] && cleanup
    keep_frozen
  fi
  for f in "$D"/cmd_*; do
    [ -f "$f" ] || continue
    while read -r c1 c2 c3 c4 c5 c6; do run "$c1" "$c2" "$c3" "$c4" "$c5" "$c6"; done < "$f"
    rm -f "$f"
  done
  [ $((n % 7)) -eq 0 ] && telemetry
  n=$((n+1))
  sleep 0.15
done
