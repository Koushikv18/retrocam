package com.koushik.retrocam

import android.app.Application
import com.koushik.retrocam.data.preset.PresetRepository
import com.koushik.retrocam.data.settings.SettingsRepository

class RetroCamApp : Application() {
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

    companion object {
        lateinit var instance: RetroCamApp
            private set
    }
}

