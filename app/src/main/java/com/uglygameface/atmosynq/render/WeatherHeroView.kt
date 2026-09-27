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
import kotlin.math.sin
import kotlin.random.Random

class WeatherHeroView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var snapshot: WeatherSnapshot? = null
    private var sceneProfile: SceneProfile = SceneProfile.DEFAULT
    private var animated = true
    private var startedAt = System.currentTimeMillis()

    fun setSceneProfile(profile: SceneProfile) {
        sceneProfile = profile
        invalidate()
    }

    fun setWeather(snapshot: WeatherSnapshot?) {
        this.snapshot = snapshot
        startedAt = System.currentTimeMillis()
        invalidate()
    }

    fun setAnimated(animated: Boolean) {
        this.animated = animated
        if (animated) {
            startedAt = System.currentTimeMillis()
            postInvalidateOnAnimation()
        } else {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val t = if (animated) {
            (System.currentTimeMillis() - startedAt) / 1000f
        } else {
            0f
        }

        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val s = snapshot

        if (s == null) {
            drawAmbientScene(canvas, t, w, h)
        } else {
            val cloud = (s.cloudCoverPct / 100.0)
                .coerceIn(0.0, 1.0)
                .toFloat()

            drawSky(canvas, s, cloud, t, w, h)
            drawAtmosphericGlow(canvas, s, cloud, t, w, h)
            drawSkyRibbons(canvas, s, cloud, t, w, h)
            drawParallaxClouds(canvas, s, cloud, t, w, h)
            drawLandscape(canvas, s, cloud, t, w, h)
            drawWater(canvas, s, cloud, t, w, h)
            drawWindMotes(canvas, s, cloud, t, w, h)
            drawPrecipitation(canvas, s, t, w, h)
            drawFog(canvas, s, t, w, h)
            drawLightning(canvas, s, t, w, h)
            drawVignette(canvas, w, h)
        }

        if (animated && isShown) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawAmbientScene(
        canvas: Canvas,
        t: Float,
        w: Float,
        h: Float
    ) {
        paint.style = Paint.Style.FILL

        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h,
            intArrayOf(
                Color.rgb(3, 12, 31),
                Color.rgb(7, 35, 67),
                Color.rgb(16, 27, 56),
                Color.rgb(2, 10, 24)
            ),
            floatArrayOf(0f, 0.38f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val glowX = w * (0.70f + sin(t * 0.18f) * 0.06f)
        val glowY = h * 0.27f
        paint.shader = RadialGradient(
            glowX,
            glowY,
            h * 0.48f,
            Color.argb(110, 48, 178, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(glowX, glowY, h * 0.48f, paint)
        paint.shader = null

        drawRibbon(
            canvas = canvas,
            y = h * 0.30f,
            amplitude = h * 0.08f,
            phase = t * 0.22f,
            w = w,
            color = Color.argb(95, 59, 220, 255),
            width = h * 0.055f
        )
        drawRibbon(
            canvas = canvas,
            y = h * 0.43f,
            amplitude = h * 0.065f,
            phase = t * 0.18f + 1.7f,
            w = w,
            color = Color.argb(82, 175, 91, 255),
            width = h * 0.045f
        )

        repeat(18) { i ->
            val phase = i * 0.73f
            val x = ((i * w * 0.17f) + t * (7f + i % 4)) % (w + 30f) - 15f
            val y = h * (0.18f + ((i * 37) % 65) / 100f)
            val pulse = 0.55f + 0.45f * sin(t * 0.8f + phase)
            paint.color = Color.argb(
                (45 + 50 * pulse).toInt().coerceIn(35, 95),
                173,
                225,
                255
            )
            canvas.drawCircle(x, y, 1.5f + (i % 3), paint)
        }

        val horizon = h * 0.69f
        paint.shader = LinearGradient(
            0f,
            horizon,
            0f,
            h,
            Color.rgb(7, 43, 69),
            Color.rgb(2, 14, 28),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, horizon, w, h, paint)
        paint.shader = null

        repeat(8) { i ->
            val y = horizon + h * (0.025f + i * 0.032f)
            val shift = if (animated) sin(t * 0.9f + i) * 12f else 0f
            paint.color = Color.argb(
                (82 - i * 7).coerceAtLeast(18),
                74,
                188,
                236
            )
            paint.strokeWidth = 1.5f
            canvas.drawLine(
                w * 0.18f + shift,
                y,
                w * 0.78f + shift,
                y,
                paint
            )
        }

        drawVignette(canvas, w, h)
    }

    private fun drawSky(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        t: Float,
        w: Float,
        h: Float
    ) {
        val breathe = if (animated) {
            0.04f * sin(t * 0.20f)
        } else {
            0f
        }

        val top = if (s.isDay) {
            blend(
                Color.rgb(17, 95, 174),
                Color.rgb(29, 46, 67),
                (cloud * 0.78f + breathe).coerceIn(0f, 1f)
            )
        } else {
            blend(
                Color.rgb(2, 12, 35),
                Color.rgb(22, 31, 47),
                cloud * 0.72f
            )
        }

        val middle = if (s.isDay) {
            blend(
                Color.rgb(72, 157, 220),
                Color.rgb(67, 87, 105),
                cloud * 0.78f
            )
        } else {
            Color.rgb(10, 33, 65)
        }

        val horizon = if (s.isDay) {
            blend(
                Color.rgb(134, 204, 239),
                Color.rgb(95, 111, 126),
                cloud * 0.70f
            )
        } else {
            Color.rgb(28, 47, 72)
        }

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h * 0.72f,
            intArrayOf(top, middle, horizon),
            floatArrayOf(0f, 0.54f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun drawAtmosphericGlow(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        t: Float,
        w: Float,
        h: Float
    ) {
        val pulse = if (animated) {
            1f + sin(t * 0.48f) * 0.05f
        } else {
            1f
        }

        val x = w * (0.72f + if (animated) sin(t * 0.09f) * 0.025f else 0f)
        val y = h * 0.22f
        val radius = h * (if (s.isDay) 0.34f else 0.26f) * pulse
        val alpha = ((1f - cloud * 0.58f) * 175f)
            .toInt()
            .coerceIn(42, 175)

        val glow = if (s.isDay) {
            Color.argb(alpha, 190, 230, 255)
        } else {
            Color.argb(alpha, 108, 159, 232)
        }

        paint.shader = RadialGradient(
            x,
            y,
            radius,
            glow,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
    }

    private fun drawSkyRibbons(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        t: Float,
        w: Float,
        h: Float
    ) {
        val alpha = if (s.isDay) {
            (55 - cloud * 20f).toInt().coerceAtLeast(24)
        } else {
            72
        }

        drawRibbon(
            canvas,
            h * 0.28f,
            h * 0.052f,
            t * 0.16f,
            w,
            Color.argb(alpha, 76, 205, 255),
            h * 0.026f
        )

        drawRibbon(
            canvas,
            h * 0.38f,
            h * 0.038f,
            t * 0.13f + 2f,
            w,
            Color.argb((alpha * 0.72f).toInt(), 177, 101, 255),
            h * 0.018f
        )
    }

    private fun drawRibbon(
        canvas: Canvas,
        y: Float,
        amplitude: Float,
        phase: Float,
        w: Float,
        color: Int,
        width: Float
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color

        val path = Path()
        path.moveTo(-w * 0.08f, y)

        val shift = sin(phase) * w * 0.05f
        path.cubicTo(
            w * 0.16f + shift,
            y - amplitude,
            w * 0.37f + shift,
            y + amplitude,
            w * 0.55f,
            y
        )
        path.cubicTo(
            w * 0.72f - shift,
            y - amplitude,
            w * 0.91f - shift,
            y + amplitude * 0.55f,
            w * 1.08f,
            y - amplitude * 0.20f
        )
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.FILL
    }

    private fun drawParallaxClouds(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        t: Float,
        w: Float,
        h: Float
    ) {
        if (cloud < 0.08f) return

        val layers = if (cloud > 0.72f) 3 else 2

        repeat(layers) { layer ->
            val speed = 5f + layer * 3.5f
            val opacity = (
                38 +
                    cloud * 48f +
                    layer * 10f
                ).toInt().coerceIn(34, 104)

            val base = if (s.isDay) {
                160 - layer * 14
            } else {
                82 - layer * 8
            }

            repeat(3) { i ->
                val span = w * (0.34f + layer * 0.05f)
                val drift = if (animated) {
                    t * speed
                } else {
                    0f
                }

                val x = (
                    (i * w * 0.43f + layer * w * 0.21f + drift) %
                        (w + span)
                    ) - span * 0.55f

                val y = h * (
                    0.14f +
                        layer * 0.09f +
                        (i % 2) * 0.045f
                    )

                paint.color = Color.argb(
                    opacity,
                    base,
                    base + 8,
                    base + 16
                )

                canvas.drawOval(
                    x,
                    y,
                    x + span,
                    y + h * 0.070f,
                    paint
                )

                canvas.drawOval(
                    x + span * 0.12f,
                    y - h * 0.025f,
                    x + span * 0.47f,
                    y + h * 0.065f,
                    paint
                )

                canvas.drawOval(
                    x + span * 0.47f,
                    y - h * 0.035f,
                    x + span * 0.79f,
                    y + h * 0.060f,
                    paint
                )
            }
        }
    }

    private fun drawLandscape(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        t: Float,
        w: Float,
        h: Float
    ) {
        val horizon = h * 0.67f
        val terrainStrength =
            when (sceneProfile.terrain) {
                TerrainKind.MOUNTAIN -> 1.0f
                TerrainKind.HIGHLAND -> 0.68f
                TerrainKind.ROLLING -> 0.36f
                TerrainKind.FLAT -> 0.05f
            }
        val shift = if (animated) {
            sin(t * 0.05f) * w * 0.004f
        } else {
            0f
        }

        val far = Path().apply {
            moveTo(-20f, horizon)
            cubicTo(
                w * 0.12f + shift,
                horizon - (horizon - h * 0.52f) * terrainStrength,
                w * 0.20f + shift,
                horizon - (horizon - h * 0.54f) * terrainStrength,
                w * 0.34f,
                horizon - (horizon - h * 0.47f) * terrainStrength
            )
            cubicTo(
                w * 0.48f,
                horizon - (horizon - h * 0.55f) * terrainStrength,
                w * 0.60f,
                horizon - (horizon - h * 0.50f) * terrainStrength,
                w * 0.72f,
                horizon - (horizon - h * 0.46f) * terrainStrength
            )
            cubicTo(
                w * 0.84f,
                horizon - (horizon - h * 0.52f) * terrainStrength,
                w * 0.94f,
                horizon - (horizon - h * 0.49f) * terrainStrength,
                w + 20f,
                horizon - (horizon - h * 0.55f) * terrainStrength
            )
            lineTo(w + 20f, horizon)
            close()
        }

        val farBase = if (s.isDay) {
            Color.rgb(50, 83, 107)
        } else {
            Color.rgb(17, 31, 49)
        }

        paint.color = blend(
            farBase,
            Color.rgb(47, 54, 63),
            cloud * 0.45f
        )
        canvas.drawPath(far, paint)

        val near = Path().apply {
            moveTo(-20f, h * 0.74f)
            cubicTo(
                w * 0.16f,
                horizon + (h * 0.59f - horizon) * terrainStrength,
                w * 0.26f,
                horizon,
                w * 0.40f,
                horizon + (h * 0.58f - horizon) * terrainStrength
            )
            cubicTo(
                w * 0.54f,
                horizon + (h * 0.68f - horizon) * terrainStrength,
                w * 0.67f,
                horizon + (h * 0.61f - horizon) * terrainStrength,
                w * 0.78f,
                horizon + (h * 0.57f - horizon) * terrainStrength
            )
            cubicTo(
                w * 0.90f,
                horizon + (h * 0.63f - horizon) * terrainStrength,
                w * 0.96f,
                horizon + (h * 0.60f - horizon) * terrainStrength,
                w + 20f,
                horizon + (h * 0.65f - horizon) * terrainStrength
            )
            lineTo(w + 20f, h * 0.76f)
            lineTo(-20f, h * 0.76f)
            close()
        }

        paint.color = if (s.isDay) {
            Color.rgb(19, 49, 68)
        } else {
            Color.rgb(7, 20, 34)
        }
        canvas.drawPath(near, paint)
    }

    private fun drawWater(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        t: Float,
        w: Float,
        h: Float
    ) {
        val top = h * 0.68f

        val waterTop = if (s.isDay) {
            blend(
                Color.rgb(26, 103, 143),
                Color.rgb(46, 64, 79),
                cloud * 0.78f
            )
        } else {
            Color.rgb(5, 31, 52)
        }

        val waterBottom = if (s.isDay) {
            Color.rgb(3, 31, 52)
        } else {
            Color.rgb(2, 12, 24)
        }

        paint.shader = LinearGradient(
            0f,
            top,
            0f,
            h,
            waterTop,
            waterBottom,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, top, w, h, paint)
        paint.shader = null

        val lightCenter = w * 0.72f
        repeat(10) { i ->
            val y = top + h * (0.025f + i * 0.026f)
            val wave = if (animated) {
                sin(t * 0.9f + i * 0.7f) * 14f
            } else {
                0f
            }

            paint.color = Color.argb(
                (78 - i * 5).coerceAtLeast(22),
                116,
                204,
                244
            )
            paint.strokeWidth = 1.8f
            val half = w * (0.035f + i * 0.010f)

            canvas.drawLine(
                lightCenter - half + wave,
                y,
                lightCenter + half + wave,
                y,
                paint
            )
        }
    }

    private fun drawWindMotes(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        t: Float,
        w: Float,
        h: Float
    ) {
        val wind = s.windSpeedKmh
            .coerceIn(0.0, 60.0)
            .toFloat()

        val count = (8 + wind / 5f).toInt().coerceAtMost(18)
        val rng = Random(1204)

        repeat(count) { i ->
            val y = h * (0.13f + rng.nextFloat() * 0.46f)
            val speed = 9f + wind * 0.55f + i % 4
            val x = (
                rng.nextFloat() * w +
                    if (animated) t * speed else 0f
                ) % (w + 70f) - 35f

            val alpha = (
                22 +
                    (1f - cloud * 0.45f) * 28f
                ).toInt()

            paint.color = Color.argb(
                alpha.coerceIn(18, 55),
                180,
                225,
                250
            )
            paint.strokeWidth = 1.3f
            canvas.drawLine(
                x,
                y,
                x + 18f + wind * 0.18f,
                y - 2f,
                paint
            )
        }
    }

    private fun drawPrecipitation(
        canvas: Canvas,
        s: WeatherSnapshot,
        t: Float,
        w: Float,
        h: Float
    ) {
        val rain = s.rainMm + s.showersMm
        val rainActive =
            rain > 0.0 ||
                s.weatherCode in setOf(
                    51, 53, 55, 61, 63, 65,
                    80, 81, 82, 95, 96, 97, 99
                )

        if (rainActive) {
            val count = (
                54 +
                    rain.coerceAtMost(8.0) * 18
                ).toInt()

            val rng = Random(420)
            val slant = (
                (s.windDirectionDeg % 360.0) / 360.0 -
                    0.5
                ).toFloat() * 18f

            paint.strokeWidth = 2.3f
            paint.color = Color.argb(
                172,
                184,
                224,
                255
            )

            repeat(count) {
                val phase = rng.nextFloat()
                val x = rng.nextFloat() * w
                val y = (
                    (phase * h) +
                        if (animated) t * 310f else 0f
                    ) % (h + 42f) - 20f

                canvas.drawLine(
                    x,
                    y,
                    x + slant,
                    y + 29f,
                    paint
                )
            }
        }

        val snowActive =
            s.snowfallCm > 0.0 ||
                s.weatherCode in setOf(
                    71, 73, 75, 77, 85, 86
                )

        if (snowActive) {
            val rng = Random(99)
            paint.color = Color.argb(
                218,
                245,
                250,
                255
            )

            repeat(82) {
                val x0 = rng.nextFloat() * w
                val y0 = rng.nextFloat() * h
                val y = (
                    y0 +
                        if (animated) t * 72f else 0f
                    ) % h

                val x = x0 +
                    if (animated) {
                        sin(t + y0) * 10f
                    } else {
                        0f
                    }

                canvas.drawCircle(
                    x,
                    y,
                    2.4f + rng.nextFloat() * 3f,
                    paint
                )
            }
        }
    }

    private fun drawFog(
        canvas: Canvas,
        s: WeatherSnapshot,
        t: Float,
        w: Float,
        h: Float
    ) {
        val fogActive =
            s.weatherCode == 45 ||
                s.weatherCode == 48 ||
                s.visibilityM < 8000

        if (!fogActive) return

        repeat(5) { i ->
            val drift = if (animated) {
                sin(t * 0.24f + i) * 48f
            } else {
                0f
            }

            val y = h * (0.34f + i * 0.105f)

            paint.shader = LinearGradient(
                0f,
                y,
                w,
                y,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(
                        54,
                        225,
                        232,
                        235
                    ),
                    Color.TRANSPARENT
                ),
                floatArrayOf(
                    0f,
                    0.5f,
                    1f
                ),
                Shader.TileMode.CLAMP
            )

            canvas.drawRect(
                -70f + drift,
                y,
                w + 70f + drift,
                y + h * 0.065f,
                paint
            )
            paint.shader = null
        }
    }

    private fun drawLightning(
        canvas: Canvas,
        s: WeatherSnapshot,
        t: Float,
        w: Float,
        h: Float
    ) {
        if (
            s.weatherCode !in setOf(
                95, 96, 97, 99
            )
        ) {
            return
        }

        val phase = t % 5.4f
        val flash = !animated || phase < 0.14f
        if (!flash) return

        paint.color = Color.argb(
            if (animated) 145 else 62,
            225,
            240,
            255
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        val bolt = Path().apply {
            moveTo(
                w * 0.66f,
                h * 0.14f
            )
            lineTo(
                w * 0.57f,
                h * 0.41f
            )
            lineTo(
                w * 0.65f,
                h * 0.38f
            )
            lineTo(
                w * 0.51f,
                h * 0.72f
            )
            lineTo(
                w * 0.73f,
                h * 0.33f
            )
            lineTo(
                w * 0.65f,
                h * 0.36f
            )
            close()
        }

        paint.color = Color.argb(
            235,
            255,
            248,
            184
        )
        canvas.drawPath(bolt, paint)
    }

    private fun drawVignette(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            h * 0.44f,
            0f,
            h,
            Color.TRANSPARENT,
            Color.argb(
                194,
                1,
                8,
                18
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            0f,
            h * 0.32f,
            w,
            h,
            paint
        )
        paint.shader = null
    }

    private fun blend(
        a: Int,
        b: Int,
        t: Float
    ): Int {
        val p = t.coerceIn(0f, 1f)

        fun channel(shift: Int): Int {
            val av = (a shr shift) and 255
            val bv = (b shr shift) and 255
            return (
                av +
                    (bv - av) * p
                ).toInt()
        }

        return Color.rgb(
            channel(16),
            channel(8),
            channel(0)
        )
    }
}
