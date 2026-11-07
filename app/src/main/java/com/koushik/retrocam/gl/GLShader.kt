package com.koushik.retrocam.gl

import android.opengl.GLES30
import android.util.Log

object GLShader {
    private const val TAG = "GLShader"

    fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("Could not create new shader of type $type")
        }

        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            Log.e(TAG, "Shader compilation failed:\n$log\nCode:\n$shaderCode")
            throw RuntimeException("Shader compilation failed: $log")
        }

        return shader
    }

    fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentCode)

        val program = GLES30.glCreateProgram()
        if (program == 0) {
            throw RuntimeException("Could not create new GL program")
        }

        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)

        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            Log.e(TAG, "Program linking failed:\n$log")
            throw RuntimeException("Program linking failed: $log")
        }

        // Shaders can be flagged for deletion after linking
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        return program
    }

    fun checkGlError(op: String) {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            val msg = "$op: glError 0x${Integer.toHexString(error)}"
            Log.e(TAG, msg)
            throw RuntimeException(msg)
        }
    }
}

