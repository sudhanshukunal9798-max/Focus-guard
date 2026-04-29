package com.focusguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.app.data.Prefs
import com.focusguard.app.service.MonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Prefs.isMonitoringOn(ctx)) {
                MonitorService.start(ctx)
            }
        }
    }
}
