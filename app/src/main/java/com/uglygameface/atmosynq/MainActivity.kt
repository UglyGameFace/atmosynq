package com.uglygameface.atmosynq

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.app.Activity
import com.uglygameface.atmosynq.location.LocationStore
import com.uglygameface.atmosynq.weather.OpenMeteoClient
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var locationButton: Button
    private lateinit var wallpaperButton: Button
    private val locationStore by lazy { LocationStore(this) }
    private val callbackUsed = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        renderSavedState()
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(36), dp(24), dp(28))
            setBackgroundColor(Color.rgb(13, 24, 40))

            addView(TextView(this@MainActivity).apply {
                text = "Atmosynq"
                textSize = 28f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            addView(TextView(this@MainActivity).apply {
                text = "Your animated scene follows local daylight, clouds, rain, snow, fog, wind, and storms."
                textSize = 16f
                setTextColor(Color.rgb(205, 220, 235))
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(24))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            status = TextView(this@MainActivity).apply {
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(16), dp(16), dp(16))
                setBackgroundColor(Color.rgb(28, 47, 68))
            }
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            addView(Space(this@MainActivity), LinearLayout.LayoutParams(1, dp(18)))

            locationButton = Button(this@MainActivity).apply {
                text = "Use my current location"
                setOnClickListener { requestOrCaptureLocation() }
            }
            addView(locationButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

            addView(Space(this@MainActivity), LinearLayout.LayoutParams(1, dp(12)))

            wallpaperButton = Button(this@MainActivity).apply {
                text = "Preview & set live wallpaper"
                setOnClickListener { openWallpaperPicker() }
            }
            addView(wallpaperButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

            addView(Space(this@MainActivity), LinearLayout.LayoutParams(1, dp(16)))

            addView(TextView(this@MainActivity).apply {
                text = "Location is only used to select weather for the wallpaper. The app stores coordinates on-device and does not require background location permission."
                textSize = 13f
                setTextColor(Color.rgb(170, 190, 210))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun renderSavedState() {
        val saved = locationStore.load()
        if (saved == null) {
            status.text = "Weather location not set yet."
            wallpaperButton.isEnabled = false
        } else {
            status.text = "Location saved. Refreshing live weather…"
            wallpaperButton.isEnabled = true
            refreshAtmosynqWeatherPreview(saved.latitude, saved.longitude)
        }
    }

    private fun requestOrCaptureLocation() {
        val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!coarseGranted) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
            return
        }
        captureCurrentLocation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != LOCATION_PERMISSION_REQUEST) return
        val coarseGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (coarseGranted) captureCurrentLocation() else status.text = "Location permission was not granted."
    }

    private fun captureCurrentLocation() {
        val manager = getSystemService(LocationManager::class.java)
        val precise = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val enabled = manager.getProviders(true)
        val provider = when {
            precise && enabled.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            enabled.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            enabled.isNotEmpty() -> enabled.first()
            else -> null
        }
        if (provider == null) {
            status.text = "Location services are off. Turn them on and try again."
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        callbackUsed.set(false)
        status.text = "Getting current location…"
        locationButton.isEnabled = false

        try {
            if (Build.VERSION.SDK_INT >= 30) {
                manager.getCurrentLocation(provider, CancellationSignal(), mainExecutor) { location ->
                    finishLocationRequest(location ?: bestLastKnown(manager, enabled))
                }
            } else {
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) = finishLocationRequest(location)
                    @Deprecated("Deprecated by Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            finishLocationRequest(null)
        }
    }

    private fun bestLastKnown(manager: LocationManager, providers: List<String>): Location? {
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun finishLocationRequest(location: Location?) {
        if (!callbackUsed.compareAndSet(false, true)) return
        locationButton.isEnabled = true
        if (location == null) {
            status.text = "Could not get a location fix. Try again with Location enabled."
            return
        }
        locationStore.save(location.latitude, location.longitude)
        wallpaperButton.isEnabled = true
        status.text = "Location saved. Loading weather…"
        refreshAtmosynqWeatherPreview(location.latitude, location.longitude)
    }

    private fun refreshAtmosynqWeatherPreview(latitude: Double, longitude: Double) {
        Thread {
            val result = runCatching { OpenMeteoClient().fetch(latitude, longitude) }
            runOnUiThread {
                result.onSuccess { weather ->
                    val rainMm = maxOf(weather.rainMm, weather.showersMm)
                    val usUnits = Locale.getDefault().country.equals("US", ignoreCase = true)
                    status.text = if (usUnits) {
                        String.format(
                            Locale.US,
                            "Synced • %.0f°F • %.0f%% clouds • rain %.2f in • wind %.0f mph",
                            weather.temperatureC * 9.0 / 5.0 + 32.0,
                            weather.cloudCoverPct,
                            rainMm / 25.4,
                            weather.windSpeedKmh * 0.621371
                        )
                    } else {
                        String.format(
                            Locale.getDefault(),
                            "Synced • %.0f°C • %.0f%% clouds • rain %.1f mm • wind %.0f km/h",
                            weather.temperatureC,
                            weather.cloudCoverPct,
                            rainMm,
                            weather.windSpeedKmh
                        )
                    }
                }.onFailure {
                    status.text = "Location saved. Weather will retry automatically when the wallpaper is active."
                }
            }
        }.apply { name = "AtmosynqWeatherPreview"; isDaemon = true }.start()
    }

    private fun openWallpaperPicker() {
        val component = ComponentName(this, WeatherWallpaperService::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }
        runCatching { startActivity(direct) }.onFailure {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 420
    }
}
