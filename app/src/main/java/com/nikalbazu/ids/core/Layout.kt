package com.nikalbazu.ids.core

import kotlin.math.abs

/** Una tarjeta del Inicio en la cuadrícula de 12 × 6. */
data class Tile(val id: String, val x: Int, val y: Int, val w: Int, val h: Int) {
    fun hits(o: Tile) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
    fun inside() = x >= 0 && y >= 0 && x + w <= Grid.COLS && y + h <= Grid.ROWS
}

data class TileInfo(val name: String, val hint: String, val color: Int, val minW: Int, val minH: Int, val defW: Int, val defH: Int)

object Grid {
    const val COLS = 12
    const val ROWS = 6

    /** color = índice de la paleta (0..4) */
    val INFO = linkedMapOf(
        "temp" to TileInfo("Temperatura", "Anillo con colores", 1, 2, 2, 4, 3),
        "consola" to TileInfo("Consola", "Pantallas, Hz, modo, ventilador", 2, 3, 2, 6, 3),
        "ct" to TileInfo("ClusterTune", "Perfil, topes y Auto Tune", 3, 3, 1, 10, 1),
        "stats" to TileInfo("Rendimiento", "CPU, GPU, RAM y consumo", 0, 2, 1, 12, 1),
        "brillo" to TileInfo("Brillo", "Las dos pantallas", 4, 1, 1, 1, 5),
        "volumen" to TileInfo("Volumen", "Barra de sonido", 2, 1, 1, 1, 5),
        "accesos" to TileInfo("Accesos", "Direct power, landscape…", 1, 2, 1, 10, 1),
        "luces" to TileInfo("Luces", "Color de los sticks", 2, 2, 1, 3, 2),
        "fan" to TileInfo("Ventilador", "Velocidad y modo", 1, 2, 1, 3, 2),
        "bateria" to TileInfo("Batería", "Porcentaje y tiempo", 1, 2, 1, 3, 2),
        "reloj" to TileInfo("Reloj", "Hora grande", 4, 2, 1, 4, 2),
        "musica" to TileInfo("Música", "Anterior, pausa, siguiente", 0, 2, 1, 3, 1),
        "juego" to TileInfo("Juego abierto", "Cerrar o ver su perfil", 4, 3, 1, 6, 1),
    )

    val DEFAULT = listOf(
        Tile("brillo", 0, 0, 1, 5), Tile("temp", 1, 0, 4, 3), Tile("consola", 5, 0, 6, 3), Tile("volumen", 11, 0, 1, 5),
        Tile("accesos", 1, 3, 10, 1), Tile("ct", 1, 4, 10, 1), Tile("stats", 0, 5, 12, 1),
    )

    /** La tarjeta fija queda donde se soltó; las demás se quedan si caben o buscan el hueco libre más cercano. */
    fun pack(list: List<Tile>, fixedId: String?): Pair<List<Tile>, List<String>> {
        val out = ArrayList<Tile>(); val lost = ArrayList<String>()
        val order = list.sortedWith(compareBy<Tile>({ if (it.id == fixedId) 0 else 1 }, { it.y }, { it.x }))
        for (c in order) {
            if (c.inside() && out.none { it.hits(c) }) { out += c; continue }
            var best: Tile? = null; var bd = Double.MAX_VALUE
            for (y in 0..ROWS - c.h) for (x in 0..COLS - c.w) {
                val t = c.copy(x = x, y = y)
                if (out.any { it.hits(t) }) continue
                val d = abs(x - c.x) + abs(y - c.y) * 1.5
                if (d < bd) { bd = d; best = t }
            }
            if (best != null) out += best else lost += c.id
        }
        return out to lost
    }

    /** Si no cabe, achica de a poco las más grandes (sin bajar de su mínimo) y después las vuelve a agrandar donde haya espacio. */
    fun fit(list: List<Tile>, fixedId: String?): List<Tile>? {
        var cur = list
        repeat(40) {
            val (out, lost) = pack(cur, fixedId)
            if (lost.isEmpty()) return regrow(out, list, fixedId)
            val cand = cur.filter { it.id != fixedId }.mapNotNull { c ->
                val i = INFO[c.id] ?: return@mapNotNull null
                val canW = c.w > i.minW; val canH = c.h > i.minH
                if (canW || canH) Triple(c, canW, canH) else null
            }.maxByOrNull { it.first.w * it.first.h } ?: return null
            val (c, canW, canH) = cand
            cur = cur.map { if (it.id != c.id) it else if (canW && (c.w >= c.h || !canH)) it.copy(w = it.w - 1) else it.copy(h = it.h - 1) }
        }
        return null
    }

    private fun regrow(out: List<Tile>, orig: List<Tile>, fixedId: String?): List<Tile> {
        val want = orig.associateBy { it.id }
        val res = out.toMutableList()
        repeat(12) {
            var grew = false
            for (i in res.indices) {
                val c = res[i]; if (c.id == fixedId) continue
                val o = want[c.id] ?: continue
                val opts = listOf(c.copy(w = c.w + 1), c.copy(h = c.h + 1), c.copy(x = c.x - 1, w = c.w + 1), c.copy(y = c.y - 1, h = c.h + 1))
                val t = opts.firstOrNull { t -> t.w <= o.w && t.h <= o.h && t.inside() && res.none { it.id != c.id && it.hits(t) } }
                if (t != null) { res[i] = t; grew = true }
            }
            if (!grew) return res
        }
        return res
    }

    /** Pone una tarjeta nueva en el primer hueco libre (o achica otras para hacerle lugar). */
    fun add(list: List<Tile>, id: String): List<Tile>? {
        val i = INFO[id] ?: return null
        val sizes = listOf(i.defW to i.defH, maxOf(i.minW, i.defW - 2) to maxOf(i.minH, i.defH - 1), i.minW to i.minH)
        for ((w, h) in sizes) for (y in 0..ROWS - h) for (x in 0..COLS - w) {
            val t = Tile(id, x, y, w, h)
            if (list.none { it.hits(t) }) return list + t
        }
        return fit(listOf(Tile(id, 0, 0, i.minW, i.minH)) + list, id)
    }
}
