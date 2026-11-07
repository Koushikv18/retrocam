package com.koushik.retrocam

import com.koushik.retrocam.data.model.CameraPreset
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PresetParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testAllPresetFilesExistAndParse() {
        val presetsDir = File("src/main/assets/presets")
        val indexFile = File(presetsDir, "index.json")

        assertTrue("index.json should exist", indexFile.exists())
        val indexContent = indexFile.readText()
        val ids: List<String> = json.decodeFromString(indexContent)

        assertTrue("Should have at least 20 presets", ids.size >= 20)

        for (id in ids) {
            val presetFile = File(presetsDir, "$id.json")
            assertTrue("Preset file $id.json should exist", presetFile.exists())

            val content = presetFile.readText()
            val preset: CameraPreset = json.decodeFromString(content)

            assertEquals("Preset ID should match filename", id, preset.id)
            assertTrue("Preset name should not be blank", preset.name.isNotBlank())
            assertTrue("Contrast should be positive", preset.contrast > 0f)
            assertTrue("Aspect ratio width should be positive", preset.aspectWidth > 0)
            assertTrue("Aspect ratio height should be positive", preset.aspectHeight > 0)
            assertTrue("Aspect ratio float should be > 0", preset.aspectRatioFloat > 0f)
            assertTrue("Tone should have 3 components", preset.tone.size == 3)
            assertTrue("Grain should be >= 0", preset.grain >= 0f)
        }
    }

    @Test
    fun testAspectRatioCalculations() {
        val preset32 = CameraPreset(id = "test", name = "Test", aspectRatio = "3:2")
        assertEquals(3, preset32.aspectWidth)
        assertEquals(2, preset32.aspectHeight)
        assertEquals(1.5f, preset32.aspectRatioFloat, 0.001f)

        val preset11 = CameraPreset(id = "test", name = "Test", aspectRatio = "1:1")
        assertEquals(1, preset11.aspectWidth)
        assertEquals(1, preset11.aspectHeight)
        assertEquals(1.0f, preset11.aspectRatioFloat, 0.001f)

        val preset43 = CameraPreset(id = "test", name = "Test", aspectRatio = "4:3")
        assertEquals(4, preset43.aspectWidth)
        assertEquals(3, preset43.aspectHeight)
        assertEquals(1.333f, preset43.aspectRatioFloat, 0.001f)
    }
}

