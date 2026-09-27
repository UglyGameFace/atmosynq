package com.uglygameface.atmosynq.location

import kotlin.math.abs

enum class SettlementKind {
    METRO,
    CITY,
    TOWN,
    SMALL_TOWN
}

enum class TerrainKind {
    FLAT,
    ROLLING,
    MOUNTAIN
}

enum class LatitudeZone {
    TROPICAL,
    WARM,
    TEMPERATE,
    COOL,
    POLAR
}

data class LocationSceneProfile(
    val displayName: String,
    val settlement: SettlementKind,
    val terrain: TerrainKind,
    val latitudeZone: LatitudeZone,
    val population: Long,
    val elevationM: Double,
    val reliefM: Double
)

object LocationSceneClassifier {
    fun from(
        location: SavedLocation
    ): LocationSceneProfile =
        classify(
            displayName = location.displayName,
            latitude = location.latitude,
            population = location.population,
            elevationM = location.elevationM,
            reliefM = location.reliefM
        )

    fun from(
        place: PlaceSearchResult,
        terrain: TerrainContext
    ): LocationSceneProfile =
        classify(
            displayName = place.shortLabel(),
            latitude = place.latitude,
            population = place.population,
            elevationM =
                place.elevationM
                    ?.takeIf { it.isFinite() }
                    ?: terrain.centerElevationM,
            reliefM = terrain.reliefM
        )

    fun classify(
        displayName: String,
        latitude: Double,
        population: Long,
        elevationM: Double,
        reliefM: Double
    ): LocationSceneProfile {
        val settlement =
            when {
                population >= 1_000_000L -> SettlementKind.METRO
                population >= 100_000L -> SettlementKind.CITY
                population >= 15_000L -> SettlementKind.TOWN
                else -> SettlementKind.SMALL_TOWN
            }

        val safeElevation = elevationM.takeIf { it.isFinite() } ?: 0.0
        val safeRelief = reliefM.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0

        val terrain =
            when {
                safeRelief >= 650.0 -> TerrainKind.MOUNTAIN
                safeElevation >= 1_000.0 && safeRelief >= 350.0 -> TerrainKind.MOUNTAIN
                safeRelief >= 180.0 || safeElevation >= 550.0 -> TerrainKind.ROLLING
                else -> TerrainKind.FLAT
            }

        val latitudeZone =
            when (abs(latitude)) {
                in 0.0..<23.5 -> LatitudeZone.TROPICAL
                in 23.5..<35.0 -> LatitudeZone.WARM
                in 35.0..<55.0 -> LatitudeZone.TEMPERATE
                in 55.0..<67.0 -> LatitudeZone.COOL
                else -> LatitudeZone.POLAR
            }

        return LocationSceneProfile(
            displayName = displayName.ifBlank { "Selected location" },
            settlement = settlement,
            terrain = terrain,
            latitudeZone = latitudeZone,
            population = population.coerceAtLeast(0L),
            elevationM = safeElevation,
            reliefM = safeRelief
        )
    }
}
