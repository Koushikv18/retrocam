package com.koushik.retrocam

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koushik.retrocam.data.preset.PresetRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraSmokeTest {

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.koushik.retrocam", appContext.packageName)
    }

    @Test
    fun testPresetRepositoryLoadsInContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = PresetRepository(appContext)
        val presets = repo.getAllPresets()

        assertTrue("Should have at least 20 presets loaded in Android context", presets.size >= 20)
        assertNotNull(repo.getPresetById("cpm35"))
        assertNotNull(repo.getPresetById("cinestill800"))
        assertNotNull(repo.getPresetById("gold200"))
    }
}

