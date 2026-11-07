package com.koushik.retrocam.data.preset

import android.content.Context
import com.koushik.retrocam.data.model.CameraPreset
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

class PresetRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var cachedPresets: List<CameraPreset> = emptyList()

    init {
        loadPresets()
    }

    @Synchronized
    private fun loadPresets() {
        if (cachedPresets.isNotEmpty()) return

        val list = mutableListOf<CameraPreset>()
        try {
            val assetManager = context.assets
            val indexContent = assetManager.open("presets/index.json").use {
                InputStreamReader(it).readText()
            }
            val ids: List<String> = json.decodeFromString(indexContent)

            for (id in ids) {
                try {
                    val presetContent = assetManager.open("presets/$id.json").use {
                        InputStreamReader(it).readText()
                    }
                    val preset: CameraPreset = json.decodeFromString(presetContent)
                    list.add(preset)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isEmpty()) {
            list.add(CameraPreset(id = "cpm35", name = "CPM35", subtitle = "35mm Color Negative"))
        }

        cachedPresets = list
    }

    fun getAllPresets(): List<CameraPreset> {
        if (cachedPresets.isEmpty()) {
            loadPresets()
        }
        return cachedPresets
    }

    fun getPresetById(id: String): CameraPreset {
        return getAllPresets().find { it.id == id } ?: getAllPresets().first()
    }

    val defaultPreset: CameraPreset
        get() = getAllPresets().first()
}

