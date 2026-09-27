package com.uglygameface.atmosynq.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationSceneClassifierTest {
    @Test
    fun lowReliefTownDoesNotBecomeMountainScene() {
        val profile =
            LocationSceneClassifier.classify(
                displayName = "Windsor, Connecticut",
                latitude = 41.85,
                population = 29_000,
                elevationM = 17.0,
                reliefM = 85.0
            )

        assertEquals(SettlementKind.TOWN, profile.settlement)
        assertEquals(TerrainKind.FLAT, profile.terrain)
        assertEquals(LatitudeZone.TEMPERATE, profile.latitudeZone)
    }

    @Test
    fun denseGlobalCityBecomesMetroWithoutInventingMountains() {
        val profile =
            LocationSceneClassifier.classify(
                displayName = "Tokyo",
                latitude = 35.68,
                population = 14_000_000,
                elevationM = 40.0,
                reliefM = 110.0
            )

        assertEquals(SettlementKind.METRO, profile.settlement)
        assertEquals(TerrainKind.FLAT, profile.terrain)
        assertEquals(LatitudeZone.TEMPERATE, profile.latitudeZone)
    }

    @Test
    fun strongLocalReliefAllowsMountainScene() {
        val profile =
            LocationSceneClassifier.classify(
                displayName = "Mountain place",
                latitude = 46.0,
                population = 8_000,
                elevationM = 1_200.0,
                reliefM = 920.0
            )

        assertEquals(SettlementKind.SMALL_TOWN, profile.settlement)
        assertEquals(TerrainKind.MOUNTAIN, profile.terrain)
    }

    @Test
    fun terrainParserUsesReliefAcrossSamples() {
        val terrain =
            TerrainContextClient().parse(
                """{"elevation":[100.0,145.0,310.0,90.0,205.0]}"""
            )

        assertEquals(100.0, terrain.centerElevationM, 0.001)
        assertEquals(90.0, terrain.minElevationM, 0.001)
        assertEquals(310.0, terrain.maxElevationM, 0.001)
        assertEquals(220.0, terrain.reliefM, 0.001)
    }
}
