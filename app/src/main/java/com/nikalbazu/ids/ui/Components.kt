package com.nikalbazu.ids.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun T(
    text: String,
    size: TextUnit = 18.sp,
    weight: FontWeight = FontWeight.Bold,
    color: Color? = null,
    modifier: Modifier = Modifier,
    num: Boolean = false,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 2,
) {
    val t = LocalTok.current
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color ?: t.tx, fontSize = size, fontWeight = weight,
            fontFamily = if (num) t.numFont else t.font, textAlign = align,
            lineHeight = size * 1.15f,
        ),
    )
}

/** Tarjeta con profundidad: brillo de color en modo negro, sombra suave en modo claro. */
fun Modifier.card(t: Tok, accent: Int? = null, radius: Dp = 32.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val ac = accent?.let { t.v[it % t.v.size] }
    return if (t.light) {
        this.shadow(10.dp, shape, ambientColor = Color(0x332B2A33), spotColor = Color(0x332B2A33))
            .background(if (accent != null) t.tile[accent % t.tile.size] else t.surf, shape)
    } else {
        this.background(t.surf, shape)
            .then(if (accent != null) Modifier.background(t.tile[accent % t.tile.size], shape) else Modifier)
            .border(1.5.dp, ac?.copy(alpha = 0.75f) ?: t.line, shape)
            .border(1.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.12f to Color.Transparent), shape)
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    accent: Int? = null,
    radius: Dp = 32.dp,
    padding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    gap: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTok.current
    Column(modifier.card(t, accent, radius).padding(padding), verticalArrangement = Arrangement.spacedBy(gap), content = content)
}

fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(), indication = null, onClick = onClick,
)

/** Botón píldora. Elegido = relleno; sin elegir = fondo suave con línea fina. */
@Composable
fun Pill(
    label: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    size: TextUnit = 17.sp,
    onClick: () -> Unit,
) {
    val t = LocalTok.current
    val shape = if (height == Dp.Unspecified) RoundedCornerShape(50) else RoundedCornerShape(height / 2)
    val base = if (selected) {
        (if (t.light) modifier.shadow(6.dp, shape, ambientColor = t.sel.copy(alpha = 0.4f), spotColor = t.sel.copy(alpha = 0.5f)) else modifier)
            .background(t.sel, shape)
    } else {
        modifier.background(t.track, shape).border(1.dp, if (t.light) Color(0x142B2A33) else Color.White.copy(alpha = 0.07f), shape)
    }
    Box((if (height == Dp.Unspecified) base else base.height(height)).clip(shape).tap(onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        T(label, size, FontWeight.ExtraBold, if (selected) t.selTx else t.tx, maxLines = 1, align = TextAlign.Center)
    }
}

@Composable
fun SegRow(
    options: List<String>,
    selected: String?,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    size: TextUnit = 17.sp,
    onPick: (String) -> Unit,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> Pill(o, o == selected, Modifier.weight(1f), height, size) { onPick(o) } }
    }
}

@Composable
fun LabeledSeg(title: String, options: List<String>, selected: String?, labelWidth: Dp = 130.dp, height: Dp = 54.dp, onPick: (String) -> Unit) {
    val t = LocalTok.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        T(title, 17.sp, FontWeight.ExtraBold, t.mu, Modifier.width(labelWidth))
        SegRow(options, selected, Modifier.weight(1f), height, 16.sp, onPick)
    }
}

/** Anillo de datos con punta redondeada. */
@Composable
fun Ring(
    value: String,
    unit: String,
    fraction: Float,
    color: Color,
    size: Dp,
    stroke: Dp = 12.dp,
    valueSize: TextUnit = 26.sp,
    unitSize: TextUnit = 14.sp,
    track: Color? = null,
) {
    val t = LocalTok.current
    val tr = track ?: t.gt
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = this.size.minDimension - sw
            val tl = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            drawArc(tr, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            drawArc(color, -90f, 360f * fraction.coerceIn(0.02f, 1f), false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(value, valueSize, FontWeight.Black, num = true, maxLines = 1)
            if (unit.isNotEmpty()) T(unit, unitSize, FontWeight.ExtraBold, t.mu, maxLines = 1)
        }
    }
}

@Composable
fun Switch(on: Boolean) {
    val t = LocalTok.current
    Box(
        Modifier.width(56.dp).height(32.dp).clip(CircleShape)
            .background(if (on) Color(0xFF0B0B0E) else if (t.light) Color(0xFFE2DDD5) else Color(0xFF2C2C33))
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) t.sel else if (t.light) Color.White else Color(0xFF77736F)))
    }
}

/**
 * Barra grande que se desliza con el dedo, con − y + a los lados.
 * onSlide recibe 0..1 mientras se mueve; onRelease se llama al soltar (ahí se aplica el cambio real).
 */
