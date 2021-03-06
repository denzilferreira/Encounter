package com.awareframework.encounter.services

import android.app.IntentService
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.Room
import com.awareframework.encounter.R
import com.awareframework.encounter.database.EncounterDatabase
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DataExportService : IntentService("Data Export") {
    override fun onHandleIntent(intent: Intent?) {
        val db =
            Room.databaseBuilder(applicationContext, EncounterDatabase::class.java, "encounters")
                .build()

        val user = db.UserDao().getUser().first()

        val encounters = db.EncounterDao().getAll()
        val encountersReadable = encounters.map {
            Date(it.timestamp).getStringTimeStampWithDate()
        }

        val exportDir = File(filesDir, "export")
        exportDir.mkdir()

        val export = File(exportDir, "encounters-${user.uuid}.json")
        export.createNewFile()

        val jsonArrayData = JSONArray()
        for ((index, encounter) in encounters.withIndex()) {
            val gSON = Gson()
            val encounterString = gSON.toJson(encounter)
            val encounterJSONObject = JSONObject(encounterString)
            encounterJSONObject.put("readable", encountersReadable[index])
            jsonArrayData.put(encounterJSONObject)
        }

        export.writeText(jsonArrayData.toString(3))

        val fileUri =
            FileProvider.getUriForFile(applicationContext, "$packageName.provider.storage", export)
        val share = Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, fileUri)
            type = "text/json"
        }, getString(R.string.share_data))
        startActivity(share.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        //Randomize UUID to a new one now
        user.uuid = UUID.randomUUID().toString()
        user.timestamp = System.currentTimeMillis()
        db.UserDao().update(user)

        db.close()
    }

    /**
     * Extension function to get a human-readable GMT date time for json export
     */
    fun Date.getStringTimeStampWithDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss z", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()
        return dateFormat.format(this)
    }
}