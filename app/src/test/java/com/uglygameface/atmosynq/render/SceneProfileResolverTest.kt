package com.uglygameface.atmosynq.render

import com.uglygameface.atmosynq.location.LocationSource
import com.uglygameface.atmosynq.location.SavedLocation
import com.uglygameface.atmosynq.location.TerrainContextClient
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneProfileResolverTest {
    @Test
    fun lowReliefTownDoesNotBecomeMountainScene() {
        val profile =
            SceneProfileResolver.resolve(
                SavedLocation(
                    latitude = 41.85,
                    longitude = -72.64,
                    savedAtEpochMs = 1L,
                    displayName = "Low-relief town",
                    population = 29_000,
                    elevationM = 17.0,
                    reliefM = 85.0,
                    featureCode = "PPL",
                    source = LocationSource.SEARCH
                )
            )

        assertEquals(SettlementKind.TOWN, profile.settlement)
        assertEquals(TerrainKind.FLAT, profile.terrain)
        assertEquals(LatitudeBand.WARM, profile.latitudeBand)
    }

    @Test
    fun denseGlobalCityDoesNotInventMountains() {
        val profile =
            SceneProfileResolver.resolve(
                SavedLocation(
                    latitude = 35.68,
                    longitude = 139.69,
                    savedAtEpochMs = 1L,
                    displayName = "Large city",
                    population = 14_000_000,
                    elevationM = 40.0,
                    reliefM = 110.0,
                    featureCode = "PPLC",
                    source = LocationSource.SEARCH
                )
            )

        assertEquals(SettlementKind.METRO, profile.settlement)
        assertEquals(TerrainKind.FLAT, profile.terrain)
        assertEquals(LatitudeBand.TEMPERATE, profile.latitudeBand)
    }

    @Test
    fun strongLocalReliefAllowsMountainScene() {
        val profile =
            SceneProfileResolver.resolve(
                SavedLocation(
                    latitude = 46.0,
                    longitude = 7.0,
                    savedAtEpochMs = 1L,
                    displayName = "Mountain place",
                    population = 8_000,
                    elevationM = 1_200.0,
                    reliefM = 920.0,
                    featureCode = "PPL",
                    source = LocationSource.SEARCH
                )
            )

        assertEquals(SettlementKind.TOWN, profile.settlement)
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