@Composable
fun StepBar(
    text: String,
    fraction: Float,
    fill: Color,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onSlide: ((Float) -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
) {
    val t = LocalTok.current
    val slide by rememberUpdatedState(onSlide)
    val release by rememberUpdatedState(onRelease)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).height(height).clip(RoundedCornerShape(height / 2)).background(t.track)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { release?.invoke() },
                        onDragCancel = { release?.invoke() },
                    ) { change, _ ->
                        change.consume()
                        slide?.invoke((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        slide?.invoke((pos.x / size.width).coerceIn(0f, 1f))
                        release?.invoke()
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(height / 2)).background(fill))
            T(text, 17.sp, FontWeight.Black, modifier = Modifier.padding(start = 18.dp), maxLines = 1)
        }
    }
}

/** Columna que se desliza hacia arriba y abajo (curva del ventilador). */
@Composable
fun VBar(text: String, fraction: Float, fill: Color, modifier: Modifier = Modifier, onSlide: (Float) -> Unit, onRelease: () -> Unit) {
    val t = LocalTok.current
    val slide by rememberUpdatedState(onSlide)
    val release by rememberUpdatedState(onRelease)
    Box(
        modifier.clip(RoundedCornerShape(26.dp)).background(t.track)
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { release() }, onDragCancel = { release() }) { change, _ ->
                    change.consume()
                    slide(1f - (change.position.y / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) { detectTapGestures { pos -> slide(1f - (pos.y / size.height).coerceIn(0f, 1f)); release() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(26.dp)).background(fill))
        T(text, 22.sp, FontWeight.Black, modifier = Modifier.padding(bottom = 12.dp), num = true)
    }
}

/** Tarjeta que se enciende y apaga. */
@Composable
fun ToggleCard(name: String, hint: String, on: Boolean, modifier: Modifier = Modifier, height: Dp = 116.dp, onClick: () -> Unit) {
    val t = LocalTok.current
    val shape = RoundedCornerShape(30.dp)
    val m = if (on) modifier.shadow(12.dp, shape, ambientColor = t.sel.copy(alpha = 0.4f), spotColor = t.sel.copy(alpha = 0.5f)).background(t.sel, shape)
    else modifier.card(t, null, 30.dp)
    Row(m.height(height).clip(shape).tap(onClick).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(name, 20.sp, FontWeight.Black, if (on) t.selTx else t.tx)
            if (hint.isNotEmpty()) T(hint, 15.sp, FontWeight.Bold, if (on) t.selTx.copy(alpha = 0.7f) else t.mu)
        }
        Switch(on)
    }
}

@Composable
fun Title(text: String) = T(text, 44.sp, FontWeight.Black, maxLines = 1)

@Composable
fun Grid(columns: Int, count: Int, gap: Dp = 14.dp, item: @Composable RowScope.(Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0 until (count + columns - 1) / columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0 until columns) {
                    val i = r * columns + c
                    if (i < count) item(i) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun Tabs(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().card(t, null, 34.dp).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, label) ->
            val on = id == selected
            val shape = RoundedCornerShape(28.dp)
            Box(
                Modifier.weight(1f).height(56.dp).clip(shape).background(if (on) t.sel else Color.Transparent, shape).tap { onPick(id) },
                contentAlignment = Alignment.Center,
            ) { T(label, 18.sp, FontWeight.ExtraBold, if (on) t.selTx else t.mu, maxLines = 1) }
        }
    }
}


/** Rueda de colores como la de AYN: tocas o arrastras para elegir el tono y la saturación. */
@Composable
fun ColorWheel(color: Color, modifier: Modifier = Modifier, onPick: (Color, Boolean) -> Unit) {
    val pick by rememberUpdatedState(onPick)
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.rgb((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()), it) }
    fun at(pos: Offset, w: Float, h: Float, final: Boolean) {
        val cx = w / 2; val cy = h / 2; val r = minOf(w, h) / 2
        val dx = pos.x - cx; val dy = pos.y - cy
        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(r)
        val hue = ((Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()) + 360) % 360).toFloat()
        pick(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, dist / r, 1f))), final)
    }
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val w = size.width.toFloat(); val h = size.height.toFloat()
                at(down.position, w, h, false)
                var last = down.position
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull() ?: break
                    if (!c.pressed) break
                    last = c.position; c.consume(); at(last, w, h, false)
                }
                at(last, w, h, true)
            }
        },
    ) {
        val r = size.minDimension / 2
        val hues = (0..360 step 30).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f))) }
        drawCircle(Brush.sweepGradient(hues), r)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), radius = r), r)
        val a = Math.toRadians(hsv[0].toDouble()); val d = hsv[1] * r
        val p = Offset(center.x + (kotlin.math.cos(a) * d).toFloat(), center.y + (kotlin.math.sin(a) * d).toFloat())
        drawCircle(Color.White, 18.dp.toPx(), p)
        drawCircle(color.copy(alpha = 1f), 13.dp.toPx(), p)
    }
}
