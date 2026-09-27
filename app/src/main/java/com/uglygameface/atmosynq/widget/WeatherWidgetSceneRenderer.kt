package com.uglygameface.atmosynq.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.uglygameface.atmosynq.weather.WeatherSnapshot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object WeatherWidgetSceneRenderer {
    private const val WIDTH = 720
    private const val HEIGHT = 420
    private const val OUTPUT_WIDTH = 480
    private const val OUTPUT_HEIGHT = 280

    fun render(snapshot: WeatherSnapshot): Bitmap {
        val bitmap = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(OUTPUT_WIDTH / WIDTH.toFloat(), OUTPUT_HEIGHT / HEIGHT.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        drawSky(canvas, paint, snapshot)
        drawSunOrMoon(canvas, paint, snapshot)
        drawClouds(canvas, paint, snapshot)

        when {
            isSnow(snapshot.weatherCode) || snapshot.snowfallCm > 0.0 -> drawSnow(canvas, paint, snapshot)
            isRain(snapshot.weatherCode) || snapshot.rainMm + snapshot.showersMm > 0.0 -> drawRain(canvas, paint, snapshot)
        }

        if (isFog(snapshot.weatherCode) || snapshot.visibilityM < 8_000.0) drawFog(canvas, paint, snapshot)
        if (isThunder(snapshot.weatherCode)) drawLightning(canvas, paint)

        drawVignette(canvas, paint)
        return bitmap
    }

    private fun drawSky(canvas: Canvas, paint: Paint, snapshot: WeatherSnapshot) {
        val cloud = (snapshot.cloudCoverPct / 100.0).coerceIn(0.0, 1.0).toFloat()
        val top = if (snapshot.isDay) {
            blend(Color.rgb(49, 147, 235), Color.rgb(78, 92, 112), cloud * 0.75f)
        } else {
            blend(Color.rgb(8, 18, 46), Color.rgb(38, 43, 57), cloud * 0.8f)
        }
        val bottom = if (snapshot.isDay) {
            blend(Color.rgb(164, 216, 250), Color.rgb(112, 122, 134), cloud * 0.7f)
        } else {
            blend(Color.rgb(23, 43, 77), Color.rgb(54, 59, 70), cloud * 0.8f)
        }
        paint.shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null
    }

    private fun drawSunOrMoon(canvas: Canvas, paint: Paint, snapshot: WeatherSnapshot) {
        if (snapshot.cloudCoverPct >= 88.0) return
        val alpha = (220 - snapshot.cloudCoverPct * 1.7).toInt().coerceIn(40, 220)
        paint.color = if (snapshot.isDay) {
            Color.argb(alpha, 255, 232, 151)
        } else {
            Color.argb(alpha, 225, 235, 255)
        }
        canvas.drawCircle(590f, 92f, if (snapshot.isDay) 42f else 31f, paint)
    }

    private fun drawClouds(canvas: Canvas, paint: Paint, snapshot: WeatherSnapshot) {
        val coverage = (snapshot.cloudCoverPct / 100.0).coerceIn(0.0, 1.0)
        if (coverage < 0.08) return

        val seed = (snapshot.fetchedAtEpochMs / 3_600_000L).toInt() xor snapshot.weatherCode
        val rng = Random(seed)
        val count = (2 + coverage * 8).toInt().coerceIn(2, 10)
        val darkness = if (isThunder(snapshot.weatherCode)) 78 else (128 + (1.0 - coverage) * 70).toInt()

        repeat(count) { index ->
            val x = 70f + rng.nextFloat() * 610f
            val y = 50f + rng.nextFloat() * 150f + index % 2 * 18f
            val scale = 0.55f + rng.nextFloat() * 0.85f
            paint.color = Color.argb(
                (135 + coverage * 90).toInt().coerceIn(120, 230),
                darkness, darkness + 4, darkness + 10
            )
            drawCloud(canvas, paint, x, y, scale)
        }
    }

    private fun drawCloud(canvas: Canvas, paint: Paint, x: Float, y: Float, scale: Float) {
        canvas.drawOval(x - 86f * scale, y, x + 92f * scale, y + 48f * scale, paint)
        canvas.drawCircle(x - 42f * scale, y + 2f * scale, 38f * scale, paint)
        canvas.drawCircle(x + 4f * scale, y - 14f * scale, 51f * scale, paint)
        canvas.drawCircle(x + 51f * scale, y + 2f * scale, 36f * scale, paint)
    }

    private fun drawRain(canvas: Canvas, paint: Paint, snapshot: WeatherSnapshot) {
        val intensity = rainIntensity(snapshot)
        val count = (60 + intensity * 150).toInt()
        val seed = snapshot.weatherCode * 97 + (snapshot.fetchedAtEpochMs / 3_600_000L).toInt()
        val rng = Random(seed)
        val wind = ((snapshot.windDirectionDeg % 360.0) / 360.0 - 0.5).toFloat() * 26f

        paint.strokeWidth = 3.2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb((125 + intensity * 100).toInt(), 184, 220, 255)

        repeat(count) {
            val x = rng.nextFloat() * WIDTH
            val y = 145f + rng.nextFloat() * (HEIGHT - 120f)
            val length = 18f + rng.nextFloat() * 26f + intensity * 18f
            canvas.drawLine(x, y, x + wind, y + length, paint)
        }
    }

    private fun drawSnow(canvas: Canvas, paint: Paint, snapshot: WeatherSnapshot) {
        val intensity = snowIntensity(snapshot)
        val count = (45 + intensity * 115).toInt()
        val seed = snapshot.weatherCode * 113 + (snapshot.fetchedAtEpochMs / 3_600_000L).toInt()
        val rng = Random(seed)
        paint.color = Color.argb((170 + intensity * 80).toInt(), 245, 250, 255)

        repeat(count) {
            val radius = 2.5f + rng.nextFloat() * (4.5f + intensity * 3f)
            val x = rng.nextFloat() * WIDTH
            val y = 115f + rng.nextFloat() * (HEIGHT - 95f)
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun drawFog(canvas: Canvas, paint: Paint, snapshot: WeatherSnapshot) {
        val visibilityFactor = when {
            snapshot.visibilityM <= 500 -> 1f
            snapshot.visibilityM >= 10_000 -> 0.2f
            else -> (1.0 - (snapshot.visibilityM - 500.0) / 9_500.0).toFloat()
        }
        paint.color = Color.argb((48 + visibilityFactor * 90).toInt(), 225, 232, 235)
        repeat(5) { index ->
            val y = 180f + index * 50f
            canvas.drawRoundRect(-40f + index * 20f, y, WIDTH + 40f, y + 34f, 40f, 40f, paint)
        }
    }

    private fun drawLightning(canvas: Canvas, paint: Paint) {
        val path = Path().apply {
            moveTo(390f, 125f)
            lineTo(345f, 220f)
            lineTo(390f, 214f)
            lineTo(330f, 330f)
            lineTo(455f, 190f)
            lineTo(407f, 196f)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(235, 255, 246, 151)
        paint.setShadowLayer(26f, 0f, 0f, Color.WHITE)
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
    }

    private fun drawVignette(canvas: Canvas, paint: Paint) {
        paint.shader = LinearGradient(
            0f, HEIGHT * 0.45f, 0f, HEIGHT.toFloat(),
            Color.TRANSPARENT, Color.argb(155, 0, 0, 0), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, HEIGHT * 0.35f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null
    }

    private fun rainIntensity(snapshot: WeatherSnapshot): Float {
        val measured = (snapshot.rainMm + snapshot.showersMm).coerceAtLeast(0.0)
        val fromRate = min(1.0, measured / 8.0).toFloat()
        val fromCode = when (snapshot.weatherCode) {
            51, 56, 61, 80 -> 0.25f
            53, 63, 66, 81, 95 -> 0.5f
            55, 57, 65, 67, 82, 96, 97, 99 -> 0.9f
            else -> 0f
        }
        return max(fromRate, fromCode)
    }

    private fun snowIntensity(snapshot: WeatherSnapshot): Float {
        val fromRate = min(1.0, snapshot.snowfallCm.coerceAtLeast(0.0) / 2.5).toFloat()
        val fromCode = when (snapshot.weatherCode) {
            71, 77, 85 -> 0.3f
            73 -> 0.55f
            75, 86 -> 0.9f
            else -> 0f
        }
        return max(fromRate, fromCode)
    }

    private fun isRain(code: Int) = code in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 97, 99)
    private fun isSnow(code: Int) = code in setOf(71, 73, 75, 77, 85, 86)
    private fun isFog(code: Int) = code == 45 || code == 48
    private fun isThunder(code: Int) = code in setOf(95, 96, 97, 99)

    private fun blend(a: Int, b: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val av = (a shr shift) and 0xff
            val bv = (b shr shift) and 0xff
            return (av + (bv - av) * clamped).toInt().coerceIn(0, 255)
        }
        return Color.rgb(channel(16), channel(8), channel(0))
    }
}
