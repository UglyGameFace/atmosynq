package com.uglygameface.atmosynq.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WeatherVisualMapperTest {
    private fun snapshot(
        isDay: Boolean = true,
        precipitation: Double = 0.0,
        rain: Double = 0.0,
        showers: Double = 0.0,
        snow: Double = 0.0,
        code: Int = 0,
        clouds: Double = 0.0,
        visibility: Double = 100_000.0,
        wind: Double = 0.0,
        gust: Double = 0.0,
        sunrise: String? = null,
        sunset: String? = null
    ) = WeatherSnapshot(
        fetchedAtEpochMs = 0L,
        timezone = "UTC",
        temperatureC = 20.0,
        isDay = isDay,
        precipitationMm = precipitation,
        rainMm = rain,
        showersMm = showers,
        snowfallCm = snow,
        weatherCode = code,
        cloudCoverPct = clouds,
        windSpeedKmh = wind,
        windDirectionDeg = 90.0,
        windGustKmh = gust,
        visibilityM = visibility,
        sunriseIsoLocal = sunrise,
        sunsetIsoLocal = sunset
    )

    @Test fun fallbackDayNightUsesIsDay() {
        assertEquals(1.0f, WeatherVisualMapper.map(snapshot(isDay = true)).daylight, 0.001f)
        assertEquals(0.0f, WeatherVisualMapper.map(snapshot(isDay = false)).daylight, 0.001f)
    }

    @Test fun daylightTransitionsAroundRealSunriseAndSunset() {
        val s = snapshot(sunrise = "2026-09-27T06:00", sunset = "2026-09-27T18:00")
        val noon = Instant.parse("2026-09-27T12:00:00Z").toEpochMilli()
        val midnight = Instant.parse("2026-09-27T00:00:00Z").toEpochMilli()
        assertTrue(WeatherVisualMapper.map(s, noon).daylight > 0.99f)
        assertTrue(WeatherVisualMapper.map(s, midnight).daylight < 0.01f)
    }

    @Test fun thunderstormMapsToLightning() {
        assertTrue(WeatherVisualMapper.map(snapshot(code = 99)).thunderIntensity > 0.9f)
    }

    @Test fun fogUsesVisibilityAndWeatherCode() {
        assertTrue(WeatherVisualMapper.map(snapshot(visibility = 300.0)).fogIntensity > 0.95f)
        assertTrue(WeatherVisualMapper.map(snapshot(code = 45)).fogIntensity >= 0.65f)
    }

    @Test fun snowConditionNeverInventsRainFromTotalPrecipitation() {
        val snow = WeatherVisualMapper.map(
            snapshot(precipitation = 4.0, rain = 0.0, showers = 0.0, snow = 1.2, code = 73)
        )
        assertTrue(snow.snowIntensity > 0f)
        assertEquals(0.0f, snow.rainIntensity, 0.001f)
    }

    @Test fun reportedPrecipitationConditionStillRendersWhenAccumulationBucketIsZero() {
        val drizzle = WeatherVisualMapper.map(snapshot(code = 51))
        val snow = WeatherVisualMapper.map(snapshot(code = 71))
        assertTrue(drizzle.rainIntensity >= 0.10f)
        assertTrue(snow.snowIntensity >= 0.12f)
    }

    @Test fun liquidRainAndShowersAreCombined() {
        val combined = WeatherVisualMapper.map(snapshot(rain = 1.0, showers = 1.0, code = 63))
        val single = WeatherVisualMapper.map(snapshot(rain = 1.0, showers = 0.0, code = 63))
        assertTrue(combined.rainIntensity > single.rainIntensity)
    }

    @Test fun heavyThunderstormCode97ProducesLightning() {
        assertTrue(WeatherVisualMapper.map(snapshot(code = 97)).thunderIntensity >= 0.85f)
    }

    @Test fun cachedSunriseClockTimeRollsForwardAcrossOfflineMidnight() {
        val yesterday = snapshot(
            isDay = false,
            sunrise = "2026-09-26T06:30",
            sunset = "2026-09-26T18:30"
        )
        val nextDayNoon = Instant.parse("2026-09-27T12:00:00Z").toEpochMilli()
        assertTrue(WeatherVisualMapper.map(yesterday, nextDayNoon).daylight > 0.99f)
    }

    @Test fun rainAndSnowScaleWithObservedRate() {
        val light = WeatherVisualMapper.map(snapshot(precipitation = 0.2, rain = 0.2))
        val heavy = WeatherVisualMapper.map(snapshot(precipitation = 8.0, rain = 8.0))
        val snow = WeatherVisualMapper.map(snapshot(snow = 2.5))
        assertTrue(light.rainIntensity > 0f)
        assertTrue(heavy.rainIntensity > light.rainIntensity)
        assertTrue(snow.snowIntensity > 0.95f)
    }
}
