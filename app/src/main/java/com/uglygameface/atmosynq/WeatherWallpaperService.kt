package com.uglygameface.atmosynq

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.uglygameface.atmosynq.render.WeatherRendererThread
import com.uglygameface.atmosynq.weather.WeatherController

class WeatherWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = WeatherEngine()

    private inner class WeatherEngine : Engine() {
        private val weatherController = WeatherController(applicationContext)
        private var renderer: WeatherRendererThread? = null
        private var currentlyVisible = false

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderer = WeatherRendererThread(
                context = applicationContext,
                holder = holder,
                visualProvider = weatherController::currentVisual
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
    }
}
