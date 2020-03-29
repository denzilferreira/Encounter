package com.awareframework.encounter.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.awareframework.encounter.services.EncounterService

class EncounterReboot : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context?.startForegroundService(Intent(context, EncounterService::class.java))
            } else {
                context?.startService(Intent(context, EncounterService::class.java))
            }
        }
    }
}