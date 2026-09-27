package com.uglygameface.atmosynq

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.uglygameface.atmosynq.location.LocationStore
import com.uglygameface.atmosynq.preferences.MotionPreferenceStore
import com.uglygameface.atmosynq.weather.DailyForecast
import com.uglygameface.atmosynq.weather.HourlyForecast
import com.uglygameface.atmosynq.weather.OpenMeteoClient
import com.uglygameface.atmosynq.weather.WeatherCode
import com.uglygameface.atmosynq.weather.WeatherReport
import com.uglygameface.atmosynq.widget.AtmosynqWidgetProvider
import com.uglygameface.atmosynq.widget.WeatherWidgetSceneRenderer
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
    private lateinit var widgetButton: Button
    private lateinit var animatedTab: TextView
    private lateinit var staticTab: TextView
    private lateinit var heroScene: ImageView

    private val locationStore by lazy { LocationStore(this) }
    private val motionStore by lazy { MotionPreferenceStore(this) }
    private val callbackUsed = AtomicBoolean(false)
    private val heroHandler = Handler(Looper.getMainLooper())

    private var heroFrames: List<Bitmap> = emptyList()
    private var heroFrameIndex = 0
    private var activeReport: WeatherReport? = null

    private val usesUsUnits: Boolean
        get() = Locale.getDefault().country.equals("US", ignoreCase = true)

    private val heroRunnable = object : Runnable {
        override fun run() {
            if (!motionStore.isAnimated() || heroFrames.size < 2 || isFinishing || isDestroyed) return
            heroFrameIndex = (heroFrameIndex + 1) % heroFrames.size
            showHeroFrame(heroFrameIndex, animate = true)
            heroHandler.postDelayed(this, HERO_FRAME_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        renderSavedState()
        applyMotionMode()
    }

    override fun onResume() {
        super.onResume()
        if (::animatedTab.isInitialized) applyMotionMode()
    }

    override fun onPause() {
        heroHandler.removeCallbacks(heroRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        heroHandler.removeCallbacks(heroRunnable)
        recycleHeroFrames()
        super.onDestroy()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(COLOR_BG_TOP, COLOR_BACKGROUND, COLOR_BACKGROUND)
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(14), dp(18), dp(30))
        }
        scroll.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.atmosynq_logo)
                contentDescription = "Atmosynq"
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LinearLayout.LayoutParams(dp(112), dp(112)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        content.addView(
            TextView(this).apply {
                text = "Weather that comes alive."
                textSize = 13.5f
                setTextColor(COLOR_MUTED)
                gravity = Gravity.CENTER
                setPadding(0, dp(1), 0, dp(14))
            },
            matchWrap()
        )

        val motionSwitcher = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = roundedBackground(COLOR_SEGMENT_TRACK, 22, COLOR_STROKE)
        }
        animatedTab = motionTab("Animated") { setMotionMode(true) }
        staticTab = motionTab("Static") { setMotionMode(false) }
        motionSwitcher.addView(animatedTab, LinearLayout.LayoutParams(0, dp(42), 1f))
        motionSwitcher.addView(staticTab, LinearLayout.LayoutParams(0, dp(42), 1f))
        content.addView(
            motionSwitcher,
            matchWrap().apply { bottomMargin = dp(10) }
        )

        status = TextView(this).apply {
            textSize = 12.5f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedBackground(COLOR_STATUS_BG, 18, COLOR_STROKE)
        }
        content.addView(
            status,
            matchWrap().apply { bottomMargin = dp(10) }
        )

        val currentCard = FrameLayout(this).apply {
            background = roundedBackground(COLOR_HERO_FALLBACK, 26, COLOR_HERO_STROKE)
            clipToOutline = true
        }

        heroScene = ImageView(this).apply {
            setImageResource(R.drawable.atmosynq_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.55f
            contentDescription = "Current Atmosynq weather scene"
        }
        currentCard.addView(
            heroScene,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        currentCard.addView(
            View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.argb(8, 4, 10, 22),
                        Color.argb(82, 4, 10, 22),
                        Color.argb(230, 4, 10, 22)
                    )
                )
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val heroContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        currentTemperature = TextView(this).apply {
            text = "--°"
            textSize = 60f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.START
            includeFontPadding = false
        }
        heroContent.addView(currentTemperature, matchWrap())

        currentCondition = TextView(this).apply {
            text = "Weather not synced"
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.START
        }
        heroContent.addView(currentCondition, matchWrap())

        currentHighLow = TextView(this).apply {
            text = "Use your location to bring the scene alive"
            textSize = 13.5f
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.START
            setPadding(0, dp(5), 0, 0)
        }
        heroContent.addView(currentHighLow, matchWrap())

        currentDetails = TextView(this).apply {
            text = "Dashboard, widget, and wallpaper stay synced to the same weather."
            textSize = 12.5f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.START
            setLineSpacing(0f, 1.18f)
            setPadding(0, dp(12), 0, 0)
            maxLines = 4
        }
        heroContent.addView(currentDetails, matchWrap())

        currentCard.addView(
            heroContent,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        content.addView(
            currentCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(270)
            ).apply {
                bottomMargin = dp(14)
            }
        )

        locationButton = premiumButton("Use current location", primary = true) {
            requestOrCaptureLocation()
        }
        content.addView(
            locationButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(8)
            }
        )

        wallpaperButton = premiumButton(
            if (Build.VERSION.SDK_INT >= 36) {
                "Set home / lock live wallpaper"
            } else {
                "Preview & set live wallpaper"
            },
            primary = false
        ) {
            openWallpaperPicker()
        }
        content.addView(
            wallpaperButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(8)
            }
        )

        widgetButton = premiumButton("Add Atmosynq home widget", primary = false) {
            requestPinWidget()
        }
        content.addView(
            widgetButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(14)
            }
        )

        hourlySection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        hourlySection.addView(sectionTitle("Next 12 hours"), matchWrap())

        hourlyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), dp(4), dp(4))
        }
        hourlySection.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(
                    hourlyContainer,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            },
            matchWrap()
        )
        content.addView(
            hourlySection,
            matchWrap().apply { bottomMargin = dp(6) }
        )

        dailySection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        dailySection.addView(sectionTitle("7-day forecast"), matchWrap())

        dailyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        dailySection.addView(dailyContainer, matchWrap())
        content.addView(dailySection, matchWrap())

        content.addView(Space(this), LinearLayout.LayoutParams(1, dp(12)))

        content.addView(
            TextView(this).apply {
                text = "Location stays in app-private storage and is excluded from Android backup. Background location permission is not required."
                textSize = 11.5f
                setTextColor(COLOR_MUTED)
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.15f)
                setPadding(dp(8), dp(10), dp(8), 0)
            },
            matchWrap()
        )

        return scroll
    }

    private fun renderSavedState() {
        val saved = locationStore.load()
        if (saved == null) {
            status.text = "Not synced yet"
            wallpaperButton.isEnabled = false
            wallpaperButton.alpha = 0.42f
            heroHandler.removeCallbacks(heroRunnable)
            heroFrames = emptyList()
            heroFrameIndex = 0
            activeReport = null
            heroScene.animate().cancel()
            heroScene.setImageResource(R.drawable.atmosynq_logo)
            heroScene.alpha = 0.34f
            currentTemperature.text = "--°"
            currentCondition.text = "Weather not synced"
            currentHighLow.text = "Use your location to bring the scene alive"
            currentDetails.text = "The dashboard, widget, and wallpaper will all use the same local weather."
        } else {
            status.text = "Refreshing local weather…"
            wallpaperButton.isEnabled = true
            wallpaperButton.alpha = 1f
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
        wallpaperButton.alpha = 1f
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

        activeReport = report
        rebuildHeroFrames(current)

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
                text = WeatherCode.symbol(hour.weatherCode, hour.isDay)
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

    private fun roundedBackground(color: Int, radiusDp: Int, strokeColor: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) {
                setStroke(dp(1), strokeColor)
            }
        }

    private fun matchWrap() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun motionTab(label: String, onClick: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun setMotionMode(animated: Boolean) {
        if (motionStore.isAnimated() == animated) return
        motionStore.setAnimated(animated)
        applyMotionMode()
        sendBroadcast(
            Intent(this, AtmosynqWidgetProvider::class.java)
                .setAction(AtmosynqWidgetProvider.ACTION_REFRESH)
        )
        status.text = if (animated) "Animated weather scenes enabled" else "Static weather scenes enabled"
    }

    private fun applyMotionMode() {
        if (!::animatedTab.isInitialized || !::staticTab.isInitialized) return
        val animated = motionStore.isAnimated()

        animatedTab.background = roundedBackground(
            if (animated) COLOR_SEGMENT_SELECTED else Color.TRANSPARENT,
            18
        )
        staticTab.background = roundedBackground(
            if (!animated) COLOR_SEGMENT_SELECTED else Color.TRANSPARENT,
            18
        )
        animatedTab.setTextColor(if (animated) Color.WHITE else COLOR_MUTED)
        staticTab.setTextColor(if (!animated) Color.WHITE else COLOR_MUTED)

        activeReport?.current?.let { rebuildHeroFrames(it) }
    }

    private fun rebuildHeroFrames(snapshot: com.uglygameface.atmosynq.weather.WeatherSnapshot) {
        heroHandler.removeCallbacks(heroRunnable)
        val animated = motionStore.isAnimated()
        val indices = if (animated) {
            listOf(0, 1, 2)
        } else {
            listOf(if (snapshot.weatherCode in setOf(95, 96, 97, 99)) 1 else 0)
        }

        heroFrames = indices.map { WeatherWidgetSceneRenderer.render(snapshot, it) }
        heroFrameIndex = 0
        showHeroFrame(0, animate = false)

        if (animated && heroFrames.size > 1) {
            heroHandler.postDelayed(heroRunnable, HERO_FRAME_MS)
        }
    }

    private fun showHeroFrame(index: Int, animate: Boolean) {
        val bitmap = heroFrames.getOrNull(index) ?: return
        heroScene.animate().cancel()

        if (!animate) {
            heroScene.setImageBitmap(bitmap)
            heroScene.alpha = 1f
            return
        }

        heroScene.animate()
            .alpha(0.70f)
            .setDuration(140L)
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                heroScene.setImageBitmap(bitmap)
                heroScene.animate()
                    .alpha(1f)
                    .setDuration(180L)
                    .start()
            }
            .start()
    }

    private fun premiumButton(label: String, primary: Boolean, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            backgroundTintList = null
            background = roundedBackground(
                if (primary) COLOR_BUTTON_PRIMARY else COLOR_BUTTON_SECONDARY,
                18,
                if (primary) COLOR_BUTTON_PRIMARY_STROKE else COLOR_STROKE
            )
            elevation = dp(1).toFloat()
            stateListAnimator = null
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { onClick() }
        }

    private fun requestPinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            status.text = "Your launcher doesn't support direct widget pinning. Long-press the Home screen and choose Widgets → Atmosynq."
            return
        }

        val provider = ComponentName(this, AtmosynqWidgetProvider::class.java)
        val requested = manager.requestPinAppWidget(provider, null, null)
        status.text = if (requested) {
            "Choose where to place the Atmosynq weather widget."
        } else {
            "The launcher didn't open the widget picker. Long-press the Home screen and choose Widgets → Atmosynq."
        }
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
        private const val HERO_FRAME_MS = 1450L

        private val COLOR_BACKGROUND = Color.rgb(5, 14, 25)
        private val COLOR_BG_TOP = Color.rgb(7, 31, 54)
        private val COLOR_CARD = Color.rgb(18, 39, 58)
        private val COLOR_MUTED = Color.rgb(161, 187, 208)
        private val COLOR_TEXT_SECONDARY = Color.rgb(220, 235, 246)
        private val COLOR_ACCENT = Color.rgb(87, 205, 255)

        private val COLOR_SEGMENT_TRACK = Color.rgb(9, 25, 40)
        private val COLOR_SEGMENT_SELECTED = Color.rgb(18, 139, 207)
        private val COLOR_STATUS_BG = Color.rgb(9, 23, 37)
        private val COLOR_STROKE = Color.rgb(34, 69, 95)

        private val COLOR_HERO_FALLBACK = Color.rgb(10, 31, 49)
        private val COLOR_HERO_STROKE = Color.rgb(37, 118, 165)

        private val COLOR_BUTTON_PRIMARY = Color.rgb(18, 137, 205)
        private val COLOR_BUTTON_PRIMARY_STROKE = Color.rgb(82, 211, 255)
        private val COLOR_BUTTON_SECONDARY = Color.rgb(17, 41, 59)
    }
}
