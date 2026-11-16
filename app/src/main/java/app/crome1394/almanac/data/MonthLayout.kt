package app.crome1394.almanac.data

/**
 * Lays out one week row of the month grid: every event becomes a bar from its first to its last
 * day in the week (columns 0..6), stacked in lanes as Google does, so an event spanning several
 * days is one bar instead of a chip on each day.
 */
object MonthLayout {
    /** [start]..[end] are inclusive columns. */
    data class Item<T>(val value: T, val start: Int, val end: Int)
    data class Bar<T>(val value: T, val start: Int, val end: Int, val lane: Int)
    /** [more]: column → how many events are hidden there ("+N", drawn in the last lane). */
    data class Week<T>(val bars: List<Bar<T>>, val more: Map<Int, Int>)

    /**
     * Places [items] in the order given (callers put multi-day events first, then each day's
     * events by time), each in the lowest lane free on all its days. At most [lanes] lanes are
     * shown; where a day has more, its last lane shows "+N" instead.
     */
    fun <T> layout(items: List<Item<T>>, lanes: Int): Week<T> {
        val occupied = mutableListOf<BooleanArray>()
        val placed = items.map { raw ->
            val it = raw.copy(start = raw.start.coerceIn(0, 6), end = raw.end.coerceIn(raw.start.coerceIn(0, 6), 6))
            var lane = occupied.indexOfFirst { row -> (it.start..it.end).none { c -> row[c] } }
            if (lane < 0) { occupied += BooleanArray(7); lane = occupied.lastIndex }
            for (c in it.start..it.end) occupied[lane][c] = true
            Bar(it.value, it.start, it.end, lane)
        }
        if (lanes <= 0) {
            val more = (0..6).associateWith { c -> placed.count { c in it.start..it.end } }.filterValues { it > 0 }
            return Week(emptyList(), more)
        }
        val overflow = (0..6).filter { c -> placed.any { it.lane >= lanes && c in it.start..it.end } }.toSet()
        val visible = placed.filter { b ->
            b.lane < lanes - 1 || (b.lane == lanes - 1 && (b.start..b.end).none { it in overflow })
        }
        val more = overflow.associateWith { c -> placed.count { b -> b !in visible && c in b.start..b.end } }
        return Week(visible, more)
    }
}
