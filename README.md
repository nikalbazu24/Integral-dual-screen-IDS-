# Integral Dual Screen (IDS)

Panel para la **pantalla de abajo de la AYN Thor**: temperatura, modos de la consola, ClusterTune, luces, ventilador, perfiles por juego y herramientas. Se abre y se cierra con el **botón AYN**, sin cerrar lo que estabas usando.

## Instalar
1. Descarga el APK más nuevo desde [Releases](../../releases).
2. Ábrelo en la Thor (pantalla de arriba) y da los permisos que pide.
3. En "Botón AYN", toca **Aprender botón** y presiona el botón AYN una vez.

## Cómo funciona
- La app usa el servicio interno de la Thor (`PServerBinder`) para iniciar un pequeño ayudante con privilegios (`app/src/main/assets/ids_helper.sh`). No hace falta root.
- El ayudante solo acepta una lista fija de órdenes y, si la app se cierra, devuelve todo a la normalidad.
- Cada cambio en `main` se compila y se publica solo en Releases.

## Secretos opcionales del repositorio
- `IDS_REPORT_URL`: dirección donde llegan los reportes de error automáticos.
- `IDS_KEYSTORE_B64`, `IDS_KEYSTORE_PASSWORD`, `IDS_KEY_ALIAS`, `IDS_KEY_PASSWORD`: llave de firma propia. Sin ellos se usa una llave guardada en la caché de GitHub Actions.

## Créditos
- Cliente de `PServerBinder` basado en [ClusterTune](https://github.com/AurelioB/ClusterTune) (MIT).
- Letras de Google Fonts (licencia OFL), descargadas al compilar.
