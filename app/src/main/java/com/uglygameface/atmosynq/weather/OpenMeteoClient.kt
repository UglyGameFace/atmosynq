package com.uglygameface.atmosynq.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OpenMeteoClient {
    fun fetch(latitude: Double, longitude: Double): WeatherSnapshot {
        val current = listOf(
            "temperature_2m", "is_day", "precipitation", "rain", "showers", "snowfall",
            "weather_code", "cloud_cover", "wind_speed_10m", "wind_direction_10m",
            "wind_gusts_10m", "visibility"
        ).joinToString(",")
        val query = buildString {
            append("latitude=").append(latitude)
            append("&longitude=").append(longitude)
            append("&current=").append(URLEncoder.encode(current, "UTF-8"))
            append("&daily=sunrise,sunset")
            append("&timezone=auto&forecast_days=1")
        }
        val connection = (URL("https://api.open-meteo.com/v1/forecast?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Atmosynq/0.1.1")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Weather request failed: HTTP $status")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): WeatherSnapshot {
        val root = JSONObject(body)
        val current = root.getJSONObject("current")
        val daily = root.optJSONObject("daily")
        val sunrise = daily?.optJSONArray("sunrise")?.optString(0)?.takeIf { it.isNotBlank() }
        val sunset = daily?.optJSONArray("sunset")?.optString(0)?.takeIf { it.isNotBlank() }
        return WeatherSnapshot(
            fetchedAtEpochMs = System.currentTimeMillis(),
            timezone = root.optString("timezone", "UTC"),
            temperatureC = current.optDouble("temperature_2m", 0.0),
            isDay = current.optInt("is_day", 0) == 1,
            precipitationMm = current.optDouble("precipitation", 0.0),
            rainMm = current.optDouble("rain", 0.0),
            showersMm = current.optDouble("showers", 0.0),
            snowfallCm = current.optDouble("snowfall", 0.0),
            weatherCode = current.optInt("weather_code", 0),
            cloudCoverPct = current.optDouble("cloud_cover", 0.0),
            windSpeedKmh = current.optDouble("wind_speed_10m", 0.0),
            windDirectionDeg = current.optDouble("wind_direction_10m", 0.0),
            windGustKmh = current.optDouble("wind_gusts_10m", 0.0),
            visibilityM = current.optDouble("visibility", 100_000.0),
            sunriseIsoLocal = sunrise,
            sunsetIsoLocal = sunset
        )
    }
}
