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
import com.awareframework.encounter.ui.ActivityWarning
import com.awareframework.encounter.workers.EncounterDataWorker
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.*
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.JsonParser
import org.jetbrains.anko.doAsync
import java.util.concurrent.TimeUnit

class EncounterService : Service() {

    companion object {
        val ENCOUNTER_FOREGROUND = 210712
        val ENCOUNTER_WARNING = 1104
        val ACTION_NEW_ENCOUNTER_PUBLISHED = "ACTION_NEW_ENCOUNTER_PUBLISHED"
        val ACTION_CHECK_WARNING = "ACTION_CHECK_WARNING"
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
        notification.priority = NotificationCompat.PRIORITY_MIN
        notification.setContentTitle(getString(R.string.app_name))
        notification.setContentText(getString(R.string.app_running))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.setChannelId("ENCOUNTER")

        startForeground(ENCOUNTER_FOREGROUND, notification.build())

        startActivity(
            Intent(
                applicationContext,
                EncounterHome::class.java
            ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        val pendingIntent = PendingIntent.getService(
            applicationContext,
            0,
            Intent(applicationContext, EncounterService::class.java).setAction(ACTION_NEW_ENCOUNTER_PUBLISHED),
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        /**
         * Subscribe to nearby messages on the background
         */
        Nearby.getMessagesClient(applicationContext).subscribe(
            pendingIntent, SubscribeOptions.Builder()
                .setCallback(object : SubscribeCallback() {
                    override fun onExpired() {
                        super.onExpired()
                        println("Subscription expired PendingIntent...")
                    }
                })
                .setStrategy(
                    Strategy.BLE_ONLY //we can only react to BLE messages on the background which is fine
                ).build()
        ).addOnSuccessListener {
            println("Subscribed background PendingIntent")
        }

        doAsync {
            val db =
                Room.databaseBuilder(
                    applicationContext,
                    EncounterDatabase::class.java,
                    "encounters"
                )
                    .build()
            val users = db.UserDao().getUser()
            if (users.isNotEmpty()) {
                FirebaseMessaging.getInstance().subscribeToTopic("positive")
                    .addOnCompleteListener {
                        println("Subscribed to Encounter awareness topic")
                    }
            }
            db.close()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action.equals(ACTION_NEW_ENCOUNTER_PUBLISHED)) {

            println("EncounterService: found Encounter published message")

            Nearby.getMessagesClient(applicationContext).handleIntent(intent!!,
                object : MessageListener() {
                    override fun onFound(message: Message?) {
                        super.onFound(message)

                        val uuidDetected = String(message?.content!!)
                        doAsync {
                            val db = Room.databaseBuilder(
                                applicationContext,
                                EncounterDatabase::class.java,
                                "encounters"
                            ).build()

                            val user = db.UserDao().getUser().first()
                            val encounter = Encounter(
                                uid = null,
                                timestamp = System.currentTimeMillis(),
                                uuid = user.uuid,
                                uuid_detected = uuidDetected
                            )
                            db.EncounterDao().insert(encounter)
                            db.close()
                        }

                        //sendNotification(getString(R.string.encounter_detected).format(String(message.content)))
                    }
                }
            )
        }

        if (intent?.action.equals(ACTION_CHECK_WARNING)) {
            //parse JSON, check local database for matches with uuid, show notification with instructions on how to proceed
            val message = intent?.extras?.getString("data")
            println("Received: $message")

            if (message != null) {
                val data = JsonParser.parseString(message).asJsonObject
                val uuidPositive = data.get("uuid").asString
                val instructions = data.get("instructions").asString
                doAsync {
                    val db =
                        Room.databaseBuilder(
                            applicationContext,
                            EncounterDatabase::class.java,
                            "encounters"
                        ).build()
                    val positiveMatches = db.EncounterDao().warning(uuidPositive)
                    if (positiveMatches.isNotEmpty()) {
                        val warningIntent = Intent(applicationContext, ActivityWarning::class.java).putExtra("instructions", instructions).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        val warningOnTap = PendingIntent.getActivity(applicationContext, 0, warningIntent, 0)
                        val notification = NotificationCompat.Builder(applicationContext, "ENCOUNTER")
                        notification.setSmallIcon(R.drawable.ic_stat_encounter_warning)
                        notification.setContentIntent(warningOnTap)
                        notification.priority = NotificationCompat.PRIORITY_MAX
                        notification.setContentTitle("${applicationContext.getString(R.string.app_name)}: ${applicationContext.getString(R.string.encounter_attention)}")
                        notification.setContentText(instructions)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.setChannelId("ENCOUNTER")
                        val notificationManager =
                            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(ENCOUNTER_WARNING, notification.build())
                    }
                }
            }
        }

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