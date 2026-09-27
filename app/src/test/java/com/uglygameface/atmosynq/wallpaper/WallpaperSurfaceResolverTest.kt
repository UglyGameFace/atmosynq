package com.uglygameface.atmosynq.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperSurfaceResolverTest {
    @Test
    fun resolvesAndroidWallpaperFlags() {
        assertEquals(WallpaperSurfaceProfile.HOME, WallpaperSurfaceResolver.fromFlags(1))
        assertEquals(WallpaperSurfaceProfile.LOCK, WallpaperSurfaceResolver.fromFlags(2))
        assertEquals(WallpaperSurfaceProfile.BOTH, WallpaperSurfaceResolver.fromFlags(3))
        assertEquals(WallpaperSurfaceProfile.AUTO, WallpaperSurfaceResolver.fromFlags(0))
    }

    @Test
    fun lockProfileIsDimmerAndLowerFrameRate() {
        assertTrue(WallpaperSurfaceProfile.LOCK.brightnessScale < WallpaperSurfaceProfile.HOME.brightnessScale)
        assertTrue(WallpaperSurfaceProfile.LOCK.frameDelayMs > WallpaperSurfaceProfile.HOME.frameDelayMs)
    }

    @Test
    fun storageKeysRoundTrip() {
        for (profile in WallpaperSurfaceProfile.entries) {
            assertEquals(profile, WallpaperSurfaceProfile.fromStorageKey(profile.storageKey))
        }
        assertEquals(WallpaperSurfaceProfile.AUTO, WallpaperSurfaceProfile.fromStorageKey("unknown"))
    }
}
