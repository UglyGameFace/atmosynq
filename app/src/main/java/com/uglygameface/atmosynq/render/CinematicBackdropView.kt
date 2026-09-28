package com.uglygameface.atmosynq.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.uglygameface.atmosynq.weather.WeatherSnapshot
import kotlin.math.sin

/**
 * Photographic/cinematic base layer for lowland town/city weather scenes.
 *
 * This intentionally replaces the "toy diorama" look for profiles where the generated
 * geometry was visually weaker than the approved Atmosynq mockup. Filament remains the
 * renderer for the higher-relief / metro paths and WeatherFxOverlayView still supplies
 * live precipitation, fog, wind and lightning over this layer.
 */
class CinematicBackdropView(context: Context) : View(context) {
    private val bitmapPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val destination = RectF()

    private var bitmap: Bitmap? = null
    private var profile: SceneProfile = SceneProfile.DEFAULT
    private var snapshot: WeatherSnapshot? = null
    private var animated = true
    private var elapsedSeconds = 0f
    private var parallaxX = 0f
    private var parallaxY = 0f

    init {
        bitmap =
            runCatching {
                context.assets.open(TEMPERATE_TOWN_ASSET)
                    .use(BitmapFactory::decodeStream)
            }.getOrNull()
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun canRender(profile: SceneProfile): Boolean =
        bitmap != null &&
            profile.terrain != TerrainKind.MOUNTAIN &&
            profile.settlement != SettlementKind.METRO &&
            (
                profile.latitudeBand == LatitudeBand.TEMPERATE ||
                    profile.latitudeBand == LatitudeBand.COOL ||
                    profile.settlement == SettlementKind.TOWN ||
                    profile.settlement == SettlementKind.LOCAL
                )

    fun setSceneProfile(profile: SceneProfile) {
        this.profile = profile
        invalidate()
    }

    fun setWeather(snapshot: WeatherSnapshot?) {
        this.snapshot = snapshot
        invalidate()
    }

    fun setAnimated(animated: Boolean) {
        this.animated = animated
        invalidate()
    }

    fun setParallax(x: Float, y: Float) {
        parallaxX = x.coerceIn(-1f, 1f)
        parallaxY = y.coerceIn(-1f, 1f)
        invalidate()
    }

    fun setElapsedSeconds(seconds: Float) {
        if (!animated) return
        elapsedSeconds = seconds
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val source = bitmap ?: return
        if (width <= 0 || height <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Slight overscan keeps drag/parallax from exposing edges.
        val scale =
            maxOf(
                w / source.width.toFloat(),
                h / source.height.toFloat()
            ) * 1.08f

        val drawWidth = source.width * scale
        val drawHeight = source.height * scale

        val idleX =
            if (animated) {
                sin(elapsedSeconds * 0.055f) * w * 0.008f
            } else {
                0f
            }
        val idleY =
            if (animated) {
                sin(elapsedSeconds * 0.041f + 1.7f) * h * 0.004f
            } else {
                0f
            }

        val centerX =
            w * 0.5f +
                parallaxX * w * 0.026f +
                idleX
        val centerY =
            h * 0.5f +
                parallaxY * h * 0.018f +
                idleY

        destination.set(
            centerX - drawWidth * 0.5f,
            centerY - drawHeight * 0.5f,
            centerX + drawWidth * 0.5f,
            centerY + drawHeight * 0.5f
        )

        bitmapPaint.colorFilter =
            ColorMatrixColorFilter(
                colorMatrixForWeather()
            )

        canvas.drawBitmap(
            source,
            null,
            destination,
            bitmapPaint
        )

        // Mockup-style readability: cinematic image first, dark glass/data second.
        overlayPaint.shader =
            LinearGradient(
                0f,
                0f,
                0f,
                h,
                intArrayOf(
                    Color.argb(120, 2, 10, 23),
                    Color.argb(18, 2, 10, 23),
                    Color.argb(30, 2, 10, 23),
                    Color.argb(126, 2, 9, 20)
                ),
                floatArrayOf(0f, 0.23f, 0.63f, 1f),
                Shader.TileMode.CLAMP
            )
        canvas.drawRect(0f, 0f, w, h, overlayPaint)
        overlayPaint.shader = null

        // Darken the left/top text zone without flattening the scenic right side.
        overlayPaint.shader =
            LinearGradient(
                0f,
                0f,
                w * 0.78f,
                0f,
                Color.argb(86, 1, 8, 19),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        canvas.drawRect(0f, 0f, w, h * 0.64f, overlayPaint)
        overlayPaint.shader = null
    }

    private fun colorMatrixForWeather(): ColorMatrix {
        val weather = snapshot
        val cloud =
            ((weather?.cloudCoverPct ?: 55.0) / 100.0)
                .coerceIn(0.0, 1.0)
                .toFloat()
        val isDay = weather?.isDay ?: false

        val saturation =
            when {
                !isDay -> 0.72f
                cloud > 0.78f -> 0.76f
                cloud > 0.45f -> 0.84f
                else -> 0.96f
            }

        val brightness =
            when {
                !isDay -> 0.62f
                cloud > 0.82f -> 0.86f
                cloud > 0.55f -> 0.92f
                else -> 1.03f
            }

        val cool =
            when (profile.latitudeBand) {
                LatitudeBand.POLAR -> 1.10f
                LatitudeBand.COOL -> 1.06f
                LatitudeBand.TEMPERATE -> 1.02f
                LatitudeBand.WARM -> 0.98f
                LatitudeBand.TROPICAL -> 0.96f
            }

        val matrix = ColorMatrix()
        matrix.setSaturation(saturation)

        val scale =
            ColorMatrix(
                floatArrayOf(
                    brightness * (2f - cool), 0f, 0f, 0f, 0f,
                    0f, brightness, 0f, 0f, 0f,
                    0f, 0f, brightness * cool, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        matrix.postConcat(scale)

        return matrix
    }

    companion object {
        private const val TEMPERATE_TOWN_ASSET =
            "backdrops/scene_temperate_town.webp"
    }
}
