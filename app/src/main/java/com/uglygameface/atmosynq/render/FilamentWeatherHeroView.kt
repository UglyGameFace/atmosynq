package com.uglygameface.atmosynq.render

import android.content.Context
import android.graphics.Color
import android.view.Choreographer
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import com.google.android.filament.View as FilamentView
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import com.uglygameface.atmosynq.weather.WeatherSnapshot
import java.nio.ByteBuffer
import java.util.Base64
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPU weather hero.
 *
 * Filament owns the real 3D/PBR scene. WeatherHeroView remains underneath it as a safety
 * fallback so an unsupported/broken graphics driver never leaves the dashboard blank.
 */
class FilamentWeatherHeroView(context: Context) : FrameLayout(context) {
    private val fallback = WeatherHeroView(context)
    private val filamentSurface = TextureView(context)

    private var viewer: ModelViewer? = null
    private var skybox: Skybox? = null
    private var indirectLight: IndirectLight? = null
    private var snapshot: WeatherSnapshot? = null

    private var animated = true
    private var attached = false
    private var framePosted = false
    private var renderOnce = true
    private var filamentReady = false
    private var startedAtNanos = 0L

    private val baseTransforms = mutableMapOf<String, FloatArray>()

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        framePosted = false
        val v = viewer ?: return@FrameCallback

        if (startedAtNanos == 0L) startedAtNanos = frameTimeNanos
        val seconds = (frameTimeNanos - startedAtNanos) / 1_000_000_000.0f

        if (animated) {
            animateScene(v, seconds)
        }
        applyWeatherLighting(v)

        val rendered = runCatching { v.render(frameTimeNanos) }.getOrDefault(false)
        if (rendered && !filamentReady) {
            filamentReady = true
            filamentSurface.alpha = 1f
            fallback.visibility = View.INVISIBLE
        }

