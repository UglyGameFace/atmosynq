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
            activeReport = null
            recycleHeroFrames()
            heroScene.setImageResource(R.drawable.atmosynq_logo)
            heroScene.scaleType = ImageView.ScaleType.CENTER_CROP
            heroScene.alpha = 0.55f
        } else {
            status.text = "Refreshing local weather…"
            wallpaperButton.isEnabled = true
            wallpaperButton.alpha = 1f
            refreshAtmosynqWeather(saved.latitude, saved.longitude)
        }
    }

    private fun setMotionMode(animated: Boolean) {
        if (motionStore.isAnimated() != animated) {
            motionStore.setAnimated(animated)
            sendBroadcast(
                Intent(this, AtmosynqWidgetProvider::class.java)
                    .setAction(AtmosynqWidgetProvider.ACTION_REFRESH)
            )
        }
        applyMotionMode()
    }

    private fun applyMotionMode() {
        if (!::animatedTab.isInitialized || !::staticTab.isInitialized) return

        val animated = motionStore.isAnimated()
        styleMotionTab(animatedTab, active = animated)
        styleMotionTab(staticTab, active = !animated)

        heroHandler.removeCallbacks(heroRunnable)
        if (heroFrames.isEmpty()) return

        if (animated) {
            heroFrameIndex %= heroFrames.size
            showHeroFrame(heroFrameIndex, animate = false)
            heroHandler.postDelayed(heroRunnable, HERO_FRAME_MS)
        } else {
            val weatherCode = activeReport?.current?.weatherCode ?: -1
            val staticIndex = if (weatherCode in THUNDER_CODES) 1 else 0
            heroFrameIndex = staticIndex.coerceAtMost(heroFrames.lastIndex)
            showHeroFrame(heroFrameIndex, animate = false)
        }
    }

    private fun showHeroFrame(index: Int, animate: Boolean) {
        if (heroFrames.isEmpty() || !::heroScene.isInitialized) return
        val safeIndex = index.coerceIn(0, heroFrames.lastIndex)

        heroScene.animate().cancel()
        heroScene.scaleType = ImageView.ScaleType.CENTER_CROP
        heroScene.setImageBitmap(heroFrames[safeIndex])

        if (animate) {
            heroScene.alpha = 0.74f
            heroScene.animate()
                .alpha(1f)
                .setDuration(260L)
                .start()
        } else {
            heroScene.alpha = 1f
        }
    }

    private fun replaceHeroFrames(frames: List<Bitmap>) {
        recycleHeroFrames()
        heroFrames = frames
        heroFrameIndex = 0
    }

    private fun recycleHeroFrames() {
        heroFrames.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        heroFrames = emptyList()
        heroFrameIndex = 0
    }

    private fun requestOrCaptureLocation() {
        val coarseGranted =
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (!coarseGranted) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
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
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (coarseGranted) {
            captureCurrentLocation()
        } else {
            status.text = "Location permission not granted"
        }
    }

    private fun captureCurrentLocation() {
        val manager = getSystemService(LocationManager::class.java)
        val precise =
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val enabled = manager.getProviders(true)

        val provider = when {
            precise && enabled.contains(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            enabled.contains(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            enabled.isNotEmpty() ->
                enabled.first()
            else ->
                null
        }

        if (provider == null) {
            status.text = "Turn on Location and try again"
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        callbackUsed.set(false)
        status.text = "Getting current location…"
        locationButton.isEnabled = false
        locationButton.alpha = 0.6f

        try {
            if (Build.VERSION.SDK_INT >= 30) {
                manager.getCurrentLocation(
                    provider,
                    CancellationSignal(),
                    mainExecutor
                ) { location ->
                    finishLocationRequest(location ?: bestLastKnown(manager, enabled))
                }
            } else {
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(
                    provider,
                    object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            finishLocationRequest(location)
                        }

                        @Deprecated("Deprecated by Android")
                        override fun onStatusChanged(
                            provider: String?,
                            status: Int,
                            extras: Bundle?
                        ) = Unit

                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    },
                    Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {
            finishLocationRequest(null)
        }
    }

    private fun bestLastKnown(
        manager: LocationManager,
        providers: List<String>
    ): Location? =
        providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

    private fun finishLocationRequest(location: Location?) {
        if (!callbackUsed.compareAndSet(false, true)) return

        locationButton.isEnabled = true
        locationButton.alpha = 1f

        if (location == null) {
            status.text = "Couldn't get a location fix"
            return
        }

        locationStore.save(location.latitude, location.longitude)
        wallpaperButton.isEnabled = true
        wallpaperButton.alpha = 1f
        status.text = "Loading local weather…"
        refreshAtmosynqWeather(location.latitude, location.longitude)
    }

    private fun refreshAtmosynqWeather(
        latitude: Double,
        longitude: Double
    ) {
        status.text = "Refreshing local weather…"

        Thread {
            val result = runCatching {
                val report = OpenMeteoClient().fetchReport(latitude, longitude)
                val frames = (0..2).map { frameIndex ->
                    WeatherWidgetSceneRenderer.renderHero(
                        report.current,
                        frameIndex
                    )
                }
                report to frames
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                result.onSuccess { pair ->
                    renderWeather(pair.first, pair.second)
                }.onFailure {
                    status.text = "Weather refresh failed • tap location to retry"
                }
            }
        }.apply {
            name = "AtmosynqWeatherDashboard"
            isDaemon = true
        }.start()
    }

    private fun renderWeather(
        report: WeatherReport,
        frames: List<Bitmap>
    ) {
        activeReport = report
        replaceHeroFrames(frames)

        val current = report.current
        val today = report.daily.firstOrNull()

        currentTemperature.text = formatTemperature(current.temperatureC)
        currentCondition.text =
            "${WeatherCode.symbol(current.weatherCode, current.isDay)}  ${WeatherCode.description(current.weatherCode)}"

        currentHighLow.text = today?.let { day ->
            "H ${formatTemperature(day.highC)}  •  L ${formatTemperature(day.lowC)}  •  ${day.precipitationProbabilityPct}% precip"
        } ?: ""

        currentDetails.text = buildString {
            append(
                "Feels ${formatTemperature(current.apparentTemperatureC)}  •  " +
                    "Humidity ${current.relativeHumidityPct.roundToInt()}%"
            )
            append("\nWind ${formatWind(current.windSpeedKmh)}")
            if (current.windGustKmh > current.windSpeedKmh + 2.0) {
                append("  •  Gusts ${formatWind(current.windGustKmh)}")
            }
            append("  •  Visibility ${formatVisibility(current.visibilityM)}")
            append("\nPrecipitation ${formatPrecipitation(current.precipitationMm)}")

            val sunrise = formatClock(current.sunriseIsoLocal)
            val sunset = formatClock(current.sunsetIsoLocal)
            if (sunrise != null || sunset != null) {
                append("  •  ")
                if (sunrise != null) append("Sunrise $sunrise")
                if (sunrise != null && sunset != null) append(" / ")
                if (sunset != null) append("Sunset $sunset")
            }
        }

        renderHourly(report)
        renderDaily(report)
        applyMotionMode()

        val zone = runCatching {
            ZoneId.of(current.timezone)
        }.getOrDefault(ZoneId.systemDefault())

        val updated = Instant.ofEpochMilli(current.fetchedAtEpochMs)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

        status.text = "Synced • $updated"
    }

    private fun renderHourly(report: WeatherReport) {
        hourlyContainer.removeAllViews()
        val upcoming = nextHourly(report)

        if (upcoming.isEmpty()) {
            hourlySection.visibility = View.GONE
            return
        }

        val zone = runCatching {
            ZoneId.of(report.current.timezone)
        }.getOrDefault(ZoneId.systemDefault())

        val currentHour = LocalDateTime.ofInstant(Instant.now(), zone)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        upcoming.forEach { hour ->
            val parsed = parseLocalDateTime(hour.timeIsoLocal)
            val label =
                if (parsed == currentHour) {
                    "Now"
                } else {
                    parsed?.format(
                        DateTimeFormatter.ofPattern("h a", Locale.getDefault())
                    ) ?: "--"
                }

            hourlyContainer.addView(hourCard(hour, label))
        }

        hourlySection.visibility = View.VISIBLE
    }

    private fun nextHourly(report: WeatherReport): List<HourlyForecast> {
        val zone = runCatching {
            ZoneId.of(report.current.timezone)
        }.getOrDefault(ZoneId.systemDefault())

        val currentHour = LocalDateTime.ofInstant(Instant.now(), zone)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        val future = report.hourly.filter { hour ->
            parseLocalDateTime(hour.timeIsoLocal)
                ?.let { parsed -> !parsed.isBefore(currentHour) } == true
        }

        return (if (future.isNotEmpty()) future else report.hourly).take(12)
    }

    private fun hourCard(
        hour: HourlyForecast,
        label: String
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = roundedBackground(
                COLOR_CARD_SOFT,
                18,
                COLOR_STROKE
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = label
                    textSize = 12f
                    setTextColor(COLOR_MUTED)
                    gravity = Gravity.CENTER
                },
                matchWrap()
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = WeatherCode.symbol(hour.weatherCode, hour.isDay)
                    textSize = 23f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, dp(1))
                },
                matchWrap()
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = formatTemperature(hour.temperatureC)
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                },
                matchWrap()
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = "${hour.precipitationProbabilityPct}%"
                    textSize = 11.5f
                    setTextColor(COLOR_ACCENT)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, 0)
                },
                matchWrap()
            )
        }.also { card ->
            card.layoutParams =
                LinearLayout.LayoutParams(
                    dp(90),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
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
                dailyContainer.addView(
                    Space(this),
                    LinearLayout.LayoutParams(1, dp(7))
                )
            }
        }

        dailySection.visibility = View.VISIBLE
    }

    private fun dayRow(
        day: DailyForecast,
        index: Int
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(
                COLOR_CARD_SOFT,
                18,
                COLOR_STROKE
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = dayLabel(day.dateIso, index)
                    textSize = 13.5f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.9f
                )
            )

            addView(
                TextView(this@MainActivity).apply {
                    text =
                        "${WeatherCode.symbol(day.weatherCode)}  " +
                            WeatherCode.description(day.weatherCode)
                    textSize = 12.5f
                    setTextColor(COLOR_TEXT_SECONDARY)
                    gravity = Gravity.START
                    maxLines = 2
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1.85f
                )
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = "${day.precipitationProbabilityPct}%"
                    textSize = 12f
                    setTextColor(COLOR_ACCENT)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    dp(44),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            addView(
                TextView(this@MainActivity).apply {
                    text =
                        "${formatTemperature(day.highC)} / ${formatTemperature(day.lowC)}"
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.END
                },
                LinearLayout.LayoutParams(
                    dp(76),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

    private fun dayLabel(
        dateIso: String,
        index: Int
    ): String {
        if (index == 0) return "Today"
        if (index == 1) return "Tomorrow"

        return runCatching {
            LocalDate.parse(dateIso)
                .format(
                    DateTimeFormatter.ofPattern(
                        "EEE",
                        Locale.getDefault()
                    )
                )
        }.getOrDefault(dateIso)
    }

    private fun motionTab(
        label: String,
        onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = label
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { onClick() }
        }

    private fun styleMotionTab(
        view: TextView,
        active: Boolean
    ) {
        view.setTextColor(if (active) Color.WHITE else COLOR_MUTED)
        view.background = roundedBackground(
            if (active) COLOR_SEGMENT_ACTIVE else Color.TRANSPARENT,
            18,
            if (active) COLOR_SEGMENT_STROKE else Color.TRANSPARENT
        )
    }

    private fun premiumButton(
        label: String,
        primary: Boolean,
        onClick: () -> Unit
    ): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                if (primary) {
                    Color.rgb(4, 18, 32)
                } else {
                    Color.WHITE
                }
            )
            background = roundedBackground(
                if (primary) COLOR_PRIMARY else COLOR_BUTTON_BG,
                18,
                if (primary) COLOR_PRIMARY else COLOR_STROKE
            )
            stateListAnimator = null
            minHeight = 0
            minWidth = 0
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { onClick() }
        }

    private fun formatTemperature(celsius: Double): String {
        val value =
            if (usesUsUnits) {
                celsius * 9.0 / 5.0 + 32.0
            } else {
                celsius
            }

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
            String.format(
                Locale.US,
                "%.1f mi",
                meters / 1609.344
            )
        } else {
            String.format(
                Locale.getDefault(),
                "%.1f km",
                meters / 1000.0
            )
        }

    private fun formatPrecipitation(mm: Double): String =
        if (usesUsUnits) {
            String.format(
                Locale.US,
                "%.2f in",
                mm / 25.4
            )
        } else {
            String.format(
                Locale.getDefault(),
                "%.1f mm",
                mm
            )
        }

    private fun formatClock(value: String?): String? {
        val parsed = parseLocalDateTime(value ?: return null) ?: return null
        return parsed.format(
            DateTimeFormatter.ofPattern(
                "h:mm a",
                Locale.getDefault()
            )
        )
    }

    private fun parseLocalDateTime(value: String): LocalDateTime? =
        runCatching {
            LocalDateTime.parse(value)
        }.getOrNull()

    private fun sectionTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(2), dp(12), 0, dp(9))
        }

    private fun roundedBackground(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()

            if (strokeColor != null && strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun requestPinWidget() {
        val manager = AppWidgetManager.getInstance(this)

        if (!manager.isRequestPinAppWidgetSupported) {
            status.text = "Long-press Home → Widgets → Atmosynq"
            return
        }

        val provider =
            ComponentName(
                this,
                AtmosynqWidgetProvider::class.java
            )

        val requested =
            manager.requestPinAppWidget(
                provider,
                null,
                null
            )

        status.text =
            if (requested) {
                "Choose where to place the widget"
            } else {
                "Long-press Home → Widgets → Atmosynq"
            }
    }

    private fun openWallpaperPicker() {
        val component =
            ComponentName(
                this,
                WeatherWallpaperService::class.java
            )

        val direct =
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    component
                )
            }

        runCatching {
            startActivity(direct)
        }.onFailure {
            startActivity(
                Intent(
                    WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER
                )
            )
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 420
        private const val HERO_FRAME_MS = 1350L

        private val THUNDER_CODES = setOf(95, 96, 97, 99)

        private val COLOR_BG_TOP = Color.rgb(7, 24, 43)
        private val COLOR_BACKGROUND = Color.rgb(4, 12, 23)
        private val COLOR_HERO_FALLBACK = Color.rgb(10, 28, 48)
        private val COLOR_HERO_STROKE = Color.rgb(39, 112, 166)
        private val COLOR_CARD_SOFT = Color.rgb(16, 37, 57)
        private val COLOR_STATUS_BG = Color.rgb(11, 31, 50)
        private val COLOR_SEGMENT_TRACK = Color.rgb(12, 31, 49)
        private val COLOR_SEGMENT_ACTIVE = Color.rgb(23, 100, 155)
        private val COLOR_SEGMENT_STROKE = Color.rgb(64, 189, 255)
        private val COLOR_BUTTON_BG = Color.rgb(12, 33, 53)
        private val COLOR_STROKE = Color.rgb(38, 69, 91)
        private val COLOR_PRIMARY = Color.rgb(104, 212, 255)
        private val COLOR_MUTED = Color.rgb(160, 186, 207)
        private val COLOR_TEXT_SECONDARY = Color.rgb(218, 230, 239)
        private val COLOR_ACCENT = Color.rgb(101, 207, 255)
    }
}
