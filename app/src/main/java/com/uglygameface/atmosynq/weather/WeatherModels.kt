package com.uglygameface.atmosynq.weather

import org.json.JSONArray
import org.json.JSONObject

data class WeatherSnapshot(
    val fetchedAtEpochMs: Long,
    val timezone: String,
    val temperatureC: Double,
    val isDay: Boolean,
    val precipitationMm: Double,
    val rainMm: Double,
    val showersMm: Double,
    val snowfallCm: Double,
    val weatherCode: Int,
    val cloudCoverPct: Double,
    val windSpeedKmh: Double,
    val windDirectionDeg: Double,
    val windGustKmh: Double,
    val visibilityM: Double,
    val sunriseIsoLocal: String?,
    val sunsetIsoLocal: String?,
    val apparentTemperatureC: Double = temperatureC,
    val relativeHumidityPct: Double = 0.0
) {
    fun toJson(): String = JSONObject().apply {
        put("fetchedAtEpochMs", fetchedAtEpochMs)
        put("timezone", timezone)
        put("temperatureC", temperatureC)
        put("apparentTemperatureC", apparentTemperatureC)
        put("relativeHumidityPct", relativeHumidityPct)
        put("isDay", isDay)
        put("precipitationMm", precipitationMm)
        put("rainMm", rainMm)
        put("showersMm", showersMm)
        put("snowfallCm", snowfallCm)
        put("weatherCode", weatherCode)
        put("cloudCoverPct", cloudCoverPct)
        put("windSpeedKmh", windSpeedKmh)
        put("windDirectionDeg", windDirectionDeg)
        put("windGustKmh", windGustKmh)
        put("visibilityM", visibilityM)
        put("sunriseIsoLocal", sunriseIsoLocal)
        put("sunsetIsoLocal", sunsetIsoLocal)
    }.toString()

    companion object {
        fun fromJson(raw: String): WeatherSnapshot? = runCatching {
            val j = JSONObject(raw)
            val temperature = j.optDouble("temperatureC", 0.0)
            WeatherSnapshot(
                fetchedAtEpochMs = j.getLong("fetchedAtEpochMs"),
                timezone = j.optString("timezone", "UTC"),
                temperatureC = temperature,
                apparentTemperatureC = j.optDouble("apparentTemperatureC", temperature),
                relativeHumidityPct = j.optDouble("relativeHumidityPct", 0.0),
                isDay = j.optBoolean("isDay", false),
                precipitationMm = j.optDouble("precipitationMm", 0.0),
                rainMm = j.optDouble("rainMm", 0.0),
                showersMm = j.optDouble("showersMm", 0.0),
                snowfallCm = j.optDouble("snowfallCm", 0.0),
                weatherCode = j.optInt("weatherCode", 0),
                cloudCoverPct = j.optDouble("cloudCoverPct", 0.0),
                windSpeedKmh = j.optDouble("windSpeedKmh", 0.0),
                windDirectionDeg = j.optDouble("windDirectionDeg", 0.0),
                windGustKmh = j.optDouble("windGustKmh", 0.0),
                visibilityM = j.optDouble("visibilityM", 100_000.0),
                sunriseIsoLocal = j.optString("sunriseIsoLocal").takeIf { it.isNotBlank() && it != "null" },
                sunsetIsoLocal = j.optString("sunsetIsoLocal").takeIf { it.isNotBlank() && it != "null" }
            )
        }.getOrNull()
    }
}

data class HourlyForecast(
    val timeIsoLocal: String,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val precipitationProbabilityPct: Int,
    val weatherCode: Int,
    val windSpeedKmh: Double,
    val isDay: Boolean
)

data class DailyForecast(
    val dateIso: String,
    val weatherCode: Int,
    val highC: Double,
    val lowC: Double,
    val precipitationProbabilityPct: Int,
    val sunriseIsoLocal: String?,
    val sunsetIsoLocal: String?
)

