package com.uglygameface.atmosynq.render

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.google.android.filament.ColorGrading
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import com.google.android.filament.View as FilamentView
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import com.uglygameface.atmosynq.weather.WeatherSnapshot
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPU weather hero.
 *
 * Filament owns the real 3D/PBR scene. Loading / graphics-failure states deliberately use
 * a neutral atmospheric surface instead of the retired Canvas scene, so obsolete geometric
 * scenery can never leak through while Filament initializes or recovers.
 */
class FilamentWeatherHeroView(context: Context) : FrameLayout(context) {
    private val loadingSurface =
        View(context).apply {
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.rgb(5, 20, 38),
                        Color.rgb(8, 37, 61),
                        Color.rgb(10, 25, 48),
                        Color.rgb(2, 9, 19)
                    )
                )
            isClickable = false
            contentDescription = "Atmosynq scene loading"
        }
    private val filamentSurface = TextureView(context)
    private val cinematicBackdrop = CinematicBackdropView(context)
    private val fxOverlay = WeatherFxOverlayView(context)

    private var viewer: ModelViewer? = null
    private var skybox: Skybox? = null
    private var indirectLight: IndirectLight? = null
    private var colorGrading: ColorGrading? = null
    private var snapshot: WeatherSnapshot? = null
    private var sceneProfile: SceneProfile = SceneProfile.DEFAULT
    private var locationResolved = false

    private var animated = true
    private var attached = false
    private var framePosted = false
    private var renderOnce = true
    private var filamentReady = false
    private var cinematicActive = false
    private var explorationMode = false
    private var sceneTapListener: ((Float, Float) -> Unit)? = null
    private var startedAtNanos = 0L

    private var cameraYaw = 0f
    private var cameraPitch = 0f
    private var cameraDistance = BASE_CAMERA_DISTANCE
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var sceneDragging = false
    private var scaleOccurred = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val baseTransforms = mutableMapOf<String, FloatArray>()

    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    scaleOccurred = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val factor = detector.scaleFactor
                    if (!factor.isFinite() || factor <= 0f) return false

                    cameraDistance =
                        (cameraDistance / factor)
                            .coerceIn(MIN_CAMERA_DISTANCE, MAX_CAMERA_DISTANCE)
                    updateInteractionParallax()
                    requestRender()
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        )

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        framePosted = false

        if (startedAtNanos == 0L) startedAtNanos = frameTimeNanos
        val seconds = (frameTimeNanos - startedAtNanos) / 1_000_000_000.0f

        cinematicBackdrop.setElapsedSeconds(seconds)

        val v = viewer
        if (v != null && !cinematicActive) {
            if (animated) {
                animateScene(v, seconds)
            }
            applyCameraPose(v, seconds)
            applyWeatherLighting(v)

            val rendered =
                runCatching {
                    v.render(frameTimeNanos)
                }.getOrDefault(false)

            if (rendered && !filamentReady) {
                filamentReady = true
                updateSceneMode()
            }
        }

        renderOnce = false
        if (attached && (animated || renderOnce)) {
            scheduleFrame()
        }
    }

    init {
        setBackgroundColor(Color.rgb(3, 12, 28))

        addView(
            loadingSurface,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )

        filamentSurface.alpha = 0f
        filamentSurface.isOpaque = true
        filamentSurface.isClickable = false
        addView(
            filamentSurface,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )

        cinematicBackdrop.alpha = 0f
        cinematicBackdrop.visibility = View.INVISIBLE
        cinematicBackdrop.isClickable = false
        addView(
            cinematicBackdrop,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )

        fxOverlay.isClickable = false
        addView(
            fxOverlay,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )

        isClickable = true
        isFocusable = true
        cinematicBackdrop.setAnimated(animated)
        fxOverlay.setAnimated(animated)
    }

    fun setSceneProfile(profile: SceneProfile) {
        sceneProfile = profile
        locationResolved = true
        cinematicBackdrop.setSceneProfile(profile)
        fxOverlay.setSceneProfile(profile)
        viewer?.let {
            applySceneProfile(it)
            applyWeatherSceneState(it)
        }
        updateSceneMode()
        requestRender()
    }

    fun setWeather(snapshot: WeatherSnapshot?) {
        this.snapshot = snapshot
        cinematicBackdrop.setWeather(snapshot)
        fxOverlay.setWeather(snapshot)
        viewer?.let {
            applyWeatherSceneState(it)
            applyWeatherLighting(it)
            applyCameraPose(it, 0f)
        }
        updateSceneMode()
        requestRender()
    }

    fun setAnimated(animated: Boolean) {
        this.animated = animated

        cinematicBackdrop.setAnimated(animated)
        fxOverlay.setAnimated(animated)

        if (animated) {
            startedAtNanos = 0L
            scheduleFrame()
        } else {
            renderOnce = true
            scheduleFrame()
        }
    }

    fun setCinematicAsset(assetPath: String?) {
        cinematicBackdrop.setSceneAsset(assetPath)
        updateSceneMode()
        requestRender()
    }

    fun setExplorationMode(enabled: Boolean) {
        explorationMode = enabled
        cinematicBackdrop.setExplorationMode(enabled)
        if (!enabled) {
            cinematicBackdrop.resetExplorationFocus()
        }
        updateInteractionParallax()
        requestRender()
    }

    fun setOnSceneTapListener(
        listener: ((u: Float, v: Float) -> Unit)?
    ) {
        sceneTapListener = listener
    }

    fun focusOnSceneRegion(
        centerU: Float,
        centerV: Float,
        zoom: Float = 1.34f
    ) {
        if (!cinematicActive) return
        cinematicBackdrop.focusOnSourceRegion(
            centerU = centerU,
            centerV = centerV,
            zoom = zoom
        )
    }

    fun resetSceneFocus() {
        cinematicBackdrop.resetExplorationFocus()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        ensureFilament()
        updateInteractionParallax()
        scheduleFrame()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTouchX = event.x
                downTouchY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                sceneDragging = false
                scaleOccurred = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                val totalDx = event.x - downTouchX
                val totalDy = event.y - downTouchY

                if (!sceneDragging &&
                    abs(totalDx) > touchSlop &&
                    abs(totalDx) > abs(totalDy) * 1.12f
                ) {
                    sceneDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                if (sceneDragging) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    val safeWidth = width.coerceAtLeast(1).toFloat()
                    val safeHeight = height.coerceAtLeast(1).toFloat()

                    cameraYaw =
                        (cameraYaw - dx / safeWidth * 0.78f)
                            .coerceIn(-MAX_CAMERA_YAW, MAX_CAMERA_YAW)
                    cameraPitch =
                        (cameraPitch + dy / safeHeight * 0.46f)
                            .coerceIn(-MAX_CAMERA_PITCH, MAX_CAMERA_PITCH)

                    updateInteractionParallax()
                    requestRender()
                }

                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                val moved =
                    abs(event.x - downTouchX) + abs(event.y - downTouchY)
                if (!sceneDragging &&
                    !scaleOccurred &&
                    !scaleDetector.isInProgress &&
                    moved < touchSlop * 2f
                ) {
                    performClick()
                    fxOverlay.addTouchPulse(event.x, event.y)

                    if (cinematicActive) {
                        cinematicBackdrop
                            .mapViewPointToSourceUv(
                                event.x,
                                event.y
                            )
                            ?.let { (u, v) ->
                                sceneTapListener?.invoke(u, v)
                            }
                    }

                    requestRender()
                }

                sceneDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                sceneDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return true
    }

    override fun onDetachedFromWindow() {
        attached = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        framePosted = false

        // ColorGrading is ours, unlike the model resources owned by ModelViewer.
        val activeViewer = viewer
        colorGrading?.let { grading ->
            runCatching {
                activeViewer?.engine?.destroyColorGrading(grading)
            }
        }
        colorGrading = null

        // ModelViewer owns a detach listener on its TextureView and destroys its
        // Filament resources there. Clear our references only, otherwise we risk
        // destroying the same native objects twice.
        viewer = null
        skybox = null
        indirectLight = null
        baseTransforms.clear()
        filamentReady = false

        super.onDetachedFromWindow()
    }

    private fun ensureFilament() {
        if (viewer != null) return

        val created = runCatching {
            ensureNativeLibraries()

            val v = ModelViewer(
                textureView = filamentSurface,
                manipulator = null
            )

            configureQuality(v)
            createEnvironment(v)
            loadAtmosynqScene(v)
            configureCamera(v)
            captureTrackedTransforms(v)
            applySceneProfile(v)
            applyWeatherSceneState(v)
            applyWeatherLighting(v)

            v
        }.getOrElse {
            filamentSurface.visibility = View.INVISIBLE
            null
        }

        viewer = created
        if (created != null) {
            filamentSurface.visibility = View.VISIBLE
            renderOnce = true
        }
        updateSceneMode()
    }

    private fun configureQuality(v: ModelViewer) {
        val view = v.view

        // Cinematic mobile preset. Dynamic resolution is deliberately kept enabled so
        // these expensive effects can stay on without turning the phone into cookware.
        view.setPostProcessingEnabled(true)
        view.setShadowingEnabled(true)
        view.setScreenSpaceRefractionEnabled(true)
        view.setShadowType(FilamentView.ShadowType.PCSS)
        view.dithering = FilamentView.Dithering.TEMPORAL

        view.renderQuality = view.renderQuality.apply {
            hdrColorBuffer = FilamentView.QualityLevel.HIGH
        }

        view.dynamicResolutionOptions =
            view.dynamicResolutionOptions.apply {
                enabled = true
                homogeneousScaling = true
                minScale = 0.68f
                maxScale = 1.0f
                sharpness = 0.94f
                quality = FilamentView.QualityLevel.HIGH
            }

        view.temporalAntiAliasingOptions =
            view.temporalAntiAliasingOptions.apply {
                enabled = true
                feedback = 0.12f
                filterWidth = 1.0f
                sharpness = 0.18f
                hdr = true
                preventFlickering = true
                historyReprojection = true
            }

        view.multiSampleAntiAliasingOptions =
            view.multiSampleAntiAliasingOptions.apply {
                enabled = true
                sampleCount = 4
            }

        // TAA owns post AA; leaving FXAA on as well softens the detailed scene.
        view.antiAliasing = FilamentView.AntiAliasing.NONE

        view.ambientOcclusionOptions =
            view.ambientOcclusionOptions.apply {
                enabled = true
                radius = 0.42f
                power = 1.05f
                resolution = 1.0f
                intensity = 1.18f
                quality = FilamentView.QualityLevel.ULTRA
                bentNormals = true
            }

        view.bloomOptions =
            view.bloomOptions.apply {
                // Device screenshots showed emissive geometry reading as floating
                // light orbs. Keep the post stack available, but do not bloom
                // environment meshes until the scene earns it.
                enabled = false
                strength = 0.0f
                lensFlare = false
                starburst = false
                chromaticAberration = 0.0f
                ghostCount = 0
            }

        view.screenSpaceReflectionsOptions =
            view.screenSpaceReflectionsOptions.apply {
                enabled = true
                thickness = 0.12f
                bias = 0.01f
                maxDistance = 12.0f
                stride = 1.0f
            }

        view.guardBandOptions =
            view.guardBandOptions.apply {
                enabled = true
            }

        view.vignetteOptions =
            view.vignetteOptions.apply {
                enabled = true
                midPoint = 0.72f
                roundness = 0.82f
                feather = 0.58f
            }
    }

    private fun createEnvironment(v: ModelViewer) {
        val engine = v.engine

        skybox = Skybox.Builder()
            .color(0.012f, 0.045f, 0.11f, 1.0f)
            .showSun(false)
            .intensity(24_000f)
            .build(engine)
            .also { v.scene.skybox = it }

        indirectLight = IndirectLight.Builder()
            .intensity(22_000f)
            .irradiance(
                1,
                floatArrayOf(
                    0.40f,
                    0.48f,
                    0.62f
                )
            )
            .build(engine)
            .also { v.scene.indirectLight = it }

        colorGrading =
            ColorGrading.Builder()
                .quality(ColorGrading.QualityLevel.ULTRA)
                .exposure(-0.34f)
                .whiteBalance(-0.035f, 0.0f)
                .contrast(1.07f)
                .vibrance(0.90f)
                .saturation(0.88f)
                .luminanceScaling(true)
                .gamutMapping(true)
                .build(engine)
                .also { v.view.colorGrading = it }
    }

    private fun loadAtmosynqScene(v: ModelViewer) {
        val bytes = context.assets
            .open(SCENE_ASSET)
            .use { it.readBytes() }

        require(bytes.size >= 20) { "Atmosynq Filament scene is too small" }
        require(
            bytes[0] == 'g'.code.toByte() &&
                bytes[1] == 'l'.code.toByte() &&
                bytes[2] == 'T'.code.toByte() &&
                bytes[3] == 'F'.code.toByte()
        ) { "Atmosynq Filament scene is not a GLB" }

        v.loadModelGlb(ByteBuffer.wrap(bytes))
    }

    private fun configureCamera(v: ModelViewer) {
        v.cameraFocalLength = 36f
        v.cameraNear = 0.08f
        v.cameraFar = 140f
        v.camera.setExposure(
            6.3f,
            1.0f / 100.0f,
            80.0f
        )
        applyCameraPose(v, 0f)
    }

    private fun applyCameraPose(v: ModelViewer, seconds: Float) {
        val idleYaw =
            if (animated) sin(seconds * 0.15f) * 0.018f else 0f
        val idleLift =
            if (animated) sin(seconds * 0.21f) * 0.045f else 0f

        val yaw = (cameraYaw + idleYaw).toDouble()
        val targetX = 0.0
        val targetY = (1.34f + cameraPitch * 1.02f).toDouble()
        val targetZ = -4.0
        val distance = cameraDistance.toDouble()

        val eyeX = sin(yaw) * distance * 0.78
        val eyeY = (3.45f + cameraPitch * 3.8f + idleLift).toDouble()
        val eyeZ = targetZ + cos(yaw) * distance

        v.camera.lookAt(
            eyeX,
            eyeY,
            eyeZ,
            targetX,
            targetY,
            targetZ,
            0.0,
            1.0,
            0.0
        )
    }

    private fun updateInteractionParallax() {
        val x = cameraYaw / MAX_CAMERA_YAW
        val y = cameraPitch / MAX_CAMERA_PITCH

        cinematicBackdrop.setParallax(x, y)
        if (explorationMode) {
            val zoom =
                (
                    BASE_CAMERA_DISTANCE /
                        cameraDistance
                    ).coerceIn(1f, 1.75f)
            cinematicBackdrop.setInteractiveZoom(zoom)
        }
        fxOverlay.setParallax(x, y)
    }

    private fun updateSceneMode() {
        val shouldUseCinematic =
            locationResolved &&
                snapshot != null &&
                cinematicBackdrop.canRender(sceneProfile)

        cinematicActive = shouldUseCinematic

        if (shouldUseCinematic) {
            loadingSurface.visibility = View.INVISIBLE
            filamentSurface.alpha = 0f
            filamentSurface.visibility = View.INVISIBLE
            cinematicBackdrop.visibility = View.VISIBLE
            cinematicBackdrop.alpha = 1f
        } else {
            cinematicBackdrop.alpha = 0f
            cinematicBackdrop.visibility = View.INVISIBLE

            if (filamentReady) {
                loadingSurface.visibility = View.INVISIBLE
                filamentSurface.visibility = View.VISIBLE
                filamentSurface.alpha = 1f
            } else {
                filamentSurface.alpha = 0f
                filamentSurface.visibility = View.INVISIBLE
                loadingSurface.visibility = View.VISIBLE
            }
        }
    }

    private fun captureTrackedTransforms(v: ModelViewer) {
        val asset = v.asset ?: return
        val tm = v.engine.transformManager

        TRACKED_ENTITIES.forEach { name ->
            val entity = asset.getFirstEntityByName(name)
            if (entity == 0) return@forEach

            val instance = tm.getInstance(entity)
            if (instance == 0) return@forEach

            baseTransforms[name] = tm.getTransform(instance, FloatArray(16)).copyOf()
        }
    }

    private fun applySceneProfile(v: ModelViewer) {
        val asset = v.asset ?: return
        val tm = v.engine.transformManager

        val mountainScale =
            if (locationResolved) {
                sceneProfile.mountainScale
            } else {
                0.0f
            }
        val rollingScale =
            when (sceneProfile.terrain) {
                TerrainKind.FLAT -> 0.0f
                TerrainKind.ROLLING -> 1.0f
                TerrainKind.HIGHLAND -> 0.72f
                TerrainKind.MOUNTAIN -> 0.45f
            }

        val cityScale =
            if (!locationResolved) {
                0.0f
            } else when (sceneProfile.settlement) {
                SettlementKind.METRO -> 1.0f
                SettlementKind.CITY -> 0.82f
                SettlementKind.TOWN -> 0.14f
                SettlementKind.LOCAL -> 0.0f
            }
        val houseScale =
            if (!locationResolved) {
                0.0f
            } else when (sceneProfile.settlement) {
                SettlementKind.METRO -> 0.10f
                SettlementKind.CITY -> 0.14f
                SettlementKind.TOWN -> 0.0f
                SettlementKind.LOCAL -> 0.0f
            }
        // The v0.5.1 device build exposed the lamp heads as floating lights.
        // Remove this layer completely until the fixtures are modeled and lit
        // with real light sources instead of emissive balls.
        val streetScale = 0.0f

        var pineScale = 0.0f
        var broadleafScale = 0.0f
        var palmScale = 0.0f
        if (locationResolved) {
            when (sceneProfile.latitudeBand) {
            LatitudeBand.TROPICAL -> {
                pineScale = 0.0f
                broadleafScale = 0.58f
                palmScale = 1.0f
            }
            LatitudeBand.WARM -> {
                pineScale = 0.28f
                broadleafScale = 1.0f
                palmScale = 0.38f
            }
            LatitudeBand.TEMPERATE -> {
                pineScale = 0.88f
                broadleafScale = 1.0f
                palmScale = 0.0f
            }
            LatitudeBand.COOL -> {
                pineScale = 1.0f
                broadleafScale = 0.52f
                palmScale = 0.0f
            }
            LatitudeBand.POLAR -> {
                pineScale = 0.46f
                broadleafScale = 0.0f
                palmScale = 0.0f
            }
        }
        }

        tm.openLocalTransformTransaction()
        try {
            applyEntityGroup(
                asset,
                tm,
                BASE_ENVIRONMENT,
                if (locationResolved) 1.0f else 0.0f,
                14.0f
            )
            applyEntityGroup(asset, tm, MOUNTAINS, mountainScale, 7.0f)
            applyEntityGroup(
                asset,
                tm,
                ROLLING_HILLS,
                if (locationResolved) rollingScale else 0.0f,
                7.0f
            )
            applyEntityGroup(asset, tm, CITY_PARTS, cityScale, 8.0f)
            applyEntityGroup(asset, tm, HOUSE_PARTS, houseScale, 6.0f)
            applyEntityGroup(asset, tm, STREET_PARTS, streetScale, 5.0f)
            applyEntityGroup(asset, tm, PINE_PARTS, pineScale, 7.0f)
            applyEntityGroup(asset, tm, BROADLEAF_PARTS, broadleafScale, 7.0f)
            applyEntityGroup(asset, tm, PALM_PARTS, palmScale, 7.0f)
            applyEntityGroup(asset, tm, RIBBONS, 0.0f, 12.0f)
        } finally {
            tm.commitLocalTransformTransaction()
        }
    }

    private fun applyEntityGroup(
        asset: com.google.android.filament.gltfio.FilamentAsset,
        tm: com.google.android.filament.TransformManager,
        names: List<String>,
        scale: Float,
        hiddenDrop: Float
    ) {
        val safeScale = scale.coerceIn(0f, 1f)
        names.forEach { name ->
            val base = baseTransforms[name] ?: return@forEach
            val entity = asset.getFirstEntityByName(name)
            if (entity == 0) return@forEach
            val instance = tm.getInstance(entity)
            if (instance == 0) return@forEach

            val matrix = base.copyOf()
            val visibleScale =
                if (safeScale < 0.02f) 0.001f else safeScale

            intArrayOf(
                0, 1, 2,
                4, 5, 6,
                8, 9, 10
            ).forEach { index ->
                matrix[index] = base[index] * visibleScale
            }

            if (safeScale < 0.02f) {
                matrix[13] = base[13] - hiddenDrop
            }
            tm.setTransform(instance, matrix)
        }
    }

    private fun applyWeatherSceneState(v: ModelViewer) {
        val asset = v.asset ?: return
        val tm = v.engine.transformManager
        val weather = snapshot

        val sunScale =
            if (
                weather != null &&
                weather.isDay &&
                weather.cloudCoverPct < 68.0
            ) {
                0.72f
            } else {
                0.0f
            }

        tm.openLocalTransformTransaction()
        try {
            applyEntityGroup(
                asset,
                tm,
                listOf("Sun"),
                sunScale,
                12.0f
            )
            applyEntityGroup(
                asset,
                tm,
                RIBBONS,
                0.0f,
                12.0f
            )
        } finally {
            tm.commitLocalTransformTransaction()
        }
    }

    private fun cloudVisualScale(): Float {
        // The generated cloud meshes still read as obvious geometry on the Samsung.
        // Keep them in the asset for future volumetric material work, but hide them
        // now. WeatherFxOverlayView owns the soft cloud deck instead.
        return 0.0f
    }

    private fun animateScene(v: ModelViewer, seconds: Float) {
        val asset = v.asset ?: return
        val tm = v.engine.transformManager
        val weather = snapshot

        val wind = ((weather?.windSpeedKmh ?: 8.0) / 35.0)
            .coerceIn(0.12, 1.8)
            .toFloat()
        val cloudScale = cloudVisualScale()

        tm.openLocalTransformTransaction()
        try {
            CLOUDS.forEachIndexed { index, name ->
                val base = baseTransforms[name] ?: return@forEachIndexed
                val entity = asset.getFirstEntityByName(name)
                if (entity == 0) return@forEachIndexed

                val instance = tm.getInstance(entity)
                if (instance == 0) return@forEachIndexed

                val matrix = base.copyOf()
                val visibleScale =
                    if (cloudScale < 0.02f) {
                        0.001f
                    } else {
                        cloudScale
                    }

                intArrayOf(
                    0, 1, 2,
                    4, 5, 6,
                    8, 9, 10
                ).forEach { matrixIndex ->
                    matrix[matrixIndex] =
                        base[matrixIndex] * visibleScale
                }

                matrix[12] =
                    base[12] +
                    sin(
                        seconds *
                            (0.07f + index * 0.012f) *
                            wind +
                            index
                    ) *
                    (0.34f + index * 0.08f)
                matrix[13] =
                    if (cloudScale < 0.02f) {
                        base[13] - 12.0f
                    } else {
                        base[13] +
                            sin(
                                seconds * 0.11f +
                                    index * 1.7f
                            ) *
                            0.035f
                    }
                tm.setTransform(instance, matrix)
            }
        } finally {
            tm.commitLocalTransformTransaction()
        }
    }

    private fun applyWeatherLighting(v: ModelViewer) {
        val weather = snapshot
        val cloud = ((weather?.cloudCoverPct ?: 30.0) / 100.0)
            .coerceIn(0.0, 1.0)
            .toFloat()
        val isDay = weather?.isDay ?: true
        val thunder = weather?.weatherCode in setOf(95, 96, 97, 99)

        val skyR: Float
        val skyG: Float
        val skyB: Float

        if (isDay) {
            skyR = 0.025f + 0.055f * cloud
            skyG = 0.12f + 0.10f * (1f - cloud)
            skyB = 0.24f + 0.16f * (1f - cloud)
        } else {
            skyR = 0.006f + 0.015f * cloud
            skyG = 0.018f + 0.022f * cloud
            skyB = 0.055f + 0.035f * cloud
        }

        skybox?.setColor(skyR, skyG, skyB, 1f)

        val ambient = if (isDay) {
            27_000f * (1f - cloud * 0.46f)
        } else {
            7_200f * (1f - cloud * 0.22f)
        }
        indirectLight?.intensity = ambient.coerceAtLeast(3_800f)

        applyFilamentFog(v, cloud, isDay)

        val lightManager = v.engine.lightManager
        val lightInstance = lightManager.getInstance(v.light)
        if (lightInstance != 0) {
            val daylight = if (isDay) 1f else 0.16f
            val stormCut = if (thunder) 0.48f else 1f
            val intensity =
                (78_000f * daylight * stormCut * (1f - cloud * 0.52f))
                    .coerceAtLeast(if (isDay) 11_000f else 1_900f)

            lightManager.setIntensity(lightInstance, intensity)
            lightManager.setDirection(
                lightInstance,
                -0.42f,
                -0.78f,
                -0.46f
            )

            if (isDay) {
                lightManager.setColor(
                    lightInstance,
                    1.0f,
                    0.93f,
                    0.82f
                )
            } else {
                lightManager.setColor(
                    lightInstance,
                    0.42f,
                    0.56f,
                    0.95f
                )
            }
        }
    }

    private fun applyFilamentFog(
        v: ModelViewer,
        cloud: Float,
        isDay: Boolean
    ) {
        val weather = snapshot
        val visibility = weather?.visibilityM ?: 100_000.0
        val fogCode = weather?.weatherCode in setOf(45, 48)
        val visibilityFog =
            ((16_000.0 - visibility) / 16_000.0)
                .coerceIn(0.0, 1.0)
                .toFloat()
        val haze =
            maxOf(
                if (fogCode) 0.72f else 0f,
                visibilityFog,
                if (cloud > 0.88f) 0.12f else 0f
            )

        v.view.fogOptions =
            v.view.fogOptions.apply {
                enabled = haze > 0.035f
                distance = 4.0f + (1f - haze) * 8.0f
                cutOffDistance = 95.0f
                maximumOpacity = (0.30f + haze * 0.58f).coerceAtMost(0.90f)
                height = 0.0f
                heightFalloff = 0.22f
                density = 0.012f + haze * 0.075f
                inScatteringStart = 6.0f
                inScatteringSize = 24.0f
                fogColorFromIbl = false
                color[0] = if (isDay) 0.48f else 0.08f
                color[1] = if (isDay) 0.58f else 0.12f
                color[2] = if (isDay) 0.68f else 0.22f
            }
    }

    private fun requestRender() {
        renderOnce = true
        scheduleFrame()
    }

    private fun scheduleFrame() {
        if (!attached || framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    companion object {
        private const val SCENE_ASSET = "filament/atmos_scene.glb"
        private const val BASE_CAMERA_DISTANCE = 20.4f
        private const val MIN_CAMERA_DISTANCE = 17.0f
        private const val MAX_CAMERA_DISTANCE = 24.5f
        private const val MAX_CAMERA_YAW = 0.42f
        private const val MAX_CAMERA_PITCH = 0.22f

        private val CLOUDS =
            (0 until 5).map { "Cloud$it" }

        private val MOUNTAINS =
            (0 until 3).map { "Mountain$it" }

        private val ROLLING_HILLS =
            (0 until 3).map { "RollingHill$it" }

        private val CITY_PARTS =
            (0 until 22).flatMap { index ->
                listOf(
                    "CityBuilding$index",
                    "CityGlass$index",
                    "CityWindows$index"
                )
            }

        private val HOUSE_PARTS =
            (0 until 14).flatMap { index ->
                listOf(
                    "House$index",
                    "HouseRoof$index",
                    "HouseWindows$index",
                    "HouseGarage$index",
                    "HouseDoor$index",
                    "HouseChimney$index"
                )
            }

        private val PINE_PARTS =
            (0 until 22).flatMap { index ->
                listOf("Pine$index", "PineTrunk$index")
            }

        private val BROADLEAF_PARTS =
            (0 until 18).flatMap { index ->
                listOf("Broadleaf$index", "BroadleafTrunk$index")
            }

        private val PALM_PARTS =
            (0 until 10).flatMap { index ->
                listOf("Palm$index", "PalmTrunk$index")
            }

        private val STREET_PARTS =
            (0 until 7).flatMap { index ->
                listOf("StreetPole$index", "StreetLight$index")
            }

        private val BASE_ENVIRONMENT =
            listOf(
                "Ground",
                "WetRoad",
                "Water"
            ) + (0 until 7).map { index ->
                "RoadMark$index"
            }

        private val RIBBONS =
            listOf(
                "RibbonCyan",
                "RibbonPurple"
            )

        private val ANIMATED_ENTITIES =
            CLOUDS

        private val TRACKED_ENTITIES =
            (
                ANIMATED_ENTITIES +
                    RIBBONS +
                    listOf("Sun") +
                    MOUNTAINS +
                    ROLLING_HILLS +
                    CITY_PARTS +
                    HOUSE_PARTS +
                    PINE_PARTS +
                    BROADLEAF_PARTS +
                    PALM_PARTS +
                    STREET_PARTS +
                    BASE_ENVIRONMENT
                ).distinct()

        @Volatile
        private var nativeReady = false

        private fun ensureNativeLibraries() {
            if (nativeReady) return
            synchronized(this) {
                if (!nativeReady) {
                    Utils.init()
                    nativeReady = true
                }
            }
        }
    }
}
