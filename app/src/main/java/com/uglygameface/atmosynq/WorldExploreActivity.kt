package com.uglygameface.atmosynq

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.uglygameface.atmosynq.location.LocationStore
import com.uglygameface.atmosynq.render.FilamentWeatherHeroView
import com.uglygameface.atmosynq.render.SceneProfileResolver
import com.uglygameface.atmosynq.weather.WeatherSnapshot
import com.uglygameface.atmosynq.worlds.EyeSpyTarget
import com.uglygameface.atmosynq.worlds.WorldExperience
import com.uglygameface.atmosynq.worlds.WorldExperienceResolver
import com.uglygameface.atmosynq.worlds.WorldProgressStore
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.hypot

class WorldExploreActivity : Activity() {
    private lateinit var eyeSpyButton: Button
    private lateinit var eyeSpyStatus: TextView
    private lateinit var eyeSpyOverlay: EyeSpyOverlayView

    private lateinit var experience: WorldExperience
    private lateinit var dailyTargets: List<EyeSpyTarget>
    private lateinit var worldDate: LocalDate
    private val progressStore by lazy { WorldProgressStore(this) }
    private var eyeSpyActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.rgb(2, 8, 17)

        val location = LocationStore(this).load()
        val profile = SceneProfileResolver.resolve(location)
        val weather =
            intent.getStringExtra(EXTRA_WEATHER_JSON)
                ?.let(WeatherSnapshot::fromJson)

        experience =
            WorldExperienceResolver.resolve(
                profile = profile,
                weather = weather
            )

        val worldZone =
            weather?.timezone
                ?.let { zoneId ->
                    runCatching {
                        ZoneId.of(zoneId)
                    }.getOrNull()
                }
                ?: ZoneId.systemDefault()
        worldDate = LocalDate.now(worldZone)
        dailyTargets =
            experience.eyeSpyTargets
                .let { targets ->
                    if (targets.isEmpty()) {
                        targets
                    } else {
                        val offset =
                            worldDate.dayOfYear % targets.size
                        targets.drop(offset) +
                            targets.take(offset)
                    }
                }

        val root =
            FrameLayout(this).apply {
                setBackgroundColor(Color.rgb(2, 8, 17))
            }

        val scene =
            FilamentWeatherHeroView(this).apply {
                setSceneProfile(profile)
                setWeather(weather)
                setAnimated(true)
                contentDescription =
                    "Atmosynq World. Drag to look around and pinch to zoom."
            }

        root.addView(
            scene,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            View(this).apply {
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            Color.argb(205, 1, 8, 18),
                            Color.argb(30, 1, 8, 18),
                            Color.argb(8, 1, 8, 18),
                            Color.argb(226, 1, 7, 16)
                        )
                    )
                isClickable = false
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        eyeSpyOverlay =
            EyeSpyOverlayView(
                targets = dailyTargets
            ) { target, found, total ->
                updateEyeSpyProgress(target, found, total)
            }

        root.addView(
            eyeSpyOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val topBar =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
            }

        val back =
            Button(this).apply {
                text = "‹"
                textSize = 28f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                minWidth = 0
                minHeight = 0
                stateListAnimator = null
                background =
                    roundedBackground(
                        Color.argb(180, 4, 20, 35),
                        22,
                        Color.argb(135, 81, 195, 246)
                    )
                setOnClickListener { finish() }
                contentDescription = "Back to Atmosynq dashboard"
            }

        topBar.addView(
            back,
            LinearLayout.LayoutParams(
                dp(46),
                dp(46)
            ).apply {
                marginEnd = dp(10)
            }
        )

