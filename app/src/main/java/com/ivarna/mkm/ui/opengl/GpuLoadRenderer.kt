package com.ivarna.mkm.ui.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GpuLoadRenderer : GLSurfaceView.Renderer {

    private val vertexShaderCode = """
        attribute vec4 vPosition;
        void main() {
            gl_Position = vPosition;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec2 uResolution;
        uniform float uTime;
        
        // Simple heavy 2D noise / fractal
        float random (in vec2 st) {
            return fract(sin(dot(st.xy, vec2(12.9898,78.233))) * 43758.5453123);
        }

        // Based on Morgan McGuire @morgan3d
        // https://www.shadertoy.com/view/4dS3Wd
        float noise (in vec2 st) {
            vec2 i = floor(st);
            vec2 f = fract(st);

            // Four corners in 2D of a tile
            float a = random(i);
            float b = random(i + vec2(1.0, 0.0));
            float c = random(i + vec2(0.0, 1.0));
            float d = random(i + vec2(1.0, 1.0));

            vec2 u = f * f * (3.0 - 2.0 * f);

            return mix(a, b, u.x) +
                    (c - a)* u.y * (1.0 - u.x) +
                    (d - b) * u.x * u.y;
        }

        #define OCTAVES 15
        float fbm (in vec2 st) {
            // Initial values
            float value = 0.0;
            float amplitude = .5;
            float frequency = 0.;
            //
            // Loop of octaves
            for (int i = 0; i < OCTAVES; i++) {
                value += amplitude * noise(st);
                st *= 2.;
                amplitude *= .5;
            }
            return value;
        }

        void main() {
            vec2 st = gl_FragCoord.xy/uResolution.xy;
            st.x *= uResolution.x/uResolution.y;

            vec3 color = vec3(0.0);
            
            // Expensive Noise calc
            color += fbm(st * 3.0 + uTime * 0.5);
            
            // Extra sin math to burn more cycles
            for(int i=0; i<30; i++) {
                 color.r += 0.01 * sin(float(i)*0.1 + st.x * 20.0 + uTime);
                 color.g += 0.01 * cos(float(i)*0.1 + st.y * 20.0 - uTime);
            }

            gl_FragColor = vec4(color, 1.0);
        }
    """.trimIndent()

    private var mProgram: Int = 0
    private var vPMatrixHandle: Int = 0
    private var uResolutionHandle: Int = 0
    private var uTimeHandle: Int = 0
    
    private var width = 0
    private var height = 0
    private var startTime = 0L

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(squareCoords.size * 4).run {
        order(ByteOrder.nativeOrder())
        asFloatBuffer().apply {
            put(squareCoords)
            position(0)
        }
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }
        
        startTime = System.currentTimeMillis()
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        this.width = width
        this.height = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(mProgram)

        val positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)

        uResolutionHandle = GLES20.glGetUniformLocation(mProgram, "uResolution")
        GLES20.glUniform2f(uResolutionHandle, width.toFloat(), height.toFloat())
        
        uTimeHandle = GLES20.glGetUniformLocation(mProgram, "uTime")
        val time = (System.currentTimeMillis() - startTime) / 1000f
        GLES20.glUniform1f(uTimeHandle, time)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    companion object {
        const val COORDS_PER_VERTEX = 2
        var squareCoords = floatArrayOf(
            -1.0f,  1.0f,
            -1.0f, -1.0f,
             1.0f,  1.0f,
             1.0f, -1.0f
        )
        const val vertexStride = COORDS_PER_VERTEX * 4
        val vertexCount = squareCoords.size / COORDS_PER_VERTEX
    }
}
