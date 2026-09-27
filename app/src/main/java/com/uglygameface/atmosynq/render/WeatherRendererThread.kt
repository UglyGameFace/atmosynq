package com.uglygameface.atmosynq.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import com.uglygameface.atmosynq.weather.WeatherVisualState
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal class WeatherRendererThread(
    private val context: Context,
    private val holder: SurfaceHolder,
    private val visualProvider: () -> WeatherVisualState
) : Thread("AtmosynqGL") {

    private val running = AtomicBoolean(true)
    private val visible = AtomicBoolean(false)
    private val frameAvailable = AtomicBoolean(false)
    private val requestedWidth = AtomicInteger(1)
    private val requestedHeight = AtomicInteger(1)

    private var mediaPlayer: MediaPlayer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var particleSystem: ParticleSystem? = null
    private var videoProgram = 0
    private var videoTexture = 0
    private var positionBuffer: FloatBuffer = GlSupport.directFloatBuffer(8)
    private var texCoordBuffer: FloatBuffer = GlSupport.directFloatBuffer(8)
    private val textureMatrix = FloatArray(16)

    private var aPosition = -1
    private var aTexCoord = -1
    private var uTexMatrix = -1
    private var uVideo = -1
    private var uHasVideo = -1
    private var uDaylight = -1
    private var uSunsetWarmth = -1
    private var uCloudiness = -1
    private var uFog = -1
    private var uLightning = -1

    private var currentVisual = WeatherVisualState.DEFAULT
    private var targetVisual = WeatherVisualState.DEFAULT
    private var lastTargetRefreshMs = 0L
    private var lastFrameNanos = 0L
    private var lightningRemaining = 0f
    private var appliedWidth = -1
    private var appliedHeight = -1
    private val rng = Random(1337)

    fun setVisible(isVisible: Boolean) {
        visible.set(isVisible)
    }

    fun resize(width: Int, height: Int) {
        requestedWidth.set(max(1, width))
        requestedHeight.set(max(1, height))
    }

    fun shutdown() {
        running.set(false)
        interrupt()
    }

    override fun run() {
        var eglDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext = EGL14.EGL_NO_CONTEXT
        var eglSurface = EGL14.EGL_NO_SURFACE
        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "Unable to initialize EGL" }

            val configAttrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(eglDisplay, configAttrs, 0, configs, 0, 1, count, 0) && count[0] > 0) {
                "Unable to choose EGL config"
            }
            val config = requireNotNull(configs[0])
            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0
            )
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }
            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                config,
                holder.surface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL surface" }
            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "Unable to make EGL current" }

            setupGl()
            for (i in textureMatrix.indices) textureMatrix[i] = 0f
            textureMatrix[0] = 1f
            textureMatrix[5] = 1f
            textureMatrix[10] = 1f
            textureMatrix[15] = 1f
            setupVideo()
            particleSystem = ParticleSystem()
            lastFrameNanos = System.nanoTime()
            var lastVisible = false

            while (running.get()) {
                val nowNanos = System.nanoTime()
                val dt = min(0.05f, ((nowNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceAtLeast(0.001f))
                lastFrameNanos = nowNanos
                val isVisible = visible.get()

                if (isVisible != lastVisible) {
                    if (isVisible) runCatching { mediaPlayer?.start() } else runCatching { mediaPlayer?.pause() }
                    lastVisible = isVisible
                }
                if (!isVisible) {
                    sleepQuietly(120)
                    continue
                }

                if (frameAvailable.compareAndSet(true, false)) {
                    surfaceTexture?.updateTexImage()
                    surfaceTexture?.getTransformMatrix(textureMatrix)
                }

                val nowMs = System.currentTimeMillis()
                if (nowMs - lastTargetRefreshMs >= 1000) {
                    targetVisual = visualProvider()
                    lastTargetRefreshMs = nowMs
                }
                currentVisual = approach(currentVisual, targetVisual, min(1f, dt * 0.10f))
                updateLightning(dt, currentVisual.thunderIntensity)
                drawFrame(currentVisual, dt)
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nowNanos)
                if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) break
                sleepQuietly(30)
            }
        } finally {
            releaseGl()
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        }
    }

    private fun setupGl() {
        videoProgram = GlSupport.createProgram(GlSupport.VERTEX_SHADER, GlSupport.FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(videoProgram, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(videoProgram, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(videoProgram, "uTexMatrix")
        uVideo = GLES20.glGetUniformLocation(videoProgram, "uVideo")
        uHasVideo = GLES20.glGetUniformLocation(videoProgram, "uHasVideo")
        uDaylight = GLES20.glGetUniformLocation(videoProgram, "uDaylight")
        uSunsetWarmth = GLES20.glGetUniformLocation(videoProgram, "uSunsetWarmth")
        uCloudiness = GLES20.glGetUniformLocation(videoProgram, "uCloudiness")
        uFog = GLES20.glGetUniformLocation(videoProgram, "uFog")
        uLightning = GLES20.glGetUniformLocation(videoProgram, "uLightning")

        positionBuffer.clear()
        positionBuffer.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).flip()
        updateTextureCrop(requestedWidth.get(), requestedHeight.get())

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        videoTexture = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun setupVideo() {
        // Scene videos are optional. The public engine intentionally does not bundle third-party
        // artwork; dropping a private/licensed `scene_neutral.mp4` into res/raw enables video.
        // Without one, the shader renders a procedural fallback so CI and the weather engine are
        // still fully buildable/testable.
        val resourceId = context.resources.getIdentifier("scene_neutral", "raw", context.packageName)
        if (resourceId == 0) return

        val st = SurfaceTexture(videoTexture)
        surfaceTexture = st
        st.setOnFrameAvailableListener({ frameAvailable.set(true) }, Handler(Looper.getMainLooper()))
        val decoderSurface = Surface(st)
        val player = MediaPlayer.create(context, resourceId)
        if (player == null) {
            decoderSurface.release()
            st.release()
            surfaceTexture = null
            return
        }
        mediaPlayer = player.apply {
            isLooping = true
            setVolume(0f, 0f)
            setSurface(decoderSurface)
        }
        decoderSurface.release()
    }

    private fun drawFrame(state: WeatherVisualState, dt: Float) {
        val width = requestedWidth.get()
        val height = requestedHeight.get()
        if (width != appliedWidth || height != appliedHeight) {
            GLES20.glViewport(0, 0, width, height)
            updateTextureCrop(width, height)
            appliedWidth = width
            appliedHeight = height
        }
        GLES20.glClearColor(0.01f, 0.02f, 0.05f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(videoProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexture)
        GLES20.glUniform1i(uVideo, 0)
        GLES20.glUniform1f(uHasVideo, if (mediaPlayer != null) 1f else 0f)
        GLES20.glUniformatrix4fv(uTexMatrix, 1, false, textureMatrix, 0)
        GLES20.glUniform1f(uDaylight, state.daylight)
        GLES20.glUniform1f(uSunsetWarmth, state.sunsetWarmth)
        GLES20.glUniform1f(uCloudiness, state.cloudiness)
        GLES20.glUniform1f(uFog, state.fogIntensity)
        GLES20.glUniform1f(uLightning, lightningAmount())

        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, positionBuffer)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)

        particleSystem?.draw(state, dt)
    }

    private fun updateTextureCrop(surfaceWidth: Int, surfaceHeight: Int) {
        val surfaceAspect = surfaceWidth.toFloat() / max(1, surfaceHeight).toFloat()
        val videoAspect = 720f / 1280f
        var xMin = 0f
        var xMax = 1f
        var yMin = 0f
        var yMax = 1f
        if (surfaceAspect < videoAspect) {
            val visibleWidth = (surfaceAspect / videoAspect).coerceIn(0f, 1f)
            val margin = (1f - visibleWidth) * 0.5f
            xMin = margin
            xMax = 1f - margin
        } else if (surfaceAspect > videoAspect) {
            val visibleHeight = (videoAspect / surfaceAspect).coerceIn(0f, 1f)
            val margin = (1f - visibleHeight) * 0.5f
            yMin = margin
            yMax = 1f - margin
        }
        texCoordBuffer.clear()
        texCoordBuffer.put(floatArrayOf(xMin, yMin, xMax, yMin, xMin, yMax, xMax, yMax)).flip()
    }

    private fun updateLightning(dt: Float, thunder: Float) {
        if (lightningRemaining > 0f) {
            lightningRemaining = max(0f, lightningRemaining - dt)
            return
        }
        if (thunder <= 0f) return
        val flashesPerSecond = 0.015f + thunder * 0.07f
        if (rng.nextFloat() < flashesPerSecond * dt) lightningRemaining = 0.14f + rng.nextFloat() * 0.12f
    }

    private fun lightningAmount(): Float {
        if (lightningRemaining <= 0f) return 0f
        val phase = (lightningRemaining / 0.26f).coerceIn(0f, 1f)
        return (0.4f + phase * 0.6f).coerceIn(0f, 1f)
    }

    private fun releaseGl() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        surfaceTexture?.release()
        surfaceTexture = null
        particleSystem?.release()
        particleSystem = null
        if (videoTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(videoTexture), 0)
        if (videoProgram != 0) GLES20.glDeleteProgram(videoProgram)
    }

    private fun sleepQuietly(ms: Long) {
        try {
            sleep(ms)
        } catch (_: InterruptedException) {
            // Loop observes running flag.
        }
    }

    private fun approach(a: WeatherVisualState, b: WeatherVisualState, t: Float): WeatherVisualState {
        fun l(x: Float, y: Float) = x + (y - x) * t
        return WeatherVisualState(
            daylight = l(a.daylight, b.daylight),
            sunsetWarmth = l(a.sunsetWarmth, b.sunsetWarmth),
            cloudiness = l(a.cloudiness, b.cloudiness),
            rainIntensity = l(a.rainIntensity, b.rainIntensity),
            snowIntensity = l(a.snowIntensity, b.snowIntensity),
            fogIntensity = l(a.fogIntensity, b.fogIntensity),
            thunderIntensity = l(a.thunderIntensity, b.thunderIntensity),
            windStrength = l(a.windStrength, b.windStrength),
            windDirectionDeg = lAngle(a.windDirectionDeg, b.windDirectionDeg, t)
        )
    }

    private fun lAngle(a: Float, b: Float, t: Float): Float {
        var delta = (b - a) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return (a + delta * t + 360f) % 360f
    }

}
