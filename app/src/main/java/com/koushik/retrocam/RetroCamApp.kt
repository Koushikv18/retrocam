package com.koushik.retrocam

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.koushik.retrocam.data.preset.PresetRepository
import com.koushik.retrocam.data.settings.SettingsRepository

class RetroCamApp : Application(), ImageLoaderFactory {
    lateinit var presetRepository: PresetRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        presetRepository = PresetRepository(this)
        settingsRepository = SettingsRepository(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: RetroCamApp
            private set
    }
}