        renderOnce = false
        if (attached && (animated || renderOnce)) {
            scheduleFrame()
        }
    }

    init {
        setBackgroundColor(Color.rgb(3, 12, 28))

        addView(
            fallback,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )

        filamentSurface.alpha = 0f
        filamentSurface.isOpaque = true
        addView(
            filamentSurface,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )

        fallback.setAnimated(animated)
    }

    fun setWeather(snapshot: WeatherSnapshot?) {
        this.snapshot = snapshot
        fallback.setWeather(snapshot)
        viewer?.let { applyWeatherLighting(it) }
        requestRender()
    }

    fun setAnimated(animated: Boolean) {
        this.animated = animated

        // Only spend Canvas frames while Filament is not yet carrying the scene.
        fallback.setAnimated(animated && !filamentReady)

        if (animated) {
            startedAtNanos = 0L
            scheduleFrame()
        } else {
            renderOnce = true
            scheduleFrame()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        ensureFilament()
        scheduleFrame()
    }

    override fun onDetachedFromWindow() {
        attached = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        framePosted = false

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
            captureAnimatedTransforms(v)
            applyWeatherLighting(v)

            v
        }.getOrElse {
            filamentSurface.visibility = View.INVISIBLE
            fallback.visibility = View.VISIBLE
            fallback.setAnimated(animated)
            null
        }

        viewer = created
        if (created != null) {
            filamentSurface.visibility = View.VISIBLE
            renderOnce = true
        }
    }

    private fun configureQuality(v: ModelViewer) {
        val view = v.view

        view.setPostProcessingEnabled(true)
        view.setShadowingEnabled(true)
        view.setScreenSpaceRefractionEnabled(true)

        view.renderQuality = view.renderQuality.apply {
            hdrColorBuffer = FilamentView.QualityLevel.HIGH
        }

        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
            enabled = true
            homogeneousScaling = true
            minScale = 0.58f
            maxScale = 1.0f
            sharpness = 0.92f
            quality = FilamentView.QualityLevel.HIGH
        }

        view.multiSampleAntiAliasingOptions =
            view.multiSampleAntiAliasingOptions.apply {
                enabled = true
                sampleCount = 4
            }

        view.antiAliasing = FilamentView.AntiAliasing.FXAA

        view.ambientOcclusionOptions =
            view.ambientOcclusionOptions.apply {
                enabled = true
            }

        view.bloomOptions =
            view.bloomOptions.apply {
                enabled = true
                strength = 0.18f
            }

        // Water and wet surfaces benefit dramatically from SSR. Dynamic resolution keeps
        // the cost bounded on mobile.
        view.screenSpaceReflectionsOptions =
            view.screenSpaceReflectionsOptions.apply {
                enabled = true
                thickness = 0.18f
                maxDistance = 4.0f
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
            .intensity(28_000f)
            .irradiance(
                1,
                floatArrayOf(
                    0.46f,
                    0.58f,
                    0.78f
                )
            )
            .build(engine)
            .also { v.scene.indirectLight = it }
    }

    private fun loadAtmosynqScene(v: ModelViewer) {
        val encoded = context.assets
            .open(SCENE_ASSET)
            .bufferedReader()
            .use { it.readText() }
            .trim()

        val bytes = Base64.getDecoder().decode(encoded)
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
        v.cameraFocalLength = 34f
        v.cameraNear = 0.1f
        v.cameraFar = 100f
        v.camera.lookAt(
            0.0,
            3.0,
            13.5,
            0.0,
            1.15,
            -2.9,
            0.0,
            1.0,
            0.0
        )
    }

    private fun captureAnimatedTransforms(v: ModelViewer) {
        val asset = v.asset ?: return
        val tm = v.engine.transformManager

        ANIMATED_ENTITIES.forEach { name ->
            val entity = asset.getFirstEntityByName(name)
            if (entity == 0) return@forEach

            val instance = tm.getInstance(entity)
            if (instance == 0) return@forEach

            baseTransforms[name] = tm.getTransform(instance, FloatArray(16)).copyOf()
        }
    }

    private fun animateScene(v: ModelViewer, seconds: Float) {
        val asset = v.asset ?: return
        val tm = v.engine.transformManager
        val weather = snapshot

        val wind = ((weather?.windSpeedKmh ?: 18.0) / 35.0)
            .coerceIn(0.20, 2.2)
            .toFloat()

        tm.openLocalTransformTransaction()
        try {
            CLOUDS.forEachIndexed { index, name ->
                val base = baseTransforms[name] ?: return@forEachIndexed
                val entity = asset.getFirstEntityByName(name)
                if (entity == 0) return@forEachIndexed

                val instance = tm.getInstance(entity)
                if (instance == 0) return@forEachIndexed

                val matrix = base.copyOf()
                matrix[12] =
                    base[12] +
                    sin(seconds * (0.13f + index * 0.025f) * wind + index) *
                    (0.75f + index * 0.18f)
                matrix[13] =
                    base[13] +
                    sin(seconds * 0.19f + index * 1.7f) *
                    0.08f
                tm.setTransform(instance, matrix)
            }

            animateEntity(
                v,
                "RibbonCyan",
                seconds,
                x = sin(seconds * 0.22f) * 0.28f,
                y = sin(seconds * 0.31f) * 0.08f
            )
            animateEntity(
                v,
                "RibbonPurple",
                seconds,
                x = cos(seconds * 0.18f) * 0.34f,
                y = cos(seconds * 0.27f) * 0.07f
            )
            animateEntity(
                v,
                "Sun",
                seconds,
                x = sin(seconds * 0.06f) * 0.12f,
                y = sin(seconds * 0.11f) * 0.08f
            )
        } finally {
            tm.commitLocalTransformTransaction()
        }
    }

    private fun animateEntity(
        v: ModelViewer,
        name: String,
        seconds: Float,
        x: Float,
        y: Float
    ) {
        val base = baseTransforms[name] ?: return
        val asset = v.asset ?: return
        val entity = asset.getFirstEntityByName(name)
        if (entity == 0) return

        val tm = v.engine.transformManager
        val instance = tm.getInstance(entity)
        if (instance == 0) return

        val matrix = base.copyOf()
        matrix[12] = base[12] + x
        matrix[13] = base[13] + y
        tm.setTransform(instance, matrix)
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
            34_000f * (1f - cloud * 0.58f)
        } else {
            8_500f * (1f - cloud * 0.28f)
        }
        indirectLight?.intensity = ambient.coerceAtLeast(4_500f)

        val lightManager = v.engine.lightManager
        val lightInstance = lightManager.getInstance(v.light)
        if (lightInstance != 0) {
            val daylight = if (isDay) 1f else 0.16f
            val stormCut = if (thunder) 0.48f else 1f
            val intensity =
                (105_000f * daylight * stormCut * (1f - cloud * 0.48f))
                    .coerceAtLeast(if (isDay) 18_000f else 2_600f)

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
        private const val SCENE_ASSET = "filament/atmos_scene.glb.b64"

        private val CLOUDS = listOf(
            "Cloud0",
            "Cloud1",
            "Cloud2"
        )

        private val ANIMATED_ENTITIES = listOf(
            "Cloud0",
            "Cloud1",
            "Cloud2",
            "RibbonCyan",
            "RibbonPurple",
            "Sun"
        )

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
