package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AmbientSoundType
import com.example.model.LearnerProfile
import com.example.model.LearnerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

val Context.stepLearnPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "steplearn_ui_prefs")

data class UiPreferences(
    val editorSideBySide: Boolean = true,
    val soundType: AmbientSoundType = AmbientSoundType.WHITE,
    val soundVolume: Int = 55,
    val soundEnabled: Boolean = false
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val SIDE_BY_SIDE = booleanPreferencesKey("editor_side_by_side")
        val SOUND_TYPE = stringPreferencesKey("ambient_sound_type")
        val SOUND_VOLUME = intPreferencesKey("ambient_sound_volume")
        val SOUND_ENABLED = booleanPreferencesKey("ambient_sound_enabled")
    }

    val preferencesFlow: Flow<UiPreferences> = context.stepLearnPrefsDataStore.data.map { prefs ->
        UiPreferences(
            editorSideBySide = prefs[Keys.SIDE_BY_SIDE] ?: true,
            soundType = AmbientSoundType.fromKey(prefs[Keys.SOUND_TYPE] ?: "white"),
            soundVolume = (prefs[Keys.SOUND_VOLUME] ?: 55).coerceIn(0, 100),
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: false
        )
    }

    suspend fun setSideBySide(enabled: Boolean) {
        context.stepLearnPrefsDataStore.edit { it[Keys.SIDE_BY_SIDE] = enabled }
    }

    suspend fun setSoundType(type: AmbientSoundType) {
        context.stepLearnPrefsDataStore.edit { it[Keys.SOUND_TYPE] = type.key }
    }

    suspend fun setSoundVolume(volume: Int) {
        context.stepLearnPrefsDataStore.edit { it[Keys.SOUND_VOLUME] = volume.coerceIn(0, 100) }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.stepLearnPrefsDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }
}

class LearnerDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS profiles (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learner_states (
                learner_id TEXT PRIMARY KEY,
                state_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        val now = System.currentTimeMillis()
        db.insertWithOnConflict(
            "profiles",
            null,
            ContentValues().apply {
                put("id", DEFAULT_LEARNER_ID)
                put("name", DEFAULT_LEARNER_NAME)
                put("is_active", 1)
                put("created_at", now)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) onCreate(db)
    }

    @Synchronized
    fun listProfiles(): List<LearnerProfile> {
        ensureDefaultProfile()
        val list = mutableListOf<LearnerProfile>()
        readableDatabase.rawQuery(
            "SELECT id, name FROM profiles ORDER BY created_at ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list += LearnerProfile(
                    id = cursor.getString(0),
                    name = cursor.getString(1)
                )
            }
        }
        return list
    }

    @Synchronized
    fun getActiveProfile(): LearnerProfile {
        ensureDefaultProfile()
        readableDatabase.rawQuery(
            "SELECT id, name FROM profiles WHERE is_active = 1 LIMIT 1",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return LearnerProfile(cursor.getString(0), cursor.getString(1))
            }
        }
        return LearnerProfile(DEFAULT_LEARNER_ID, DEFAULT_LEARNER_NAME)
    }

    @Synchronized
    fun createProfile(rawName: String): Result<LearnerProfile> {
        val cleanName = rawName.trim().take(30)
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("اكتب اسمًا قصيرًا للملف أولًا."))
        }
        val existing = listProfiles()
        if (existing.size >= MAX_PROFILES) {
            return Result.failure(IllegalStateException("وصلت للحد الأقصى (٣٠ ملفًا) على هذا الجهاز."))
        }
        if (existing.any { it.name.equals(cleanName, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("يوجد ملف بهذا الاسم بالفعل؛ اختر اسمًا آخر أو افتحه من القائمة."))
        }
        val id = "learner-" + UUID.randomUUID().toString().take(8)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE profiles SET is_active = 0")
            db.insertOrThrow(
                "profiles",
                null,
                ContentValues().apply {
                    put("id", id)
                    put("name", cleanName)
                    put("is_active", 1)
                    put("created_at", System.currentTimeMillis())
                }
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return Result.success(LearnerProfile(id = id, name = cleanName))
    }

    @Synchronized
    fun activateProfile(profileId: String): Boolean {
        val profiles = listProfiles()
        if (profiles.none { it.id == profileId }) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE profiles SET is_active = 0")
            db.update(
                "profiles",
                ContentValues().apply { put("is_active", 1) },
                "id = ?",
                arrayOf(profileId)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    @Synchronized
    fun loadState(learnerId: String): LearnerState {
        readableDatabase.rawQuery(
            "SELECT state_json FROM learner_states WHERE learner_id = ? LIMIT 1",
            arrayOf(learnerId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val raw = cursor.getString(0)
                return runCatching { json.decodeFromString<LearnerState>(raw) }
                    .getOrElse { LearnerState() }
            }
        }
        return LearnerState()
    }

    @Synchronized
    fun saveState(learnerId: String, state: LearnerState) {
        val encoded = json.encodeToString(state)
        writableDatabase.insertWithOnConflict(
            "learner_states",
            null,
            ContentValues().apply {
                put("learner_id", learnerId)
                put("state_json", encoded)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun ensureDefaultProfile() {
        val db = writableDatabase
        db.rawQuery("SELECT COUNT(*) FROM profiles", null).use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                db.rawQuery("SELECT COUNT(*) FROM profiles WHERE is_active = 1", null).use { active ->
                    if (active.moveToFirst() && active.getInt(0) == 0) {
                        db.execSQL("UPDATE profiles SET is_active = 1 WHERE id = (SELECT id FROM profiles ORDER BY created_at ASC LIMIT 1)")
                    }
                }
                return
            }
        }
        db.insertWithOnConflict(
            "profiles",
            null,
            ContentValues().apply {
                put("id", DEFAULT_LEARNER_ID)
                put("name", DEFAULT_LEARNER_NAME)
                put("is_active", 1)
                put("created_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    companion object {
        const val DB_NAME = "khotwa_learners.db"
        const val DB_VERSION = 1
        const val MAX_PROFILES = 30
        const val DEFAULT_LEARNER_ID = "default"
        const val DEFAULT_LEARNER_NAME = "المتعلم الحالي"
    }
}
