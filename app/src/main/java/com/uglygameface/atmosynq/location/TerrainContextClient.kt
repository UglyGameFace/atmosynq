package com.uglygameface.atmosynq.location

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class TerrainContext(
    val centerElevationM: Double,
    val minElevationM: Double,
    val maxElevationM: Double,
    val reliefM: Double
)

/**
 * Samples global 90 m terrain around a selected place so Atmosynq can decide whether
 * mountains actually belong in the scene instead of displaying them everywhere.
 */
class TerrainContextClient {
    fun fetch(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 30.0
    ): TerrainContext {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)

        val points = samplePoints(latitude, longitude, radiusKm.coerceIn(5.0, 60.0))
        val latitudes = points.joinToString(",") { it.first.toString() }
        val longitudes = points.joinToString(",") { it.second.toString() }

        val url =
            "https://api.open-meteo.com/v1/elevation" +
                "?latitude=$latitudes&longitude=$longitudes"

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Atmosynq/0.4.2")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Elevation request failed: HTTP $status")

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): TerrainContext {
        val root = JSONObject(body)
        val elevations = root.optJSONArray("elevation") ?: error("Elevation data missing")
        require(elevations.length() > 0) { "Elevation data empty" }

        val values = buildList {
            for (index in 0 until elevations.length()) {
                val value = elevations.optDouble(index, Double.NaN)
                if (value.isFinite()) add(value)
            }
        }
        require(values.isNotEmpty()) { "Elevation data invalid" }

        val center = values.first()
        val min = values.minOrNull() ?: center
        val max = values.maxOrNull() ?: center

        return TerrainContext(
            centerElevationM = center,
            minElevationM = min,
            maxElevationM = max,
            reliefM = (max - min).coerceAtLeast(0.0)
        )
    }

    internal fun samplePoints(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<Pair<Double, Double>> {
        val points = mutableListOf(latitude to longitude)
        val latRadians = latitude * PI / 180.0
        val longitudeScale = cos(latRadians).coerceAtLeast(0.15)

        listOf(radiusKm * 0.55, radiusKm).forEach { ringKm ->
            repeat(8) { index ->
                val angle = index * (PI / 4.0)
                val northKm = cos(angle) * ringKm
                val eastKm = sin(angle) * ringKm

                val sampleLat =
                    (latitude + northKm / 111.32)
                        .coerceIn(-89.9, 89.9)
                val sampleLon =
                    normalizeLongitude(
                        longitude + eastKm / (111.32 * longitudeScale)
                    )

                points += sampleLat to sampleLon
            }
        }

        return points
    }

    private fun normalizeLongitude(value: Double): Double {
        var result = value
        while (result > 180.0) result -= 360.0
        while (result < -180.0) result += 360.0
        return result
    }
}
