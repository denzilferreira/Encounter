package com.awareframework.encounter.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.awareframework.encounter.EncounterHome
import com.awareframework.encounter.database.EncounterDatabase
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class EncounterPublisher : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        doAsync {
            val db =
                Room.databaseBuilder(applicationContext, EncounterDatabase::class.java, "encounters")
                    .build()
            val user = db.UserDao().getUser().first()

            val broadcast = Message(user.uuid.toByteArray())
            val publishOptions = PublishOptions.Builder()
                .setStrategy(
                    Strategy.Builder()
                        .setDistanceType(Strategy.DISTANCE_TYPE_DEFAULT)
                        .setDiscoveryMode(Strategy.DISCOVERY_MODE_DEFAULT)
                        .setTtlSeconds(Strategy.TTL_SECONDS_MAX)
                        .build()
                )
                .build()

            Nearby.getMessagesClient(EncounterHome.encounterHomeContext, MessagesOptions.Builder().setPermissions(NearbyPermissions.DEFAULT).build()).publish(broadcast, publishOptions)
                .addOnFailureListener {
                    println("Failed to publish nearby encounter: ${it.message}")
                }
                .addOnSuccessListener {
                    println("Published nearby successfully")
                }

            uiThread {
                finish()
            }
        }
    }
}