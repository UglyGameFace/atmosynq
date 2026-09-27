package com.uglygameface.atmosynq.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
            postInvalidateOnAnimation()
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
        val top = if (s.isDay) blend(Color.rgb(39,132,220), Color.rgb(57,72,92), cloud) else blend(Color.rgb(4,14,38), Color.rgb(34,39,52), cloud)
        val bottom = if (s.isDay) blend(Color.rgb(137,194,235), Color.rgb(89,101,116), cloud) else blend(Color.rgb(14,34,66), Color.rgb(44,49,60), cloud)
        paint.shader = LinearGradient(0f,0f,0f,h,top,bottom,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,paint)
        paint.shader = null

        if (s.cloudCoverPct < 88) {
            val pulse = if (animated) 1f + sin(t * 1.2f) * 0.035f else 1f
            paint.color = if (s.isDay) Color.argb(210,255,231,145) else Color.argb(205,226,236,255)
            canvas.drawCircle(w*0.80f,h*0.24f,h*0.10f*pulse,paint)
        }

        val clouds = (2 + cloud * 6).toInt()
        repeat(clouds) { i ->
            val base = i * 137f
            val drift = if (animated) (t * (8f + i*1.7f)) else 0f
            val x = ((base + drift) % (w + 180f)) - 90f
            val y = h * (0.18f + (i % 3) * 0.12f)
            paint.color = Color.argb((115 + cloud*100).toInt(), 128, 139, 151)
            canvas.drawOval(x-70f,y-22f,x+90f,y+34f,paint)
            canvas.drawCircle(x-25f,y-18f,36f,paint)
            canvas.drawCircle(x+28f,y-30f,46f,paint)
        }

        val rain = s.rainMm + s.showersMm
        if (rain > 0.0 || s.weatherCode in setOf(51,53,55,61,63,65,80,81,82,95,96,97,99)) {
            val count = (50 + rain.coerceAtMost(8.0)*18).toInt()
            val rng = Random(420)
            paint.strokeWidth = 3f
            paint.color = Color.argb(180,184,220,255)
            repeat(count) {
                val phase = rng.nextFloat()
                val x = rng.nextFloat()*w
                val y = (((phase*h) + if(animated) t*260f else 0f) % (h+45f))-20f
                canvas.drawLine(x,y,x-10f,y+30f,paint)
            }
        }

        if (s.snowfallCm > 0.0 || s.weatherCode in setOf(71,73,75,77,85,86)) {
            val rng = Random(99)
            paint.color = Color.argb(220,245,250,255)
            repeat(85) {
                val x0 = rng.nextFloat()*w
                val y0 = rng.nextFloat()*h
                val y = (y0 + if(animated) t*75f else 0f) % h
                val x = x0 + if(animated) sin(t + y0)*10f else 0f
                canvas.drawCircle(x,y,3.5f + rng.nextFloat()*3f,paint)
            }
        }

        if (s.weatherCode == 45 || s.weatherCode == 48 || s.visibilityM < 8000) {
            paint.color = Color.argb(65,225,232,235)
            repeat(4) { i ->
                val drift = if(animated) sin(t*0.35f+i)*25f else 0f
                val y = h*(0.42f+i*0.12f)
                canvas.drawRoundRect(-40f+drift,y,w+40f+drift,y+30f,30f,30f,paint)
            }
        }

        if (s.weatherCode in setOf(95,96,97,99)) {
            val flash = !animated || ((t % 5.2f) in 0.0f..0.18f)
            if (flash) {
                val p=Path().apply {
                    moveTo(w*0.62f,h*0.28f); lineTo(w*0.53f,h*0.55f); lineTo(w*0.62f,h*0.52f)
                    lineTo(w*0.48f,h*0.82f); lineTo(w*0.72f,h*0.46f); lineTo(w*0.63f,h*0.48f); close()
                }
                paint.color=Color.argb(235,255,246,155)
                canvas.drawPath(p,paint)
            }
        }

        paint.shader = LinearGradient(0f,h*0.45f,0f,h,Color.TRANSPARENT,Color.argb(185,3,9,18),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,h*0.35f,w,h,paint)
        paint.shader=null

        if (animated && isShown) postInvalidateOnAnimation()
    }

    private fun blend(a:Int,b:Int,t:Float):Int {
        val p=t.coerceIn(0f,1f)
        fun c(shift:Int):Int {
            val av=(a shr shift) and 255
            val bv=(b shr shift) and 255
            return (av+(bv-av)*p).toInt()
        }
        return Color.rgb(c(16),c(8),c(0))
    }
}
