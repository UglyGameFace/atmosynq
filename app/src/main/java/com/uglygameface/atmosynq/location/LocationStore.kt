package com.uglygameface.atmosynq.location

import android.content.Context

data class SavedLocation(
    val latitude: Double,
    val longitude: Double,
    val savedAtEpochMs: Long,
    val displayName: String = "Local weather",
    val source: String = SOURCE_GPS,
    val population: Long = 0L,
    val elevationM: Double = Double.NaN,
    val reliefM: Double = 0.0,
    val countryCode: String? = null
) {
    companion object {
        const val SOURCE_GPS = "gps"
        const val SOURCE_SEARCH = "search"
    }
}

class LocationStore(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            "atmosynq_location",
            Context.MODE_PRIVATE
        )

    fun save(
        latitude: Double,
        longitude: Double,
        displayName: String = "Local weather",
        source: String = SavedLocation.SOURCE_GPS,
        population: Long = 0L,
        elevationM: Double = Double.NaN,
        reliefM: Double = 0.0,
        countryCode: String? = null
    ): SavedLocation {
        val saved =
            SavedLocation(
                latitude = latitude,
                longitude = longitude,
                savedAtEpochMs = System.currentTimeMillis(),
                displayName = displayName.ifBlank { "Local weather" },
                source = source,
                population = population.coerceAtLeast(0L),
                elevationM = elevationM,
                reliefM = reliefM.coerceAtLeast(0.0),
                countryCode = countryCode
            )
        save(saved)
        return saved
    }

    fun save(location: SavedLocation) {
        val editor =
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
                .putString("source", location.source)
                .putLong("population", location.population)
                .putLong(
                    "elevation_bits",
                    java.lang.Double.doubleToRawLongBits(location.elevationM)
                )
                .putLong(
                    "relief_bits",
                    java.lang.Double.doubleToRawLongBits(location.reliefM)
                )

        if (location.countryCode.isNullOrBlank()) {
            editor.remove("country_code")
        } else {
            editor.putString("country_code", location.countryCode)
        }

        editor.apply()
    }

    fun load(): SavedLocation? {
        if (!prefs.contains("lat_bits") || !prefs.contains("lon_bits")) {
            return null
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
            displayName =
                prefs.getString("display_name", null)
                    ?.takeIf { it.isNotBlank() }
                    ?: "Local weather",
            source =
                prefs.getString("source", null)
                    ?.takeIf { it.isNotBlank() }
                    ?: SavedLocation.SOURCE_GPS,
            population = prefs.getLong("population", 0L).coerceAtLeast(0L),
            elevationM =
                if (prefs.contains("elevation_bits")) {
                    java.lang.Double.longBitsToDouble(
                        prefs.getLong("elevation_bits", 0L)
                    )
                } else {
                    Double.NaN
                },
            reliefM =
                if (prefs.contains("relief_bits")) {
                    java.lang.Double.longBitsToDouble(
                        prefs.getLong("relief_bits", 0L)
                    ).coerceAtLeast(0.0)
                } else {
                    0.0
                },
            countryCode =
                prefs.getString("country_code", null)
                    ?.takeIf { it.isNotBlank() }
        )
    }
}
