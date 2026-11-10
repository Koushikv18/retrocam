package com.koushik.retrocam.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.koushik.retrocam.data.model.CameraSettings
import com.koushik.retrocam.data.model.SaveFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "retrocam_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val PRESET_ID = stringPreferencesKey("preset_id")
        val DATE_STAMP = booleanPreferencesKey("date_stamp")
        val GRID = booleanPreferencesKey("grid")
        val LEVEL = booleanPreferencesKey("level")
        val TIMER = intPreferencesKey("timer")
        val MIRROR_FRONT = booleanPreferencesKey("mirror_front")
        val SHUTTER_SOUND = booleanPreferencesKey("shutter_sound")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SAVE_FORMAT = stringPreferencesKey("save_format")
        val FRAME_ENABLED = booleanPreferencesKey("frame_enabled")
        val PRO_COACH = booleanPreferencesKey("pro_coach")
    }

    val settingsFlow: Flow<CameraSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val presetId = prefs[Keys.PRESET_ID] ?: "cpm35"
            val dateStamp = prefs[Keys.DATE_STAMP] ?: true
            val grid = prefs[Keys.GRID] ?: false
            val level = prefs[Keys.LEVEL] ?: false
            val timer = prefs[Keys.TIMER] ?: 0
            val mirror = prefs[Keys.MIRROR_FRONT] ?: true
            val sound = prefs[Keys.SHUTTER_SOUND] ?: true
            val haptics = prefs[Keys.HAPTICS] ?: true
            val formatStr = prefs[Keys.SAVE_FORMAT] ?: SaveFormat.JPEG_HIGH.name
            val format = try { SaveFormat.valueOf(formatStr) } catch (e: Exception) { SaveFormat.JPEG_HIGH }
            val frameEnabled = prefs[Keys.FRAME_ENABLED] ?: true
            val proCoach = prefs[Keys.PRO_COACH] ?: true

            CameraSettings(
                selectedPresetId = presetId,
                dateStampEnabled = dateStamp,
                gridEnabled = grid,
                levelEnabled = level,
                timerSeconds = timer,
                mirrorFrontCamera = mirror,
                shutterSound = sound,
                hapticFeedback = haptics,
                saveFormat = format,
                frameEnabled = frameEnabled,
                proCoach = proCoach
            )
        }

    suspend fun updatePresetId(id: String) {
        context.dataStore.edit { it[Keys.PRESET_ID] = id }
    }

    suspend fun toggleDateStamp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DATE_STAMP] = enabled }
    }

    suspend fun toggleGrid(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GRID] = enabled }
    }

    suspend fun toggleLevel(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LEVEL] = enabled }
    }

    suspend fun setTimerSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.TIMER] = seconds }
    }

    suspend fun toggleMirrorFront(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MIRROR_FRONT] = enabled }
    }

    suspend fun toggleShutterSound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHUTTER_SOUND] = enabled }
    }

    suspend fun toggleHaptics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setSaveFormat(format: SaveFormat) {
        context.dataStore.edit { it[Keys.SAVE_FORMAT] = format.name }
    }

    suspend fun toggleFrame(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FRAME_ENABLED] = enabled }
    }

    suspend fun toggleProCoach(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PRO_COACH] = enabled }
    }
}
