package com.uglygameface.atmosynq.location

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class PlaceSearchResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevationM: Double?,
    val timezone: String?,
    val featureCode: String?,
    val countryCode: String?,
    val country: String?,
    val admin1: String?,
    val population: Long,
    val postcodes: List<String>
) {
    fun displayLabel(): String {
        val parts = mutableListOf(name)
        admin1
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(name, ignoreCase = true) }
            ?.let(parts::add)

        val countryPart =
            countryCode
                ?.trim()
                ?.uppercase(Locale.US)
                ?.takeIf { it.isNotEmpty() }
                ?: country?.trim()?.takeIf { it.isNotEmpty() }

        countryPart
            ?.takeIf { part -> parts.none { it.equals(part, ignoreCase = true) } }
            ?.let(parts::add)

        return parts.joinToString(", ")
    }

    fun shortLabel(): String {
        val suffix =
            admin1
                ?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals(name, ignoreCase = true) }
                ?: countryCode?.trim()?.uppercase(Locale.US)
                ?: country?.trim()

        return listOfNotNull(name.trim().takeIf { it.isNotEmpty() }, suffix)
            .joinToString(", ")
    }
}

/**
 * Global city / postal-code search backed by Open-Meteo's geocoding index.
 *
 * A query is intentionally not restricted to the United States. Users can type a city,
 * city + state/province/country, or a postal code in any supported country.
 */
class GeocodingClient {
    fun search(
        query: String,
        language: String = Locale.getDefault().language.ifBlank { "en" },
        countryCode: String? = null,
        count: Int = 8
    ): List<PlaceSearchResult> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()

        val safeCount = count.coerceIn(1, 20)
        val params = buildString {
            append("name=").append(URLEncoder.encode(normalized, "UTF-8"))
            append("&count=").append(safeCount)
            append("&format=json")
            append("&language=")
                .append(URLEncoder.encode(language.lowercase(Locale.US), "UTF-8"))

            countryCode
                ?.trim()
                ?.uppercase(Locale.US)
                ?.takeIf { it.length == 2 }
                ?.let {
                    append("&countryCode=")
                        .append(URLEncoder.encode(it, "UTF-8"))
                }
        }

        val connection =
            (
                URL("https://geocoding-api.open-meteo.com/v1/search?$params")
                    .openConnection() as HttpURLConnection
                ).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Atmosynq/0.4.2")
            }

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                error("Location search failed: HTTP $status")
            }

            val body =
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

            return parse(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): List<PlaceSearchResult> {
        val root = JSONObject(body)
        val results = root.optJSONArray("results") ?: return emptyList()

        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                if (!item.has("latitude") || !item.has("longitude")) continue

                val postcodes = buildList {
                    val values = item.optJSONArray("postcodes")
                    if (values != null) {
                        for (postcodeIndex in 0 until values.length()) {
                            values.optString(postcodeIndex)
                                .trim()
                                .takeIf { it.isNotEmpty() }
                                ?.let(::add)
                        }
                    }
                }

                add(
                    PlaceSearchResult(
                        id = item.optLong("id", 0L),
                        name = item.optString("name", "").trim(),
                        latitude = item.optDouble("latitude"),
                        longitude = item.optDouble("longitude"),
                        elevationM =
                            item.optDouble("elevation", Double.NaN)
                                .takeIf { it.isFinite() },
                        timezone =
                            item.optString("timezone", "")
                                .trim()
                                .takeIf { it.isNotEmpty() },
                        featureCode =
                            item.optString("feature_code", "")
                                .trim()
                                .takeIf { it.isNotEmpty() },
                        countryCode =
                            item.optString("country_code", "")
                                .trim()
                                .takeIf { it.isNotEmpty() },
                        country =
                            item.optString("country", "")
                                .trim()
                                .takeIf { it.isNotEmpty() },
                        admin1 =
                            item.optString("admin1", "")
                                .trim()
                                .takeIf { it.isNotEmpty() },
                        population = item.optLong("population", 0L).coerceAtLeast(0L),
                        postcodes = postcodes
                    )
                )
            }
        }
    }
}
