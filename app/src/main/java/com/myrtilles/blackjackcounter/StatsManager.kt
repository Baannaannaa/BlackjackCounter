package com.myrtilles.blackjackcounter

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ───────────────────────────────────────────────
// DATASTORE CONFIGURATION
// ───────────────────────────────────────────────

val Context.dataStore by preferencesDataStore(name = "bj_stats")

private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
private val KEY_STREAK_COUNT = intPreferencesKey("streak_count")
private val KEY_LAST_DRILL_TIMESTAMP = longPreferencesKey("last_drill_timestamp")
private val KEY_SESSIONS = stringPreferencesKey("sessions_json")
private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")

// ───────────────────────────────────────────────
// DATA CLASSES
// ───────────────────────────────────────────────

data class SessionStat(
    val date: Long,           // timestamp in ms
    val numCards: Int,
    val speedMs: Int,
    val numDecks: Int,
    val trueRC: Int,
    val userRC: Int?,
    val trueTC: Double,
    val userTC: Double?,
    val rcOk: Boolean,
    val tcOk: Boolean,
) {
    fun formattedDate(): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(date))

    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("numCards", numCards)
        put("speedMs", speedMs)
        put("numDecks", numDecks)
        put("trueRC", trueRC)
        put("userRC", userRC ?: JSONObject.NULL)
        put("trueTC", trueTC)
        put("userTC", userTC ?: JSONObject.NULL)
        put("rcOk", rcOk)
        put("tcOk", tcOk)
    }

    companion object {
        fun fromJson(o: JSONObject) = SessionStat(
            date = o.getLong("date"),
            numCards = o.getInt("numCards"),
            speedMs = o.getInt("speedMs"),
            numDecks = o.getInt("numDecks"),
            trueRC = o.getInt("trueRC"),
            userRC = if (o.isNull("userRC")) null else o.getInt("userRC"),
            trueTC = o.getDouble("trueTC"),
            userTC = if (o.isNull("userTC")) null else o.getDouble("userTC"),
            rcOk = o.getBoolean("rcOk"),
            tcOk = o.getBoolean("tcOk")
        )
    }
}

// ───────────────────────────────────────────────
// STATS MANAGER
// ───────────────────────────────────────────────

object StatsManager {
    private const val MAX_SESSIONS = 100 // Keep the last 100 sessions

    // --- Session Logic ---
    
    fun sessionsFlow(context: Context): Flow<List<SessionStat>> =
        context.dataStore.data.map { prefs ->
            val json = prefs[KEY_SESSIONS] ?: "[]"
            val arr = JSONArray(json)
            List(arr.length()) { SessionStat.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.date }
        }

    suspend fun addSession(context: Context, session: SessionStat) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_SESSIONS] ?: "[]"
            val arr = JSONArray(json)
            arr.put(session.toJson())

            // Truncate if > MAX_SESSIONS (keep most recent)
            val trimmed = if (arr.length() > MAX_SESSIONS) {
                val newArr = JSONArray()
                for (i in (arr.length() - MAX_SESSIONS) until arr.length()) {
                    newArr.put(arr.getJSONObject(i))
                }
                newArr
            } else arr

            prefs[KEY_SESSIONS] = trimmed.toString()
        }
    }

    suspend fun clearAll(context: Context) {
        context.dataStore.edit { it.remove(KEY_SESSIONS) }
    }

    // --- Preferences Logic ---

    fun darkModeFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DARK_MODE] ?: false }

    suspend fun setDarkMode(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    fun soundFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SOUND_ENABLED] ?: true }

    suspend fun setSound(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUND_ENABLED] = enabled }
    }

    // --- Streak Logic ---
    
    fun streakFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_STREAK_COUNT] ?: 0 }

    suspend fun updateStreak(context: Context) {
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            val lastTimestamp = prefs[KEY_LAST_DRILL_TIMESTAMP] ?: 0L
            val currentStreak = prefs[KEY_STREAK_COUNT] ?: 0

            if (lastTimestamp == 0L) {
                // First drill ever
                prefs[KEY_STREAK_COUNT] = 1
            } else {
                val lastDate = Calendar.getInstance().apply { timeInMillis = lastTimestamp }
                val currentDate = Calendar.getInstance().apply { timeInMillis = now }

                val isSameDay = lastDate.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) &&
                        lastDate.get(Calendar.DAY_OF_YEAR) == currentDate.get(Calendar.DAY_OF_YEAR)

                if (!isSameDay) {
                    val yesterday = Calendar.getInstance().apply {
                        timeInMillis = now
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                    val isYesterday = lastDate.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                            lastDate.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)

                    if (isYesterday) {
                        prefs[KEY_STREAK_COUNT] = currentStreak + 1
                    } else {
                        prefs[KEY_STREAK_COUNT] = 1 // Reset if day missed
                    }
                }
            }
            prefs[KEY_LAST_DRILL_TIMESTAMP] = now
        }
    }

    suspend fun checkAndResetStreak(context: Context) {
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            val lastTimestamp = prefs[KEY_LAST_DRILL_TIMESTAMP] ?: 0L
            if (lastTimestamp != 0L) {
                val lastDate = Calendar.getInstance().apply { timeInMillis = lastTimestamp }
                val yesterday = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -1)
                }

                // If last drill was before yesterday, reset streak
                val isTodayOrYesterday = (lastDate.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                        lastDate.get(Calendar.DAY_OF_YEAR) >= yesterday.get(Calendar.DAY_OF_YEAR)) ||
                        (lastDate.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR) &&
                                lastDate.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR))

                if (!isTodayOrYesterday) {
                    prefs[KEY_STREAK_COUNT] = 0
                }
            }
        }
    }
}
