package com.uglygameface.atmosynq.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OpenMeteoClient {
    fun fetch(latitude: Double, longitude: Double): WeatherSnapshot =
        fetchReport(latitude, longitude).current

    fun fetchReport(latitude: Double, longitude: Double): WeatherReport {
        val current = listOf(
            "temperature_2m", "apparent_temperature", "relative_humidity_2m", "is_day",
            "precipitation", "rain", "showers", "snowfall", "weather_code", "cloud_cover",
            "wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "visibility"
        ).joinToString(",")
        val hourly = listOf(
            "temperature_2m", "apparent_temperature", "precipitation_probability",
            "weather_code", "wind_speed_10m"
        ).joinToString(",")
        val daily = listOf(
            "weather_code", "temperature_2m_max", "temperature_2m_min",
            "precipitation_probability_max", "sunrise", "sunset"
        ).joinToString(",")

        val query = buildString {
            append("latitude=").append(latitude)
            append("&longitude=").append(longitude)
            append("&current=").append(URLEncoder.encode(current, "UTF-8"))
            append("&hourly=").append(URLEncoder.encode(hourly, "UTF-8"))
            append("&daily=").append(URLEncoder.encode(daily, "UTF-8"))
            append("&timezone=auto")
            append("&forecast_days=7")
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
            return parseReport(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): WeatherSnapshot = parseReport(body).current

    internal fun parseReport(body: String): WeatherReport {
        val root = JSONObject(body)
        val current = root.getJSONObject("current")
        val daily = root.optJSONObject("daily")
        val sunrise = daily?.optJSONArray("sunrise")?.optString(0)?.takeIf { it.isNotBlank() }
        val sunset = daily?.optJSONArray("sunset")?.optString(0)?.takeIf { it.isNotBlank() }
        val temperature = current.optDouble("temperature_2m", 0.0)

        val snapshot = WeatherSnapshot(
            fetchedAtEpochMs = System.currentTimeMillis(),
            timezone = root.optString("timezone", "UTC"),
            temperatureC = temperature,
            apparentTemperatureC = current.optDouble("apparent_temperature", temperature),
            relativeHumidityPct = current.optDouble("relative_humidity_2m", 0.0),
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

        val hourlyObject = root.optJSONObject("hourly")
        val hourlyTimes = hourlyObject?.optJSONArray("time")
        val hourlyTemperatures = hourlyObject?.optJSONArray("temperature_2m")
        val hourlyFeels = hourlyObject?.optJSONArray("apparent_temperature")
        val hourlyPrecipitation = hourlyObject?.optJSONArray("precipitation_probability")
        val hourlyCodes = hourlyObject?.optJSONArray("weather_code")
        val hourlyWind = hourlyObject?.optJSONArray("wind_speed_10m")
        val hourlyCount = minOf(
            hourlyTimes?.length() ?: 0,
            hourlyTemperatures?.length() ?: 0
        )
        val hourlyForecast = (0 until hourlyCount).map { index ->
            val temp = hourlyTemperatures!!.optDouble(index, 0.0)
            HourlyForecast(
                timeIsoLocal = hourlyTimes!!.optString(index, ""),
                temperatureC = temp,
                apparentTemperatureC = hourlyFeels?.optDouble(index, temp) ?: temp,
                precipitationProbabilityPct = hourlyPrecipitation?.optInt(index, 0) ?: 0,
                weatherCode = hourlyCodes?.optInt(index, 0) ?: 0,
                windSpeedKmh = hourlyWind?.optDouble(index, 0.0) ?: 0.0
            )
        }

        val dailyDates = daily?.optJSONArray("time")
        val dailyCodes = daily?.optJSONArray("weather_code")
        val dailyHighs = daily?.optJSONArray("temperature_2m_max")
        val dailyLows = daily?.optJSONArray("temperature_2m_min")
        val dailyPrecipitation = daily?.optJSONArray("precipitation_probability_max")
        val dailySunrise = daily?.optJSONArray("sunrise")
        val dailySunset = daily?.optJSONArray("sunset")
        val dailyCount = minOf(
            dailyDates?.length() ?: 0,
            dailyHighs?.length() ?: 0,
            dailyLows?.length() ?: 0
        )
        val dailyForecast = (0 until dailyCount).map { index ->
            DailyForecast(
                dateIso = dailyDates!!.optString(index, ""),
                weatherCode = dailyCodes?.optInt(index, 0) ?: 0,
                highC = dailyHighs!!.optDouble(index, 0.0),
                lowC = dailyLows!!.optDouble(index, 0.0),
                precipitationProbabilityPct = dailyPrecipitation?.optInt(index, 0) ?: 0,
                sunriseIsoLocal = dailySunrise?.optString(index)?.takeIf { it.isNotBlank() },
                sunsetIsoLocal = dailySunset?.optString(index)?.takeIf { it.isNotBlank() }
            )
        }

        return WeatherReport(
            current = snapshot,
            hourly = hourlyForecast,
            daily = dailyForecast
        )
    }
}
