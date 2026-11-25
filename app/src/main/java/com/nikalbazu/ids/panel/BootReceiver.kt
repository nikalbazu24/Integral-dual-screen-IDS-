package com.nikalbazu.ids.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nikalbazu.ids.core.Store
import com.nikalbazu.ids.priv.Device

/** Al encender la consola: inicia el servicio y vuelve a aplicar el perfil de CPU/GPU. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            PanelService.send(context)
            if ("boot" in Store.settings.value.automation && Store.settings.value.ct != "Stock") {
                Device.applyCaps(Store.settings.value.ct, Store.settings.value.caps)
            }
        }
    }
}
