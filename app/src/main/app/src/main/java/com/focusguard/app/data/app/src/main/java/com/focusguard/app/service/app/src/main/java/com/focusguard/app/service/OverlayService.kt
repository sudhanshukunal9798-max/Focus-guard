package com.focusguard.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.data.Prefs
import com.focusguard.app.service.MonitorService.Companion.EXTRA_ELAPSED
import com.focusguard.app.service.MonitorService.Companion.EXTRA_LIMIT
import com.focusguard.app.service.MonitorService.Companion.EXTRA_PCT
import com.focusguard.app.service.MonitorService.Companion.EXTRA_PLATFORM
import com.focusguard.app.service.MonitorService.Companion.EXTRA_SCROLLS

class OverlayService : Service() {

    companion object {
        fun show(ctx: Context, platform: String) {
            val i = Intent(ctx, OverlayService::class.java).apply {
                putExtra(EXTRA_PLATFORM, platform)
            }
            ctx.startService(i)
        }

        fun hide(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: return
            val elapsed  = intent.getLongExtra(EXTRA_ELAPSED, 0)
            val limit    = intent.getLongExtra(EXTRA_LIMIT, 1)
            val scrolls  = intent.getIntExtra(EXTRA_SCROLLS, 0)
            val pct      = intent.getIntExtra(EXTRA_PCT, 0)
            updateOverlay(platform, elapsed, limit, scrolls, pct)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            updateReceiver,
            IntentFilter(MonitorService.ACTION_SESSION_UPDATE),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) createOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(updateReceiver)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bar, null)

        val gravity = if (Prefs.isOverlayTop(this)) Gravity.TOP else Gravity.BOTTOM
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
        }

        windowManager?.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
    }

    private fun updateOverlay(platform: String, elapsedSec: Long, limitSec: Long, scrolls: Int, pct: Int) {
        val view = overlayView ?: return

        val platformName = when (platform) {
            "youtube" -> "▶ YouTube"
            "instagram" -> "◉ Instagram"
            else -> "⬡ Browser"
        }

        view.findViewById<TextView>(R.id.tv_platform).text = platformName
        view.findViewById<TextView>(R.id.tv_time).text = "${formatTime(elapsedSec)} / ${formatTime(limitSec)}"
        view.findViewById<TextView>(R.id.tv_scrolls).text = "↕ $scrolls scrolls"
        view.findViewById<TextView>(R.id.tv_pct).text = "$pct%"

        val progressBar = view.findViewById<android.view.View>(R.id.progress_fill)
        progressBar.layoutParams = (progressBar.layoutParams as android.widget.LinearLayout.LayoutParams).also {
            it.weight = pct.coerceIn(0, 100).toFloat()
        }

        val accentColor = when {
            pct >= 80 -> Color.parseColor("#FF1744")
            pct >= 60 -> Color.parseColor("#FFD740")
            else      -> Color.parseColor("#00E676")
        }
        progressBar.setBackgroundColor(accentColor)
        view.findViewById<TextView>(R.id.tv_pct).setTextColor(accentColor)

        if (pct >= 100) {
            view.setBackgroundColor(Color.parseColor("#1AFF1744"))
        } else {
            view.setBackgroundColor(Color.parseColor("#E6000000"))
        }
    }

    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m >= 60) "${m / 60}h${m % 60}m" else "${m}m${s}s"
    }
}
