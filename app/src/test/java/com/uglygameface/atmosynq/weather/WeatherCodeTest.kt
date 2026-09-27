package com.uglygameface.atmosynq.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodeTest {
    @Test
    fun mapsOfficialWmoConditionsUsedByAtmosynq() {
        assertEquals("Clear sky", WeatherCode.description(0))
        assertEquals("Moderate rain", WeatherCode.description(63))
        assertEquals("Heavy snowfall", WeatherCode.description(75))
        assertEquals("Heavy thunderstorm", WeatherCode.description(97))
        assertEquals("Thunderstorm with heavy hail", WeatherCode.description(99))
    }

    @Test
    fun clearSkySymbolTracksDayAndNight() {
        assertEquals("☀️", WeatherCode.symbol(0, true))
        assertEquals("🌙", WeatherCode.symbol(0, false))
    }
}
