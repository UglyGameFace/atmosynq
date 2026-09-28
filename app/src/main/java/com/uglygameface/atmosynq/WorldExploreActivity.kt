package com.uglygameface.atmosynq

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
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
import com.uglygameface.atmosynq.worlds.WorldHotspotAction
import com.uglygameface.atmosynq.worlds.WorldProgressStore
import com.uglygameface.atmosynq.worlds.WorldSceneLayer
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

class WorldExploreActivity : Activity() {
    private lateinit var sceneView: FilamentWeatherHeroView
    private lateinit var sceneFrame: FrameLayout
    private lateinit var worldTitle: TextView
    private lateinit var worldSubtitle: TextView
    private lateinit var eyeSpyButton: TextView
    private lateinit var interactionStatus: TextView

    private lateinit var experience: WorldExperience
    private var activeScene: WorldSceneLayer? = null
    private lateinit var dailyTargets: List<EyeSpyTarget>
    private lateinit var worldDate: LocalDate
    private val progressStore by lazy { WorldProgressStore(this) }

    private var eyeSpyActive = false
    private var eyeSpyIndex = 0

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
        activeScene = experience.primaryScene
        dailyTargets =
            rotateDailyTargets(
                experience.primaryScene?.hotspots.orEmpty()
            )

        val root =
            FrameLayout(this).apply {
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            Color.rgb(3, 16, 29),
                            Color.rgb(2, 10, 20),
                            Color.rgb(1, 7, 15)
                        )
                    )
            }

        sceneView =
            FilamentWeatherHeroView(this).apply {
                setCinematicAsset(activeScene?.assetPath)
                setSceneProfile(profile)
                setWeather(weather)
                setAnimated(true)
                setExplorationMode(true)
                contentDescription =
                    if (activeScene != null) {
                        "Interactive Atmosynq world. Drag to look around, pinch to zoom and tap visible landmarks."
                    } else {
                        "Live Atmosynq 3D weather scene."
                    }
                setOnSceneTapListener(
                    if (activeScene != null) {
                        { u, v ->
                            handleSceneTap(u, v)
                        }
                    } else {
                        null
                    }
                )
            }

        sceneFrame =
            FrameLayout(this).apply {
                clipToOutline = true
                elevation = dp(10).toFloat()
                background =
                    roundedBackground(
                        Color.rgb(5, 23, 40),
                        28,
                        Color.rgb(55, 151, 202)
                    )

                addView(
                    sceneView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                addView(
                    TextView(this@WorldExploreActivity).apply {
                        text = "TAP LANDMARKS  •  DRAG  •  PINCH"
                        textSize = 8.5f
                        letterSpacing = 0.08f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.rgb(190, 225, 243))
                        setPadding(
                            dp(10),
                            dp(7),
                            dp(10),
                            dp(7)
                        )
                        background =
                            roundedBackground(
                                Color.argb(190, 3, 17, 30),
                                12,
                                Color.argb(120, 72, 188, 237)
                            )
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.START
                    ).apply {
                        leftMargin = dp(10)
                        bottomMargin = dp(10)
                    }
                )
            }

        root.addView(
            sceneFrame,
            FrameLayout.LayoutParams(
                dp(240),
                dp(180),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )

        val topBar = buildTopBar()
        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val bottomPanel = buildBottomPanel()
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

        root.post {
            layoutSourceAspectViewport(
                root = root,
                topBar = topBar,
                bottomPanel = bottomPanel,
                sceneFrame = sceneFrame
            )
        }

        if (
            intent.getBooleanExtra(
                EXTRA_START_EYE_SPY,
                false
            )
        ) {
            root.post { startEyeSpy() }
        }
    }

    private fun buildTopBar(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))

            addView(
                TextView(this@WorldExploreActivity).apply {
                    text = "‹"
                    textSize = 31f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background =
                        roundedBackground(
                            Color.argb(190, 4, 20, 35),
                            23,
                            Color.argb(160, 81, 195, 246)
                        )
                    setOnClickListener { finish() }
                    contentDescription = "Back to Atmosynq dashboard"
                },
                LinearLayout.LayoutParams(
                    dp(48),
                    dp(48)
                ).apply {
                    marginEnd = dp(10)
                }
            )

            val titleStack =
                LinearLayout(this@WorldExploreActivity).apply {
                    orientation = LinearLayout.VERTICAL

                    addView(
                        TextView(this@WorldExploreActivity).apply {
                            text = "ATMOSYNQ WORLDS"
                            textSize = 9f
                            letterSpacing = 0.14f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(Color.rgb(75, 218, 255))
                        }
                    )

                    worldTitle =
                        TextView(this@WorldExploreActivity).apply {
                            text =
                                activeScene?.title
                                    ?: experience.title
                            textSize = 20f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        }
                    addView(worldTitle)
                }

            addView(
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
                TextView(this@WorldExploreActivity).apply {
                    text =
                        when {
                            activeScene == null ->
                                "LIVE 3D"
                            savedProgress.completedToday ->
                                "REPLAY"
                            else ->
                                "DAILY EYE SPY"
                        }
                    textSize = 10f
                    letterSpacing = 0.055f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background =
                        roundedBackground(
                            Color.argb(205, 8, 42, 66),
                            20,
                            Color.argb(200, 82, 211, 255)
                        )
                    isEnabled = activeScene != null
                    alpha = if (activeScene != null) 1f else 0.52f
                    setOnClickListener {
                        if (activeScene == null) {
                            return@setOnClickListener
                        }
                        if (eyeSpyActive) {
                            stopEyeSpy()
                        } else {
                            startEyeSpy()
                        }
                    }
                }

            addView(
                eyeSpyButton,
                LinearLayout.LayoutParams(
                    dp(120),
                    dp(44)
                ).apply {
                    marginStart = dp(8)
                }
            )
        }

    private fun buildBottomPanel(): LinearLayout {
        val savedProgress =
            progressStore.progressFor(
                worldDate,
                experience.kind
            )

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(15), dp(17), dp(15))
            background =
                gradientBackground(
                    intArrayOf(
                        Color.argb(238, 7, 27, 47),
                        Color.argb(244, 3, 14, 27)
                    ),
                    26,
                    Color.argb(185, 62, 151, 201)
                )

            worldSubtitle =
                TextView(this@WorldExploreActivity).apply {
                    text =
                        activeScene?.subtitle
                            ?: experience.subtitle
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(230, 242, 249))
                }
            addView(worldSubtitle)

            addView(
                TextView(this@WorldExploreActivity).apply {
                    text =
                        "WORLD DNA  //  ${experience.signatureMoment.uppercase()}"
                    textSize = 9.5f
                    letterSpacing = 0.07f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(108, 207, 255))
                    setPadding(0, dp(8), 0, 0)
                }
            )

            interactionStatus =
                TextView(this@WorldExploreActivity).apply {
                    text =
                        if (savedProgress.completedToday) {
                            "✓ Daily discovery complete • ${savedProgress.streakDays}-day world streak. Tap landmarks to explore or replay Eye Spy."
                        } else if (activeScene != null) {
                            "Tap a visible landmark to inspect it. Daily Eye Spy uses these same real scene regions."
                        } else {
                            "LIVE 3D WORLD  //  This scene is rendered live, but semantic landmark gameplay stays off until its dedicated World asset ships."
                        }
                    textSize = 12.5f
                    setTextColor(Color.rgb(178, 207, 225))
                    setLineSpacing(0f, 1.10f)
                    setPadding(0, dp(10), 0, 0)
                }

            addView(interactionStatus)
        }
    }

    private fun layoutSourceAspectViewport(
        root: FrameLayout,
        topBar: View,
        bottomPanel: View,
        sceneFrame: View
    ) {
        val horizontalMargin = dp(12)
        val maxWidth =
            (root.width - horizontalMargin * 2)
                .coerceAtLeast(dp(220))

        val sceneWidth =
            activeScene?.sourceWidth?.toFloat()
                ?: 16f
        val sceneHeight =
            activeScene?.sourceHeight?.toFloat()
                ?: 10f

        val desiredHeight =
            (maxWidth * sceneHeight /
                sceneWidth).roundToInt()

        val top =
            topBar.bottom + dp(10)
        val bottom =
            bottomPanel.top - dp(10)
        val availableHeight =
            (bottom - top)
                .coerceAtLeast(dp(150))

        val height =
            min(
                desiredHeight,
                availableHeight
            )
        val width =
            min(
                maxWidth,
                (
                    height *
                        sceneWidth /
                        sceneHeight
                    ).roundToInt()
            )

        val params =
            sceneFrame.layoutParams as FrameLayout.LayoutParams
        params.width = width
        params.height = height
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.topMargin =
            top +
                ((availableHeight - height) / 2)
                    .coerceAtLeast(0)
        sceneFrame.layoutParams = params
    }

    private fun rotateDailyTargets(
        targets: List<EyeSpyTarget>
    ): List<EyeSpyTarget> {
        if (targets.isEmpty()) return targets

        val offset =
            worldDate.dayOfYear % targets.size

        return targets.drop(offset) +
            targets.take(offset)
    }

    private fun handleSceneTap(
        u: Float,
        v: Float
    ) {
        val tappedTarget =
            activeScene
                ?.hotspots
                ?.firstOrNull { target ->
                    target.region.contains(u, v)
                }

        if (eyeSpyActive) {
            handleEyeSpyTap(tappedTarget)
            return
        }

        if (tappedTarget == null) {
            sceneView.resetSceneFocus()
            interactionStatus.text =
                "No landmark there. Tap a visible tree line, cloud bank, light, water feature or horizon detail."
            sceneView.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP
            )
            return
        }

        if (
            tappedTarget.action == WorldHotspotAction.ENTER &&
            activeScene?.id == experience.primaryScene?.id &&
            experience.interiorScene != null
        ) {
            enterInteriorScene()
            return
        }

        sceneView.focusOnSceneRegion(
            centerU = tappedTarget.region.centerX,
            centerV = tappedTarget.region.centerY,
            zoom = 1.30f
        )
        sceneView.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS
        )

        interactionStatus.text =
            "${tappedTarget.label.replaceFirstChar { it.uppercase() }}  //  ${tappedTarget.detail}"
    }

    private fun handleEyeSpyTap(
        tappedTarget: EyeSpyTarget?
    ) {
        val expected =
            dailyTargets.getOrNull(eyeSpyIndex)
                ?: return

        if (tappedTarget?.id != expected.id) {
            sceneView.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP
            )
            interactionStatus.text =
                "Not that one. ${expected.hint}"
            return
        }

        sceneView.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS
        )
        sceneView.focusOnSceneRegion(
            centerU = expected.region.centerX,
            centerV = expected.region.centerY,
            zoom = 1.34f
        )

        eyeSpyIndex += 1
        updateEyeSpyProgress(
            target = expected,
            found = eyeSpyIndex,
            total = dailyTargets.size
        )
    }

    private fun enterInteriorScene() {
        val interior =
            experience.interiorScene
                ?: return

        if (eyeSpyActive) {
            stopEyeSpy()
        }

        activeScene = interior
        sceneView.setCinematicAsset(interior.assetPath)
        sceneView.resetSceneFocus()
        worldTitle.text = interior.title
        worldSubtitle.text = interior.subtitle

        eyeSpyButton.text = "OUTSIDE"
        eyeSpyButton.isEnabled = true
        eyeSpyButton.alpha = 1f
        eyeSpyButton.setOnClickListener {
            returnToPrimaryScene()
        }

        interactionStatus.text =
            "INSIDE THE CABIN  //  Tap the fireplace, window or hearth. Press back or OUTSIDE to return."

        relayoutSceneViewport()
    }

    private fun returnToPrimaryScene() {
        val primary =
            experience.primaryScene
                ?: return

        activeScene = primary
        sceneView.setCinematicAsset(primary.assetPath)
        sceneView.resetSceneFocus()
        worldTitle.text = primary.title
        worldSubtitle.text = primary.subtitle
        dailyTargets =
            rotateDailyTargets(primary.hotspots)

        configurePrimarySceneAction()
        interactionStatus.text =
            "Back outside. Tap a visible landmark to inspect it or start Daily Eye Spy."

        relayoutSceneViewport()
    }

    private fun configurePrimarySceneAction() {
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
        eyeSpyButton.isEnabled = true
        eyeSpyButton.alpha = 1f
        eyeSpyButton.setOnClickListener {
            if (eyeSpyActive) {
                stopEyeSpy()
            } else {
                startEyeSpy()
            }
        }
    }

    private fun relayoutSceneViewport() {
        val root =
            sceneFrame.parent as? FrameLayout
                ?: return

        val topBar =
            root.getChildAt(1)
                ?: return
        val bottomPanel =
            root.getChildAt(2)
                ?: return

        root.post {
            layoutSourceAspectViewport(
                root = root,
                topBar = topBar,
                bottomPanel = bottomPanel,
                sceneFrame = sceneFrame
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (eyeSpyActive) {
            stopEyeSpy()
            return
        }

        if (
            experience.interiorScene != null &&
            activeScene?.id == experience.interiorScene?.id
        ) {
            returnToPrimaryScene()
            return
        }

        super.onBackPressed()
    }

    private fun startEyeSpy() {
        eyeSpyActive = true
        eyeSpyIndex = 0
        sceneView.resetSceneFocus()
        eyeSpyButton.text = "STOP"
        eyeSpyButton.background =
            roundedBackground(
                Color.argb(215, 79, 37, 116),
                20,
                Color.argb(220, 181, 94, 255)
            )
        showCurrentEyeSpyHint()
    }

    private fun stopEyeSpy() {
        eyeSpyActive = false
        eyeSpyIndex = 0
        sceneView.resetSceneFocus()

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
                Color.argb(205, 8, 42, 66),
                20,
                Color.argb(200, 82, 211, 255)
            )

        interactionStatus.text =
            if (progress.completedToday) {
                "✓ Daily discovery complete • ${progress.streakDays}-day world streak. Tap landmarks to keep exploring."
            } else {
                "Tap a visible landmark to inspect it. Daily Eye Spy uses these same real scene regions."
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
            interactionStatus.text =
                "✓ Daily world complete • ${progress.streakDays}-day streak. ${target.label.replaceFirstChar { it.uppercase() }} was the final discovery."
            eyeSpyButton.text = "REPLAY"
            eyeSpyActive = false
            return
        }

        interactionStatus.text =
            "FOUND  $found/$total  //  ${target.label.replaceFirstChar { it.uppercase() }}. Next: ${dailyTargets[found].hint}"
    }

    private fun showCurrentEyeSpyHint() {
        val target =
            dailyTargets.getOrNull(eyeSpyIndex)
                ?: return

        interactionStatus.text =
            "TODAY'S EYE SPY  $eyeSpyIndex/${dailyTargets.size}  //  Find ${target.label}. ${target.hint}"
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
        const val EXTRA_WEATHER_JSON =
            "com.uglygameface.atmosynq.extra.WEATHER_JSON"
        const val EXTRA_START_EYE_SPY =
            "com.uglygameface.atmosynq.extra.START_EYE_SPY"
    }
}
