package ai.pleos.challenge.arrival

/** Display groups retain each visit's original number and favorite, without moving saved coordinates. */
data class JourneyMarker(val point: JourneyPoint, val visits: List<IndexedValue<JourneyStop>>) {
    fun representative(selected: String?): IndexedValue<JourneyStop> =
        visits.firstOrNull { it.value.id == selected }
            ?: visits.firstOrNull { it.value.favorite }
            ?: visits.last()

}

fun journeyMarkers(stops: List<JourneyStop>, project: (JourneyPoint) -> Pair<Double, Double>): List<JourneyMarker> {
    val groups = mutableListOf<MutableList<IndexedValue<JourneyStop>>>()
    stops.withIndex().forEach { visit ->
        val point = visit.value.point?.takeIf { it.valid } ?: return@forEach
        val (x, y) = project(point)
        val overlapping = groups.filter { group ->
            group.any {
                val (gx, gy) = project(it.value.point!!)
                (x - gx) * (x - gx) + (y - gy) * (y - gy) < 72.0 * 72.0
            }
        }
        val merged = (overlapping.flatten() + visit).sortedBy { it.index }.toMutableList()
        groups.removeAll(overlapping.toSet())
        groups.add(merged)
    }
    return groups.map { JourneyMarker(it.first().value.point!!, it.toList()) }
}
