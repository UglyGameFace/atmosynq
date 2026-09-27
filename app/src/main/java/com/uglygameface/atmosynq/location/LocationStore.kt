package com.uglygameface.atmosynq.location

import android.content.Context

enum class LocationSource {
    DEVICE,
    SEARCH
}

data class SavedLocation(
    val latitude: Double,
    val longitude: Double,
    val savedAtEpochMs: Long,
    val displayName: String? = null,
    val locality: String? = null,
    val admin1: String? = null,
    val countryCode: String? = null,
    val country: String? = null,
    val postalCode: String? = null,
    val elevationM: Double? = null,
    val reliefM: Double? = null,
    val population: Long? = null,
    val featureCode: String? = null,
    val source: LocationSource = LocationSource.DEVICE
) {
    fun heroLabel(): String =
        displayName
            ?.takeIf { it.isNotBlank() }
            ?: buildList {
                locality?.takeIf { it.isNotBlank() }?.let(::add)
                admin1
                    ?.takeIf {
                        it.isNotBlank() &&
                            !it.equals(locality, ignoreCase = true)
                    }
                    ?.let(::add)
                countryCode?.takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(", ")
                .takeIf { it.isNotBlank() }
            ?: postalCode?.takeIf { it.isNotBlank() }
            ?: "Current location"
}

class LocationStore(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            "atmosynq_location",
            Context.MODE_PRIVATE
        )

    fun save(latitude: Double, longitude: Double) {
        save(
            SavedLocation(
                latitude = latitude,
                longitude = longitude,
                savedAtEpochMs = System.currentTimeMillis(),
                source = LocationSource.DEVICE
            )
        )
    }

    fun save(location: SavedLocation) {
        prefs.edit()
            .putLong(
                "lat_bits",
                java.lang.Double.doubleToRawLongBits(location.latitude)
            )
            .putLong(
                "lon_bits",
                java.lang.Double.doubleToRawLongBits(location.longitude)
            )
            .putLong("saved_at", location.savedAtEpochMs)
            .putString("display_name", location.displayName)
            .putString("locality", location.locality)
            .putString("admin1", location.admin1)
            .putString("country_code", location.countryCode)
            .putString("country", location.country)
            .putString("postal_code", location.postalCode)
            .putString("elevation_m", location.elevationM?.toString())
            .putString("relief_m", location.reliefM?.toString())
            .putString("population_text", location.population?.toString())
            .putString("feature_code", location.featureCode)
            .putString("source", location.source.name)
            .apply()
    }

    fun load(): SavedLocation? {
        if (!prefs.contains("lat_bits") || !prefs.contains("lon_bits")) {
            return null
        }

        val source =
            prefs.getString("source", null)
                ?.let { raw ->
                    runCatching { LocationSource.valueOf(raw) }.getOrNull()
                }
                ?: LocationSource.DEVICE

        val all = prefs.all

        val elevation =
            when (val value = all["elevation_m"]) {
                is String -> value.toDoubleOrNull()
                is Number -> value.toDouble()
                else ->
                    if (prefs.contains("elevation_bits")) {
                        java.lang.Double.longBitsToDouble(
                            prefs.getLong("elevation_bits", 0L)
                        ).takeIf { it.isFinite() }
                    } else {
                        null
                    }
            }

        val relief =
            when (val value = all["relief_m"]) {
                is String -> value.toDoubleOrNull()
                is Number -> value.toDouble()
                else ->
                    if (prefs.contains("relief_bits")) {
                        java.lang.Double.longBitsToDouble(
                            prefs.getLong("relief_bits", 0L)
                        ).takeIf { it.isFinite() }
                    } else {
                        null
                    }
            }

        val population =
            when (val value = all["population_text"] ?: all["population"]) {
                is String -> value.toLongOrNull()
                is Number -> value.toLong()
                else -> null
            }

        return SavedLocation(
            latitude =
                java.lang.Double.longBitsToDouble(
                    prefs.getLong("lat_bits", 0L)
                ),
            longitude =
                java.lang.Double.longBitsToDouble(
                    prefs.getLong("lon_bits", 0L)
                ),
            savedAtEpochMs = prefs.getLong("saved_at", 0L),
            displayName = prefs.getString("display_name", null),
            locality = prefs.getString("locality", null),
            admin1 = prefs.getString("admin1", null),
            countryCode = prefs.getString("country_code", null),
            country = prefs.getString("country", null),
            postalCode = prefs.getString("postal_code", null),
            elevationM = elevation,
            reliefM = relief?.coerceAtLeast(0.0),
            population = population?.coerceAtLeast(0L),
            featureCode = prefs.getString("feature_code", null),
            source = source
        )
    }
}
