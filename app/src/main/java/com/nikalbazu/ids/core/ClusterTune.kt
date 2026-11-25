package com.nikalbazu.ids.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File

/** Estado de la app ClusterTune (Auto Tune y perfil activo), leído de sus archivos por el ayudante. */
data class CtState(val installed: Boolean = false, val autoTune: Boolean = false, val profile: String? = null, val source: String? = null)

object ClusterTune {
    const val PKG = "com.aure.clustertune"
    val state = MutableStateFlow(CtState())
    private var stamp = 0L

    fun refresh(ctx: Context, dir: File) {
        val installed = runCatching { ctx.packageManager.getPackageInfo(PKG, 0); true }.getOrDefault(false)
        val fs = File(dir, "ct_android_tuner_settings.pb"); val fp = File(dir, "ct_android_tuner.pb")
        val st = fs.lastModified() + fp.lastModified()
        if (st == stamp && state.value.installed == installed) return
        stamp = st
        val s = runCatching { prefs(fs.readBytes()) }.getOrDefault(emptyMap())
        val p = runCatching { prefs(fp.readBytes()) }.getOrDefault(emptyMap())
        val eff = (p["effective_profile_state"] as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }
        state.value = CtState(
            installed = installed,
            autoTune = s["auto_tune_enabled"] as? Boolean ?: false,
            profile = eff?.optString("name")?.takeIf { it.isNotBlank() },
            source = eff?.optString("source"),
        )
    }

    // ---- Lector mínimo del formato de DataStore (protobuf PreferenceMap) ----
    private class Buf(val b: ByteArray, var i: Int = 0, val end: Int = b.size) {
        fun more() = i < end
        fun varint(): Long { var r = 0L; var sh = 0; while (true) { val x = b[i++].toInt() and 0xFF; r = r or ((x and 0x7F).toLong() shl sh); if (x < 0x80) return r; sh += 7 } }
        fun sub(): Buf { val n = varint().toInt(); val s = Buf(b, i, i + n); i += n; return s }
        fun skip(wire: Int) { when (wire) { 0 -> varint(); 1 -> i += 8; 2 -> i += varint().toInt(); 5 -> i += 4 } }
    }

    private fun prefs(bytes: ByteArray): Map<String, Any> {
        val out = HashMap<String, Any>()
        val top = Buf(bytes)
        while (top.more()) {
            val tag = top.varint().toInt()
            if (tag shr 3 != 1 || tag and 7 != 2) { top.skip(tag and 7); continue }
            val e = top.sub(); var key = ""; var v: Any? = null
            while (e.more()) {
                val t = e.varint().toInt()
                when (t shr 3) {
                    1 -> { val s = e.sub(); key = String(s.b, s.i, s.end - s.i) }
                    2 -> {
                        val val_ = e.sub()
                        while (val_.more()) {
                            val vt = val_.varint().toInt()
                            when {
                                vt shr 3 == 1 && vt and 7 == 0 -> v = val_.varint() != 0L
                                vt shr 3 == 5 && vt and 7 == 2 -> { val s = val_.sub(); v = String(s.b, s.i, s.end - s.i) }
                                else -> val_.skip(vt and 7)
                            }
                        }
                    }
                    else -> e.skip(t and 7)
                }
            }
            if (key.isNotEmpty() && v != null) out[key] = v
        }
        return out
    }
}
