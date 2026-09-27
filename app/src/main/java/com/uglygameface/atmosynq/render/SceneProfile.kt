package com.uglygameface.atmosynq.render

import com.uglygameface.atmosynq.location.SavedLocation
import kotlin.math.abs

enum class SettlementKind {
    METRO,
    CITY,
    TOWN,
    LOCAL
}

enum class TerrainKind {
    FLAT,
    ROLLING,
    HIGHLAND,
    MOUNTAIN
}

enum class LatitudeBand {
    TROPICAL,
    WARM,
    TEMPERATE,
    COOL,
    POLAR
}

data class SceneProfile(
    val label: String,
    val settlement: SettlementKind,
    val terrain: TerrainKind,
    val latitudeBand: LatitudeBand,
    val elevationM: Double,
    val population: Long?
) {
    val mountainScale: Float
        get() =
            when (terrain) {
                TerrainKind.FLAT -> 0.015f
                TerrainKind.ROLLING -> 0.16f
                TerrainKind.HIGHLAND -> 0.46f
                TerrainKind.MOUNTAIN -> 1.0f
            }

    val urbanStrength: Float
        get() =
            when (settlement) {
                SettlementKind.METRO -> 1.0f
                SettlementKind.CITY -> 0.78f
                SettlementKind.TOWN -> 0.44f
                SettlementKind.LOCAL -> 0.18f
            }

    companion object {
        val DEFAULT =
            SceneProfile(
                label = "Local weather",
                settlement = SettlementKind.LOCAL,
                terrain = TerrainKind.FLAT,
                latitudeBand = LatitudeBand.TEMPERATE,
                elevationM = 0.0,
                population = null
            )
    }
}

/**
 * Converts globally available place metadata into a visual environment profile.
 *
 * This deliberately avoids a finite city whitelist. Every saved coordinate can
 * resolve to a profile, while richer geocoder metadata improves the result.
 */
object SceneProfileResolver {
    fun resolve(location: SavedLocation?): SceneProfile {
        if (location == null) return SceneProfile.DEFAULT

        val elevation = (location.elevationM ?: 0.0).coerceAtLeast(-420.0)
        val population = location.population
        val feature = location.featureCode.orEmpty().uppercase()

        val settlement =
            when {
                population != null && population >= 1_000_000L ->
                    SettlementKind.METRO
                population != null && population >= 100_000L ->
                    SettlementKind.CITY
                population != null && population >= 20_000L ->
                    SettlementKind.TOWN
                feature == "PPLC" ->
                    SettlementKind.METRO
                feature.startsWith("PPLA") ->
                    SettlementKind.CITY
                feature.startsWith("PPL") ->
                    SettlementKind.TOWN
                else ->
                    SettlementKind.LOCAL
            }

        val terrain =
            when {
                elevation >= 850.0 -> TerrainKind.MOUNTAIN
                elevation >= 350.0 -> TerrainKind.HIGHLAND
                elevation >= 120.0 -> TerrainKind.ROLLING
                else -> TerrainKind.FLAT
            }

        val latitude = abs(location.latitude)
        val latitudeBand =
            when {
                latitude < 23.5 -> LatitudeBand.TROPICAL
                latitude < 36.0 -> LatitudeBand.WARM
                latitude < 58.0 -> LatitudeBand.TEMPERATE
                latitude < 67.0 -> LatitudeBand.COOL
                else -> LatitudeBand.POLAR
            }

        return SceneProfile(
            label = location.heroLabel(),
            settlement = settlement,
            terrain = terrain,
            latitudeBand = latitudeBand,
            elevationM = elevation,
            population = population
        )
    }
}
