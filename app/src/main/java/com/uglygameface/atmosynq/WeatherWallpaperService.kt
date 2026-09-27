package com.uglygameface.atmosynq

import android.annotation.TargetApi
import android.app.wallpaper.WallpaperDescription
import android.os.Build
import android.os.PersistableBundle
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.uglygameface.atmosynq.render.WeatherRendererThread
import com.uglygameface.atmosynq.wallpaper.WallpaperSurfaceProfile
import com.uglygameface.atmosynq.wallpaper.WallpaperSurfaceResolver
import com.uglygameface.atmosynq.weather.WeatherController

class WeatherWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = WeatherEngine(WallpaperSurfaceProfile.AUTO)

    @TargetApi(36)
    override fun onCreateEngine(description: WallpaperDescription): Engine =
        WeatherEngine(
            WallpaperSurfaceProfile.fromStorageKey(
                description.content.getString(KEY_SURFACE_PROFILE)
            )
        )

    private inner class WeatherEngine(
        initialSurfaceProfile: WallpaperSurfaceProfile
    ) : Engine() {
        private val weatherController = WeatherController(applicationContext)
        private var renderer: WeatherRendererThread? = null
        private var currentlyVisible = false
        private var surfaceProfile = initialSurfaceProfile

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            refreshSurfaceProfileFromFlags()
            renderer = WeatherRendererThread(
                context = applicationContext,
                holder = holder,
                visualProvider = weatherController::currentVisual,
                initialSurfaceProfile = surfaceProfile
            ).also {
                val frame = holder.surfaceFrame
                it.resize(frame.width(), frame.height())
                it.setVisible(currentlyVisible)
                it.start()
            }
            if (currentlyVisible) weatherController.start()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer?.resize(width, height)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            currentlyVisible = visible
            renderer?.setVisible(visible)
            if (visible) weatherController.start() else weatherController.stop()
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which)
            surfaceProfile = WallpaperSurfaceResolver.fromFlags(which)
            renderer?.setSurfaceProfile(surfaceProfile)
        }

        @TargetApi(36)
        override fun onApplyWallpaper(which: Int): WallpaperDescription {
            surfaceProfile = WallpaperSurfaceResolver.fromFlags(which)
            renderer?.setSurfaceProfile(surfaceProfile)

            val content = PersistableBundle().apply {
                putString(KEY_SURFACE_PROFILE, surfaceProfile.storageKey)
            }
            val destination = when (surfaceProfile) {
                WallpaperSurfaceProfile.HOME -> "Home screen"
                WallpaperSurfaceProfile.LOCK -> "Lock screen"
                WallpaperSurfaceProfile.BOTH -> "Home + lock screens"
                WallpaperSurfaceProfile.AUTO -> "Live wallpaper"
            }
            val description = mutableListOf<CharSequence>(
                "Real weather, daylight, rain, snow, fog, wind, and storms.",
                "Atmosynq render profile: $destination"
            )

            return WallpaperDescription.Builder()
                .setId("atmosynq-${surfaceProfile.storageKey}")
                .setTitle("Atmosynq • $destination")
                .setDescription(description)
                .setContent(content)
                .build()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            weatherController.stop()
            renderer?.shutdown()
            runCatching { renderer?.join(1500) }
            renderer = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            weatherController.stop()
            renderer?.shutdown()
            runCatching { renderer?.join(1500) }
            renderer = null
            super.onDestroy()
        }

        private fun refreshSurfaceProfileFromFlags() {
            if (surfaceProfile != WallpaperSurfaceProfile.AUTO) return
            if (Build.VERSION.SDK_INT < 34) return
            surfaceProfile = WallpaperSurfaceResolver.fromFlags(wallpaperFlags)
        }
    }

    companion object {
        private const val KEY_SURFACE_PROFILE = "atmosynq_surface_profile"
    }
}
