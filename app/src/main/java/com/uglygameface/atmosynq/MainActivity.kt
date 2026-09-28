package com.uglygameface.atmosynq

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.WindowInsets
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.uglygameface.atmosynq.location.GeocodingClient
import com.uglygameface.atmosynq.location.PlaceSearchResult
import com.uglygameface.atmosynq.location.LocationSource
import com.uglygameface.atmosynq.location.LocationStore
import com.uglygameface.atmosynq.location.SavedLocation
import com.uglygameface.atmosynq.location.TerrainContext
import com.uglygameface.atmosynq.location.TerrainContextClient
import com.uglygameface.atmosynq.preferences.MotionPreferenceStore
import com.uglygameface.atmosynq.weather.DailyForecast
import com.uglygameface.atmosynq.weather.HourlyForecast
import com.uglygameface.atmosynq.weather.OpenMeteoClient
import com.uglygameface.atmosynq.weather.WeatherCode
import com.uglygameface.atmosynq.weather.WeatherReport
import com.uglygameface.atmosynq.widget.AtmosynqWidgetProvider
import com.uglygameface.atmosynq.render.FilamentWeatherHeroView
import com.uglygameface.atmosynq.render.SceneProfileResolver
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
    private var activeLocation: SavedLocation? = null

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
        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            Color.rgb(3, 18, 33),
                            Color.rgb(3, 12, 23),
                            Color.rgb(2, 8, 17)
                        )
                    )
            }

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(14), dp(8), dp(14), dp(28))
            }

        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int

            if (Build.VERSION.SDK_INT >= 30) {
                val bars =
                    insets.getInsets(
                        WindowInsets.Type.systemBars()
                    )
                topInset = bars.top
                bottomInset = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }

            content.setPadding(
                dp(14),
                topInset + dp(8),
                dp(14),
                bottomInset + dp(24)
            )
            insets
        }

        scroll.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // ---------------------------------------------------------------------
        // ORBIT HEADER
        // A compact asymmetric header keeps the product identity visible without
        // consuming the first quarter of the screen.
        // ---------------------------------------------------------------------
        val header =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        val brandStack =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
            }

        brandLogo =
            ImageView(this).apply {
                setImageResource(
                    R.drawable.atmosynq_horizontal_logo
                )
                scaleType = ImageView.ScaleType.FIT_START
                adjustViewBounds = true
                contentDescription = "Atmosynq"
            }

        brandStack.addView(
            brandLogo,
            LinearLayout.LayoutParams(
                dp(190),
                dp(44)
            )
        )

        brandStack.addView(
            TextView(this).apply {
                text = "WEATHER THAT COMES ALIVE"
                textSize = 9.5f
                letterSpacing = 0.14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(132, 181, 215))
                includeFontPadding = false
                setPadding(dp(3), dp(1), 0, 0)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        header.addView(
            brandStack,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(
            TextView(this).apply {
                text = "⚙"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(224, 243, 255))
                background =
                    roundedBackground(
                        Color.argb(190, 7, 27, 47),
                        22,
                        Color.argb(175, 65, 148, 201)
                    )
                elevation = dp(4).toFloat()
                contentDescription = "Atmosynq settings"
                setOnClickListener {
                    showQuickSettings()
                }
            },
            LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        )

        content.addView(
            header,
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        content.addView(
            View(this).apply {
                background =
                    gradientBackground(
                        intArrayOf(
                            Color.argb(0, 50, 215, 255),
                            Color.argb(210, 50, 215, 255),
                            Color.argb(190, 125, 91, 255),
                            Color.argb(0, 125, 91, 255)
                        ),
                        1
                    )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(2)
            ).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                bottomMargin = dp(10)
            }
        )

        // ---------------------------------------------------------------------
        // ATMOSPHERE CONTROL DECK
        // Motion mode and sync status live in one surface instead of three stacked
        // rounded boxes.
        // ---------------------------------------------------------------------
        val controlDeck =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), dp(5), dp(5), dp(5))
                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(8, 31, 52),
                            Color.rgb(7, 24, 43),
                            Color.rgb(10, 25, 49)
                        ),
                        26,
                        Color.rgb(39, 83, 113)
                    )
                elevation = dp(4).toFloat()
            }

        val motionSwitcher =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(2), dp(2), dp(2))
                background =
                    roundedBackground(
                        Color.argb(150, 3, 16, 29),
                        20,
                        Color.argb(90, 54, 114, 151)
                    )
            }

        animatedTab =
            motionTab("▶  LIVE") {
                setMotionMode(true)
            }
        staticTab =
            motionTab("▣  STILL") {
                setMotionMode(false)
            }

        motionSwitcher.addView(
            animatedTab,
            LinearLayout.LayoutParams(
                0,
                dp(40),
                1f
            )
        )
        motionSwitcher.addView(
            staticTab,
            LinearLayout.LayoutParams(
                0,
                dp(40),
                1f
            )
        )

        controlDeck.addView(
            motionSwitcher,
            LinearLayout.LayoutParams(
                0,
                dp(44),
                1.55f
            ).apply {
                marginEnd = dp(6)
            }
        )

        status =
            TextView(this).apply {
                text = "OFFLINE"
                textSize = 11.5f
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.04f
                setTextColor(Color.rgb(192, 225, 243))
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(10), 0, dp(10), 0)
                background =
                    roundedBackground(
                        Color.argb(180, 10, 41, 65),
                        20,
                        Color.argb(145, 67, 163, 218)
                    )
                contentDescription =
                    "Weather sync status. Tap to refresh."
                setOnClickListener {
                    refreshSavedWeather()
                }
            }

        controlDeck.addView(
            status,
            LinearLayout.LayoutParams(
                0,
                dp(44),
                0.85f
            )
        )

        content.addView(
            controlDeck,
            matchWrap().apply {
                bottomMargin = dp(12)
            }
        )

        // ---------------------------------------------------------------------
        // LIVE SKY HERO
        // ---------------------------------------------------------------------
        val currentCard =
            FrameLayout(this).apply {
                background =
                    roundedBackground(
                        COLOR_HERO_FALLBACK,
                        30,
                        Color.rgb(55, 147, 204)
                    )
                clipToOutline = true
                elevation = dp(10).toFloat()
            }

        heroScene =
            FilamentWeatherHeroView(this).apply {
                contentDescription =
                    "Current Atmosynq weather scene. Tap to explore."
                setAnimated(motionStore.isAnimated())
                setOnClickListener {
                    openWorldExplorer(startEyeSpy = false)
                }
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
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            Color.argb(128, 1, 8, 20),
                            Color.argb(16, 1, 8, 20),
                            Color.argb(22, 1, 8, 20),
                            Color.argb(170, 1, 7, 18)
                        )
                    )
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Signature Atmosynq spectrum rail.
        currentCard.addView(
            View(this).apply {
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            Color.argb(0, 43, 216, 255),
                            Color.rgb(43, 216, 255),
                            Color.rgb(111, 117, 255),
                            Color.rgb(178, 76, 255),
                            Color.argb(0, 178, 76, 255)
                        )
                    ).apply {
                        cornerRadius = dp(2).toFloat()
                    }
            },
            FrameLayout.LayoutParams(
                dp(3),
                dp(280),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )

        currentCard.addView(
            TextView(this).apply {
                text = "•••"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.08f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background =
                    roundedBackground(
                        Color.argb(192, 3, 18, 33),
                        22,
                        Color.argb(180, 83, 189, 244)
                    )
                contentDescription =
                    "Live scene controls and information"
                setOnClickListener {
                    showSceneMenu()
                }
            },
            FrameLayout.LayoutParams(
                dp(44),
                dp(44),
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(16)
                rightMargin = dp(16)
            }
        )

        val heroPrimary =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
                setPadding(dp(22), dp(18), dp(72), 0)
            }

        heroPrimary.addView(
            TextView(this).apply {
                text = "NOW  //  LIVE SKY"
                textSize = 9f
                letterSpacing = 0.16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(73, 216, 255))
                includeFontPadding = false
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(5)
            }
        )

        heroLocation =
            TextView(this).apply {
                text = "⌖  Select a location"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(231, 245, 253))
                gravity = Gravity.START
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setShadowLayer(
                    8f,
                    0f,
                    2f,
                    Color.argb(220, 0, 0, 0)
                )
            }

        heroPrimary.addView(
            heroLocation,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        currentTemperature =
            TextView(this).apply {
                text = "--°"
                textSize = 84f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.START
                includeFontPadding = false
                setShadowLayer(
                    12f,
                    0f,
                    2f,
                    Color.argb(220, 0, 0, 0)
                )
                setPadding(0, dp(2), 0, 0)
            }

        heroPrimary.addView(
            currentTemperature,
            matchWrap()
        )

        currentCondition =
            TextView(this).apply {
                text = "Weather not synced"
                textSize = 23f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.START
                setShadowLayer(
                    9f,
                    0f,
                    2f,
                    Color.argb(220, 0, 0, 0)
                )
            }

        heroPrimary.addView(
            currentCondition,
            matchWrap()
        )

        currentHighLow =
            TextView(this).apply {
                text =
                    "Choose a location to wake the atmosphere"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(222, 237, 247))
                gravity = Gravity.START
                setPadding(0, dp(5), 0, 0)
                setShadowLayer(
                    7f,
                    0f,
                    2f,
                    Color.argb(205, 0, 0, 0)
                )
            }

        heroPrimary.addView(
            currentHighLow,
            matchWrap()
        )

        currentCard.addView(
            heroPrimary,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        // A two-cluster metrics dock avoids one giant generic rectangle.
        val metricDock =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }

        val metricTopRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        metricFeels = metricValue("--°")
        metricHumidity = metricValue("--%")
        metricWind = metricValue("--")
        metricVisibility = metricValue("--")

        val thermalCluster =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(9), dp(8), dp(9))
                background =
                    roundedBackground(
                        Color.argb(138, 3, 19, 34),
                        18,
                        Color.argb(125, 72, 160, 207)
                    )
                addView(
                    metricCell("THERMAL", metricFeels),
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
                addView(
                    metricCell("HUMIDITY", metricHumidity),
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
            }

        val airCluster =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(9), dp(8), dp(9))
                background =
                    roundedBackground(
                        Color.argb(138, 3, 19, 34),
                        18,
                        Color.argb(125, 72, 160, 207)
                    )
                addView(
                    metricCell("WIND", metricWind),
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
                addView(
                    metricCell("RANGE", metricVisibility),
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
            }

        metricTopRow.addView(
            thermalCluster,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(5)
            }
        )
        metricTopRow.addView(
            airCluster,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(5)
            }
        )

        metricDock.addView(
            metricTopRow,
            matchWrap()
        )

        val lowerMetricStrip =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background =
                    roundedBackground(
                        Color.argb(154, 3, 17, 31),
                        18,
                        Color.argb(145, 72, 160, 207)
                    )
            }

        metricPrecipitation =
            TextView(this).apply {
                text = "💧  --"
                textSize = 11.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(216, 236, 248))
                gravity = Gravity.START
                maxLines = 1
            }

        metricSun =
            TextView(this).apply {
                text = "☀  --"
                textSize = 11.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(216, 236, 248))
                gravity = Gravity.END
                maxLines = 1
            }

        lowerMetricStrip.addView(
            metricPrecipitation,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        lowerMetricStrip.addView(
            metricSun,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.15f
            )
        )

        metricDock.addView(
            lowerMetricStrip,
            matchWrap().apply {
                topMargin = dp(8)
            }
        )

        currentCard.addView(
            metricDock,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(6)
                rightMargin = dp(6)
                bottomMargin = dp(6)
            }
        )

        content.addView(
            currentCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(500)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        // ---------------------------------------------------------------------
        // ATMOSPHERE DOCK
        // ---------------------------------------------------------------------
        val actionDock =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(8, 29, 49),
                            Color.rgb(5, 20, 36)
                        ),
                        26,
                        Color.rgb(38, 79, 105)
                    )
            }

        val dockHeader =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), 0, dp(6), dp(7))
            }

        dockHeader.addView(
            TextView(this).apply {
                text = "ATMOSPHERE DOCK"
                textSize = 9.5f
                letterSpacing = 0.13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(97, 210, 255))
            },
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        dockHeader.addView(
            TextView(this).apply {
                text = "MAKE IT YOURS"
                textSize = 9f
                letterSpacing = 0.08f
                setTextColor(Color.rgb(129, 155, 177))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        actionDock.addView(
            dockHeader,
            matchWrap()
        )

        wallpaperButton =
            premiumButton(
                "▣   LIVE WALLPAPER     ↗",
                primary = true
            ) {
                openWallpaperPicker()
            }

        actionDock.addView(
            wallpaperButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(62)
            ).apply {
                bottomMargin = dp(7)
            }
        )

        val secondaryActions =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

        locationButton =
            premiumButton(
                "⌖  LOCATION",
                primary = false
            ) {
                showLocationChooser()
            }

        widgetButton =
            premiumButton(
                "▦  WIDGET",
                primary = false
            ) {
                requestPinWidget()
            }

        secondaryActions.addView(
            locationButton,
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            ).apply {
                marginEnd = dp(4)
            }
        )

        secondaryActions.addView(
            widgetButton,
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            ).apply {
                marginStart = dp(4)
            }
        )

        actionDock.addView(
            secondaryActions,
            matchWrap()
        )

        content.addView(
            actionDock,
            matchWrap().apply {
                bottomMargin = dp(14)
            }
        )

        // ---------------------------------------------------------------------
        // FORECAST STREAM
        // ---------------------------------------------------------------------
        hourlySection =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(dp(10), dp(10), dp(10), dp(12))
                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(8, 27, 46),
                            Color.rgb(5, 18, 33)
                        ),
                        24,
                        Color.rgb(31, 67, 91)
                    )
            }

        hourlySection.addView(
            sectionHeader(
                "NEXT 12 HOURS",
                "TIMELINE  ›"
            ) {
                showHourlyTimeline()
            },
            matchWrap()
        )

        hourlyContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(1), dp(4), dp(4), dp(3))
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
            matchWrap().apply {
                bottomMargin = dp(12)
            }
        )

        dailySection =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(dp(10), dp(10), dp(10), dp(12))
                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(7, 25, 43),
                            Color.rgb(4, 16, 30)
                        ),
                        24,
                        Color.rgb(31, 67, 91)
                    )
            }

        dailySection.addView(
            sectionHeader(
                "7-DAY OUTLOOK",
                "FULL FORECAST  ›"
            ) {
                showFullForecast()
            },
            matchWrap()
        )

        dailyContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        dailySection.addView(
            dailyContainer,
            matchWrap()
        )

        content.addView(
            dailySection,
            matchWrap().apply {
                bottomMargin = dp(6)
            }
        )

        content.addView(
            TextView(this).apply {
                text = "ATMOSYNQ  //  LIVE WEATHER SYSTEM"
                textSize = 8.5f
                letterSpacing = 0.14f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(71, 103, 127))
                setPadding(0, dp(12), 0, dp(6))
            },
            matchWrap()
        )

        return scroll
    }

    private fun renderSavedState() {
        val saved = locationStore.load()
        if (saved == null) {
            status.text = "OFFLINE"
            wallpaperButton.isEnabled = false
            wallpaperButton.alpha = 0.42f
            activeReport = null
            heroScene.setWeather(null)
        } else {
            applySelectedLocation(saved)
            status.text = "SYNCING…"
            wallpaperButton.isEnabled = true
            wallpaperButton.alpha = 1f
            refreshAtmosynqWeather(saved.latitude, saved.longitude)
        }
    }

    private fun applySelectedLocation(location: SavedLocation) {
        activeLocation = location
        heroLocation.text = "⌖  ${location.heroLabel()}"
        heroScene.setSceneProfile(
            SceneProfileResolver.resolve(location)
        )
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

    private fun showSceneMenu() {
        AlertDialog.Builder(this)
            .setTitle("Atmosynq World")
            .setItems(
                arrayOf(
                    "Explore this weather world",
                    "Start Eye Spy",
                    "Scene controls"
                )
            ) { _, which ->
                when (which) {
                    0 -> openWorldExplorer(startEyeSpy = false)
                    1 -> openWorldExplorer(startEyeSpy = true)
                    2 ->
                        AlertDialog.Builder(this)
                            .setTitle("Live Sky controls")
                            .setMessage(
                                "Drag horizontally to look around, pinch to zoom, or tap the scene to enter Atmosynq Worlds. Weather and lighting follow the selected location."
                            )
                            .setPositiveButton("Done", null)
                            .show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openWorldExplorer(startEyeSpy: Boolean) {
        val intent =
            Intent(this, WorldExploreActivity::class.java)
                .putExtra(
                    WorldExploreActivity.EXTRA_START_EYE_SPY,
                    startEyeSpy
                )

        activeReport?.current?.toJson()?.let { weatherJson ->
            intent.putExtra(
                WorldExploreActivity.EXTRA_WEATHER_JSON,
                weatherJson
            )
        }

        startActivity(intent)
    }

    private fun showHourlyTimeline() {
        val report = activeReport
        if (report == null) {
            status.text = "SYNC WEATHER FIRST"
            refreshSavedWeather()
            return
        }

        val rows =
            nextHourly(report).joinToString("\n\n") { hour ->
                val time =
                    parseLocalDateTime(hour.timeIsoLocal)
                        ?.format(
                            DateTimeFormatter.ofPattern(
                                "EEE h:mm a",
                                Locale.getDefault()
                            )
                        )
                        ?: hour.timeIsoLocal

                buildString {
                    append(time)
                    append("   ")
                    append(formatTemperature(hour.temperatureC))
                    append("\n")
                    append(
                        WeatherCode.description(
                            hour.weatherCode
                        )
                    )
                    append("  •  Rain ")
                    append(hour.precipitationProbabilityPct)
                    append("%  •  Wind ")
                    append(formatWind(hour.windSpeedKmh))
                }
            }

        AlertDialog.Builder(this)
            .setTitle("Next 12 Hours")
            .setMessage(
                rows.ifBlank {
                    "No hourly forecast is available yet."
                }
            )
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showFullForecast() {
        val report = activeReport
        if (report == null) {
            status.text = "SYNC WEATHER FIRST"
            refreshSavedWeather()
            return
        }

        val rows =
            report.daily.take(7)
                .mapIndexed { index, day ->
                    buildString {
                        append(dayLabel(day.dateIso, index))
                        append("   ")
                        append(
                            WeatherCode.description(
                                day.weatherCode
                            )
                        )
                        append("\n")
                        append("High ")
                        append(formatTemperature(day.highC))
                        append("  •  Low ")
                        append(formatTemperature(day.lowC))
                        append("  •  Rain ")
                        append(day.precipitationProbabilityPct)
                        append("%")
                    }
                }
                .joinToString("\n\n")

        AlertDialog.Builder(this)
            .setTitle("7-Day Forecast")
            .setMessage(
                rows.ifBlank {
                    "No daily forecast is available yet."
                }
            )
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showQuickSettings() {
        val animated = motionStore.isAnimated()
        val motionLabel =
            if (animated) {
                "Switch to Static mode"
            } else {
                "Switch to Animated mode"
            }

        AlertDialog.Builder(this)
            .setTitle("Atmosynq")
            .setItems(
                arrayOf(
                    "Choose location",
                    "Refresh weather",
                    motionLabel,
                    "Location & privacy"
                )
            ) { _, which ->
                when (which) {
                    0 -> showLocationChooser()
                    1 -> refreshSavedWeather()
                    2 -> setMotionMode(!animated)
                    3 ->
                        AlertDialog.Builder(this)
                            .setTitle("Location & privacy")
                            .setMessage(
                                "City and postal-code search works without device location permission. Approximate current location is optional and Atmosynq does not require background location."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun refreshSavedWeather() {
        val saved =
            activeLocation ?: locationStore.load()
        if (saved == null) {
            showLocationChooser()
            return
        }

        status.text = "SYNCING…"
        refreshAtmosynqWeather(
            saved.latitude,
            saved.longitude
        )
    }

    private fun showLocationChooser() {
        AlertDialog.Builder(this)
            .setTitle("Choose weather location")
            .setItems(
                arrayOf(
                    "Search city or postal code",
                    "Use approximate current location"
                )
            ) { _, which ->
                when (which) {
                    0 -> showLocationSearchDialog()
                    1 -> requestOrCaptureLocation()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLocationSearchDialog() {
        val input =
            EditText(this).apply {
                hint = "City, region/country, or postal code"
                inputType =
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_WORDS
                setSingleLine(true)
                setPadding(dp(18), dp(12), dp(18), dp(12))
            }

        val container =
            FrameLayout(this).apply {
                setPadding(dp(18), dp(4), dp(18), 0)
                addView(
                    input,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Search anywhere")
                .setMessage(
                    "Enter a city, city + state/province/country, or postal code. No location permission is needed."
                )
                .setView(container)
                .setPositiveButton("Search", null)
                .setNegativeButton("Cancel", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val query = input.text?.toString()?.trim().orEmpty()
                    if (query.length < 2) {
                        input.error = "Enter at least 2 characters"
                        return@setOnClickListener
                    }

                    dialog.dismiss()
                    searchGlobalLocation(query)
                }
        }

        dialog.show()
    }

    private fun searchGlobalLocation(query: String) {
        status.text = "SEARCHING…"
        locationButton.isEnabled = false
        locationButton.alpha = 0.6f

        Thread {
            val result =
                runCatching {
                    GeocodingClient().search(query = query, count = 12)
                }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                locationButton.isEnabled = true
                locationButton.alpha = 1f

                result.onSuccess { places ->
                    if (places.isEmpty()) {
                        status.text = "NO MATCH"
                    } else {
                        showLocationResults(query, places)
                    }
                }.onFailure {
                    status.text = "SEARCH FAILED"
                }
            }
        }.apply {
            name = "AtmosynqGlobalLocationSearch"
            isDaemon = true
        }.start()
    }

    private fun showLocationResults(
        query: String,
        places: List<PlaceSearchResult>
    ) {
        val labels =
            places.map { place ->
                place.displayLabel()
            }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select location")
            .setItems(labels) { _, which ->
                places.getOrNull(which)?.let { place ->
                    selectSearchedPlace(query, place)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectSearchedPlace(
        query: String,
        place: PlaceSearchResult
    ) {
        status.text = "BUILDING SKY…"
        locationButton.isEnabled = false
        locationButton.alpha = 0.6f

        Thread {
            val terrain =
                runCatching {
                    TerrainContextClient().fetch(
                        latitude = place.latitude,
                        longitude = place.longitude
                    )
                }.getOrElse {
                    val elevation = place.elevationM ?: 0.0
                    TerrainContext(
                        centerElevationM = elevation,
                        minElevationM = elevation,
                        maxElevationM = elevation,
                        reliefM = 0.0
                    )
                }

            val normalizedQuery =
                query.replace(" ", "").uppercase(Locale.ROOT)
            val matchedPostal =
                place.postcodes.firstOrNull { postcode ->
                    postcode.replace(" ", "").uppercase(Locale.ROOT) ==
                        normalizedQuery
                }

            val saved =
                SavedLocation(
                    latitude = place.latitude,
                    longitude = place.longitude,
                    savedAtEpochMs = System.currentTimeMillis(),
                    displayName =
                        matchedPostal?.let { postcode ->
                            "${place.shortLabel()} • $postcode"
                        } ?: place.shortLabel(),
                    locality = place.name,
                    admin1 = place.admin1,
                    countryCode = place.countryCode,
                    country = place.country,
                    postalCode = matchedPostal,
                    elevationM =
                        place.elevationM
                            ?: terrain.centerElevationM,
                    reliefM = terrain.reliefM,
                    population = place.population,
                    featureCode = place.featureCode,
                    source = LocationSource.SEARCH
                )

            locationStore.save(saved)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                locationButton.isEnabled = true
                locationButton.alpha = 1f
                wallpaperButton.isEnabled = true
                wallpaperButton.alpha = 1f
                applySelectedLocation(saved)
                status.text = "SYNCING…"
                refreshAtmosynqWeather(
                    saved.latitude,
                    saved.longitude
                )
            }
        }.apply {
            name = "AtmosynqLocationSceneResolver"
            isDaemon = true
        }.start()
    }

    private fun requestOrCaptureLocation() {
        val coarseGranted =
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (!coarseGranted) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION
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
            status.text = "LOCATION OPTIONAL"
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
        status.text = "LOCATING…"
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

        if (location == null) {
            locationButton.isEnabled = true
            locationButton.alpha = 1f
            status.text = "TRY CITY / ZIP"
            return
        }

        status.text = "RESOLVING…"

        Thread {
            val reverse = reverseGeocode(location)
            val matchedPlace =
                reverse.second
                    ?.let { query ->
                        runCatching {
                            GeocodingClient()
                                .search(query = query, count = 12)
                                .minByOrNull { candidate ->
                                    val dLat =
                                        candidate.latitude -
                                            location.latitude
                                    val dLon =
                                        candidate.longitude -
                                            location.longitude
                                    dLat * dLat + dLon * dLon
                                }
                        }.getOrNull()
                    }

            val terrain =
                runCatching {
                    TerrainContextClient().fetch(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }.getOrElse {
                    val elevation =
                        when {
                            matchedPlace?.elevationM != null ->
                                matchedPlace.elevationM
                            location.hasAltitude() ->
                                location.altitude
                            else ->
                                0.0
                        }

                    TerrainContext(
                        centerElevationM = elevation,
                        minElevationM = elevation,
                        maxElevationM = elevation,
                        reliefM = 0.0
                    )
                }

            val displayName =
                reverse.first
                    .takeIf { it.isNotBlank() }
                    ?: matchedPlace?.displayLabel()
                    ?: "Approximate location"

            val saved =
                SavedLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    savedAtEpochMs = System.currentTimeMillis(),
                    displayName = displayName,
                    locality = matchedPlace?.name,
                    admin1 = matchedPlace?.admin1,
                    countryCode = matchedPlace?.countryCode,
                    country = matchedPlace?.country,
                    elevationM =
                        matchedPlace?.elevationM
                            ?: terrain.centerElevationM,
                    reliefM = terrain.reliefM,
                    population = matchedPlace?.population,
                    featureCode = matchedPlace?.featureCode,
                    source = LocationSource.DEVICE
                )

            locationStore.save(saved)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                locationButton.isEnabled = true
                locationButton.alpha = 1f
                wallpaperButton.isEnabled = true
                wallpaperButton.alpha = 1f
                applySelectedLocation(saved)
                status.text = "SYNCING…"
                refreshAtmosynqWeather(
                    saved.latitude,
                    saved.longitude
                )
            }
        }.apply {
            name = "AtmosynqApproxLocationResolver"
            isDaemon = true
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(location: Location): Pair<String, String?> {
        if (!Geocoder.isPresent()) {
            return "Approximate location" to null
        }

        return runCatching {
            val address =
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(
                        location.latitude,
                        location.longitude,
                        1
                    )
                    ?.firstOrNull()
                    ?: return@runCatching "Approximate location" to null

            val locality =
                address.locality
                    ?: address.subAdminArea
                    ?: address.adminArea
            val region =
                address.adminArea
                    ?.takeIf {
                        it.isNotBlank() &&
                            !it.equals(locality, ignoreCase = true)
                    }
            val country =
                address.countryCode
                    ?.uppercase(Locale.US)
                    ?: address.countryName

            val label =
                listOfNotNull(
                    locality?.takeIf { it.isNotBlank() },
                    region?.takeIf { it.isNotBlank() },
                    country?.takeIf { it.isNotBlank() }
                )
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { "Approximate location" }

            val searchQuery =
                listOfNotNull(
                    locality?.takeIf { it.isNotBlank() },
                    region?.takeIf { it.isNotBlank() },
                    country?.takeIf { it.isNotBlank() }
                )
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() }

            label to searchQuery
        }.getOrDefault("Approximate location" to null)
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

        val selectedLocation =
            activeLocation ?: locationStore.load()
        heroLocation.text =
            "⌖  ${selectedLocation?.heroLabel() ?: "Selected location"}"
        selectedLocation?.let {
            heroScene.setSceneProfile(
                SceneProfileResolver.resolve(it)
            )
        }
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

        status.text = "●  LIVE • $updated  ↻"
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
            setPadding(dp(10), dp(11), dp(10), dp(10))
            background =
                if (label == "Now") {
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(10, 89, 126),
                            Color.rgb(13, 55, 88)
                        ),
                        18,
                        Color.rgb(55, 214, 255)
                    )
                } else {
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(10, 38, 61),
                            Color.rgb(7, 27, 47)
                        ),
                        18,
                        Color.rgb(36, 76, 101)
                    )
                }

            addView(
                TextView(this@MainActivity).apply {
                    text = label.uppercase(Locale.getDefault())
                    textSize = 10.5f
                    letterSpacing = 0.06f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(
                        if (label == "Now") {
                            Color.rgb(90, 226, 255)
                        } else {
                            COLOR_MUTED
                        }
                    )
                    gravity = Gravity.CENTER
                },
                matchWrap()
            )

            addView(
                TextView(this@MainActivity).apply {
                    text =
                        WeatherCode.symbol(
                            hour.weatherCode,
                            hour.isDay
                        )
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(5), 0, dp(1))
                },
                matchWrap()
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = formatTemperature(hour.temperatureC)
                    textSize = 19f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                },
                matchWrap()
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = "RAIN ${hour.precipitationProbabilityPct}%"
                    textSize = 9.5f
                    letterSpacing = 0.04f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(95, 213, 255))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, dp(5))
                },
                matchWrap()
            )

            val track =
                FrameLayout(this@MainActivity).apply {
                    background =
                        roundedBackground(
                            Color.argb(160, 1, 17, 30),
                            2
                        )

                    val fillWidth =
                        (
                            6 +
                                48 *
                                hour.precipitationProbabilityPct
                                    .coerceIn(0, 100) /
                                100f
                            ).roundToInt()

                    addView(
                        View(this@MainActivity).apply {
                            background =
                                gradientBackground(
                                    intArrayOf(
                                        Color.rgb(48, 211, 255),
                                        Color.rgb(129, 83, 255)
                                    ),
                                    2
                                )
                        },
                        FrameLayout.LayoutParams(
                            dp(fillWidth),
                            dp(3),
                            Gravity.START
                        )
                    )
                }

            addView(
                track,
                LinearLayout.LayoutParams(
                    dp(54),
                    dp(3)
                )
            )
        }.also { card ->
            card.layoutParams =
                LinearLayout.LayoutParams(
                    dp(86),
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
            dailyContainer.addView(
                dayRow(day, index),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin =
                        if (index < minOf(6, report.daily.lastIndex)) {
                            dp(6)
                        } else {
                            0
                        }
                }
            )
        }

        dailySection.visibility = View.VISIBLE
    }

    private fun dayRow(
        day: DailyForecast,
        index: Int
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(9))
            background =
                gradientBackground(
                    intArrayOf(
                        Color.argb(210, 10, 38, 61),
                        Color.argb(210, 7, 26, 46)
                    ),
                    17,
                    Color.argb(115, 55, 102, 132)
                )

            val row =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

            row.addView(
                TextView(this@MainActivity).apply {
                    text = dayLabel(day.dateIso, index)
                    textSize = 12.5f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(
                    dp(76),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            row.addView(
                TextView(this@MainActivity).apply {
                    text = WeatherCode.symbol(day.weatherCode)
                    textSize = 18f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    dp(36),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            row.addView(
                TextView(this@MainActivity).apply {
                    text = WeatherCode.description(day.weatherCode)
                    textSize = 11.5f
                    setTextColor(COLOR_TEXT_SECONDARY)
                    gravity = Gravity.START
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            row.addView(
                TextView(this@MainActivity).apply {
                    text =
                        "${formatTemperature(day.highC)}  ${formatTemperature(day.lowC)}"
                    textSize = 12.5f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.END
                },
                LinearLayout.LayoutParams(
                    dp(78),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            row.addView(
                TextView(this@MainActivity).apply {
                    text = "💧${day.precipitationProbabilityPct}%"
                    textSize = 10.5f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(102, 218, 255))
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(3), dp(5), dp(3))
                    background =
                        roundedBackground(
                            Color.argb(155, 8, 58, 88),
                            11,
                            Color.argb(130, 57, 174, 229)
                        )
                },
                LinearLayout.LayoutParams(
                    dp(58),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            val probabilityTrack =
                FrameLayout(this@MainActivity).apply {
                    background =
                        roundedBackground(
                            Color.argb(145, 3, 17, 31),
                            2
                        )

                    val widthDp =
                        (
                            8 +
                                92 *
                                day.precipitationProbabilityPct
                                    .coerceIn(0, 100) /
                                100f
                            ).roundToInt()

                    addView(
                        View(this@MainActivity).apply {
                            background =
                                gradientBackground(
                                    intArrayOf(
                                        Color.rgb(41, 207, 255),
                                        Color.rgb(104, 117, 255),
                                        Color.rgb(163, 79, 255)
                                    ),
                                    2
                                )
                        },
                        FrameLayout.LayoutParams(
                            dp(widthDp),
                            dp(3),
                            Gravity.START
                        )
                    )
                }

            addView(
                probabilityTrack,
                LinearLayout.LayoutParams(
                    dp(100),
                    dp(3)
                ).apply {
                    topMargin = dp(7)
                }
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
            textSize = 11.5f
            letterSpacing = 0.045f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { onClick() }
        }

    private fun styleMotionTab(
        view: TextView,
        active: Boolean
    ) {
        view.setTextColor(
            if (active) {
                Color.WHITE
            } else {
                Color.rgb(125, 158, 183)
            }
        )

        view.background =
            if (active) {
                gradientBackground(
                    intArrayOf(
                        Color.rgb(16, 155, 210),
                        Color.rgb(25, 104, 181)
                    ),
                    17,
                    Color.rgb(76, 216, 255)
                )
            } else {
                roundedBackground(
                    Color.TRANSPARENT,
                    17,
                    Color.TRANSPARENT
                )
            }
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
            textSize = if (primary) 15f else 11.5f
            letterSpacing = if (primary) 0.035f else 0.065f
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background =
                if (primary) {
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(18, 194, 247),
                            Color.rgb(24, 121, 243),
                            Color.rgb(121, 75, 250)
                        ),
                        21,
                        Color.rgb(108, 225, 255)
                    )
                } else {
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(11, 41, 64),
                            Color.rgb(7, 29, 49)
                        ),
                        17,
                        Color.rgb(42, 84, 111)
                    )
                }
            stateListAnimator = null
            minHeight = 0
            minWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(70L)
                    .withEndAction {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120L)
                            .start()
                        onClick()
                    }
                    .start()
            }
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

    private fun sectionHeader(
        title: String,
        action: String,
        onAction: () -> Unit
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(9))

            addView(
                View(this@MainActivity).apply {
                    background =
                        GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(
                                Color.rgb(42, 217, 255),
                                Color.rgb(142, 78, 255)
                            )
                        ).apply {
                            cornerRadius = dp(2).toFloat()
                        }
                },
                LinearLayout.LayoutParams(
                    dp(3),
                    dp(30)
                ).apply {
                    marginEnd = dp(9)
                }
            )

            val titleStack =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL

                    addView(
                        TextView(this@MainActivity).apply {
                            text = "FORECAST STREAM"
                            textSize = 8.5f
                            letterSpacing = 0.12f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(Color.rgb(78, 194, 240))
                            includeFontPadding = false
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )

                    addView(
                        TextView(this@MainActivity).apply {
                            text = title
                            textSize = 17f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            includeFontPadding = false
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }

            addView(
                titleStack,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = action
                    textSize = 10.5f
                    letterSpacing = 0.03f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(88, 210, 255))
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    contentDescription = "$action for $title"
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background =
                        roundedBackground(
                            Color.argb(80, 34, 123, 166),
                            14,
                            Color.argb(120, 77, 202, 255)
                        )
                    setOnClickListener {
                        animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(60L)
                            .withEndAction {
                                animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(100L)
                                    .start()
                                onAction()
                            }
                            .start()
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

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
