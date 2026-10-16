package com.lanteng.star.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lanteng.star.app.floating.FloatingService
import com.lanteng.star.app.util.Prefs

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (prefs.bootStartup && prefs.foregroundService) {
            FloatingService.start(context)
        }
    }
}
