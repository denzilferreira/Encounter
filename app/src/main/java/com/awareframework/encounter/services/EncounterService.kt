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
import androidx.room.Room
import androidx.work.*
import com.awareframework.encounter.EncounterHome
import com.awareframework.encounter.R
import com.awareframework.encounter.database.Encounter
import com.awareframework.encounter.database.EncounterDatabase
import com.awareframework.encounter.database.User
import com.awareframework.encounter.ui.EncountersFragment
import com.awareframework.encounter.workers.EncounterDataWorker
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.*
import org.jetbrains.anko.doAsync
import java.util.concurrent.TimeUnit

class EncounterService : Service() {

    companion object {
        val ENCOUNTER_FOREGROUND = 210712
        lateinit var ping: Message
        lateinit var user: User
    }

    val messageListener = object : MessageListener() {
        override fun onDistanceChanged(message: Message?, distance: Distance?) {
            super.onDistanceChanged(message, distance)

            val encounter = Encounter(
                null,
                user.uuid,
                System.currentTimeMillis(),
                isFound = true,
                isLost = false,
                uuid_detected = message?.content!!.contentToString(),
                distance_meters = distance?.meters,
                distance_accuracy = distance?.accuracy,
                signal_rssi = -1,
                signal_power = -1
            )

            doAsync {
                val db =
                    Room.databaseBuilder(applicationContext, EncounterDatabase::class.java, "covid")
                        .build()
                db.EncounterDao().insert(encounter)

                println("onDistanceChanged: $encounter")
                sendBroadcast(Intent(EncountersFragment.ACTION_NEW_ENCOUNTER))
            }
        }

        override fun onBleSignalChanged(message: Message?, bleSignal: BleSignal?) {
            super.onBleSignalChanged(message, bleSignal)

            val encounter = Encounter(
                null,
                user.uuid,
                System.currentTimeMillis(),
                isFound = true,
                isLost = false,
                uuid_detected = message?.content!!.contentToString(),
                distance_meters = -1.0,
                distance_accuracy = -1,
                signal_rssi = bleSignal!!.rssi,
                signal_power = bleSignal.txPower
            )

            doAsync {
                val db =
                    Room.databaseBuilder(applicationContext, EncounterDatabase::class.java, "covid")
                        .build()
                db.EncounterDao().insert(encounter)

                println("onBleSignalChanged: $encounter")
                sendBroadcast(Intent(EncountersFragment.ACTION_NEW_ENCOUNTER))
            }
        }

        override fun onFound(message: Message?) {
            super.onFound(message)

            val encounter = Encounter(
                null,
                user.uuid,
                System.currentTimeMillis(),
                isFound = true,
                isLost = false,
                uuid_detected = message?.content!!.contentToString(),
                distance_meters = -1.0,
                distance_accuracy = -1,
                signal_rssi = -1,
                signal_power = -1
            )

            doAsync {
                val db =
                    Room.databaseBuilder(applicationContext, EncounterDatabase::class.java, "covid")
                        .build()
                db.EncounterDao().insert(encounter)

                println("onFound: $encounter")
                sendBroadcast(Intent(EncountersFragment.ACTION_NEW_ENCOUNTER))
            }
        }

        override fun onLost(message: Message?) {
            super.onLost(message)

            val encounter = Encounter(
                null,
                user.uuid,
                System.currentTimeMillis(),
                isFound = false,
                isLost = true,
                uuid_detected = message?.content!!.contentToString(),
                distance_meters = -1.0,
                distance_accuracy = -1,
                signal_rssi = -1,
                signal_power = -1
            )

            doAsync {
                val db =
                    Room.databaseBuilder(applicationContext, EncounterDatabase::class.java, "covid")
                        .build()
                db.EncounterDao().insert(encounter)

                println("onLost: $encounter")
                sendBroadcast(Intent(EncountersFragment.ACTION_NEW_ENCOUNTER))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        Nearby.getMessagesClient(applicationContext)

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
        notification.priority = NotificationCompat.PRIORITY_MIN
        notification.setContentTitle(getString(R.string.app_name))
        notification.setContentText(getString(R.string.app_running))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.setChannelId("ENCOUNTER")

        startForeground(ENCOUNTER_FOREGROUND, notification.build())

        doAsync {
            val db =
                Room.databaseBuilder(applicationContext, EncounterDatabase::class.java, "covid")
                    .build()

            val users = db.UserDao().getUser()
            user = users.first()

            ping = Message(user.uuid.toByteArray())
            Nearby.getMessagesClient(applicationContext).publish(ping, PublishOptions.DEFAULT)
        }

        Nearby.getMessagesClient(applicationContext).subscribe(messageListener)
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

    override fun onDestroy() {
        super.onDestroy()
        Nearby.getMessagesClient(applicationContext).unpublish(ping)
        Nearby.getMessagesClient(applicationContext).unsubscribe(messageListener)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}