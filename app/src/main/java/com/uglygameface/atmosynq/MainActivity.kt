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

        content.addView(TextView(this).apply {
            text = "Weather that comes alive."
            textSize = 13.5f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(1), 0, dp(14))
        }, matchWrap())

        val motionSwitcher = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = roundedBackground(COLOR_SEGMENT_TRACK, 22, COLOR_STROKE)
        }
        animatedTab = motionTab("Animated") { setMotionMode(true) }
        staticTab = motionTab("Static") { setMotionMode(false) }
        motionSwitcher.addView(animatedTab, LinearLayout.LayoutParams(0, dp(42), 1f))
        motionSwitcher.addView(staticTab, LinearLayout.LayoutParams(0, dp(42), 1f))
        content.addView(motionSwitcher, matchWrap().apply { bottomMargin = dp(10) })

        status = TextView(this).apply {
            textSize = 12.5f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedBackground(COLOR_STATUS_BG, 18, COLOR_STROKE)
        }
        content.addView(status, matchWrap().apply { bottomMargin = dp(10) })

        val currentCard = FrameLayout(this).apply {
            background = roundedBackground(COLOR_HERO_FALLBACK, 26, COLOR_HERO_STROKE)
            clipToOutline = true
        }

        heroScene = ImageView(this).apply {
            setImageResource(R.drawable.atmosynq_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.58f
        }
        currentCard.addView(
            heroScene,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        currentCard.addView(
            View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.argb(15, 4, 10, 22),
                        Color.argb(90, 4, 10, 22),
                        Color.argb(226, 4, 10, 22)
                    )
                )
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
            text = "The dashboard, widget, and wallpaper all stay synced to the same weather."
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
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(270)).apply {
                bottomMargin = dp(14)
            }
        )

        locationButton = premiumButton("Use current location", primary = true) {
            requestOrCaptureLocation()
        }
        content.addView(
            locationButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(8) }
        )

        wallpaperButton = premiumButton(
            if (Build.VERSION.SDK_INT >= 36) "Set home / lock live wallpaper" else "Preview & set live wallpaper",
            primary = false
        ) { openWallpaperPicker() }
        content.addView(
            wallpaperButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(8) }
        )

        widgetButton = premiumButton("Add Atmosynq home widget", primary = false) {
            requestPinWidget()
        }
        content.addView(
            widgetButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(14) }
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
        hourlySection.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                hourlyContainer,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }, matchWrap())
        content.addView(hourlySection, matchWrap().apply { bottomMargin = dp(6) })

        dailySection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        dailySection.addView(sectionTitle("7-day forecast"), matchWrap())
        dailyContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dailySection.addView(dailyContainer, matchWrap())
        content.addView(dailySection, matchWrap())

        content.addView(Space(this), LinearLayout.LayoutParams(1, dp(12)))

        content.addView(TextView(this).apply {
            text = "Location stays in app-private storage and is excluded from Android backup. Background location permission is not required."
            textSize = 11.5f
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(dp(8), dp(10), dp(8), 0)
        }, matchWrap())

        return scroll
    }

    private fun renderSavedState() {
        val saved = locationStore.load()
        if (saved == null) {
            status.text = "Not synced yet"
            wallpaperButton.isEnabled = false
            wallpaperButton.alpha = 0.42f
            heroFrames = emptyList()
            activeReport = null
            heroScene.setImageResource(R.drawable.atmosynq_logo)
            