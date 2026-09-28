package com.uglygameface.atmosynq.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WeatherReportJsonTest {
    @Test
    fun reportRoundTripsThroughJson() {
        val report =
            WeatherReport(
                current =
                    WeatherSnapshot(
                        fetchedAtEpochMs = 1234L,
                        timezone = "America/New_York",
                        temperatureC = 12.5,
                        isDay = true,
                        precipitationMm = 1.2,
                        rainMm = 1.0,
                        showersMm = 0.2,
                        snowfallCm = 0.0,
                        weatherCode = 61,
                        cloudCoverPct = 82.0,
                        windSpeedKmh = 18.0,
                        windDirectionDeg = 210.0,
                        windGustKmh = 29.0,
                        visibilityM = 11_000.0,
                        sunriseIsoLocal = "2026-09-28T06:46",
                        sunsetIsoLocal = "2026-09-28T18:39",
                        apparentTemperatureC = 11.8,
                        relativeHumidityPct = 91.0
                    ),
                hourly =
                    listOf(
                        HourlyForecast(
                            timeIsoLocal = "2026-09-28T12:00",
                            temperatureC = 13.0,
                            apparentTemperatureC = 12.0,
                            precipitationProbabilityPct = 77,
                            weatherCode = 61,
                            windSpeedKmh = 16.0,
                            isDay = true
                        )
                    ),
                daily =
                    listOf(
                        DailyForecast(
                            dateIso = "2026-09-28",
                            weatherCode = 61,
                            highC = 15.0,
                            lowC = 10.0,
                            precipitationProbabilityPct = 82,
                            sunriseIsoLocal = "2026-09-28T06:46",
                            sunsetIsoLocal = "2026-09-28T18:39"
                        )
                    )
            )

        val decoded = WeatherReport.fromJson(report.toJson())

        assertNotNull(decoded)
        assertEquals(report.current, decoded?.current)
        assertEquals(report.hourly, decoded?.hourly)
        assertEquals(report.daily, decoded?.daily)
    }
}