        val titleStack =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        titleStack.addView(
            TextView(this).apply {
                text = "ATMOSYNQ WORLDS"
                textSize = 9f
                letterSpacing = 0.14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(75, 218, 255))
            }
        )

        titleStack.addView(
            TextView(this).apply {
                text = experience.title
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        )

        topBar.addView(
            titleStack,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val savedProgress =
            progressStore.progressFor(
                worldDate,
                experience.kind
            )

        eyeSpyButton =
            Button(this).apply {
                text =
                    if (savedProgress.completedToday) {
                        "REPLAY"
                    } else {
                        "DAILY EYE SPY"
                    }
                textSize = 10.5f
                letterSpacing = 0.06f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                minWidth = 0
                minHeight = 0
                stateListAnimator = null
                background =
                    roundedBackground(
                        Color.argb(190, 8, 42, 66),
                        18,
                        Color.argb(180, 82, 211, 255)
                    )
                setOnClickListener {
                    if (eyeSpyActive) {
                        stopEyeSpy()
                    } else {
                        startEyeSpy()
                    }
                }
            }

        topBar.addView(
            eyeSpyButton,
            LinearLayout.LayoutParams(
                dp(118),
                dp(42)
            ).apply {
                marginStart = dp(8)
            }
        )

        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val bottomPanel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background =
                    gradientBackground(
                        intArrayOf(
                            Color.argb(228, 7, 27, 47),
                            Color.argb(236, 3, 14, 27)
                        ),
                        26,
                        Color.argb(170, 62, 151, 201)
                    )
            }

        bottomPanel.addView(
            TextView(this).apply {
                text = experience.subtitle
                textSize = 14f
                setTextColor(Color.rgb(224, 239, 248))
            }
        )

        bottomPanel.addView(
            TextView(this).apply {
                text =
                    "WORLD DNA  //  ${experience.signatureMoment.uppercase()}"
                textSize = 9.5f
                letterSpacing = 0.07f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(108, 207, 255))
                setPadding(0, dp(8), 0, 0)
            }
        )

        eyeSpyStatus =
            TextView(this).apply {
                text =
                    if (savedProgress.completedToday) {
                        "✓ Daily discovery complete • ${savedProgress.streakDays}-day world streak. Replay anytime."
                    } else {
                        "TODAY'S DISCOVERY  //  Drag, zoom, then find three hidden details in this live world."
                    }
                textSize = 12f
                setTextColor(Color.rgb(171, 198, 218))
                setPadding(0, dp(10), 0, 0)
            }

        bottomPanel.addView(eyeSpyStatus)

        root.addView(
            bottomPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(12)
            }
        )

        root.setOnApplyWindowInsetsListener { _, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars =
                    insets.getInsets(
                        WindowInsets.Type.systemBars()
                    )
                topBar.setPadding(
                    dp(14),
                    bars.top + dp(8),
                    dp(14),
                    dp(8)
                )
                val params =
                    bottomPanel.layoutParams as FrameLayout.LayoutParams
                params.bottomMargin = bars.bottom + dp(12)
                bottomPanel.layoutParams = params
            }
            insets
        }

        setContentView(root)

        if (
            intent.getBooleanExtra(
                EXTRA_START_EYE_SPY,
                false
            )
        ) {
            root.post { startEyeSpy() }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (eyeSpyActive) {
            stopEyeSpy()
            return
        }
        super.onBackPressed()
    }

    private fun startEyeSpy() {
        eyeSpyActive = true
        eyeSpyOverlay.startGame()
        eyeSpyButton.text = "STOP"
        eyeSpyButton.background =
            roundedBackground(
                Color.argb(205, 79, 37, 116),
                18,
                Color.argb(210, 181, 94, 255)
            )
        showCurrentEyeSpyHint(found = 0)
    }

    private fun stopEyeSpy() {
        eyeSpyActive = false
        eyeSpyOverlay.stopGame()
        val progress =
            progressStore.progressFor(
                worldDate,
                experience.kind
            )
        eyeSpyButton.text =
            if (progress.completedToday) {
                "REPLAY"
            } else {
                "DAILY EYE SPY"
            }
        eyeSpyButton.background =
            roundedBackground(
                Color.argb(190, 8, 42, 66),
                18,
                Color.argb(180, 82, 211, 255)
            )
        eyeSpyStatus.text =
            if (progress.completedToday) {
                "✓ Daily discovery complete • ${progress.streakDays}-day world streak. Replay anytime."
            } else {
                "TODAY'S DISCOVERY  //  Drag, zoom, then find three hidden details in this live world."
            }
    }

    private fun updateEyeSpyProgress(
        target: EyeSpyTarget,
        found: Int,
        total: Int
    ) {
        if (found >= total) {
            val progress =
                progressStore.markCompleted(
                    worldDate,
                    experience.kind
                )
            eyeSpyStatus.text =
                "✓ Daily world complete • ${progress.streakDays}-day streak. ${target.label.replaceFirstChar { it.uppercase() }} was the last find."
            eyeSpyButton.text = "REPLAY"
            eyeSpyActive = false
            return
        }

        showCurrentEyeSpyHint(found)
    }

    private fun showCurrentEyeSpyHint(found: Int) {
        val target =
            dailyTargets
                .getOrNull(found)
                ?: return

        eyeSpyStatus.text =
            "TODAY'S EYE SPY  $found/${dailyTargets.size}  •  Find ${target.label}. ${target.hint}"
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
            ).toInt()

    private inner class EyeSpyOverlayView(
        private val targets: List<EyeSpyTarget>,
        private val onFound: (
            target: EyeSpyTarget,
            found: Int,
            total: Int
        ) -> Unit
    ) : View(this@WorldExploreActivity) {
        private val markerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
            }
        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dp(15).toFloat()
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

        private var active = false
        private var foundCount = 0
        private var pulseX = -1f
        private var pulseY = -1f
        private var missPulse = false

        init {
            isClickable = false
            contentDescription = "Eye Spy scene overlay"
        }

        fun startGame() {
            active = true
            foundCount = 0
            isClickable = true
            pulseX = -1f
            pulseY = -1f
            invalidate()
        }

        fun stopGame() {
            active = false
            foundCount = 0
            isClickable = false
            pulseX = -1f
            pulseY = -1f
            invalidate()
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!active) return false

            if (event.actionMasked != MotionEvent.ACTION_UP) {
                return true
            }

            performClick()

            val target =
                targets.getOrNull(foundCount)
                    ?: return true
            val tx = target.xFraction * width
            val ty = target.yFraction * height
            val hitRadius = dp(58).toFloat()
            val hit =
                hypot(
                    event.x - tx,
                    event.y - ty
                ) <= hitRadius

            pulseX = event.x
            pulseY = event.y
            missPulse = !hit

            if (hit) {
                foundCount += 1
                onFound(
                    target,
                    foundCount,
                    targets.size
                )
                if (foundCount >= targets.size) {
                    active = false
                    isClickable = false
                }
            }

            invalidate()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (pulseX < 0f || pulseY < 0f) return

            markerPaint.color =
                if (missPulse) {
                    Color.argb(185, 255, 121, 121)
                } else {
                    Color.argb(225, 89, 235, 255)
                }
            canvas.drawCircle(
                pulseX,
                pulseY,
                dp(20).toFloat(),
                markerPaint
            )

            if (!missPulse) {
                canvas.drawText(
                    "✓",
                    pulseX,
                    pulseY + dp(5),
                    textPaint
                )
            }
        }
    }

    companion object {
        const val EXTRA_WEATHER_JSON =
            "com.uglygameface.atmosynq.extra.WEATHER_JSON"
        const val EXTRA_START_EYE_SPY =
            "com.uglygameface.atmosynq.extra.START_EYE_SPY"
    }
}
