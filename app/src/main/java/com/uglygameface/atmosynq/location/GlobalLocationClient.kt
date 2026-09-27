package com.uglygameface.atmosynq.location

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class LocationCandidate(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevationM: Double?,
    val featureCode: String?,
    val countryCode: String?,
    val country: String?,
    val admin1: String?,
    val admin2: String?,
    val timezone: String?,
    val population: Long?,
    val postcodes: List<String>
) {
    fun displayLabel(query: String? = null): String {
        val normalizedQuery = query
            ?.trim()
            ?.replace(" ", "")
            ?.uppercase(Locale.ROOT)

        val matchedPostcode =
            postcodes.firstOrNull { postcode ->
                postcode.replace(" ", "").uppercase(Locale.ROOT) == normalizedQuery
            }

        val place = buildList {
            add(name)
            admin1
                ?.takeIf {
                    it.isNotBlank() &&
                        !it.equals(name, ignoreCase = true)
                }
                ?.let(::add)
            country
                ?.takeIf {
                    it.isNotBlank() &&
                        !it.equals(admin1, ignoreCase = true)
                }
                ?.let(::add)
        }.distinct().joinToString(", ")

        return if (matchedPostcode != null) {
            "$place • $matchedPostcode"
        } else {
            place
        }
    }

    fun toSavedLocation(query: String? = null): SavedLocation {
        val normalizedQuery = query
            ?.trim()
            ?.replace(" ", "")
            ?.uppercase(Locale.ROOT)

        val matchedPostcode =
            postcodes.firstOrNull { postcode ->
                postcode.replace(" ", "").uppercase(Locale.ROOT) == normalizedQuery
            }

        return SavedLocation(
            latitude = latitude,
            longitude = longitude,
            savedAtEpochMs = System.currentTimeMillis(),
            displayName = displayLabel(query),
            locality = name,
            admin1 = admin1,
            countryCode = countryCode,
            country = country,
            postalCode = matchedPostcode,
            elevationM = elevationM,
            population = population,
            featureCode = featureCode,
            source = LocationSource.SEARCH
        )
    }
}

/**
 * Global city / place / postal-code resolver.
 *
 * Open-Meteo's geocoder is backed by GeoNames and accepts either a place name
 * or postal code. Keeping this as a small client means the dashboard does not
 * need a hard-coded US-only ZIP table or a finite list of supported cities.
 */
class GlobalLocationClient {
    fun search(
        rawQuery: String,
        language: String = Locale.getDefault().language
    ): List<LocationCandidate> {
        val query = rawQuery.trim()
        require(query.length >= 2) {
            "Enter at least two characters for a city or postal code."
        }

        val encodedName = URLEncoder.encode(query, "UTF-8")
        val safeLanguage =
            language
                .lowercase(Locale.ROOT)
                .takeIf { it.matches(Regex("[a-z]{2,3}")) }
                ?: "en"

        val url =
            URL(
                "https://geocoding-api.open-meteo.com/v1/search" +
                    "?name=$encodedName" +
                    "&count=$MAX_RESULTS" +
                    "&language=$safeLanguage" +
                    "&format=json"
            )

        val connection =
            (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Atmosynq/0.4.1")
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

            return parseSearch(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseSearch(body: String): List<LocationCandidate> {
        val root = JSONObject(body)
        val results = root.optJSONArray("results") ?: return emptyList()

        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isBlank()) continue

                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                if (!latitude.isFinite() || !longitude.isFinite()) continue

                val postcodes =
                    item.optJSONArray("postcodes")
                        ?.let { array ->
                            buildList {
                                for (postcodeIndex in 0 until array.length()) {
                                    array.optString(postcodeIndex)
                                        .trim()
                                        .takeIf { it.isNotBlank() }
                                        ?.let(::add)
                                }
                            }
                        }
                        ?: emptyList()

                add(
                    LocationCandidate(
                        id = item.optLong("id", 0L),
                        name = name,
                        latitude = latitude,
                        longitude = longitude,
                        elevationM =
                            item.optDouble("elevation", Double.NaN)
                                .takeIf { it.isFinite() },
                        featureCode =
                            item.optString("feature_code")
                                .takeIf { it.isNotBlank() },
                        countryCode =
                            item.optString("country_code")
                                .takeIf { it.isNotBlank() },
                        country =
                            item.optString("country")
                                .takeIf { it.isNotBlank() },
                        admin1 =
                            item.optString("admin1")
                                .takeIf { it.isNotBlank() },
                        admin2 =
                            item.optString("admin2")
                                .takeIf { it.isNotBlank() },
                        timezone =
                            item.optString("timezone")
                                .takeIf { it.isNotBlank() },
                        population =
                            item
                                .takeIf { it.has("population") }
                                ?.optLong("population")
                                ?.takeIf { it > 0L },
                        postcodes = postcodes
                    )
                )
            }
        }
    }

    companion object {
        private const val MAX_RESULTS = 12
    }
}
