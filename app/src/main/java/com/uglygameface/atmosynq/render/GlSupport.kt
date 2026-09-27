package com.uglygameface.atmosynq.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal object GlSupport {
    const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
        }
    """

    const val FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uVideo;
        uniform float uHasVideo;
        uniform float uDaylight;
        uniform float uSunsetWarmth;
        uniform float uCloudiness;
        uniform float uFog;
        uniform float uLightning;

        void main() {
            vec4 src;
            if (uHasVideo > 0.5) {
                src = texture2D(uVideo, vTexCoord);
            } else {
                // Built-in neutral ocean/sky fallback for the open-source engine.
                // It keeps the weather renderer functional without redistributing scene art.
                float horizon = smoothstep(0.42, 0.58, vTexCoord.y);
                vec3 deepOcean = vec3(0.025, 0.16, 0.30);
                vec3 clearSky = vec3(0.12, 0.43, 0.72);
                vec3 baseFallback = mix(deepOcean, clearSky, horizon);
                float glow = 0.10 * (1.0 - distance(vTexCoord, vec2(0.72, 0.72)));
                src = vec4(baseFallback + vec3(max(glow, 0.0)), 1.0);
            }
            vec3 base = src.rgb;

            float blueDominance = base.b - max(base.r, base.g);
            float blueMask = smoothstep(0.02, 0.22, blueDominance);
            float warmMask = smoothstep(0.08, 0.38, base.r - base.b) * smoothstep(0.22, 0.72, base.g);

            vec3 dayGrade = pow(max(base, vec3(0.0)), vec3(0.86));
            dayGrade *= vec3(1.15, 1.19, 1.23);
            vec3 clearBlue = vec3(0.16, 0.55, 0.93);
            dayGrade = mix(dayGrade, clearBlue * (0.72 + base.b * 0.58), blueMask * 0.38);
            dayGrade = mix(dayGrade, dayGrade * vec3(0.93, 0.89, 0.78), warmMask * 0.22);

            vec3 color = mix(base, dayGrade, uDaylight);

            float gray = dot(color, vec3(0.299, 0.587, 0.114));
            vec3 overcast = mix(color, vec3(gray), 0.22) * 0.88;
            color = mix(color, overcast, uCloudiness * 0.55);

            vec3 sunsetTint = vec3(1.0, 0.48, 0.22);
            color = mix(color, color * 0.72 + sunsetTint * 0.32, uSunsetWarmth * 0.48);

            vec3 fogColor = mix(vec3(0.60, 0.69, 0.76), vec3(0.76, 0.80, 0.82), uDaylight);
            color = mix(color, fogColor, uFog * 0.54);
            color = mix(color, vec3(1.0), uLightning * 0.72);

            gl_FragColor = vec4(clamp(color, 0.0, 1.0), src.a);
        }
    """

    fun directFloatBuffer(capacity: Int): FloatBuffer =
        ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun createProgram(vertex: String, fragment: String): Int {
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
