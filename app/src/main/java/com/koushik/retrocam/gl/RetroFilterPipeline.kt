            // p is in display space (0,0 = bottom-left of what the user sees).
            // Crop, zoom and mirror are applied here, THEN the camera's transform matrix
            // (which carries rotation/flip of the sensor buffer) maps to buffer coordinates.
            vec2 toTex(vec2 p) {
                vec2 tc = (p - 0.5) * uCrop / uZoom;
                if (uMirror > 0.5) {
                    tc.x = -tc.x;
                }
                tc += 0.5;
                return clamp((uTexMatrix * vec4(tc, 0.0, 1.0)).xy, 0.0, 1.0);
            }

            vec3 sampleMain(vec2 p) {
                return texture(uMainTexture, toTex(p)).rgb;
            }

package com.koushik.retrocam.gl

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.koushik.retrocam.data.model.CameraPreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class RetroFilterPipeline {
    private var oesProgram = 0
    private var standardProgram = 0

    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private val vertexArray = floatArrayOf(
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f
    )

    private val texCoordArray = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )

    private val identityMatrix = floatArrayOf(
        1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 1.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f
    )

    // Bitmap textures have row 0 at the top, GL has t=0 at the bottom: flip when sampling.
    private val flipYMatrix = floatArrayOf(
        1.0f,  0.0f, 0.0f, 0.0f,
        0.0f, -1.0f, 0.0f, 0.0f,
        0.0f,  0.0f, 1.0f, 0.0f,
        0.0f,  1.0f, 0.0f, 1.0f
    )

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertexArray.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertexArray)
                position(0)
            }

        texCoordBuffer = ByteBuffer.allocateDirect(texCoordArray.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(texCoordArray)
                position(0)
            }

        initPrograms()
    }

    private fun initPrograms() {
        val vs = """#version 300 es
            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in vec2 aTexCoord;
            out vec2 vPos;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vPos = aTexCoord;
            }
        """.trimIndent()

        val fsOes = getFragmentShaderSource(isOes = true)
        val fsStandard = getFragmentShaderSource(isOes = false)

        oesProgram = GLShader.createProgram(vs, fsOes)
        standardProgram = GLShader.createProgram(vs, fsStandard)
    }

    private fun getFragmentShaderSource(isOes: Boolean): String {
        val header = if (isOes) {
            """#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : enable
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            precision highp sampler2D;
            precision highp sampler3D;
            #else
            precision mediump float;
            precision mediump sampler2D;
            precision mediump sampler3D;
            #endif
            uniform samplerExternalOES uMainTexture;
            """.trimIndent()
        } else {
            """#version 300 es
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            precision highp sampler2D;
            precision highp sampler3D;
            #else
            precision mediump float;
            precision mediump sampler2D;
            precision mediump sampler3D;
            #endif
            uniform sampler2D uMainTexture;
            """.trimIndent()
        }

        val body = """
            in vec2 vPos;
            out vec4 fragColor;

            uniform sampler3D uLut3D;
            uniform vec2 uResolution;
            uniform float uTime;
            uniform float uZoom;
            uniform float uMirror;
            uniform mat4 uTexMatrix;
            uniform vec2 uCrop;

            uniform float uExposure;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uTemperature;
            uniform float uTint;
            uniform float uFade;
            uniform float uGrain;
            uniform float uVignette;
            uniform float uHalation;
            uniform float uAberration;
            uniform float uBloom;
            uniform float uLeak;
            uniform float uScanlines;
            uniform float uDust;
            uniform vec3 uTone;
            uniform float uToneAmt;

            uniform sampler2D uDoubleExpTexture;
            uniform float uDoubleExpIntensity;
            uniform int uHasDoubleExp;

            float hash(vec2 s) {
                return fract(sin(dot(s, vec2(12.9898, 78.233))) * 43758.5453);
            }

            vec3 sRGBToLinear(vec3 c) {
                return pow(c, vec3(2.2));
            }

            vec3 linearTosRGB(vec3 c) {
                return pow(c, vec3(1.0 / 2.2));
            }

            vec3 sampleMain(vec2 uv) {
                vec2 coord = clamp(uv, 0.0, 1.0);
                return texture(uMainTexture, coord).rgb;
            }

            void main() {
                vec2 uv = vPos;

                vec2 c = uv - 0.5;
                float r2 = dot(c, c);
                vec2 d = c * r2 * uAberration * 6.0;
                
                vec3 col = vec3(
                    sampleMain(uv - d).r,
                    sampleMain(uv).g,
                    sampleMain(uv + d).b
                );

                if (uHasDoubleExp > 0) {
                    vec3 expCol = texture(uDoubleExpTexture, vec2(uMirror > 0.5 ? 1.0 - uv.x : uv.x, 1.0 - uv.y)).rgb;
                    col = 1.0 - (1.0 - col) * (1.0 - expCol * uDoubleExpIntensity);
                }

                vec3 linearCol = sRGBToLinear(col);

                if (uHalation > 0.01 || uBloom > 0.01) {
                    vec3 haloAcc = vec3(0.0);
                    float aspect = uResolution.x / max(uResolution.y, 1.0);
                    for (int i = 0; i < 8; i++) {
                        float angle = float(i) * 0.785398;
                        float dist = (i % 2 == 0) ? 0.007 : 0.015;
                        vec2 offset = vec2(cos(angle) / aspect, sin(angle)) * dist;
                        vec3 s = sRGBToLinear(sampleMain(uv + offset));
                        float sLum = dot(s, vec3(0.2126, 0.7152, 0.0722));
                        haloAcc += s * smoothstep(0.55, 1.0, sLum);
                    }
                    haloAcc /= 8.0;

                    vec3 halationColor = haloAcc * vec3(1.0, 0.28, 0.08) * uHalation * 2.5;
                    vec3 bloomColor = haloAcc * uBloom * 1.5;
                    linearCol += halationColor + bloomColor;
                }

                linearCol *= pow(2.0, uExposure * 2.0);

                vec3 gradeCol = linearTosRGB(clamp(linearCol, 0.0, 1.0));
                gradeCol += vec3(uTemperature, uTint * 0.5, -uTemperature);

                vec3 lutCol = texture(uLut3D, clamp(gradeCol, 0.0, 1.0)).rgb;

                float l = dot(lutCol, vec3(0.2126, 0.7152, 0.0722));
                lutCol = mix(vec3(l), lutCol, uSaturation);
                lutCol = (lutCol - 0.5) * uContrast + 0.5;
                lutCol = mix(lutCol, lutCol * uTone, clamp(uToneAmt * (1.0 - l) * 2.5, 0.0, 1.0));
                lutCol = lutCol * (1.0 - uFade) + uFade;

                if (uLeak > 0.01) {
                    float lk = smoothstep(0.45, 1.0, uv.x) * smoothstep(0.15, 0.85, uv.y) * uLeak;
                    vec3 leakColor = vec3(1.0, 0.42, 0.15) * (0.7 + 0.3 * sin(uTime * 0.6));
                    lutCol += leakColor * lk;
                }

                if (uDust > 0.01) {
                    vec2 dustCoord = floor(uv * uResolution * 0.5);
                    float dNoise = hash(dustCoord + floor(uTime * 12.0) * 17.0);
                    if (dNoise > 0.996) {
                        lutCol = mix(lutCol, vec3(0.92, 0.88, 0.82), uDust * 0.8);
                    }
                }

                if (uScanlines > 0.01) {
                    float scan = sin(uv.y * uResolution.y * 1.2 + uTime * 10.0);
                    lutCol *= 1.0 - uScanlines * 0.3 * (0.5 + 0.5 * scan);
                }

                if (uGrain > 0.01) {
                    vec2 grainCoord = floor(uv * uResolution * 0.75);
                    float gNoise = hash(grainCoord + fract(uTime * 43.123) * 91.0) - 0.5;
                    float grainWeight = smoothstep(0.0, 0.4, l) * smoothstep(1.0, 0.6, l) * 1.5 + 0.2;
                    lutCol += gNoise * uGrain * grainWeight;
                }

                lutCol *= 1.0 - uVignette * smoothstep(0.15, 0.85, r2 * 2.2);

                float dither = (hash(gl_FragCoord.xy) - 0.5) / 255.0;
                lutCol += dither;

                fragColor = vec4(clamp(lutCol, 0.0, 1.0), 1.0);
            }
        """.trimIndent()

        return "$header\n$body"
    }

    fun render(
        mainTextureId: Int,
        isOesTexture: Boolean,
        lutManager: LUTManager,
        preset: CameraPreset,
        width: Int,
        height: Int,
        timeSeconds: Float,
        zoom: Float = 1.0f,
        mirror: Boolean = false,
        texMatrix: FloatArray? = null,
        cropX: Float = 1.0f,
        cropY: Float = 1.0f,
        doubleExpTextureId: Int? = null,
        doubleExpIntensity: Float = 0.5f
    ) {
        val program = if (isOesTexture) oesProgram else standardProgram
        GLES30.glUseProgram(program)

        GLES30.glViewport(0, 0, width, height)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)

        // Main texture unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        if (isOesTexture) {
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mainTextureId)
        } else {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mainTextureId)
        }
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uMainTexture"), 0)

        // 3D LUT unit 1
        lutManager.bindLut(preset.lut, GLES30.GL_TEXTURE1)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uLut3D"), 1)

        // Double exposure texture unit 2
        val uHasDoubleExpLoc = GLES30.glGetUniformLocation(program, "uHasDoubleExp")
        if (doubleExpTextureId != null && doubleExpTextureId > 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, doubleExpTextureId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uDoubleExpTexture"), 2)
            GLES30.glUniform1i(uHasDoubleExpLoc, 1)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uDoubleExpIntensity"), doubleExpIntensity)
        } else {
            GLES30.glUniform1i(uHasDoubleExpLoc, 0)
        }

        // Matrices and coordinates
        val mat = texMatrix ?: if (isOesTexture) identityMatrix else flipYMatrix
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uTexMatrix"), 1, false, mat, 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uResolution"), width.toFloat(), height.toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uTime"), timeSeconds)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uZoom"), zoom)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uMirror"), if (mirror) 1.0f else 0.0f)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uCrop"), cropX, cropY)

        // Preset parameters
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uExposure"), preset.exposure)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uContrast"), preset.contrast)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSaturation"), preset.saturation)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uTemperature"), preset.temperature)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uTint"), preset.tint)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uFade"), preset.fade)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uGrain"), preset.grain)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uVignette"), preset.vignette)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uHalation"), preset.halation)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uAberration"), preset.aberration)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uBloom"), preset.bloom)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uLeak"), preset.leak)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uScanlines"), preset.scanlines)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uDust"), preset.dust)

        val toneR = preset.tone.getOrElse(0) { 1.0f }
        val toneG = preset.tone.getOrElse(1) { 1.0f }
        val toneB = preset.tone.getOrElse(2) { 1.0f }
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uTone"), toneR, toneG, toneB)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uToneAmt"), preset.toneAmt)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    fun release() {
        if (oesProgram != 0) {
            GLES30.glDeleteProgram(oesProgram)
            oesProgram = 0
        }
        if (standardProgram != 0) {
            GLES30.glDeleteProgram(standardProgram)
            standardProgram = 0
        }
    }
}

