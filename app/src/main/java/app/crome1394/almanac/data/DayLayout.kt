package app.crome1394.almanac.data

/**
 * Places overlapping events side by side in a day column. Events that overlap form a cluster;
 * each takes the first free column, and every event in a cluster shares its column count.
 * An event also widens into free columns to its right, as Google Calendar does.
 */
object DayLayout {
    data class Item<T>(val value: T, val start: Int, val end: Int)
    data class Placed<T>(val value: T, val start: Int, val end: Int, val col: Int, val span: Int, val cols: Int)

    /** [minHeight] is the shortest an event is drawn (minutes), so tiny ones still overlap visually. */
    fun <T> layout(items: List<Item<T>>, minHeight: Int = 20): List<Placed<T>> {
        val sorted = items.sortedWith(compareBy<Item<T>> { it.start }.thenByDescending { it.end })
        val out = mutableListOf<Placed<T>>()
        var cluster = mutableListOf<Pair<Item<T>, Int>>()
        val colEnds = mutableListOf<Int>()
        var clusterEnd = Int.MIN_VALUE

        fun flush() {
            if (cluster.isEmpty()) return
            val cols = colEnds.size
            for ((it, col) in cluster) {
                val drawEnd = maxOf(it.end, it.start + minHeight)
                // Grow right while no event in the next column overlaps this one.
                var span = 1
                while (col + span < cols && cluster.none { (o, oc) ->
                        oc == col + span && o.start < drawEnd && maxOf(o.end, o.start + minHeight) > it.start
                    }) span++
                out += Placed(it.value, it.start, it.end, col, span, cols)
            }
            cluster = mutableListOf()
            colEnds.clear()
        }

        for (item in sorted) {
            val drawEnd = maxOf(item.end, item.start + minHeight)
            if (item.start >= clusterEnd) { flush(); clusterEnd = Int.MIN_VALUE }
            var col = colEnds.indexOfFirst { it <= item.start }
            if (col == -1) { colEnds += drawEnd; col = colEnds.lastIndex } else colEnds[col] = drawEnd
            cluster += item to col
            clusterEnd = maxOf(clusterEnd, drawEnd)
        }
        flush()
        return out
    }
}
