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
import kotlin.math.sin
import kotlin.random.Random

class WeatherHeroView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var snapshot: WeatherSnapshot? = null
    private var animated = true
    private var startedAt = System.currentTimeMillis()

    fun setWeather(snapshot: WeatherSnapshot?) {
        this.snapshot = snapshot
        startedAt = System.currentTimeMillis()
        invalidate()
    }

    fun setAnimated(animated: Boolean) {
        this.animated = animated
        if (animated) {
            startedAt = System.currentTimeMillis()
            postInvalidateDelayed(FRAME_DELAY_MS)
        } else {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = snapshot ?: return
        val t = if (animated) (System.currentTimeMillis() - startedAt) / 1000f else 0f
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val cloud = (s.cloudCoverPct / 100.0).coerceIn(0.0, 1.0).toFloat()

        drawSky(canvas, s, cloud, w, h)
        drawAtmosphericGlow(canvas, s, cloud, t, w, h)
        drawCloudBanks(canvas, s, cloud, t, w, h)
        drawMountains(canvas, s, cloud, w, h)
        drawLake(canvas, s, cloud, t, w, h)
        drawPrecipitation(canvas, s, t, w, h)
        drawFog(canvas, s, t, w, h)
        drawLightning(canvas, s, t, w, h)
        drawVignette(canvas, w, h)

        if (animated && isShown) postInvalidateDelayed(FRAME_DELAY_MS)
    }

    private fun drawSky(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        w: Float,
        h: Float
    ) {
        val top = if (s.isDay) {
            blend(Color.rgb(19, 85, 154), Color.rgb(35, 47, 67), cloud)
        } else {
            blend(Color.rgb(3, 12, 35), Color.rgb(24, 30, 43), cloud)
        }
        val horizon = if (s.isDay) {
            blend(Color.rgb(116, 180, 226), Color.rgb(86, 99, 115), cloud)
        } else {
            blend(Color.rgb(15, 42, 78), Color.rgb(48, 52, 61), cloud)
        }

        paint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.72f,
            top, horizon, Shader.TileMode.CLAMP
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
        val pulse = if (animated) 1f + sin(t * 0.55f) * 0.035f else 1f
        val glowX = w * 0.73f
        val glowY = h * 0.24f
        val radius = h * (if (s.isDay) 0.31f else 0.22f) * pulse
        val alpha = ((1f - cloud * 0.72f) * 170f).toInt().coerceIn(28, 170)
        val color = if (s.isDay) {
            Color.argb(alpha, 195, 226, 255)
        } else {
            Color.argb(alpha, 122, 162, 225)
        }

        paint.shader = RadialGradient(
            glowX, glowY, radius,
            color, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(glowX, glowY, radius, paint)
        paint.shader = null
    }

    private fun drawCloudBanks(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        t: Float,
        w: Float,
        h: Float
    ) {
        if (cloud < 0.05f) return

        val count = (2 + cloud * 5).toInt().coerceIn(2, 7)
        repeat(count) { i ->
            val speed = 4.5f + i * 1.15f
            val drift = if (animated) t * speed else 0f
            val bandWidth = w * (0.42f + (i % 3) * 0.08f)
            val x = ((i * w * 0.28f + drift) % (w + bandWidth)) - bandWidth * 0.45f
            val y = h * (0.10f + (i % 3) * 0.105f)
            val shade = if (s.isDay) 145 else 78
            val alpha = (82 + cloud * 105).toInt().coerceIn(70, 185)

            paint.color = Color.argb(alpha, shade, shade + 9, shade + 18)
            canvas.drawOval(
                x,
                y,
                x + bandWidth,
                y + h * 0.105f,
                paint
            )
            canvas.drawOval(
                x + bandWidth * 0.18f,
                y - h * 0.035f,
                x + bandWidth * 0.55f,
                y + h * 0.095f,
                paint
            )
            canvas.drawOval(
                x + bandWidth * 0.48f,
                y - h * 0.055f,
                x + bandWidth * 0.82f,
                y + h * 0.085f,
                paint
            )
        }
    }

    private fun drawMountains(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        w: Float,
        h: Float
    ) {
        val horizon = h * 0.62f

        val distant = Path().apply {
            moveTo(0f, horizon)
            lineTo(w * 0.10f, h * 0.48f)
            lineTo(w * 0.18f, h * 0.56f)
            lineTo(w * 0.31f, h * 0.40f)
            lineTo(w * 0.44f, h * 0.56f)
            lineTo(w * 0.58f, h * 0.44f)
            lineTo(w * 0.70f, h * 0.55f)
            lineTo(w * 0.84f, h * 0.41f)
            lineTo(w, h * 0.57f)
            lineTo(w, horizon)
            close()
        }
        val distantBase = if (s.isDay) Color.rgb(53, 83, 110) else Color.rgb(20, 34, 54)
        paint.color = blend(distantBase, Color.rgb(50, 54, 61), cloud * 0.55f)
        canvas.drawPath(distant, paint)

        val near = Path().apply {
            moveTo(0f, h * 0.72f)
            lineTo(w * 0.12f, h * 0.59f)
            lineTo(w * 0.25f, h * 0.67f)
            lineTo(w * 0.40f, h * 0.52f)
            lineTo(w * 0.54f, h * 0.68f)
            lineTo(w * 0.68f, h * 0.58f)
            lineTo(w * 0.78f, h * 0.66f)
            lineTo(w * 0.92f, h * 0.54f)
            lineTo(w, h * 0.63f)
            lineTo(w, h * 0.74f)
            close()
        }
        paint.color = if (s.isDay) Color.rgb(27, 57, 76) else Color.rgb(10, 24, 39)
        canvas.drawPath(near, paint)

        paint.color = Color.argb(if (s.isDay) 90 else 48, 205, 226, 236)
        paint.strokeWidth = 2f
        val snowCaps = listOf(
            floatArrayOf(w * 0.31f, h * 0.40f, w * 0.26f, h * 0.47f, w * 0.35f, h * 0.47f),
            floatArrayOf(w * 0.84f, h * 0.41f, w * 0.79f, h * 0.48f, w * 0.88f, h * 0.48f)
        )
        snowCaps.forEach { p ->
            val cap = Path().apply {
                moveTo(p[0], p[1])
                lineTo(p[2], p[3])
                lineTo(p[4], p[5])
                close()
            }
            canvas.drawPath(cap, paint)
        }
    }

    private fun drawLake(
        canvas: Canvas,
        s: WeatherSnapshot,
        cloud: Float,
        t: Float,
        w: Float,
        h: Float
    ) {
        val lakeTop = h * 0.66f
        val lakeBottom = h

        val lakeTopColor = if (s.isDay) {
            blend(Color.rgb(41, 105, 142), Color.rgb(53, 67, 81), cloud)
        } else {
            Color.rgb(8, 31, 50)
        }
        val lakeBottomColor = if (s.isDay) Color.rgb(7, 42, 65) else Color.rgb(3, 17, 30)

        paint.shader = LinearGradient(
            0f, lakeTop, 0f, lakeBottom,
            lakeTopColor, lakeBottomColor, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, lakeTop, w, lakeBottom, paint)
        paint.shader = null

        val glimmerX = w * 0.73f
        val shift = if (animated) sin(t * 0.65f) * w * 0.02f else 0f
        repeat(7) { i ->
            val y = lakeTop + h * (0.035f + i * 0.035f)
            val half = w * (0.02f + i * 0.012f)
            paint.color = Color.argb((88 - i * 8).coerceAtLeast(20), 149, 210, 244)
            paint.strokeWidth = 2f
            canvas.drawLine(
                glimmerX - half + shift,
                y,
                glimmerX + half + shift,
                y,
                paint
            )
        }

        paint.color = Color.argb(72, 118, 174, 207)
        paint.strokeWidth = 1.5f
        repeat(8) { i ->
            val y = lakeTop + h * (0.03f + i * 0.038f)
            val wave = if (animated) sin(t * 1.1f + i) * 10f else 0f
            canvas.drawLine(w * 0.08f + wave, y, w * 0.38f + wave, y, paint)
            canvas.drawLine(w * 0.57f - wave, y + 5f, w * 0.93f - wave, y + 5f, paint)
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
                s.weatherCode in setOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 97, 99)

        if (rainActive) {
            val count = (48 + rain.coerceAtMost(8.0) * 16).toInt()
            val rng = Random(420)
            val windSlant = ((s.windDirectionDeg % 360.0) / 360.0 - 0.5).toFloat() * 18f
            paint.strokeWidth = 2.2f
            paint.color = Color.argb(155, 184, 220, 255)

            repeat(count) {
                val phase = rng.nextFloat()
                val x = rng.nextFloat() * w
                val y = (((phase * h) + if (animated) t * 290f else 0f) % (h + 42f)) - 20f
                canvas.drawLine(x, y, x + windSlant, y + 27f, paint)
            }
        }

        val snowActive =
            s.snowfallCm > 0.0 ||
                s.weatherCode in setOf(71, 73, 75, 77, 85, 86)

        if (snowActive) {
            val rng = Random(99)
            paint.color = Color.argb(210, 245, 250, 255)

            repeat(76) {
                val x0 = rng.nextFloat() * w
                val y0 = rng.nextFloat() * h
                val y = (y0 + if (animated) t * 68f else 0f) % h
                val x = x0 + if (animated) sin(t + y0) * 9f else 0f
                canvas.drawCircle(x, y, 2.5f + rng.nextFloat() * 2.8f, paint)
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
        if (s.weatherCode != 45 && s.weatherCode != 48 && s.visibilityM >= 8000) return

        repeat(5) { i ->
            val drift = if (animated) sin(t * 0.28f + i) * 34f else 0f
            val y = h * (0.38f + i * 0.105f)
            paint.shader = LinearGradient(
                0f, y, w, y,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(58, 225, 232, 235),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(-60f + drift, y, w + 60f + drift, y + h * 0.07f, paint)
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
        if (s.weatherCode !in setOf(95, 96, 97, 99)) return

        val phase = t % 5.6f
        val flash = !animated || phase < 0.16f
        if (!flash) return

        paint.color = Color.argb(if (animated) 150 else 70, 230, 242, 255)
        canvas.drawRect(0f, 0f, w, h, paint)

        val bolt = Path().apply {
            moveTo(w * 0.65f, h * 0.15f)
            lineTo(w * 0.56f, h * 0.43f)
            lineTo(w * 0.64f, h * 0.40f)
            lineTo(w * 0.50f, h * 0.72f)
            lineTo(w * 0.73f, h * 0.34f)
            lineTo(w * 0.65f, h * 0.37f)
            close()
        }
        paint.color = Color.argb(235, 255, 248, 184)
        canvas.drawPath(bolt, paint)
    }

    private fun drawVignette(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        paint.shader = LinearGradient(
            0f, h * 0.46f, 0f, h,
            Color.TRANSPARENT,
            Color.argb(185, 1, 8, 18),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.35f, w, h, paint)
        paint.shader = null
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val p = t.coerceIn(0f, 1f)

        fun channel(shift: Int): Int {
            val av = (a shr shift) and 255
            val bv = (b shr shift) and 255
            return (av + (bv - av) * p).toInt()
        }

        return Color.rgb(channel(16), channel(8), channel(0))
    }

    companion object {
        private const val FRAME_DELAY_MS = 33L
    }
}
