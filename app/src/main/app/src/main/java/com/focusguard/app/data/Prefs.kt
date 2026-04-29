package com.focusguard.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class SessionRecord(
    val platform: String,
    val date: String,
    val durationSeconds: Long,
    val scrollCount: Int,
    val limitSeconds: Long,
    val exceededLimit: Boolean
)

data class DailyStats(
    val date: String,
    val youtubeSeconds: Long,
    val instagramSeconds: Long,
    val youtubeScrolls: Int,
    val instagramScrolls: Int
)

object Prefs {
    private const val NAME = "focusguard_prefs"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_YT_LIMIT = "yt_limit_seconds"
    private const val KEY_IG_LIMIT = "ig_limit_seconds"
    private const val KEY_OVERLAY_TOP = "overlay_top"
    private const val KEY_MONITORING_ON = "monitoring_on"
    private const val KEY_NUDGES_ON = "nudges_on"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getYtLimit(ctx: Context): Long = sp(ctx).getLong(KEY_YT_LIMIT, 30 * 60L)
    fun getIgLimit(ctx: Context): Long = sp(ctx).getLong(KEY_IG_LIMIT, 20 * 60L)
    fun setYtLimit(ctx: Context, seconds: Long) = sp(ctx).edit().putLong(KEY_YT_LIMIT, seconds).apply()
    fun setIgLimit(ctx: Context, seconds: Long) = sp(ctx).edit().putLong(KEY_IG_LIMIT, seconds).apply()

    fun isOverlayTop(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_OVERLAY_TOP, false)
    fun setOverlayTop(ctx: Context, top: Boolean) = sp(ctx).edit().putBoolean(KEY_OVERLAY_TOP, top).apply()

    fun isMonitoringOn(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_MONITORING_ON, true)
    fun setMonitoringOn(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean(KEY_MONITORING_ON, on).apply()
    fun isNudgesOn(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_NUDGES_ON, true)
    fun setNudgesOn(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean(KEY_NUDGES_ON, on).apply()

    fun saveSessions(ctx: Context, sessions: List<SessionRecord>) {
        val json = Gson().toJson(sessions)
        sp(ctx).edit().putString(KEY_SESSIONS, json).apply()
    }

    fun getSessions(ctx: Context): MutableList<SessionRecord> {
        val json = sp(ctx).getString(KEY_SESSIONS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<SessionRecord>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun addSession(ctx: Context, record: SessionRecord) {
        val sessions = getSessions(ctx)
        sessions.add(record)
        if (sessions.size > 500) sessions.removeAt(0)
        saveSessions(ctx, sessions)
    }

    fun getTodayStats(ctx: Context): DailyStats {
        val today = todayStr()
        return buildStats(ctx, today)
    }

    fun getWeekStats(ctx: Context): List<DailyStats> {
        val cal = Calendar.getInstance()
        return (6 downTo 0).map { daysAgo ->
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            buildStats(ctx, date)
        }
    }

    private fun buildStats(ctx: Context, date: String): DailyStats {
        val sessions = getSessions(ctx).filter { it.date == date }
        return DailyStats(
            date = date,
            youtubeSeconds = sessions.filter { it.platform == "youtube" }.sumOf { it.durationSeconds },
            instagramSeconds = sessions.filter { it.platform == "instagram" }.sumOf { it.durationSeconds },
            youtubeScrolls = sessions.filter { it.platform == "youtube" }.sumOf { it.scrollCount },
            instagramScrolls = sessions.filter { it.platform == "instagram" }.sumOf { it.scrollCount }
        )
    }

    fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
