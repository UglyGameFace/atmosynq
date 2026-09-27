package com.uglygameface.atmosynq.wallpaper

enum class WallpaperSurfaceProfile(
    val storageKey: String,
    val brightnessScale: Float,
    val frameDelayMs: Long
) {
    AUTO("auto", 1.00f, 30L),
    HOME("home", 1.00f, 30L),
    LOCK("lock", 0.86f, 42L),
    BOTH("both", 0.94f, 34L);

    companion object {
        fun fromStorageKey(value: String?): WallpaperSurfaceProfile =
            entries.firstOrNull { it.storageKey == value } ?: AUTO
    }
}

object WallpaperSurfaceResolver {
    const val FLAG_SYSTEM = 1
    const val FLAG_LOCK = 2

    fun fromFlags(which: Int): WallpaperSurfaceProfile =
        when (which and (FLAG_SYSTEM or FLAG_LOCK)) {
            FLAG_SYSTEM -> WallpaperSurfaceProfile.HOME
            FLAG_LOCK -> WallpaperSurfaceProfile.LOCK
            FLAG_SYSTEM or FLAG_LOCK -> WallpaperSurfaceProfile.BOTH
            else -> WallpaperSurfaceProfile.AUTO
        }
}
