package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ExportQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "noorcut_studio_prefs")

data class SavedStudioDefaults(
    val exportQuality: ExportQuality = ExportQuality.MODEST_720P,
    val slowedAndReverb: Boolean = true,
    val slowedSpeed: Float = 0.86f,
    val reverbWetLevel: Float = 0.45f,
    val originalVolume: Float = 0.85f,
    val bgMusicVolume: Float = 0.42f
)

class StudioPreferences(private val context: Context) {
    private object Keys {
        val EXPORT_QUALITY = stringPreferencesKey("export_quality")
        val SLOWED_AND_REVERB = booleanPreferencesKey("slowed_and_reverb")
        val SLOWED_SPEED = floatPreferencesKey("slowed_speed")
        val REVERB_WET = floatPreferencesKey("reverb_wet")
        val ORIG_VOL = floatPreferencesKey("orig_vol")
        val BG_VOL = floatPreferencesKey("bg_vol")
    }

    val defaultsFlow: Flow<SavedStudioDefaults> = context.studioDataStore.data.map { prefs ->
        val quality = runCatching {
            ExportQuality.valueOf(prefs[Keys.EXPORT_QUALITY] ?: ExportQuality.MODEST_720P.name)
        }.getOrDefault(ExportQuality.MODEST_720P)

        SavedStudioDefaults(
            exportQuality = quality,
            slowedAndReverb = prefs[Keys.SLOWED_AND_REVERB] ?: true,
            slowedSpeed = prefs[Keys.SLOWED_SPEED] ?: 0.86f,
            reverbWetLevel = prefs[Keys.REVERB_WET] ?: 0.45f,
            originalVolume = prefs[Keys.ORIG_VOL] ?: 0.85f,
            bgMusicVolume = prefs[Keys.BG_VOL] ?: 0.42f
        )
    }

    suspend fun saveExportQuality(quality: ExportQuality) {
        context.studioDataStore.edit { it[Keys.EXPORT_QUALITY] = quality.name }
    }

    suspend fun saveAudioDefaults(
        slowedAndReverb: Boolean,
        slowedSpeed: Float,
        reverbWet: Float,
        origVol: Float,
        bgVol: Float
    ) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.SLOWED_AND_REVERB] = slowedAndReverb
            prefs[Keys.SLOWED_SPEED] = slowedSpeed
            prefs[Keys.REVERB_WET] = reverbWet
            prefs[Keys.ORIG_VOL] = origVol
            prefs[Keys.BG_VOL] = bgVol
        }
    }
}
