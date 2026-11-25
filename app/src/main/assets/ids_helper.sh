#!/system/bin/sh
# Integral Dual Screen - ayudante con privilegios.
# Lo inicia el servicio interno de la Thor (PServerBinder) o "su".
# Solo ejecuta las órdenes de la lista de abajo; cualquier otra se ignora.
# Si la app deja de estar viva, devuelve todo a la normalidad y se cierra.

D=/data/media/0/Android/data/com.nikalbazu.ids/files/h
U="$1"
PKG=com.nikalbazu.ids
STOCK=com.odin.dualscreen.assistant
VERSION=3

[ -d "$D" ] || exit 1
if [ -f "$D/helper.pid" ]; then
  old=$(cat "$D/helper.pid")
  [ -n "$old" ] && [ "$old" != "$$" ] && kill "$old" 2>/dev/null
fi
echo $$ > "$D/helper.pid"

own() { chown "$U:$U" "$1" 2>/dev/null; chmod 666 "$1" 2>/dev/null; }
num() { case "$1" in ''|*[!0-9]*) return 1;; esac; return 0; }

thaw() { for p in $(pidof $STOCK); do kill -CONT "$p" 2>/dev/null; done; }
freeze() { for p in $(pidof $STOCK); do kill -STOP "$p" 2>/dev/null; done; }

cleanup() {
  thaw
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
    echo "leds=$(ls /sys/class/leds 2>/dev/null | tr '\n' ',')"
    echo "stock=$(pidof $STOCK)"
  } > "$D/tele.tmp"
  mv "$D/tele.tmp" "$D/tele.txt"
  own "$D/tele.txt"
}

run() {
  v="$1"; a="$2"; b="$3"
  case "$v" in
    fan) num "$a" && settings put system fan_mode "$a";;
    perf) num "$a" && settings put system performance_mode "$a";;
    hz) num "$a" && settings put system peak_refresh_rate "$a.0" && settings put system min_refresh_rate "$a.0";;
    bright) num "$a" && settings put system screen_brightness "$a";;
    blight)
      case "$a" in ''|*/*|*..*) return;; esac
      num "$b" && echo "$b" > "/sys/class/backlight/$a/brightness";;
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
  if pidof $PKG >/dev/null 2>&1; then miss=0; else miss=$((miss+1)); fi
  [ $miss -ge 20 ] && cleanup
  for f in "$D"/cmd_*; do
    [ -f "$f" ] || continue
    while read -r c1 c2 c3; do run "$c1" "$c2" "$c3"; done < "$f"
    rm -f "$f"
  done
  [ $((n % 2)) -eq 0 ] && telemetry
  n=$((n+1))
  sleep 0.5
done
