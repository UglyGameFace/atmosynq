package com.uglygameface.atmosynq

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
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
import android.view.animation.AccelerateDecelerateInterpolator
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
import com.uglygameface.atmosynq.render.FilamentWeatherHeroView
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
    private lateinit var heroLocation: TextView
    private lateinit var metricFeels: TextView
    private lateinit var metricHumidity: TextView
    private lateinit var metricWind: TextView
    private lateinit var metricVisibility: TextView
    private lateinit var metricPrecipitation: TextView
    private lateinit var metricSun: TextView
    private lateinit var hourlySection: LinearLayout
    private lateinit var hourlyContainer: LinearLayout
    private lateinit var dailySection: LinearLayout
    private lateinit var dailyContainer: LinearLayout
    private lateinit var locationButton: Button
    private lateinit var wallpaperButton: Button
    private lateinit var brandLogo: ImageView
    private var brandAnimator: AnimatorSet? = null
    private lateinit var widgetButton: Button
    private lateinit var animatedTab: TextView
    private lateinit var staticTab: TextView
    private lateinit var heroScene: FilamentWeatherHeroView

    private val locationStore by lazy { LocationStore(this) }
    private val motionStore by lazy { MotionPreferenceStore(this) }
    private val callbackUsed = AtomicBoolean(false)
    private var activeReport: WeatherReport? = null

    private val usesUsUnits: Boolean
        get() = Locale.getDefault().country.equals("US", ignoreCase = true)

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
        brandAnimator?.cancel()
        brandAnimator = null
        super.onPause()
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

        brandLogo = ImageView(this).apply {
            setImageResource(R.drawable.atmosynq_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Atmosynq"
        }
        content.addView(
            brandLogo,
            LinearLayout.LayoutParams(
                dp(82),
                dp(82)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(1)
            }
        )

        content.addView(
            TextView(this).apply {
                text = "Weather that comes alive."
                textSize = 12.5f
                setTextColor(COLOR_MUTED)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(10))
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
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(10)
            }
        )

        val currentCard = FrameLayout(this).apply {
            background = roundedBackground(COLOR_HERO_FALLBACK, 28, COLOR_HERO_STROKE)
            clipToOutline = true
            elevation = dp(8).toFloat()
        }

        heroScene = FilamentWeatherHeroView(this).apply {
            contentDescription = "Current Atmosynq weather scene"
            setAnimated(motionStore.isAnimated())
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
                        Color.argb(6, 2, 10, 24),
                        Color.argb(14, 2, 10, 24),
                        Color.argb(132, 2, 9, 20)
                    )
                )
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        currentCard.addView(
            TextView(this).apply {
                text = "LIVE 3D  •  DRAG  •  PINCH  •  TAP"
                textSize = 9.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(202, 239, 255))
                gravity = Gravity.CENTER
                setPadding(dp(9), dp(6), dp(9), dp(6))
                background = roundedBackground(
                    Color.argb(150, 5, 21, 38),
                    14,
                    Color.argb(150, 82, 195, 255)
                )
                contentDescription =
                    "Interactive live weather scene. Drag to look around, pinch to zoom, tap for atmosphere."
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(13)
                rightMargin = dp(13)
            }
        )

        val heroPrimary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(18), dp(16), dp(18), 0)
        }

        heroLocation = TextView(this).apply {
            text = "●  Local weather"
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_LOCATION)
            gravity = Gravity.START
        }
        heroPrimary.addView(heroLocation, matchWrap())

        currentTemperature = TextView(this).apply {
            text = "--°"
            textSize = 70f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.START
            includeFontPadding = false
            setPadding(0, dp(2), 0, 0)
        }
        heroPrimary.addView(currentTemperature, matchWrap())

        currentCondition = TextView(this).apply {
            text = "Weather not synced"
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.START
        }
        heroPrimary.addView(currentCondition, matchWrap())

        currentHighLow = TextView(this).apply {
            text = "Use your location to bring the scene alive"
            textSize = 13.5f
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.START
            setPadding(0, dp(4), 0, 0)
        }
        heroPrimary.addView(currentHighLow, matchWrap())

        currentCard.addView(
            heroPrimary,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val metricsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = roundedBackground(
                Color.argb(142, 7, 22, 39),
                20,
                Color.argb(132, 87, 188, 235)
            )
        }

        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        metricFeels = metricValue("--°")
        metricHumidity = metricValue("--%")
        metricWind = metricValue("--")
        metricVisibility = metricValue("--")

        metricsRow.addView(metricCell("Feels like", metricFeels), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        metricsRow.addView(metricCell("Humidity", metricHumidity), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        metricsRow.addView(metricCell("Wind", metricWind), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        metricsRow.addView(metricCell("Visibility", metricVisibility), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        metricsPanel.addView(metricsRow, matchWrap())

        metricsPanel.addView(
            View(this).apply { setBackgroundColor(Color.argb(80, 150, 205, 235)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        )

        val metricsBottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        metricPrecipitation = TextView(this).apply {
            text = "Precipitation  --"
            textSize = 11.5f
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.START
        }
        metricSun = TextView(this).apply {
            text = "Sunrise / sunset  --"
            textSize = 11.5f
            setTextColor(COLOR_TEXT_SECONDARY)
            gravity = Gravity.END
        }
        metricsBottom.addView(metricPrecipitation, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        metricsBottom.addView(metricSun, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f))
        metricsPanel.addView(metricsBottom, matchWrap())

        currentCard.addView(
            metricsPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(14)
                rightMargin = dp(14)
                bottomMargin = dp(14)
            }
        )

        content.addView(
            currentCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(430)
            ).apply {
                bottomMargin = dp(14)
            }
        )

        wallpaperButton = premiumButton("▣   Set Live Wallpaper   ›", primary = true) {
            openWallpaperPicker()
        }
        content.addView(
            wallpaperButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                bottomMargin = dp(10)
            }
        )

        val secondaryActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        locationButton = premiumButton("⌖  Current Location", primary = false) {
            requestOrCaptureLocation()
        }
        widgetButton = premiumButton("▦  Add Home Widget", primary = false) {
            requestPinWidget()
        }

        secondaryActions.addView(
            locationButton,
            LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginEnd = dp(5)
            }
        )
        secondaryActions.addView(
            widgetButton,
            LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginStart = dp(5)
            }
        )
        content.addView(
            secondaryActions,
            matchWrap().apply { bottomMargin = dp(14) }
        )

        hourlySection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = roundedBackground(COLOR_SECTION_BG, 22, COLOR_STROKE)
        }
        hourlySection.addView(sectionTitle("Next 12 Hours"), matchWrap())

        hourlyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), dp(4), dp(2))
        }
        hourlySection.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
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
            matchWrap().apply { bottomMargin = dp(12) }
        )

        dailySection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(COLOR_SECTION_BG, 22, COLOR_STROKE)
        }
        dailySection.addView(sectionTitle("7-Day Forecast"), matchWrap())

        dailyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        dailySection.addView(dailyContainer, matchWrap())
        content.addView(
            dailySection,
            matchWrap().apply { bottomMargin = dp(4) }
        )

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
            heroScene.setWeather(null)
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

        if (::heroScene.isInitialized) {
            heroScene.setAnimated(animated)
        }

        brandAnimator?.cancel()
        brandAnimator = null

        if (::brandLogo.isInitialized) {
            brandLogo.translationY = 0f
            brandLogo.scaleX = 1f
            brandLogo.scaleY = 1f
            brandLogo.alpha = 1f

            if (animated) {
                val lift = ObjectAnimator.ofFloat(
                    brandLogo,
                    View.TRANSLATION_Y,
                    0f,
                    -dp(3).toFloat(),
                    0f
                )
                val scaleX = ObjectAnimator.ofFloat(
                    brandLogo,
                    View.SCALE_X,
                    1f,
                    1.025f,
                    1f
                )
                val scaleY = ObjectAnimator.ofFloat(
                    brandLogo,
                    View.SCALE_Y,
                    1f,
                    1.025f,
                    1f
                )
                val glow = ObjectAnimator.ofFloat(
                    brandLogo,
                    View.ALPHA,
                    0.92f,
                    1f,
                    0.92f
                )

                listOf(lift, scaleX, scaleY, glow).forEach { animator ->
                    animator.repeatCount = ValueAnimator.INFINITE
                    animator.repeatMode = ValueAnimator.RESTART
                }

                brandAnimator = AnimatorSet().apply {
                    playTogether(lift, scaleX, scaleY, glow)
                    duration = 4200L
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        }
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
                OpenMeteoClient().fetchReport(latitude, longitude)
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                result.onSuccess { report ->
                    renderWeather(report)
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
        report: WeatherReport
    ) {
        activeReport = report
        heroScene.setWeather(report.current)

        val current = report.current
        val today = report.daily.firstOrNull()

        currentTemperature.text = formatTemperature(current.temperatureC)
        currentCondition.text =
            "${WeatherCode.symbol(current.weatherCode, current.isDay)}  ${WeatherCode.description(current.weatherCode)}"

        currentHighLow.text = today?.let { day ->
            "H ${formatTemperature(day.highC)}  •  L ${formatTemperature(day.lowC)}  •  ${day.precipitationProbabilityPct}% precip"
        } ?: ""

        heroLocation.text = "●  Local weather"
        metricFeels.text = formatTemperature(current.apparentTemperatureC)
        metricHumidity.text = "${current.relativeHumidityPct.roundToInt()}%"
        metricWind.text =
            if (current.windGustKmh > current.windSpeedKmh + 2.0) {
                "${formatWind(current.windSpeedKmh)}\nG ${formatWind(current.windGustKmh)}"
            } else {
                formatWind(current.windSpeedKmh)
            }
        metricVisibility.text = formatVisibility(current.visibilityM)
        metricPrecipitation.text = "💧  Precipitation  ${formatPrecipitation(current.precipitationMm)}"

        val sunrise = formatClock(current.sunriseIsoLocal)
        val sunset = formatClock(current.sunsetIsoLocal)
        metricSun.text = buildString {
            append("☀  ")
            if (sunrise != null) append(sunrise)
            if (sunrise != null && sunset != null) append(" / ")
            if (sunset != null) append(sunset)
            if (sunrise == null && sunset == null) append("--")
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

        status.text = "●  Synced • $updated"
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
                if (label == "Now") COLOR_HOURLY_ACTIVE else COLOR_CARD_SOFT,
                16,
                if (label == "Now") COLOR_SEGMENT_STROKE else COLOR_STROKE
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
                    dp(82),
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
                    View(this).apply {
                        setBackgroundColor(Color.argb(70, 118, 164, 194))
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1)
                    ).apply {
                        leftMargin = dp(8)
                        rightMargin = dp(8)
                    }
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
            setBackgroundColor(Color.TRANSPARENT)

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

    private fun metricValue(initial: String): TextView =
        TextView(this).apply {
            text = initial
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.START
            maxLines = 2
            includeFontPadding = false
        }

    private fun metricCell(
        label: String,
        valueView: TextView
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(4), 0, dp(4), 0)

            addView(
                TextView(this@MainActivity).apply {
                    text = label
                    textSize = 10.5f
                    setTextColor(COLOR_MUTED)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            addView(
                valueView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                }
            )
        }

    private fun gradientBackground(
        colors: IntArray,
        radiusDp: Int,
        strokeColor: Int? = null
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            colors
        ).apply {
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null && strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
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
            setTextColor(Color.WHITE)
            background =
                if (primary) {
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(25, 199, 255),
                            Color.rgb(20, 119, 245),
                            Color.rgb(141, 82, 255)
                        ),
                        20,
                        Color.rgb(109, 222, 255)
                    )
                } else {
                    roundedBackground(
                        COLOR_BUTTON_BG,
                        18,
                        COLOR_STROKE
                    )
                }
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
        private val THUNDER_CODES = setOf(95, 96, 97, 99)

        private val COLOR_BG_TOP = Color.rgb(7, 24, 43)
        private val COLOR_BACKGROUND = Color.rgb(4, 12, 23)
        private val COLOR_HERO_FALLBACK = Color.rgb(10, 28, 48)
        private val COLOR_HERO_STROKE = Color.rgb(52, 139, 196)
        private val COLOR_BRAND_STROKE = Color.rgb(57, 151, 220)
        private val COLOR_LOCATION = Color.rgb(177, 222, 255)
        private val COLOR_SECTION_BG = Color.rgb(8, 27, 45)
        private val COLOR_HOURLY_ACTIVE = Color.rgb(11, 63, 96)
        private val COLOR_CARD_SOFT = Color.rgb(13, 40, 62)
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
