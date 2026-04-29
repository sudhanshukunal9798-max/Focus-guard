package com.focusguard.app.service

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.focusguard.app.R
import com.focusguard.app.data.Prefs
import com.focusguard.app.data.SessionRecord
import com.focusguard.app.ui.MainActivity

class MonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "focusguard_monitor"
        const val NOTIF_ID = 1
        const val WARN_NOTIF_ID = 2
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_CHROME = "com.android.chrome"
        const val PKG_FIREFOX = "org.mozilla.firefox"
        const val PKG_SAMSUNG_BROWSER = "com.sec.android.app.sbrowser"
        const val ACTION_SESSION_UPDATE = "com.focusguard.SESSION_UPDATE"
        const val EXTRA_PLATFORM = "platform"
        const val EXTRA_ELAPSED = "elapsed"
        const val EXTRA_LIMIT = "limit"
        const val EXTRA_SCROLLS = "scrolls"
        const val EXTRA_PCT = "pct"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, MonitorService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usageStatsManager: UsageStatsManager

    private var activePlatform: String? = null
    private var sessionStartMs: Long = 0L
    private var sessionScrolls: Int = 0
    private var warnFired = false
    private var limitFired = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildBaseNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        endCurrentSession()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun poll() {
        if (!Prefs.isMonitoringOn(this)) return
        val foreground = getForegroundPackage()
        val detectedPlatform = when (foreground) {
            PKG_YOUTUBE -> "youtube"
            PKG_INSTAGRAM -> "instagram"
            PKG_CHROME, PKG_FIREFOX, PKG_SAMSUNG_BROWSER -> "browser"
            else -> null
        }
        if (detectedPlatform != activePlatform) {
            endCurrentSession()
            if (detectedPlatform != null) startSession(detectedPlatform)
        }
        activePlatform?.let { platform ->
            val elapsedSec = (System.currentTimeMillis() - sessionStartMs) / 1000L
            val limit = getLimit(platform)
            val pct = if (limit > 0) (elapsedSec * 100 / limit).toInt() else 0
            if (pct >= 80 && !warnFired) {
                warnFired = true
                fireWarningNotification(platform, elapsedSec, limit)
            }
            if (pct >= 100 && !limitFired) {
                limitFired = true
                fireLimitNotification(platform)
                if (Prefs.isNudgesOn(this)) fireNudge(platform)
            }
            broadcastUpdate(platform, elapsedSec, limit, sessionScrolls, pct)
            updateNotification(platform, elapsedSec, limit)
        }
    }

    private fun startSession(platform: String) {
        activePlatform = platform
        sessionStartMs = System.currentTimeMillis()
        sessionScrolls = 0
        warnFired = false
        limitFired = false
        OverlayService.show(this, platform)
    }

    private fun endCurrentSession() {
        val platform = activePlatform ?: return
        val durationSec = (System.currentTimeMillis() - sessionStartMs) / 1000L
        if (durationSec < 5) { activePlatform = null; return }
        val limit = getLimit(platform)
        Prefs.addSession(this, SessionRecord(
            platform = platform,
            date = Prefs.todayStr(),
            durationSeconds = durationSec,
            scrollCount = sessionScrolls,
            limitSeconds = limit,
            exceededLimit = durationSec > limit
        ))
        activePlatform = null
        OverlayService.hide(this)
        sendBroadcast(Intent(ACTION_SESSION_UPDATE))
    }

    private fun getForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, now - 5000, now
        ) ?: return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun getLimit(platform: String): Long = when (platform) {
        "youtube" -> Prefs.getYtLimit(this)
        "instagram" -> Prefs.getIgLimit(this)
        else -> 30 * 60L
    }

    private fun broadcastUpdate(platform: String, elapsed: Long, limit: Long, scrolls: Int, pct: Int) {
        val intent = Intent(ACTION_SESSION_UPDATE).apply {
            putExtra(EXTRA_PLATFORM, platform)
            putExtra(EXTRA_ELAPSED, elapsed)
            putExtra(EXTRA_LIMIT, limit)
            putExtra(EXTRA_SCROLLS, scrolls)
            putExtra(EXTRA_PCT, pct)
            `package` = packageName
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "FocusGuard Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Tracks screen time in background" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildBaseNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusGuard active")
            .setContentText("Monitoring screen time")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(platform: String, elapsedSec: Long, limitSec: Long) {
        val platformName = if (platform == "youtube") "YouTube" else "Instagram"
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$platformName — ${formatTime(elapsedSec)} / ${formatTime(limitSec)}")
            .setContentText("FocusGuard is tracking your session")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif)
    }

    private fun fireWarningNotification(platform: String, elapsed: Long, limit: Long) {
        val name = if (platform == "youtube") "YouTube" else "Instagram"
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ 80% of $name limit reached")
            .setContentText("${formatTime(elapsed)} used of ${formatTime(limit)}")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(WARN_NOTIF_ID, notif)
    }

    private fun fireLimitNotification(platform: String) {
        val name = if (platform == "youtube") "YouTube" else "Instagram"
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 $name time limit reached!")
            .setContentText("You've hit your set limit. Time for a break?")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(WARN_NOTIF_ID + 1, notif)
    }

    private fun fireNudge(platform: String) {
        val nudges = if (platform == "youtube") listOf(
            "Try a 5-minute walk instead 🚶",
            "Your eyes need a rest — look away for 20 seconds",
            "What's one real-world thing you could do right now?",
            "A 10-min break now = more focus later 🧠"
        ) else listOf(
            "Real connections > scrolling. Text someone you love 💬",
            "Step outside for 5 minutes — fresh air resets your mind",
            "You've been scrolling — try journaling for 5 mins ✍️",
            "Stretch your body — your screen will still be there"
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusGuard Nudge 💡")
            .setContentText(nudges.random())
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(WARN_NOTIF_ID + 2, notif)
    }

    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m ${s}s"
    }
}
