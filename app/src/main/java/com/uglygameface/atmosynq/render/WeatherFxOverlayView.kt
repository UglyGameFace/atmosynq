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
    private var sceneProfile: SceneProfile = SceneProfile.DEFAULT
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

    fun setSceneProfile(profile: SceneProfile) {
        sceneProfile = profile
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
        drawForegroundDepth(canvas, seconds, isDay, cloud, wind)
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

    private fun drawForegroundDepth(
        canvas: Canvas,
        seconds: Float,
        isDay: Boolean,
        cloud: Float,
        wind: Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Geography now lives in the Filament environment kit. The overlay only adds
        // optical/weather detail, otherwise we would put flat Canvas buildings back on
        // top of real 3D buildings and recreate the exact fake look we are replacing.
        repeat(28) { index ->
            val seed = hash(1_700 + index * 71)
            val y = h * (0.60f + index / 28f * 0.30f)
            val travel =
                fract(
                    seconds * (0.024f + wind * 0.020f) +
                        index * 0.091f
                )
            val baseX = ((seed and 0x3ff) / 1023f) * w
            val x = (baseX + travel * w * 0.20f) % w
            val length =
                w *
                    (
                        0.020f +
                            ((seed shr 10) and 0xff) /
                            255f * 0.085f
                        )

            strokePaint.strokeWidth = 0.7f + (index % 3) * 0.38f
            val purple = index % 6 == 0
            strokePaint.color =
                if (purple) {
                    Color.argb(
                        if (isDay) 17 else 31,
                        184,
                        105,
                        255
                    )
                } else {
                    Color.argb(
                        if (isDay) 24 else 43,
                        112,
                        220,
                        255
                    )
                }

            canvas.drawLine(
                x + parallaxX * 9f,
                y,
                (x + length).coerceAtMost(w),
                y + sin(seconds * 0.7f + index) * 1.3f,
                strokePaint
            )
        }

        // A subtle contact haze blends rain/fog into the PBR scene without painting
        // fake foreground silhouettes over the real geometry.
        if (cloud > 0.70f) {
            paint.style = Paint.Style.FILL
            paint.shader =
                LinearGradient(
                    0f,
                    h * 0.70f,
                    0f,
                    h,
                    Color.argb(
                        ((cloud - 0.70f) * 70f)
                            .toInt()
                            .coerceIn(0, 24),
                        137,
                        180,
                        204
                    ),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            canvas.drawRect(0f, h * 0.70f, w, h, paint)
            paint.shader = null
        }
    }

    private fun drawRollingHorizon(
        canvas: Canvas,
        isDay: Boolean,
        baseY: Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.style = Paint.Style.FILL
        paint.color =
            Color.argb(
                if (isDay) 82 else 145,
                12,
                38,
                50
            )

        path.reset()
        path.moveTo(-w * 0.1f, h)
        path.lineTo(-w * 0.1f, baseY)
        path.cubicTo(
            w * 0.18f,
            baseY - h * 0.055f,
            w * 0.34f,
            baseY + h * 0.025f,
            w * 0.52f,
            baseY - h * 0.035f
        )
        path.cubicTo(
            w * 0.70f,
            baseY - h * 0.085f,
            w * 0.84f,
            baseY + h * 0.015f,
            w * 1.1f,
            baseY - h * 0.025f
        )
        path.lineTo(w * 1.1f, h)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawCitySkyline(
        canvas: Canvas,
        isDay: Boolean,
        cloud: Float,
        dense: Boolean
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val population =
            (sceneProfile.population ?: 0L)
                .coerceAtLeast(0L)
        val populationBoost =
            (population / 500_000L)
                .toInt()
                .coerceIn(0, 10)
        val count =
            (if (dense) 22 else 14) +
                populationBoost
        val baseY = h * 0.91f
        val slot = w / count
        val locationSeed = sceneProfile.label.hashCode()

        repeat(count) { index ->
            val seed =
                hash(
                    locationSeed xor
                        (3_000 + index * 97)
                )
            val widthFactor = 0.58f + ((seed and 0xff) / 255f) * 0.34f
            val maxHeight = if (dense) 0.28f else 0.19f
            val heightFactor =
                0.055f +
                    (((seed shr 8) and 0xff) / 255f) * maxHeight
            val buildingWidth = slot * widthFactor
            val buildingHeight = h * heightFactor
            val x =
                index * slot +
                    (slot - buildingWidth) * 0.5f +
                    parallaxX * (5f + index % 5)
            val top = baseY - buildingHeight

            paint.style = Paint.Style.FILL
            paint.color =
                Color.argb(
                    if (isDay) {
                        (118f + cloud * 38f).toInt()
                    } else {
                        205
                    },
                    3 + index % 3 * 3,
                    19 + index % 4 * 3,
                    32 + index % 5 * 3
                )
            canvas.drawRoundRect(
                x,
                top,
                x + buildingWidth,
                baseY,
                buildingWidth * 0.06f,
                buildingWidth * 0.06f,
                paint
            )

            if (!isDay) {
                val windowColor =
                    if (index % 4 == 0) {
                        Color.argb(112, 207, 143, 255)
                    } else {
                        Color.argb(120, 255, 209, 121)
                    }
                paint.color = windowColor
                val rows = (buildingHeight / (h * 0.027f)).toInt().coerceIn(1, 7)
                repeat(rows) { row ->
                    val wy = top + h * 0.018f + row * h * 0.027f
                    canvas.drawRect(
                        x + buildingWidth * 0.20f,
                        wy,
                        x + buildingWidth * 0.34f,
                        wy + h * 0.008f,
                        paint
                    )
                    if (buildingWidth > slot * 0.65f) {
                        canvas.drawRect(
                            x + buildingWidth * 0.60f,
                            wy,
                            x + buildingWidth * 0.74f,
                            wy + h * 0.008f,
                            paint
                        )
                    }
                }
            }
        }
    }

    private fun drawNeighborhood(
        canvas: Canvas,
        isDay: Boolean,
        dense: Boolean
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val count = if (dense) 11 else 8
        val slot = w / count
        val baseY = h * 0.925f
        val locationSeed = sceneProfile.label.hashCode()

        repeat(count) { index ->
            val seed =
                hash(
                    locationSeed xor
                        (5_100 + index * 83)
                )
            val houseWidth = slot * (0.58f + (seed and 0xff) / 255f * 0.20f)
            val houseHeight = h * (0.050f + ((seed shr 8) and 0xff) / 255f * 0.035f)
            val x =
                index * slot +
                    (slot - houseWidth) * 0.5f +
                    parallaxX * (8f + index % 4)
            val top = baseY - houseHeight

            paint.style = Paint.Style.FILL
            paint.color =
                Color.argb(
                    if (isDay) 142 else 218,
                    4,
                    22,
                    34
                )
            canvas.drawRect(x, top, x + houseWidth, baseY, paint)

            path.reset()
            path.moveTo(x - houseWidth * 0.10f, top)
            path.lineTo(x + houseWidth * 0.50f, top - houseHeight * 0.50f)
            path.lineTo(x + houseWidth * 1.10f, top)
            path.close()
            canvas.drawPath(path, paint)

            if (!isDay && index % 2 == 0) {
                paint.color = Color.argb(155, 255, 205, 124)
                canvas.drawRect(
                    x + houseWidth * 0.22f,
                    top + houseHeight * 0.30f,
                    x + houseWidth * 0.40f,
                    top + houseHeight * 0.58f,
                    paint
                )
            }
        }
    }

    private fun drawLocationVegetation(
        canvas: Canvas,
        isDay: Boolean,
        cloud: Float,
        zone: LatitudeBand,
        settlement: SettlementKind
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val count =
            when (settlement) {
                SettlementKind.METRO -> 3
                SettlementKind.CITY -> 5
                SettlementKind.TOWN -> 8
                SettlementKind.LOCAL -> 11
            }
        val alpha =
            if (isDay) {
                (104f + cloud * 44f).toInt()
            } else {
                195
            }

        repeat(count) { index ->
            val fraction =
                if (count == 1) {
                    0.5f
                } else {
                    index / (count - 1f)
                }
            val x =
                w * (0.025f + fraction * 0.95f) +
                    parallaxX * (14f + index % 4 * 4f)
            val baseY = h * (0.94f + (index % 3) * 0.008f)
            val size = h * (0.07f + (index % 4) * 0.012f)

            when (zone) {
                LatitudeBand.TROPICAL ->
                    drawPalm(canvas, x, baseY, size, alpha)
                LatitudeBand.WARM ->
                    if (index % 3 == 0) {
                        drawPalm(canvas, x, baseY, size * 0.88f, alpha)
                    } else {
                        drawBroadleaf(canvas, x, baseY, size, alpha)
                    }
                LatitudeBand.TEMPERATE ->
                    if (index % 2 == 0) {
                        drawBroadleaf(canvas, x, baseY, size, alpha)
                    } else {
                        drawPine(canvas, x, baseY, size, alpha)
                    }
                LatitudeBand.COOL ->
                    drawPine(canvas, x, baseY, size, alpha)
                LatitudeBand.POLAR ->
                    if (index % 2 == 0) {
                        drawPine(canvas, x, baseY, size * 0.72f, alpha)
                    }
            }
        }
    }

    private fun drawPine(
        canvas: Canvas,
        x: Float,
        baseY: Float,
        size: Float,
        alpha: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha.coerceIn(0, 230), 2, 18, 28)

        val top = baseY - size
        val trunkWidth = size * 0.055f
        canvas.drawRect(
            x - trunkWidth,
            baseY - size * 0.24f,
            x + trunkWidth,
            baseY,
            paint
        )

        repeat(4) { tier ->
            val t = tier / 3f
            val tierY = top + size * (0.22f + t * 0.52f)
            val half = size * (0.16f + t * 0.21f)
            val tierHeight = size * 0.34f

            path.reset()
            path.moveTo(x, tierY - tierHeight * 0.58f)
            path.lineTo(x - half, tierY + tierHeight * 0.55f)
            path.lineTo(x + half, tierY + tierHeight * 0.55f)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun drawBroadleaf(
        canvas: Canvas,
        x: Float,
        baseY: Float,
        size: Float,
        alpha: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha.coerceIn(0, 230), 3, 24, 30)
        canvas.drawRect(
            x - size * 0.045f,
            baseY - size * 0.35f,
            x + size * 0.045f,
            baseY,
            paint
        )
        canvas.drawOval(
            x - size * 0.30f,
            baseY - size,
            x + size * 0.30f,
            baseY - size * 0.28f,
            paint
        )
        canvas.drawOval(
            x - size * 0.42f,
            baseY - size * 0.82f,
            x + size * 0.12f,
            baseY - size * 0.30f,
            paint
        )
        canvas.drawOval(
            x - size * 0.10f,
            baseY - size * 0.86f,
            x + size * 0.42f,
            baseY - size * 0.32f,
            paint
        )
    }

    private fun drawPalm(
        canvas: Canvas,
        x: Float,
        baseY: Float,
        size: Float,
        alpha: Int
    ) {
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = (size * 0.055f).coerceAtLeast(1f)
        strokePaint.color = Color.argb(alpha.coerceIn(0, 230), 5, 27, 29)
        canvas.drawLine(
            x,
            baseY,
            x + size * 0.09f,
            baseY - size * 0.72f,
            strokePaint
        )

        val crownX = x + size * 0.09f
        val crownY = baseY - size * 0.72f
        repeat(6) { index ->
            val angle = -2.75 + index * 0.62
            val endX = crownX + cos(angle).toFloat() * size * 0.42f
            val endY = crownY + sin(angle).toFloat() * size * 0.28f
            strokePaint.strokeWidth = (size * 0.035f).coerceAtLeast(1f)
            canvas.drawLine(crownX, crownY, endX, endY, strokePaint)
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
        while (pulses.isNotEmpty()) {
            val age = (nowNanos - pulses.first().startedAtNanos) / 1_000_000_000f
            if (age <= 1.05f) break
            pulses.removeFirst()
        }

        pulses.forEach { pulse ->
            val age = (nowNanos - pulse.startedAtNanos) / 1_000_000_000f
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
        return x and Int.MAX_VALUE
    }

    private fun fract(value: Float): Float =
        value - floor(value.toDouble()).toFloat()
}
