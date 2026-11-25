package com.nikalbazu.ids.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.nikalbazu.ids.core.Settings

/** Colores y letras activos del panel. */
data class Tok(
    val light: Boolean,
    val bg: Color,
    val surf: Brush,
    val surfSolid: Color,
    val tx: Color,
    val mu: Color,
    val track: Color,
    val sel: Color,
    val selTx: Color,
    val gt: Color,
    val line: Color,
    val v: List<Color>,
    val tile: List<Brush>,
    val font: FontFamily,
    val numFont: FontFamily,
)

val LocalTok = compositionLocalOf<Tok> { error("Sin tema") }

private fun c(hex: Long) = Color(hex)

val PALETTES: Map<String, Pair<List<Color>, Color>> = mapOf(
    "Prisma" to (listOf(c(0xFFFF9B7A), c(0xFF5FD49A), c(0xFF7AA8FF), c(0xFFB39BFF), c(0xFFFFC35A)) to c(0xFFF4F1EC)),
    "Brasa" to (listOf(c(0xFFFF7A59), c(0xFFFFB547), c(0xFFFF5F7E), c(0xFFF2766B), c(0xFFFFD36B)) to c(0xFFFFB547)),
    "Océano" to (listOf(c(0xFF5EEAD4), c(0xFF38BDF8), c(0xFF60A5FA), c(0xFF818CF8), c(0xFF67E8F9)) to c(0xFF7DD3FC)),
    "Monocromo" to (listOf(c(0xFFE9E7EF), c(0xFFCFCDD6), c(0xFFFFFFFF), c(0xFFBDBBC4), c(0xFFDEDCE4)) to c(0xFFFFFFFF)),
)
private val LIGHT_V = listOf(c(0xFFF08A5D), c(0xFF36B37E), c(0xFF4F86E8), c(0xFF8F6BE0), c(0xFFE09A24))
private val LIGHT_T = listOf(c(0xFFFFD9C4), c(0xFFCFEBDB), c(0xFFD2E4F6), c(0xFFE4DAF6), c(0xFFF7E7B4))

/** Nombre visible → archivos de letra (se descargan al compilar; si faltan, se usa la del sistema). */
val FONTS = linkedMapOf(
    "Nunito" to listOf("nunito"),
    "Inter" to listOf("inter"),
    "Outfit" to listOf("outfit"),
    "Lexend" to listOf("lexend"),
    "Space Grotesk" to listOf("spacegrotesk"),
    "Legible" to listOf("atkinson_regular", "atkinson_bold"),
)

private val fontCache = HashMap<String, FontFamily>()

@OptIn(ExperimentalTextApi::class)
@SuppressLint("DiscouragedApi")
fun fontFamily(ctx: Context, name: String): FontFamily = fontCache.getOrPut(name) {
    val files = FONTS[name] ?: return@getOrPut FontFamily.Default
    val ids = files.map { ctx.resources.getIdentifier(it, "font", ctx.packageName) }
    if (ids.any { it == 0 }) return@getOrPut FontFamily.Default
    runCatching {
        if (ids.size == 2) {
            FontFamily(Font(ids[0], FontWeight.Normal), Font(ids[1], FontWeight.Bold))
        } else {
            val w = listOf(400, 500, 600, 700, 800, 900)
            FontFamily(w.map { wt ->
                Font(ids[0], FontWeight(wt), variationSettings = FontVariation.Settings(FontVariation.weight(wt)))
            })
        }
    }.getOrDefault(FontFamily.Default)
}

fun buildTok(ctx: Context, s: Settings): Tok {
    val light = s.design == "Aurora claro"
    val font = fontFamily(ctx, s.font)
    val num = if (s.techNumbers) fontFamily(ctx, "Space Grotesk") else font
    if (light) {
        return Tok(
            light = true, bg = c(0xFFF4F1EC), surf = Brush.verticalGradient(listOf(Color.White, Color.White)),
            surfSolid = Color.White, tx = c(0xFF2B2A33), mu = c(0xFF6F6B78), track = c(0xFFF1EDE7),
            sel = c(0xFF2B2A33), selTx = Color.White, gt = Color.White, line = c(0x1F2B2A33),
            v = LIGHT_V, tile = LIGHT_T.map { Brush.verticalGradient(listOf(it, it)) }, font = font, numFont = num,
        )
    }
    val (v, sel) = PALETTES[s.palette] ?: PALETTES.getValue("Prisma")
    return Tok(
        light = false, bg = Color.Black,
        surf = Brush.verticalGradient(listOf(c(0xFF16161B), c(0xFF0A0A0D))), surfSolid = c(0xFF0F0F13),
        tx = c(0xFFF4F1EC), mu = c(0xFF9D99A6), track = c(0xFF1C1C22), sel = sel, selTx = Color.Black,
        gt = c(0xFF202027), line = c(0xFF2E2D35), v = v,
        tile = v.map { Brush.verticalGradient(listOf(it.copy(alpha = 0.14f), it.copy(alpha = 0.04f))) },
        font = font, numFont = num,
    )
}
