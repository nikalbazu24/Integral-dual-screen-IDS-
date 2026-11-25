package com.nikalbazu.ids.panel

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nikalbazu.ids.core.Store

/** Acceso en los ajustes rápidos de Android para mostrar u ocultar el panel. */
class PanelTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = if (Store.panelVisible.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        PanelService.send(this, PanelService.ACTION_TOGGLE)
        qsTile?.apply {
            state = if (Store.panelVisible.value) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }
}
