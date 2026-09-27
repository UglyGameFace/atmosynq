package com.uglygameface.atmosynq.location

import android.content.Context

data class SavedLocation(val latitude: Double, val longitude: Double, val savedAtEpochMs: Long)

class LocationStore(context: Context) {
    private val prefs = context.getSharedPreferences("atmosynq_location", Context.MODE_PRIVATE)

    fun save(latitude: Double, longitude: Double) {
        prefs.edit()
            .putLong("lat_bits", java.lang.Double.doubleToRawLongBits(latitude))
            .putLong("lon_bits", java.lang.Double.doubleToRawLongBits(longitude))
            .putLong("saved_at", System.currentTimeMillis())
            .apply()
    }

    fun load(): SavedLocation? {
        if (!prefs.contains("lat_bits") || !prefs.contains("lon_bits")) return null
        return SavedLocation(
            latitude = java.lang.Double.longBitsToDouble(prefs.getLong("lat_bits", 0L)),
            longitude = java.lang.Double.longBitsToDouble(prefs.getLong("lon_bits", 0L)),
            savedAtEpochMs = prefs.getLong("saved_at", 0L)
        )
    }
}