data class WeatherReport(
    val current: WeatherSnapshot,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>
) {
    fun toJson(): String =
        JSONObject().apply {
            put("current", JSONObject(current.toJson()))
            put(
                "hourly",
                JSONArray().apply {
                    hourly.forEach { hour ->
                        put(
                            JSONObject().apply {
                                put("timeIsoLocal", hour.timeIsoLocal)
                                put("temperatureC", hour.temperatureC)
                                put("apparentTemperatureC", hour.apparentTemperatureC)
                                put(
                                    "precipitationProbabilityPct",
                                    hour.precipitationProbabilityPct
                                )
                                put("weatherCode", hour.weatherCode)
                                put("windSpeedKmh", hour.windSpeedKmh)
                                put("isDay", hour.isDay)
                            }
                        )
                    }
                }
            )
            put(
                "daily",
                JSONArray().apply {
                    daily.forEach { day ->
                        put(
                            JSONObject().apply {
                                put("dateIso", day.dateIso)
                                put("weatherCode", day.weatherCode)
                                put("highC", day.highC)
                                put("lowC", day.lowC)
                                put(
                                    "precipitationProbabilityPct",
                                    day.precipitationProbabilityPct
                                )
                                put("sunriseIsoLocal", day.sunriseIsoLocal)
                                put("sunsetIsoLocal", day.sunsetIsoLocal)
                            }
                        )
                    }
                }
            )
        }.toString()

    companion object {
        fun fromJson(raw: String): WeatherReport? =
            runCatching {
                val root = JSONObject(raw)
                val current =
                    WeatherSnapshot.fromJson(
                        root.getJSONObject("current").toString()
                    ) ?: return@runCatching null

                val hourlyJson = root.optJSONArray("hourly") ?: JSONArray()
                val hourly =
                    buildList {
                        repeat(hourlyJson.length()) { index ->
                            val item = hourlyJson.getJSONObject(index)
                            add(
                                HourlyForecast(
                                    timeIsoLocal =
                                        item.getString("timeIsoLocal"),
                                    temperatureC =
                                        item.optDouble("temperatureC", 0.0),
                                    apparentTemperatureC =
                                        item.optDouble(
                                            "apparentTemperatureC",
                                            item.optDouble("temperatureC", 0.0)
                                        ),
                                    precipitationProbabilityPct =
                                        item.optInt(
                                            "precipitationProbabilityPct",
                                            0
                                        ),
                                    weatherCode =
                                        item.optInt("weatherCode", 0),
                                    windSpeedKmh =
                                        item.optDouble("windSpeedKmh", 0.0),
                                    isDay =
                                        item.optBoolean("isDay", false)
                                )
                            )
                        }
                    }

                val dailyJson = root.optJSONArray("daily") ?: JSONArray()
                val daily =
                    buildList {
                        repeat(dailyJson.length()) { index ->
                            val item = dailyJson.getJSONObject(index)
                            add(
                                DailyForecast(
                                    dateIso =
                                        item.getString("dateIso"),
                                    weatherCode =
                                        item.optInt("weatherCode", 0),
                                    highC =
                                        item.optDouble("highC", 0.0),
                                    lowC =
                                        item.optDouble("lowC", 0.0),
                                    precipitationProbabilityPct =
                                        item.optInt(
                                            "precipitationProbabilityPct",
                                            0
                                        ),
                                    sunriseIsoLocal =
                                        item.optString("sunriseIsoLocal")
                                            .takeIf {
                                                it.isNotBlank() &&
                                                    it != "null"
                                            },
                                    sunsetIsoLocal =
                                        item.optString("sunsetIsoLocal")
                                            .takeIf {
                                                it.isNotBlank() &&
                                                    it != "null"
                                            }
                                )
                            )
                        }
                    }

                WeatherReport(
                    current = current,
                    hourly = hourly,
                    daily = daily
                )
            }.getOrNull()
    }
}

data class WeatherVisualState(
    val daylight: Float,
    val sunsetWarmth: Float,
    val cloudiness: Float,
    val rainIntensity: Float,
    val snowIntensity: Float,
    val fogIntensity: Float,
    val thunderIntensity: Float,
    val windStrength: Float,
    val windDirectionDeg: Float
) {
    companion object {
        val DEFAULT = WeatherVisualState(
            daylight = 0.0f,
            sunsetWarmth = 0.0f,
            cloudiness = 0.25f,
            rainIntensity = 0.0f,
            snowIntensity = 0.0f,
            fogIntensity = 0.0f,
            thunderIntensity = 0.0f,
            windStrength = 0.0f,
            windDirectionDeg = 0.0f
        )
    }
}
