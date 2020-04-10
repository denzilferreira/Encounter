package com.awareframework.encounter.services

import android.app.IntentService
import android.content.Intent
import androidx.room.Room
import com.awareframework.encounter.database.EncounterDatabase

class DataExportService : IntentService("Data Export") {
    override fun onHandleIntent(intent: Intent?) {
        val db =
            Room.databaseBuilder(applicationContext, EncounterDatabase::class.java, "encounters")
                .build()

        db.EncounterDao()

        db.close()
    }
}