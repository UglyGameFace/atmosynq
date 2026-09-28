package com.uglygameface.atmosynq.worlds

import com.uglygameface.atmosynq.render.LatitudeBand
import com.uglygameface.atmosynq.render.SceneProfile
import com.uglygameface.atmosynq.render.SettlementKind
import com.uglygameface.atmosynq.render.TerrainKind
import com.uglygameface.atmosynq.weather.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldExperienceResolverTest {
    @Test
    fun mountainProfileResolvesToCabinWorld() {
        val world =
            WorldExperienceResolver.resolve(
                profile(
                    settlement = SettlementKind.LOCAL,
                    terrain = TerrainKind.MOUNTAIN,
                    latitude = LatitudeBand.COOL
                ),
                weather = null
            )

        assertEquals(WorldKind.CABIN, world.kind)
        assertEquals(3, world.eyeSpyTargets.size)
        assertEquals(
            "worlds/cabin_night_exterior.webp",
            world.primaryScene?.assetPath
        )
        assertEquals(
            "worlds/cabin_fireplace_interior.webp",
            world.interiorScene?.assetPath
        )
        assertTrue(
            world.primaryScene
                ?.hotspots
                ?.any {
                    it.action == WorldHotspotAction.ENTER
                } == true
        )
    }

    @Test
    fun wetWeatherGetsStormWorldBeforeCityDefault() {
        val weather =
            WeatherSnapshot(
                fetchedAtEpochMs = 1L,
                timezone = "UTC",
                temperatureC = 18.0,
                isDay = true,
                precipitationMm = 2.0,
                rainMm = 2.0,
                showersMm = 0.0,
                snowfallCm = 0.0,
                weatherCode = 61,
                cloudCoverPct = 95.0,
                windSpeedKmh = 20.0,
                windDirectionDeg = 180.0,
                windGustKmh = 32.0,
                visibilityM = 8_000.0,
                sunriseIsoLocal = null,
                sunsetIsoLocal = null
            )

        assertEquals(
            WorldKind.STORM,
            WorldExperienceResolver.resolve(
                profile(
                    settlement = SettlementKind.CITY,
                    terrain = TerrainKind.FLAT,
                    latitude = LatitudeBand.TEMPERATE
                ),
                weather
            ).kind
        )
    }

    @Test
    fun dryMetroGetsCityWorld() {
        assertEquals(
            WorldKind.CITY,
            WorldExperienceResolver.resolve(
                profile(
                    settlement = SettlementKind.METRO,
                    terrain = TerrainKind.FLAT,
                    latitude = LatitudeBand.TEMPERATE
                ),
                weather = null
            ).kind
        )
    }

    @Test
    fun ordinaryTemperateLocalProfileGetsHighResolutionForestScene() {
        val world =
            WorldExperienceResolver.resolve(
                profile(
                    settlement = SettlementKind.LOCAL,
                    terrain = TerrainKind.FLAT,
                    latitude = LatitudeBand.TEMPERATE
                ),
                weather = null
            )

        assertEquals(WorldKind.FOREST, world.kind)
        assertEquals(
            "worlds/forest_mist_lake.webp",
            world.primaryScene?.assetPath
        )
        assertEquals(1440, world.primaryScene?.sourceWidth)
        assertEquals(2160, world.primaryScene?.sourceHeight)
    }

    @Test
    fun targetsStayInsideNormalizedSceneSpace() {
        val worlds =
            listOf(
                WorldExperienceResolver.resolve(
                    profile(
                        SettlementKind.LOCAL,
                        TerrainKind.MOUNTAIN,
                        LatitudeBand.COOL
                    ),
                    null
                ),
                WorldExperienceResolver.resolve(
                    profile(
                        SettlementKind.CITY,
                        TerrainKind.FLAT,
                        LatitudeBand.TEMPERATE
                    ),
                    null
                ),
                WorldExperienceResolver.resolve(
                    profile(
                        SettlementKind.LOCAL,
                        TerrainKind.FLAT,
                        LatitudeBand.TROPICAL
                    ),
                    null
                )
            )

        worlds.flatMap { it.eyeSpyTargets }.forEach { target ->
            val region = target.region
            assertTrue(region.left in 0f..1f)
            assertTrue(region.top in 0f..1f)
            assertTrue(region.right in 0f..1f)
            assertTrue(region.bottom in 0f..1f)
            assertTrue(region.left < region.right)
            assertTrue(region.top < region.bottom)
            assertTrue(
                region.contains(
                    region.centerX,
                    region.centerY
                )
            )
        }
    }

    private fun profile(
        settlement: SettlementKind,
        terrain: TerrainKind,
        latitude: LatitudeBand
    ): SceneProfile =
        SceneProfile(
            label = "Test",
            settlement = settlement,
            terrain = terrain,
            latitudeBand = latitude,
            elevationM = 0.0,
            reliefM = 0.0,
            population = null
        )
}
