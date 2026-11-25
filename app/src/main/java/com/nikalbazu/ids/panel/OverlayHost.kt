package com.nikalbazu.ids.panel

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nikalbazu.ids.core.Store
import com.nikalbazu.ids.ui.PanelRoot

/** Ventana del panel sobre la pantalla de abajo. Se muestra y oculta sin perder lo que había debajo. */
class OverlayHost(private val base: Context) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private var wm: WindowManager? = null
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    var displayId: Int = -1; private set

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    /** Busca la pantalla de abajo: la elegida en ajustes o la primera que no sea la principal. */
    fun findBottomDisplay(): Display? {
        val dm = base.getSystemService(DisplayManager::class.java)
        val wanted = Store.settings.value.bottomDisplay
        val all = dm.displays.toList()
        return all.firstOrNull { it.displayId == wanted && wanted != Display.DEFAULT_DISPLAY }
            ?: all.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
            ?: all.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    private fun attach(): Boolean {
        if (view != null) return true
        val display = findBottomDisplay() ?: return false
        displayId = display.displayId
        val wctx = base.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        val manager = wctx.getSystemService(WindowManager::class.java)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; title = "IDS panel" }
        val v = ComposeView(wctx).apply {
            setViewTreeLifecycleOwner(this@OverlayHost)
            setViewTreeSavedStateRegistryOwner(this@OverlayHost)
            setViewTreeViewModelStoreOwner(this@OverlayHost)
            setContent { PanelRoot() }
        }
        manager.addView(v, p)
        registry.currentState = Lifecycle.State.RESUMED
        wm = manager; view = v; params = p
        return true
    }

    private val handler = Handler(Looper.getMainLooper())

    /** Aparece con animación: primero la ventana, luego el contenido entra deslizándose. */
    fun show(): Boolean {
        if (!attach()) return false
        handler.removeCallbacksAndMessages(null)
        view?.visibility = View.VISIBLE
        setFlags(touchable = true, focusable = false)
        Store.panelVisible.value = true
        return true
    }

    /** Se va con animación y, al terminar, la ventana deja pasar los toques a lo de abajo. */
    fun hide() {
        Store.panelVisible.value = false
        setFlags(touchable = false, focusable = false)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!Store.panelVisible.value) view?.visibility = View.GONE }, 320)
    }

    fun toggle(): Boolean = if (Store.panelVisible.value) { hide(); true } else show()

    fun setFocusable(focusable: Boolean) {
        if (Store.panelVisible.value) setFlags(touchable = true, focusable = focusable)
    }

    private fun setFlags(touchable: Boolean, focusable: Boolean) {
        val p = params ?: return
        var f = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (!focusable) f = f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        p.flags = f
        runCatching { wm?.updateViewLayout(view, p) }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { view?.let { wm?.removeView(it) } }
        view = null
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        Store.panelVisible.value = false
    }
}
