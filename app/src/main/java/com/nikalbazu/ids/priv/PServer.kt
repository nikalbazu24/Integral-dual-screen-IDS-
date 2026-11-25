// SPDX-License-Identifier: MIT
// Cliente mínimo de PServerBinder basado en ClusterTune (c) 2026 Aurelio Bernal, licencia MIT:
// https://github.com/AurelioB/ClusterTune (app/src/main/java/com/aure/clustertune/root/PServerHostLauncher.kt)
package com.nikalbazu.ids.priv

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.TimeUnit

/** Formas de lanzar el ayudante con privilegios: el servicio interno de la Thor o "su". */
object PServer {

    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    private fun service(): IBinder? = runCatching {
        Class.forName("android.os.ServiceManager")
            .getMethod("checkService", String::class.java)
            .invoke(null, "PServerBinder") as? IBinder
    }.getOrNull()

    val available: Boolean get() = service()?.isBinderAlive == true

    /** Envía un comando una sola vez (máximo 255 bytes). No se reintenta. */
    fun launch(command: String): Boolean {
        if (command.isBlank() || command.toByteArray(Charsets.UTF_8).size > 255) return false
        val binder = service() ?: return false
        if (!binder.isBinderAlive) return false
        val req = Parcel.obtain()
        val res = Parcel.obtain()
        return try {
            // El firmware lee: cantidad de textos, luego cada texto. El último ("0") desactiva la captura de salida.
            req.writeInt(2)
            req.writeString(command)
            req.writeString("0")
            binder.transact(0, req, res, 0)
        } catch (_: Throwable) {
            false
        } finally {
            res.recycle(); req.recycle()
        }
    }

    /** Alternativa para consolas con root. */
    fun launchWithSu(command: String): Boolean = runCatching {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        p.waitFor(10, TimeUnit.SECONDS)
        true
    }.getOrDefault(false)
}
