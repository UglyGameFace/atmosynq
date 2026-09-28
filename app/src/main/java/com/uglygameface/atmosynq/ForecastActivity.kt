package com.uglygameface.atmosynq

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.uglygameface.atmosynq.location.LocationStore
import com.uglygameface.atmosynq.render.FilamentWeatherHeroView
import com.uglygameface.atmosynq.render.SceneProfileResolver
import com.uglygameface.atmosynq.weather.DailyForecast
import com.uglygameface.atmosynq.weather.HourlyForecast
import com.uglygameface.atmosynq.weather.WeatherCode
import com.uglygameface.atmosynq.weather.WeatherReport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class ForecastActivity : Activity() {
    private lateinit var report: WeatherReport
    private lateinit var contentHost: LinearLayout
    private lateinit var hourlyTab: TextView
    private lateinit var dailyTab: TextView
    private var mode: String = MODE_DAILY

    private val usesUsUnits: Boolean
        get() = Locale.getDefault().country.equals("US", ignoreCase = true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val parsed =
            intent.getStringExtra(EXTRA_REPORT_JSON)
                ?.let(WeatherReport::fromJson)

        if (parsed == null) {
            finish()
            return
        }

        report = parsed
        mode =
            intent.getStringExtra(EXTRA_MODE)
                ?.takeIf { it == MODE_HOURLY || it == MODE_DAILY }
                ?: MODE_DAILY

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.rgb(2, 8, 17)

        setContentView(buildUi())
        renderMode(animate = false)
    }

    private fun buildUi(): FrameLayout {
        val root =
            FrameLayout(this).apply {
                background =
                    gradientBackground(
                        intArrayOf(
                            Color.rgb(3, 17, 31),
                            Color.rgb(2, 10, 20),
                            Color.rgb(1, 7, 15)
                        ),
                        0
                    )
            }

        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(28))
            }

        val hero =
            FrameLayout(this).apply {
                clipToOutline = true
                background =
                    roundedBackground(
                        Color.rgb(7, 26, 46),
                        30,
                        Color.rgb(50, 143, 198)
                    )
                elevation = dp(10).toFloat()
            }

        val location = LocationStore(this).load()
        val scene =
            FilamentWeatherHeroView(this).apply {
                location?.let {
                    setSceneProfile(
                        SceneProfileResolver.resolve(it)
                    )
                }
                setWeather(report.current)
                setAnimated(true)
                isClickable = false
            }

        hero.addView(
            scene,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        hero.addView(
            View(this).apply {
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            Color.argb(190, 1, 8, 18),
                            Color.argb(35, 1, 8, 18),
                            Color.argb(110, 1, 8, 18),
                            Color.argb(235, 1, 7, 16)
                        )
                    )
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val back =
            TextView(this).apply {
                text = "‹"
                textSize = 30f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background =
                    roundedBackground(
                        Color.argb(190, 5, 23, 40),
                        22,
                        Color.argb(150, 75, 197, 247)
                    )
                setOnClickListener { finish() }
                contentDescription = "Back"
            }

        hero.addView(
            back,
            FrameLayout.LayoutParams(
                dp(46),
                dp(46),
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = dp(14)
                topMargin = dp(14)
            }
        )

        hero.addView(
            TextView(this).apply {
                text = "FORECAST  //  LIVE ATMOSPHERE"
                textSize = 9f
                letterSpacing = 0.13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(73, 216, 255))
                gravity = Gravity.END
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                rightMargin = dp(18)
                topMargin = dp(24)
            }
        )

        val heroInfo =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), 0, dp(20), dp(18))
            }

        heroInfo.addView(
            TextView(this).apply {
                text =
                    location?.heroLabel()
                        ?: "Selected location"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(220, 239, 251))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        )

        heroInfo.addView(
            TextView(this).apply {
                text = formatTemperature(report.current.temperatureC)
                textSize = 68f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                includeFontPadding = false
            }
        )

        heroInfo.addView(
            TextView(this).apply {
                text =
                    "${WeatherCode.symbol(
                        report.current.weatherCode,
                        report.current.isDay
                    )}  ${WeatherCode.description(report.current.weatherCode)}"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
        )

        hero.addView(
            heroInfo,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        content.addView(
            hero,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(292)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        val modeDeck =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background =
                    roundedBackground(
                        Color.argb(220, 7, 27, 46),
                        22,
                        Color.rgb(37, 78, 104)
                    )
            }

        hourlyTab =
            modeTab("NEXT 24 HOURS") {
                mode = MODE_HOURLY
                renderMode()
            }

        dailyTab =
            modeTab("7-DAY OUTLOOK") {
                mode = MODE_DAILY
                renderMode()
            }

        modeDeck.addView(
            hourlyTab,
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            )
        )
        modeDeck.addView(
            dailyTab,
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            )
        )

        content.addView(
            modeDeck,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        )

        contentHost =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        content.addView(
            contentHost,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        scroll.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int
            if (android.os.Build.VERSION.SDK_INT >= 30) {
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

        return root
    }

    private fun renderMode(animate: Boolean = true) {
        styleTab(
            hourlyTab,
            active = mode == MODE_HOURLY
        )
        styleTab(
            dailyTab,
            active = mode == MODE_DAILY
        )

        contentHost.removeAllViews()

        if (mode == MODE_HOURLY) {
            renderHourly()
        } else {
            renderDaily()
        }

        if (animate) {
            contentHost.alpha = 0f
            contentHost.translationY = dp(12).toFloat()
            contentHost.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start()
        }
    }

    private fun renderHourly() {
        val upcoming =
            report.hourly
                .filter {
                    parseDateTime(it.timeIsoLocal)
                        ?.let { time ->
                            !time.isBefore(
                                LocalDateTime.now()
                                    .minusHours(1)
                            )
                        } ?: true
                }
                .take(24)

        upcoming.forEachIndexed { index, hour ->
            contentHost.addView(
                hourlyCard(hour),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin =
                        if (index < upcoming.lastIndex) dp(9) else 0
                }
            )
        }
    }

    private fun hourlyCard(
        hour: HourlyForecast
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(13))
            background =
                gradientBackground(
                    intArrayOf(
                        Color.rgb(10, 40, 64),
                        Color.rgb(6, 25, 44)
                    ),
                    22,
                    Color.argb(150, 56, 117, 153)
                )

            val row =
                LinearLayout(this@ForecastActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

            val whenText =
                parseDateTime(hour.timeIsoLocal)
                    ?.format(
                        DateTimeFormatter.ofPattern(
                            "EEE  h a",
                            Locale.getDefault()
                        )
                    )
                    ?: hour.timeIsoLocal

            row.addView(
                TextView(this@ForecastActivity).apply {
                    text = whenText.uppercase(Locale.getDefault())
                    textSize = 11f
                    letterSpacing = 0.06f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(96, 216, 255))
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            row.addView(
                TextView(this@ForecastActivity).apply {
                    text =
                        WeatherCode.symbol(
                            hour.weatherCode,
                            hour.isDay
                        )
                    textSize = 24f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    dp(42),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            row.addView(
                TextView(this@ForecastActivity).apply {
                    text = formatTemperature(hour.temperatureC)
                    textSize = 25f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.END
                },
                LinearLayout.LayoutParams(
                    dp(74),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            addView(row)

            addView(
                TextView(this@ForecastActivity).apply {
                    text =
                        WeatherCode.description(hour.weatherCode)
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(225, 238, 247))
                    setPadding(0, dp(5), 0, dp(8))
                }
            )

            addView(
                metricLine(
                    "RAIN",
                    "${hour.precipitationProbabilityPct}%",
                    "WIND",
                    formatWind(hour.windSpeedKmh)
                )
            )

            addView(
                probabilityBar(
                    hour.precipitationProbabilityPct
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(4)
                ).apply {
                    topMargin = dp(10)
                }
            )
        }

    private fun renderDaily() {
        report.daily.take(7)
            .forEachIndexed { index, day ->
                contentHost.addView(
                    dailyCard(day, index),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin =
                            if (index < minOf(
                                    6,
                                    report.daily.lastIndex
                                )
                            ) {
                                dp(9)
                            } else {
                                0
                            }
                    }
                )
            }
    }

    private fun dailyCard(
        day: DailyForecast,
        index: Int
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(13))
            background =
                gradientBackground(
                    intArrayOf(
                        Color.rgb(9, 38, 61),
                        Color.rgb(6, 23, 41)
                    ),
                    22,
                    Color.argb(145, 55, 111, 145)
                )

            val header =
                LinearLayout(this@ForecastActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

            header.addView(
                TextView(this@ForecastActivity).apply {
                    text =
                        dayLabel(day.dateIso, index)
                            .uppercase(Locale.getDefault())
                    textSize = 12f
                    letterSpacing = 0.065f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(91, 216, 255))
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            header.addView(
                TextView(this@ForecastActivity).apply {
                    text = WeatherCode.symbol(day.weatherCode)
                    textSize = 23f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    dp(44),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            header.addView(
                TextView(this@ForecastActivity).apply {
                    text =
                        "${formatTemperature(day.highC)}  /  ${formatTemperature(day.lowC)}"
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.END
                }
            )

            addView(header)

            addView(
                TextView(this@ForecastActivity).apply {
                    text = WeatherCode.description(day.weatherCode)
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(224, 238, 247))
                    setPadding(0, dp(6), 0, dp(8))
                }
            )

            addView(
                metricLine(
                    "PRECIP",
                    "${day.precipitationProbabilityPct}%",
                    "SUN",
                    formatSun(day)
                )
            )

            addView(
                probabilityBar(
                    day.precipitationProbabilityPct
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(4)
                ).apply {
                    topMargin = dp(10)
                }
            )
        }

    private fun metricLine(
        leftLabel: String,
        leftValue: String,
        rightLabel: String,
        rightValue: String
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                metricText(
                    leftLabel,
                    leftValue,
                    Gravity.START
                ),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            addView(
                metricText(
                    rightLabel,
                    rightValue,
                    Gravity.END
                ),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }

    private fun metricText(
        label: String,
        value: String,
        gravityValue: Int
    ): TextView =
        TextView(this).apply {
            text = "$label  //  $value"
            textSize = 10.5f
            letterSpacing = 0.04f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(147, 184, 208))
            gravity = gravityValue
        }

    private fun probabilityBar(percent: Int): FrameLayout =
        FrameLayout(this).apply {
            background =
                roundedBackground(
                    Color.argb(180, 2, 15, 28),
                    3
                )

            val safePercent = percent.coerceIn(0, 100)
            post {
                val available = width.coerceAtLeast(1)
                val fillWidth =
                    (
                        available *
                            safePercent /
                            100f
                        ).roundToInt()
                            .coerceAtLeast(
                                if (safePercent > 0) dp(6) else 0
                            )

                addView(
                    View(this@ForecastActivity).apply {
                        background =
                            gradientBackground(
                                intArrayOf(
                                    Color.rgb(44, 215, 255),
                                    Color.rgb(99, 123, 255),
                                    Color.rgb(166, 79, 255)
                                ),
                                3
                            )
                    },
                    FrameLayout.LayoutParams(
                        fillWidth,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }

    private fun modeTab(
        label: String,
        onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = label
            textSize = 10.5f
            letterSpacing = 0.055f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }

    private fun styleTab(
        tab: TextView,
        active: Boolean
    ) {
        tab.setTextColor(
            if (active) {
                Color.WHITE
            } else {
                Color.rgb(128, 161, 184)
            }
        )
        tab.background =
            if (active) {
                gradientBackground(
                    intArrayOf(
                        Color.rgb(20, 168, 218),
                        Color.rgb(38, 94, 192),
                        Color.rgb(103, 70, 221)
                    ),
                    18,
                    Color.rgb(89, 219, 255)
                )
            } else {
                roundedBackground(
                    Color.TRANSPARENT,
                    18
                )
            }
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
                        "EEEE",
                        Locale.getDefault()
                    )
                )
        }.getOrDefault(dateIso)
    }

    private fun formatSun(
        day: DailyForecast
    ): String {
        val rise =
            parseDateTime(day.sunriseIsoLocal)
                ?.format(
                    DateTimeFormatter.ofPattern(
                        "h:mm a",
                        Locale.getDefault()
                    )
                )
        val set =
            parseDateTime(day.sunsetIsoLocal)
                ?.format(
                    DateTimeFormatter.ofPattern(
                        "h:mm a",
                        Locale.getDefault()
                    )
                )

        return when {
            rise != null && set != null ->
                "$rise / $set"
            rise != null ->
                rise
            set != null ->
                set
            else ->
                "--"
        }
    }

    private fun parseDateTime(
        raw: String?
    ): LocalDateTime? =
        raw?.let {
            runCatching {
                LocalDateTime.parse(it)
            }.getOrNull()
        }

    private fun formatTemperature(
        celsius: Double
    ): String {
        val value =
            if (usesUsUnits) {
                celsius * 9.0 / 5.0 + 32.0
            } else {
                celsius
            }
        return "${value.roundToInt()}°"
    }

    private fun formatWind(
        kmh: Double
    ): String =
        if (usesUsUnits) {
            "${(kmh * 0.621371).roundToInt()} mph"
        } else {
            "${kmh.roundToInt()} km/h"
        }

    private fun roundedBackground(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (
                strokeColor != null &&
                strokeColor != Color.TRANSPARENT
            ) {
                setStroke(dp(1), strokeColor)
            }
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
            if (
                strokeColor != null &&
                strokeColor != Color.TRANSPARENT
            ) {
                setStroke(dp(1), strokeColor)
            }
        }

    private fun dp(value: Int): Int =
        (
            value *
                resources.displayMetrics.density
            ).roundToInt()

    companion object {
        const val EXTRA_REPORT_JSON =
            "com.uglygameface.atmosynq.extra.REPORT_JSON"
        const val EXTRA_MODE =
            "com.uglygameface.atmosynq.extra.FORECAST_MODE"

        const val MODE_HOURLY = "hourly"
        const val MODE_DAILY = "daily"
    }
}
