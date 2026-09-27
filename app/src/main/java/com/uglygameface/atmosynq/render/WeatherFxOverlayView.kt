package com.uglygameface.atmosynq.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import com.uglygameface.atmosynq.weather.WeatherSnapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * High-frequency atmospheric layer rendered above Filament.
 *
 * The 3D scene provides depth, geometry and PBR lighting. This view adds the
 * things that make weather feel alive on a phone: rain/snow, fog, gust streaks,
 * lightning bloom, aurora ribbons and touch ripples. Everything is deterministic
 * from the weather state so the effect does not pop when the dashboard redraws.
 */
class WeatherFxOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    private var snapshot: WeatherSnapshot? = null
    private var animated = true
    private var startedAtNanos = System.nanoTime()
    private var parallaxX = 0f
    private var parallaxY = 0f

    private var auroraGradient: LinearGradient? = null
    private var vignetteGradient: RadialGradient? = null

    private data class Pulse(
        val x: Float,
        val y: Float,
        val startedAtNanos: Long
    )

    private val pulses = ArrayDeque<Pulse>()

    fun setWeather(snapshot: WeatherSnapshot?) {
        this.snapshot = snapshot
        startedAtNanos = System.nanoTime()
        invalidate()
    }

    fun setAnimated(animated: Boolean) {
        this.animated = animated
        if (animated) {
            startedAtNanos = System.nanoTime()
            postInvalidateOnAnimation()
        } else {
            invalidate()
        }
    }

    fun setParallax(x: Float, y: Float) {
        parallaxX = x.coerceIn(-1f, 1f)
        parallaxY = y.coerceIn(-1f, 1f)
        invalidate()
    }

    fun addTouchPulse(x: Float, y: Float) {
        if (pulses.size >= 5) pulses.removeFirst()
        pulses.addLast(Pulse(x, y, System.nanoTime()))
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        auroraGradient = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            h * 0.58f,
            intArrayOf(
                Color.argb(0, 34, 236, 255),
                Color.argb(150, 39, 208, 255),
                Color.argb(130, 157, 79, 255),
                Color.argb(0, 255, 71, 214)
            ),
            floatArrayOf(0f, 0.30f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )

        vignetteGradient = RadialGradient(
            w * 0.5f,
            h * 0.42f,
            maxOf(w, h) * 0.68f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(18, 0, 4, 15),
                Color.argb(150, 0, 4, 13)
            ),
            floatArrayOf(0f, 0.70f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val now = System.nanoTime()
        val seconds =
            if (animated) (now - startedAtNanos) / 1_000_000_000f else 0f

        val weather = snapshot
        val isDay = weather?.isDay ?: true
        val cloud = ((weather?.cloudCoverPct ?: 38.0) / 100.0)
            .coerceIn(0.0, 1.0)
            .toFloat()
        val wind = ((weather?.windSpeedKmh ?: 14.0) / 55.0)
            .coerceIn(0.0, 1.8)
            .toFloat()

        drawAtmosphere(canvas, seconds, isDay, cloud)
        drawWind(canvas, seconds, wind, cloud)
        drawPrecipitation(canvas, seconds, weather, wind)
        drawFog(canvas, seconds, weather, cloud)
        drawLightning(canvas, seconds, weather)
        drawTouchPulses(canvas, now)
        drawVignette(canvas)

        if (animated || pulses.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawAtmosphere(
        canvas: Canvas,
        seconds: Float,
        isDay: Boolean,
        cloud: Float
    ) {
        val alphaBase =
            if (isDay) {
                (18f + cloud * 30f)
            } else {
                (42f + cloud * 54f)
            }

        paint.style = Paint.Style.FILL
        paint.shader = auroraGradient
        paint.alpha = alphaBase.toInt().coerceIn(0, 105)

        val w = width.toFloat()
        val h = height.toFloat()
        val offsetX = parallaxX * w * 0.035f
        val offsetY = parallaxY * h * 0.025f

        repeat(3) { band ->
            val phase = seconds * (0.11f + band * 0.025f) + band * 1.7f
            val baseY = h * (0.18f + band * 0.12f)
            val amplitude = h * (0.045f + band * 0.012f)

            path.reset()
            path.moveTo(-w * 0.12f + offsetX, baseY + sin(phase) * amplitude + offsetY)
            path.cubicTo(
                w * 0.18f,
                baseY - amplitude * 1.7f + sin(phase + 0.9f) * amplitude,
                w * 0.48f,
                baseY + amplitude * 1.5f + cos(phase * 0.8f) * amplitude,
                w * 0.70f,
                baseY - amplitude * 0.55f + offsetY
            )
            path.cubicTo(
                w * 0.86f,
                baseY - amplitude * 1.1f,
                w * 1.04f,
                baseY + amplitude * 0.8f,
                w * 1.13f,
                baseY + sin(phase + 1.6f) * amplitude
            )
            path.lineTo(w * 1.13f, baseY + h * 0.115f)
            path.cubicTo(
                w * 0.80f,
                baseY + h * 0.05f,
                w * 0.36f,
                baseY + h * 0.16f,
                -w * 0.12f,
                baseY + h * 0.085f
            )
            path.close()
            canvas.drawPath(path, paint)
        }

        paint.shader = null

        if (!isDay) {
            drawStars(canvas, seconds, cloud)
        }
    }

    private fun drawStars(canvas: Canvas, seconds: Float, cloud: Float) {
        val visible = (1f - cloud * 0.78f).coerceIn(0.08f, 1f)
        paint.style = Paint.Style.FILL

        repeat(34) { index ->
            val seed = hash(index * 37 + 11)
            val x = ((seed and 0x3ff) / 1023f) * width
            val y = (((seed shr 10) and 0x3ff) / 1023f) * height * 0.58f
            val twinkle = 0.62f + 0.38f * sin(seconds * (0.7f + index % 5 * 0.12f) + index)
            paint.color = Color.argb(
                (75f * visible * twinkle).toInt().coerceIn(0, 90),
                214,
                238,
                255
            )
            val radius = 0.7f + (index % 3) * 0.45f
            canvas.drawCircle(
                x + parallaxX * (index % 5) * 1.8f,
                y + parallaxY * (index % 4) * 1.2f,
                radius,
                paint
            )
        }
    }

    private fun drawWind(
        canvas: Canvas,
        seconds: Float,
        wind: Float,
        cloud: Float
    ) {
        if (wind < 0.22f) return

        strokePaint.strokeWidth = 1.25f + wind * 0.8f
        strokePaint.color = Color.argb(
            (24f + wind * 35f + cloud * 18f).toInt().coerceIn(0, 84),
            174,
            228,
            255
        )

        val w = width.toFloat()
        val h = height.toFloat()
        val speed = 0.18f + wind * 0.44f

        repeat(15) { index ->
            val seed = hash(index * 59 + 101)
            val y = h * (0.16f + (((seed shr 7) and 0x3ff) / 1023f) * 0.66f)
            val phase = fract((seconds * speed + index * 0.137f))
            val x = phase * (w * 1.35f) - w * 0.18f
            val length = w * (0.035f + (index % 4) * 0.012f)
            val bend = sin(seconds * 0.7f + index) * h * 0.006f
            canvas.drawLine(
                x,
                y + bend,
                x + length,
                y + bend - h * 0.012f * wind,
                strokePaint
            )
        }
    }

    private fun drawPrecipitation(
        canvas: Canvas,
        seconds: Float,
        weather: WeatherSnapshot?,
        wind: Float
    ) {
        val code = weather?.weatherCode ?: 0
        val rainAmount =
            maxOf(
                weather?.rainMm ?: 0.0,
                weather?.showersMm ?: 0.0,
                weather?.precipitationMm ?: 0.0
            )
        val snowfall = weather?.snowfallCm ?: 0.0

        val rainCode = code in 51..67 || code in 80..82 || code in 95..99
        val snowCode = code in 71..77 || code in 85..86

        if (rainCode || rainAmount > 0.02) {
            val intensity =
                (0.24f + rainAmount.toFloat() * 0.34f)
                    .coerceIn(0.22f, 1f)
            drawRain(canvas, seconds, intensity, wind)
        }

        if (snowCode || snowfall > 0.01) {
            val intensity =
                (0.32f + snowfall.toFloat() * 0.22f)
                    .coerceIn(0.25f, 1f)
            drawSnow(canvas, seconds, intensity, wind)
        }
    }

    private fun drawRain(
        canvas: Canvas,
        seconds: Float,
        intensity: Float,
        wind: Float
    ) {
        val count = (42 + intensity * 105f).toInt()
        val w = width.toFloat()
        val h = height.toFloat()

        strokePaint.strokeWidth = 1.0f + intensity * 1.25f
        strokePaint.color = Color.argb(
            (74 + intensity * 82f).toInt().coerceIn(70, 170),
            174,
            224,
            255
        )

        repeat(count) { index ->
            val seed = hash(index * 97 + 701)
            val x0 = ((seed and 0xffff) / 65535f) * w
            val ySeed = (((seed shr 16) and 0xffff) / 65535f)
            val speed = 0.48f + ((index % 11) / 11f) * 0.55f
            val progress = fract(ySeed + seconds * speed * (0.72f + intensity))
            val y0 = progress * (h * 1.18f) - h * 0.09f
            val length = h * (0.025f + intensity * 0.045f + (index % 4) * 0.004f)
            val slant = w * (0.008f + wind * 0.018f)

            canvas.drawLine(
                x0 + parallaxX * 9f,
                y0,
                x0 - slant + parallaxX * 13f,
                y0 + length,
                strokePaint
            )
        }
    }

    private fun drawSnow(
        canvas: Canvas,
        seconds: Float,
        intensity: Float,
        wind: Float
    ) {
        val count = (30 + intensity * 70f).toInt()
        val w = width.toFloat()
        val h = height.toFloat()

        paint.style = Paint.Style.FILL

        repeat(count) { index ->
            val seed = hash(index * 83 + 911)
            val baseX = ((seed and 0xffff) / 65535f) * w
            val ySeed = (((seed shr 16) and 0xffff) / 65535f)
            val speed = 0.055f + (index % 9) * 0.008f
            val progress = fract(ySeed + seconds * speed)
            val y = progress * (h * 1.12f) - h * 0.06f
            val drift = sin(seconds * (0.6f + index % 4 * 0.08f) + index) *
                (10f + wind * 20f)
            val radius = 1.6f + (index % 4) * 0.65f

            paint.color = Color.argb(
                105 + (index % 5) * 20,
                235,
                247,
                255
            )
            canvas.drawCircle(
                baseX + drift + parallaxX * 14f,
                y,
                radius,
                paint
            )
        }
    }

    private fun drawFog(
        canvas: Canvas,
        seconds: Float,
        weather: WeatherSnapshot?,
        cloud: Float
    ) {
        val fogCode = weather?.weatherCode in setOf(45, 48)
        val visibility = weather?.visibilityM ?: 100_000.0
        val lowVisibility =
            ((8_000.0 - visibility) / 8_000.0)
                .coerceIn(0.0, 1.0)
                .toFloat()
        val fog = maxOf(
            if (fogCode) 0.58f else 0f,
            lowVisibility * 0.72f,
            if (cloud > 0.90f) 0.10f else 0f
        )
        if (fog <= 0.02f) return

        val w = width.toFloat()
        val h = height.toFloat()

        repeat(3) { band ->
            val travel = fract(seconds * (0.018f + band * 0.006f) + band * 0.31f)
            val x = -w * 0.35f + travel * w * 0.70f
            paint.shader = LinearGradient(
                x,
                0f,
                x + w * 0.85f,
                0f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb((fog * 95f).toInt(), 185, 216, 229),
                    Color.argb((fog * 70f).toInt(), 210, 230, 238),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )
            paint.style = Paint.Style.FILL
            val top = h * (0.52f + band * 0.095f) + parallaxY * 10f
            canvas.drawOval(
                -w * 0.20f,
                top,
                w * 1.20f,
                top + h * (0.14f + band * 0.025f),
                paint
            )
        }

        paint.shader = null
    }

    private fun drawLightning(
        canvas: Canvas,
        seconds: Float,
        weather: WeatherSnapshot?
    ) {
        if (weather?.weatherCode !in setOf(95, 96, 97, 99)) return

        val cycle = seconds % 7.8f
        val flash =
            when {
                cycle < 0.055f -> 1f - cycle / 0.055f
                cycle in 0.12f..0.18f -> 0.62f * (1f - (cycle - 0.12f) / 0.06f)
                else -> 0f
            }
        if (flash <= 0f) return

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(
            (flash * 110f).toInt().coerceIn(0, 120),
            205,
            229,
            255
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        strokePaint.strokeWidth = 2.2f
        strokePaint.color = Color.argb(
            (flash * 220f).toInt().coerceIn(0, 235),
            227,
            241,
            255
        )

        val w = width.toFloat()
        val h = height.toFloat()
        val x = w * 0.72f

        path.reset()
        path.moveTo(x, h * 0.05f)
        path.lineTo(x - w * 0.06f, h * 0.22f)
        path.lineTo(x + w * 0.005f, h * 0.205f)
        path.lineTo(x - w * 0.08f, h * 0.42f)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawTouchPulses(
        canvas: Canvas,
        nowNanos: Long
    ) {
        val iterator = pulses.iterator()
        while (iterator.hasNext()) {
            val pulse = iterator.next()
            val age = (nowNanos - pulse.startedAtNanos) / 1_000_000_000f
            if (age > 1.05f) {
                iterator.remove()
                continue
            }

            val t = (age / 1.05f).coerceIn(0f, 1f)
            val radius = 16f + width * 0.16f * t
            val alpha = ((1f - t) * 150f).toInt()

            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 2.4f - t * 1.1f
            strokePaint.color = Color.argb(alpha, 112, 225, 255)
            canvas.drawCircle(pulse.x, pulse.y, radius, strokePaint)

            strokePaint.strokeWidth = 1.4f
            strokePaint.color = Color.argb((alpha * 0.62f).toInt(), 185, 98, 255)
            canvas.drawCircle(pulse.x, pulse.y, radius * 0.72f, strokePaint)
        }
    }

    private fun drawVignette(canvas: Canvas) {
        val shader = vignetteGradient ?: return
        paint.style = Paint.Style.FILL
        paint.shader = shader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun hash(value: Int): Int {
        var x = value
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        return abs(x)
    }

    private fun fract(value: Float): Float =
        value - floor(value)

    companion object {
        private const val TAU = (PI * 2.0)
    }
}
