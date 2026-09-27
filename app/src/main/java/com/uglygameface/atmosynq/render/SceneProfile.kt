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
    val reliefM: Double,
    val population: Long?
) {
    val mountainScale: Float
        get() =
            when (terrain) {
                TerrainKind.FLAT -> 0.0f
                TerrainKind.ROLLING -> 0.08f
                TerrainKind.HIGHLAND -> 0.42f
                TerrainKind.MOUNTAIN -> 1.0f
            }

    companion object {
        val DEFAULT =
            SceneProfile(
                label = "Local weather",
                settlement = SettlementKind.LOCAL,
                terrain = TerrainKind.FLAT,
                latitudeBand = LatitudeBand.TEMPERATE,
                elevationM = 0.0,
                reliefM = 0.0,
                population = null
            )
    }
}

/**
 * Turns globally available place metadata + local terrain relief into a visual profile.
 *
 * No city whitelist is used. Any resolved coordinate can produce a profile.
 */
object SceneProfileResolver {
    fun resolve(location: SavedLocation?): SceneProfile {
        if (location == null) return SceneProfile.DEFAULT

        val elevation = (location.elevationM ?: 0.0).coerceAtLeast(-420.0)
        val relief = (location.reliefM ?: 0.0).coerceAtLeast(0.0)
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
                relief >= 650.0 ||
                    (elevation >= 1_000.0 && relief >= 350.0) ->
                    TerrainKind.MOUNTAIN
                relief >= 320.0 || elevation >= 650.0 ->
                    TerrainKind.HIGHLAND
                relief >= 140.0 || elevation >= 250.0 ->
                    TerrainKind.ROLLING
                else ->
                    TerrainKind.FLAT
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
            reliefM = relief,
            population = population
        )
    }
}
