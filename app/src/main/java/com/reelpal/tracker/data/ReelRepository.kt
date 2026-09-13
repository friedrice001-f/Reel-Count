package com.reelpal.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReelRepository private constructor(context: Context) {

    private val dao = AppDatabase.getInstance(context).reelScrollDao()
    private val prefs = context.getSharedPreferences("reelpal_settings", Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Records one reel scroll for [packageName] today. Returns the new total. */
    suspend fun incrementCount(packageName: String): Int {
        val date = today()
        val existing = dao.getRow(date, packageName)
        val newCount = (existing?.count ?: 0) + 1
        dao.upsert(ReelScrollEntity(date, packageName, newCount))
        return newCount
    }

    fun observeToday(): Flow<List<ReelScrollEntity>> = dao.observeForDate(today())

    /** Total reels per day for the last [n] days, keyed by yyyy-MM-dd. */
    fun observeLastNDays(n: Int): Flow<Map<String, Int>> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        val since = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        return dao.observeSince(since).map { rows ->
            rows.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.count } }
        }
    }

    fun isOverlayEnabled(): Boolean = prefs.getBoolean(KEY_OVERLAY_ENABLED, true)
    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
    }

    fun isAppMonitored(packageName: String): Boolean =
        prefs.getBoolean(KEY_PREFIX_APP_ENABLED + packageName, true)

    fun setAppMonitored(packageName: String, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREFIX_APP_ENABLED + packageName, enabled).apply()
    }

    companion object {
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_PREFIX_APP_ENABLED = "app_enabled_"

        @Volatile private var INSTANCE: ReelRepository? = null

        fun getInstance(context: Context): ReelRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReelRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
