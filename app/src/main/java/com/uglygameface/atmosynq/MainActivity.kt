package com.uglygameface.atmosynq

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.uglygameface.atmosynq.location.LocationStore
import com.uglygameface.atmosynq.weather.DailyForecast
import com.uglygameface.atmosynq.weather.HourlyForecast
import com.uglygameface.atmosynq.weather.OpenMeteoClient
import com.uglygameface.atmosynq.weather.WeatherCode
import com.uglygameface.atmosynq.weather.WeatherReport
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var currentTemperature: TextView
    private lateinit var currentCondition: TextView
    private lateinit var currentHighLow: TextView
    private lateinit var currentDetails: TextView
    private lateinit var hourlySection: LinearLayout
    private lateinit var hourlyContainer: LinearLayout
    private lateinit var dailySection: LinearLayout
    private lateinit var dailyContainer: LinearLayout
    private lateinit var locationButton: Button
    private lateinit var wallpaperButton: Button

    private val locationStore by lazy { LocationStore(this) }
    private val callbackUsed = AtomicBoolean(false)
    private val usesUsUnits: Boolean
        get() = Locale.getDefault().country.equals("US", ignoreCase = true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        renderSavedState()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(COLOR_BACKGROUND)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        scroll.addView(
            content,
            ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        content.addView(TextView(this).apply {
            text = "Atmosynq"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, matchWrap())

        content.addView(TextView(this).apply {
            text = "Weather that comes alive."
            textSize = 15f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(18))
        }, matchWrap())

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        content.addView(status, matchWrap())

        val currentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = roundedBackground(COLOR_CARD, 22)
        }
        currentTemperature = TextView(this).apply {
            text = "--°"
            textSize = 58f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        currentCard.addView(currentTemperature, matchWrap())

        currentCondition = TextView(this).apply {
            text = "Choose your location to load weather"
            textSize = 19f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        currentCard.addView(currentCondition, matchWrap())

        currentHighLow = TextView(this).apply {
            textSize = 15f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        currentCard.addView(currentHighLow, matchWrap())

        currentDetails = TextView(this).apply {
            textSize = 14f
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(16), 0, 0)
        }
        currentCard.addView(currentDetails, matchWrap())
        content.addView(currentCard, matchWrap().apply { topMargin = dp(4) })

        hourlySection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        hourlySection.addView(sectionTitle("Next 12 hours"), matchWrap())
        hourlyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), dp(4), dp(4))
        }
        hourlySection.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                hourlyContainer,
                HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }, matchWrap())
        content.addView(hourlySection, matchWrap().apply { topMargin = dp(14) })

        dailySection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        dailySection.addView(sectionTitle("7-day forecast"), matchWrap())
        dailyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        dailySection.addView(dailyContainer, matchWrap())
        content.addView(dailySection, matchWrap().apply { topMargin = dp(6) })

        content.addView(Space(this), LinearLayout.LayoutParams(1, dp(22)))

        locationButton = Button(this).apply {
            text = "Use my current location"
            setOnClickListener { requestOrCaptureLocation() }
        }
        content.addView(locationButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        content.addView(Space(this), LinearLayout.LayoutParams(1, dp(10)))

        wallpaperButton = Button(this).apply {
            text = "Preview & set live wallpaper"
            setOnClickListener { openWallpaperPicker() }
        }
        content.addView(wallpaperButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        content.addView(TextView(this).apply {
            text = "Your saved coordinates stay on-device. Atmosynq only needs foreground location access to choose local weather; background location permission is not required."
            textSize = 12.5f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(dp(4), dp(18), dp(4), 0)
        }, matchWrap())

        return scroll
    }

    private fun renderSavedState() {
        val saved = locationStore.load()
        if (saved == null) {
            status.text = "Weather location not set yet"
            wallpaperButton.isEnabled = false
        } else {
            status.text = "Refreshing local weather…"
            wallpaperButton.isEnabled = true
            refreshAtmosynqWeather(saved.latitude, saved.longitude)
        }
    }

    private fun requestOrCaptureLocation() {
        val coarseGranted =
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!coarseGranted) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
            return
        }
        captureCurrentLocation()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != LOCATION_PERMISSION_REQUEST) return
        val coarseGranted =
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (coarseGranted) {
            captureCurrentLocation()
        } else {
            status.text = "Location permission was not granted"
        }
    }

    private fun captureCurrentLocation() {
        val manager = getSystemService(LocationManager::class.java)
        val precise =
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
                    @Deprecated("Deprecated by Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            finishLocationRequest(null)
        }
    }

    private fun bestLastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

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
        refreshAtmosynqWeather(location.latitude, location.longitude)
    }

    private fun refreshAtmosynqWeather(latitude: Double, longitude: Double) {
        status.text = "Refreshing local weather…"
        Thread {
            val result = runCatching { OpenMeteoClient().fetchReport(latitude, longitude) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { report ->
                    renderWeather(report)
                }.onFailure {
                    status.text = "Couldn't refresh weather right now. The live wallpaper will retry automatically."
                }
            }
        }.apply {
            name = "AtmosynqWeatherDashboard"
            isDaemon = true
        }.start()
    }

    private fun renderWeather(report: WeatherReport) {
        val current = report.current
        val today = report.daily.firstOrNull()

        currentTemperature.text = formatTemperature(current.temperatureC)
        currentCondition.text =
            "${WeatherCode.symbol(current.weatherCode, current.isDay)}  ${WeatherCode.description(current.weatherCode)}"
        currentHighLow.text = today?.let {
            "H ${formatTemperature(it.highC)}  •  L ${formatTemperature(it.lowC)}  •  ${it.precipitationProbabilityPct}% precip"
        } ?: ""

        currentDetails.text = buildString {
            append("Feels like ${formatTemperature(current.apparentTemperatureC)}  •  Humidity ${current.relativeHumidityPct.roundToInt()}%")
            append("\nWind ${formatWind(current.windSpeedKmh)}")
            if (current.windGustKmh > current.windSpeedKmh + 2.0) {
                append("  •  Gusts ${formatWind(current.windGustKmh)}")
            }
            append("\nClouds ${current.cloudCoverPct.roundToInt()}%  •  Visibility ${formatVisibility(current.visibilityM)}")
            append("\nPrecipitation now ${formatPrecipitation(current.precipitationMm)}")
            val sunrise = formatClock(current.sunriseIsoLocal)
            val sunset = formatClock(current.sunsetIsoLocal)
            if (sunrise != null || sunset != null) {
                append("\n")
                if (sunrise != null) append("Sunrise $sunrise")
                if (sunrise != null && sunset != null) append("  •  ")
                if (sunset != null) append("Sunset $sunset")
            }
        }

        renderHourly(report)
        renderDaily(report)

        val zone = runCatching { ZoneId.of(current.timezone) }.getOrDefault(ZoneId.systemDefault())
        val updated = Instant.ofEpochMilli(current.fetchedAtEpochMs)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        status.text = "Local weather synced • $updated"
    }

    private fun renderHourly(report: WeatherReport) {
        hourlyContainer.removeAllViews()
        val upcoming = nextHourly(report)
        if (upcoming.isEmpty()) {
            hourlySection.visibility = View.GONE
            return
        }

        val zone = runCatching { ZoneId.of(report.current.timezone) }.getOrDefault(ZoneId.systemDefault())
        val currentHour = LocalDateTime.ofInstant(Instant.now(), zone)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        upcoming.forEach { hour ->
            val parsed = parseLocalDateTime(hour.timeIsoLocal)
            val label = if (parsed == currentHour) {
                "Now"
            } else {
                parsed?.format(DateTimeFormatter.ofPattern("h a", Locale.getDefault())) ?: "--"
            }
            hourlyContainer.addView(hourCard(hour, label))
        }
        hourlySection.visibility = View.VISIBLE
    }

    private fun nextHourly(report: WeatherReport): List<HourlyForecast> {
        val zone = runCatching { ZoneId.of(report.current.timezone) }.getOrDefault(ZoneId.systemDefault())
        val currentHour = LocalDateTime.ofInstant(Instant.now(), zone)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        val future = report.hourly.filter { hour ->
            parseLocalDateTime(hour.timeIsoLocal)?.let { !it.isBefore(currentHour) } == true
        }
        return (if (future.isNotEmpty()) future else report.hourly).take(12)
    }

    private fun hourCard(hour: HourlyForecast, label: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = roundedBackground(COLOR_CARD, 16)

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 12.5f
                setTextColor(COLOR_MUTED)
                gravity = Gravity.CENTER
            }, matchWrap())

            addView(TextView(this@MainActivity).apply {
                text = WeatherCode.symbol(hour.weatherCode)
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(2))
            }, matchWrap())

            addView(TextView(this@MainActivity).apply {
                text = formatTemperature(hour.temperatureC)
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, matchWrap())

            addView(TextView(this@MainActivity).apply {
                text = "${hour.precipitationProbabilityPct}% precip"
                textSize = 11.5f
                setTextColor(COLOR_ACCENT)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }, matchWrap())
        }.also { card ->
            card.layoutParams = LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(8)
            }
        }

    private fun renderDaily(report: WeatherReport) {
        dailyContainer.removeAllViews()
        if (report.daily.isEmpty()) {
            dailySection.visibility = View.GONE
            return
        }

        report.daily.take(7).forEachIndexed { index, day ->
            dailyContainer.addView(dayRow(day, index))
            if (index < minOf(6, report.daily.lastIndex)) {
                dailyContainer.addView(Space(this), LinearLayout.LayoutParams(1, dp(7)))
            }
        }
        dailySection.visibility = View.VISIBLE
    }

    private fun dayRow(day: DailyForecast, index: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(COLOR_CARD, 15)

            addView(TextView(this@MainActivity).apply {
                text = dayLabel(day.dateIso, index)
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))

            addView(TextView(this@MainActivity).apply {
                text = "${WeatherCode.symbol(day.weatherCode)}  ${WeatherCode.description(day.weatherCode)}"
                textSize = 13f
                setTextColor(COLOR_TEXT_SECONDARY)
                gravity = Gravity.START
                maxLines = 2
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.8f))

            addView(TextView(this@MainActivity).apply {
                text = "${day.precipitationProbabilityPct}%"
                textSize = 12f
                setTextColor(COLOR_ACCENT)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))

            addView(TextView(this@MainActivity).apply {
                text = "${formatTemperature(day.highC)} / ${formatTemperature(day.lowC)}"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun dayLabel(dateIso: String, index: Int): String {
        if (index == 0) return "Today"
        if (index == 1) return "Tomorrow"
        return runCatching {
            LocalDate.parse(dateIso).format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        }.getOrDefault(dateIso)
    }

    private fun formatTemperature(celsius: Double): String {
        val value = if (usesUsUnits) celsius * 9.0 / 5.0 + 32.0 else celsius
        return "${value.roundToInt()}°"
    }

    private fun formatWind(kmh: Double): String =
        if (usesUsUnits) {
            "${(kmh * 0.621371).roundToInt()} mph"
        } else {
            "${kmh.roundToInt()} km/h"
        }

    private fun formatVisibility(meters: Double): String =
        if (usesUsUnits) {
            String.format(Locale.US, "%.1f mi", meters / 1609.344)
        } else {
            String.format(Locale.getDefault(), "%.1f km", meters / 1000.0)
        }

    private fun formatPrecipitation(mm: Double): String =
        if (usesUsUnits) {
            String.format(Locale.US, "%.2f in", mm / 25.4)
        } else {
            String.format(Locale.getDefault(), "%.1f mm", mm)
        }

    private fun formatClock(value: String?): String? {
        val parsed = parseLocalDateTime(value ?: return null) ?: return null
        return parsed.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

    private fun parseLocalDateTime(value: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(value) }.getOrNull()

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(dp(2), dp(12), 0, dp(9))
    }

    private fun roundedBackground(color: Int, radiusDp: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun matchWrap() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

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
        private val COLOR_BACKGROUND = Color.rgb(8, 18, 30)
        private val COLOR_CARD = Color.rgb(24, 42, 61)
        private val COLOR_MUTED = Color.rgb(166, 188, 208)
        private val COLOR_TEXT_SECONDARY = Color.rgb(207, 221, 234)
        private val COLOR_ACCENT = Color.rgb(112, 202, 255)
    }
}
