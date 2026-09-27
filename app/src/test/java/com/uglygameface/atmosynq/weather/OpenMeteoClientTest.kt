package com.uglygameface.atmosynq.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoClientTest {
    @Test
    fun parsesSharedCurrentHourlyAndDailyWeatherReport() {
        val report = OpenMeteoClient().parseReport(
            """
            {
              "timezone": "America/New_York",
              "current": {
                "temperature_2m": 18.0,
                "apparent_temperature": 17.0,
                "relative_humidity_2m": 71,
                "is_day": 1,
                "precipitation": 0.1,
                "rain": 0.1,
                "showers": 0.0,
                "snowfall": 0.0,
                "weather_code": 61,
                "cloud_cover": 80,
                "wind_speed_10m": 16.0,
                "wind_direction_10m": 270,
                "wind_gusts_10m": 25.0,
                "visibility": 12000
              },
              "hourly": {
                "time": ["2026-09-27T19:00", "2026-09-27T20:00"],
                "temperature_2m": [17.0, 16.0],
                "apparent_temperature": [16.0, 15.0],
                "precipitation_probability": [35, 50],
                "weather_code": [2, 0],
                "wind_speed_10m": [14.0, 12.0],
                "is_day": [1, 0]
              },
              "daily": {
                "time": ["2026-09-27"],
                "weather_code": [61],
                "temperature_2m_max": [21.0],
                "temperature_2m_min": [12.0],
                "precipitation_probability_max": [55],
                "sunrise": ["2026-09-27T06:45"],
                "sunset": ["2026-09-27T18:40"]
              }
            }
            """.trimIndent()
        )

        assertEquals("America/New_York", report.current.timezone)
        assertEquals(17.0, report.current.apparentTemperatureC, 0.001)
        assertEquals(71.0, report.current.relativeHumidityPct, 0.001)
        assertEquals(2, report.hourly.size)
        assertTrue(report.hourly[0].isDay)
        assertFalse(report.hourly[1].isDay)
        assertEquals(50, report.hourly[1].precipitationProbabilityPct)
        assertEquals(1, report.daily.size)
        assertEquals(21.0, report.daily[0].highC, 0.001)
        assertEquals("2026-09-27T18:40", report.daily[0].sunsetIsoLocal)
    }
}
