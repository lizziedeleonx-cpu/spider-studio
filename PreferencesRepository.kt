package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.RulerUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.spiderDataStore: DataStore<Preferences> by preferencesDataStore(name = "spider_studio_prefs")

data class StudioPreferences(
    val showWelcomeOnLaunch: Boolean = true,
    val autoSaveEnabled: Boolean = true,
    val autoSaveIntervalSec: Int = 15,
    val defaultShowGrid: Boolean = true,
    val defaultGridSizePx: Int = 40,
    val defaultShowRulers: Boolean = true,
    val defaultSnapEnabled: Boolean = true,
    val defaultRulerUnit: RulerUnit = RulerUnit.PX,
    val stylusPressureEnabled: Boolean = true,
    val defaultExportScale: Int = 2,
    val lastOpenedProjectId: String? = null,
    val colorHistory: List<Long> = listOf(
        0xFF8B5CF6, 0xFFA78BFA, 0xFF38BDF8, 0xFFF43F5E,
        0xFF22C55E, 0xFFF59E0B, 0xFFF4F4F8, 0xFF14141C
    )
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SHOW_WELCOME = booleanPreferencesKey("show_welcome")
        val AUTO_SAVE_ENABLED = booleanPreferencesKey("auto_save_enabled")
        val AUTO_SAVE_SEC = intPreferencesKey("auto_save_sec")
        val DEFAULT_GRID = booleanPreferencesKey("default_grid")
        val DEFAULT_GRID_SIZE = intPreferencesKey("default_grid_size")
        val DEFAULT_RULERS = booleanPreferencesKey("default_rulers")
        val DEFAULT_SNAP = booleanPreferencesKey("default_snap")
        val RULER_UNIT = stringPreferencesKey("ruler_unit")
        val STYLUS_PRESSURE = booleanPreferencesKey("stylus_pressure")
        val EXPORT_SCALE = intPreferencesKey("export_scale")
        val LAST_PROJECT_ID = stringPreferencesKey("last_project_id")
        val COLOR_HISTORY = stringPreferencesKey("color_history")
    }

    val preferencesFlow: Flow<StudioPreferences> = context.spiderDataStore.data.map { prefs ->
        val historyCsv = prefs[Keys.COLOR_HISTORY]
        val history = if (historyCsv.isNullOrBlank()) {
            StudioPreferences().colorHistory
        } else {
            historyCsv.split(",").mapNotNull { it.trim().toLongOrNull(16) }
                .ifEmpty { StudioPreferences().colorHistory }
        }
        StudioPreferences(
            showWelcomeOnLaunch = prefs[Keys.SHOW_WELCOME] ?: true,
            autoSaveEnabled = prefs[Keys.AUTO_SAVE_ENABLED] ?: true,
            autoSaveIntervalSec = prefs[Keys.AUTO_SAVE_SEC] ?: 15,
            defaultShowGrid = prefs[Keys.DEFAULT_GRID] ?: true,
            defaultGridSizePx = prefs[Keys.DEFAULT_GRID_SIZE] ?: 40,
            defaultShowRulers = prefs[Keys.DEFAULT_RULERS] ?: true,
            defaultSnapEnabled = prefs[Keys.DEFAULT_SNAP] ?: true,
            defaultRulerUnit = runCatching {
                RulerUnit.valueOf(prefs[Keys.RULER_UNIT] ?: RulerUnit.PX.name)
            }.getOrDefault(RulerUnit.PX),
            stylusPressureEnabled = prefs[Keys.STYLUS_PRESSURE] ?: true,
            defaultExportScale = prefs[Keys.EXPORT_SCALE] ?: 2,
            lastOpenedProjectId = prefs[Keys.LAST_PROJECT_ID],
            colorHistory = history
        )
    }

    suspend fun updateShowWelcome(show: Boolean) {
        context.spiderDataStore.edit { it[Keys.SHOW_WELCOME] = show }
    }

    suspend fun updateAutoSave(enabled: Boolean, intervalSec: Int) {
        context.spiderDataStore.edit {
            it[Keys.AUTO_SAVE_ENABLED] = enabled
            it[Keys.AUTO_SAVE_SEC] = intervalSec
        }
    }

    suspend fun updateCanvasDefaults(
        showGrid: Boolean,
        gridSizePx: Int,
        showRulers: Boolean,
        snapEnabled: Boolean,
        rulerUnit: RulerUnit
    ) {
        context.spiderDataStore.edit {
            it[Keys.DEFAULT_GRID] = showGrid
            it[Keys.DEFAULT_GRID_SIZE] = gridSizePx
            it[Keys.DEFAULT_RULERS] = showRulers
            it[Keys.DEFAULT_SNAP] = snapEnabled
            it[Keys.RULER_UNIT] = rulerUnit.name
        }
    }

    suspend fun updateStylusPressure(enabled: Boolean) {
        context.spiderDataStore.edit { it[Keys.STYLUS_PRESSURE] = enabled }
    }

    suspend fun updateDefaultExportScale(scale: Int) {
        context.spiderDataStore.edit { it[Keys.EXPORT_SCALE] = scale }
    }

    suspend fun setLastOpenedProjectId(id: String?) {
        context.spiderDataStore.edit {
            if (id == null) it.remove(Keys.LAST_PROJECT_ID) else it[Keys.LAST_PROJECT_ID] = id
        }
    }

    suspend fun pushColorToHistory(colorArgb: Long) {
        context.spiderDataStore.edit { prefs ->
            val current = prefs[Keys.COLOR_HISTORY]
                ?.split(",")
                ?.mapNotNull { it.trim().toLongOrNull(16) }
                ?: StudioPreferences().colorHistory
            val next = (listOf(colorArgb) + current.filter { it != colorArgb }).take(16)
            prefs[Keys.COLOR_HISTORY] = next.joinToString(",") { it.toString(16) }
        }
    }
}
