package com.awareframework.encounter.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.awareframework.encounter.EncounterHome
import com.awareframework.encounter.R
import com.awareframework.encounter.workers.EncounterDataWorker
import java.util.concurrent.TimeUnit

class EncounterService : Service() {

    companion object {
        val ENCOUNTER_FOREGROUND = 210712
    }

    override fun onCreate() {
        super.onCreate()

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.app_name)
            val descriptionText = applicationContext.getString(R.string.app_name)
            val channel = NotificationChannel(
                "ENCOUNTER",
                name,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        val foregroundIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, EncounterHome::class.java),
            0
        )
        val notification = NotificationCompat.Builder(applicationContext, "ENCOUNTER")
        notification.setSmallIcon(R.drawable.ic_stat_encounter)
        notification.setOngoing(true)
        notification.setOnlyAlertOnce(true)
        notification.setContentIntent(foregroundIntent)
        notification.setPriority(NotificationCompat.PRIORITY_MIN)
        notification.setContentTitle(getString(R.string.app_name))
        notification.setContentText(getString(R.string.app_running))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.setChannelId("ENCOUNTER")

        startForeground(ENCOUNTER_FOREGROUND, notification.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val networkAvailable =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val data = PeriodicWorkRequestBuilder<EncounterDataWorker>(
            15,
            TimeUnit.MINUTES
        ).setConstraints(networkAvailable).build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork("ENCOUNTER-DATA", ExistingPeriodicWorkPolicy.KEEP, data)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}