package com.uglygameface.atmosynq.render

import android.opengl.GLES20
import com.uglygameface.atmosynq.weather.WeatherVisualState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

internal class ParticleSystem {
    private data class Particle(var x: Float, var y: Float, var speed: Float, var phase: Float)

    private val rng = Random(420)
    private val rain = Array(500) { newParticle(rng.nextFloat() * 2f - 1f, rng.nextFloat() * 2.4f - 1.2f, true) }
    private val snow = Array(260) { newParticle(rng.nextFloat() * 2f - 1f, rng.nextFloat() * 2.4f - 1.2f, false) }
    private val rainBuffer = directFloatBuffer(500 * 4)
    private val snowBuffer = directFloatBuffer(260 * 2)

    private val program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    private val aPosition = GLES20.glGetAttribLocation(program, "aPosition")
    private val uColor = GLES20.glGetUniformLocation(program, "uColor")
    private val uPointSize = GLES20.glGetUniformLocation(program, "uPointSize")
    private val uRoundPoints = GLES20.glGetUniformLocation(program, "uRoundPoints")

    fun draw(state: WeatherVisualState, deltaSeconds: Float) {
        if (state.rainIntensity > 0.01f) drawRain(state, deltaSeconds)
        if (state.snowIntensity > 0.01f) drawSnow(state, deltaSeconds)
    }

    private fun drawRain(state: WeatherVisualState, dt: Float) {
        val count = (rain.size * state.rainIntensity.coerceIn(0f, 1f)).toInt().coerceAtLeast(24)
        val directionRad = state.windDirectionDeg / 180f * PI.toFloat()
        // Meteorological direction is where wind comes from; particles move roughly opposite it.
        val drift = -sin(directionRad) * (0.15f + state.windStrength * 0.55f)
        val fallMultiplier = 0.85f + state.rainIntensity * 1.55f
        val length = 0.035f + state.rainIntensity * 0.085f

        rainBuffer.clear()
        for (i in 0 until count) {
            val p = rain[i]
            p.x += drift * dt
            p.y -= p.speed * fallMultiplier * dt
            if (p.y < -1.25f || p.x < -1.3f || p.x > 1.3f) reset(p, true)
            rainBuffer.put(p.x).put(p.y)
            rainBuffer.put(p.x - drift * 0.055f).put(p.y + length)
        }
        rainBuffer.flip()

        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, rainBuffer)
        GLES20.glUniform4f(uColor, 0.72f, 0.86f, 1.0f, 0.32f + state.rainIntensity * 0.38f)
        GLES20.glUniform1f(uPointSize, 1.0f)
        GLES20.glUniform1f(uRoundPoints, 0.0f)
        GLES20.glLineWidth(1.2f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, count * 2)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawSnow(state: WeatherVisualState, dt: Float) {
        val count = (snow.size * state.snowIntensity.coerceIn(0f, 1f)).toInt().coerceAtLeast(18)
        val directionRad = state.windDirectionDeg / 180f * PI.toFloat()
        val drift = -sin(directionRad) * (0.04f + state.windStrength * 0.22f)
        snowBuffer.clear()
        for (i in 0 until count) {
            val p = snow[i]
            p.phase += dt * (0.8f + p.speed)
            p.x += drift * dt + sin(p.phase) * dt * 0.045f
            p.y -= p.speed * (0.15f + state.snowIntensity * 0.18f) * dt
            if (p.y < -1.2f || p.x < -1.3f || p.x > 1.3f) reset(p, false)
            snowBuffer.put(p.x).put(p.y)
        }
        snowBuffer.flip()

        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, snowBuffer)
        GLES20.glUniform4f(uColor, 0.96f, 0.98f, 1.0f, 0.72f)
        GLES20.glUniform1f(uPointSize, 4.5f + state.snowIntensity * 4.0f)
        GLES20.glUniform1f(uRoundPoints, 1.0f)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun release() {
        GLES20.glDeleteProgram(program)
    }

    private fun newParticle(x: Float, y: Float, rainMode: Boolean) = Particle(
        x = x,
        y = y,
        speed = if (rainMode) 0.85f + rng.nextFloat() * 0.75f else 0.55f + rng.nextFloat() * 0.75f,
        phase = rng.nextFloat() * PI.toFloat() * 2f
    )

    private fun reset(p: Particle, rainMode: Boolean) {
        p.x = rng.nextFloat() * 2.4f - 1.2f
        p.y = 1.05f + rng.nextFloat() * 0.35f
        p.speed = if (rainMode) 0.85f + rng.nextFloat() * 0.75f else 0.55f + rng.nextFloat() * 0.75f
        p.phase = rng.nextFloat() * PI.toFloat() * 2f
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            uniform float uPointSize;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                gl_PointSize = uPointSize;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform float uRoundPoints;
            void main() {
                if (uRoundPoints > 0.5) {
                    vec2 p = gl_PointCoord - vec2(0.5);
                    if (dot(p, p) > 0.25) discard;
                }
                gl_FragColor = uColor;
            }
        """

        private fun directFloatBuffer(capacity: Int): FloatBuffer =
            ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        private fun createProgram(vertex: String, fragment: String): Int {
            fun compile(type: Int, source: String): Int {
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val ok = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
                if (ok[0] == 0) {
                    val log = GLES20.glGetShaderInfoLog(shader)
                    GLES20.glDeleteShader(shader)
                    error("Shader compile failed: $log")
                }
                return shader
            }
            val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
            val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, vs)
            GLES20.glAttachShader(p, fs)
            GLES20.glLinkProgram(p)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(p)
                GLES20.glDeleteProgram(p)
                error("Program link failed: $log")
            }
            return p
        }
    }
}
