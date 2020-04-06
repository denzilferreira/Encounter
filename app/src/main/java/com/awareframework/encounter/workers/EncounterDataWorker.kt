package com.awareframework.encounter.workers

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.awareframework.encounter.EncounterHome
import com.awareframework.encounter.database.EncounterDatabase
import com.awareframework.encounter.database.Stats
import com.awareframework.encounter.database.User
import com.awareframework.encounter.services.EncounterService
import com.awareframework.encounter.ui.EncounterPublisher
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.*
import org.jetbrains.anko.doAsync
import java.text.SimpleDateFormat
import java.util.*

class EncounterDataWorker(appContext: Context, workerParameters: WorkerParameters) :
    Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val data_source = "https://pomber.github.io/covid19/timeseries.json"
        val requestQueue = Volley.newRequestQueue(applicationContext)
        val serverRequest = JsonObjectRequest(Request.Method.GET, data_source, null,
            Response.Listener { dataObj ->
                doAsync {
                    val countries = dataObj.keys()
                    countries.forEach { country ->
                        val recordsCountry = dataObj.getJSONArray(country)
                        for (i in 0 until recordsCountry.length()) {
                            val record = recordsCountry.getJSONObject(i)
                            val formatter = SimpleDateFormat(
                                "yyyy-M-d",
                                Locale.US
                            ).parse(record.getString("date"))!!

                            val db =
                                Room.databaseBuilder(
                                    applicationContext,
                                    EncounterDatabase::class.java,
                                    "encounters"
                                ).build()

                            val existingDayData = db.StatsDao().getCountryDayData(country, formatter.time)
                            if (existingDayData.isNotEmpty()) {
                                var updated = false

                                val currentStats = existingDayData.last()
                                if (currentStats.confirmed != record.getLong("confirmed")) {
                                    currentStats.confirmed = record.getLong("confirmed")
                                    updated = true
                                }

                                if (currentStats.deaths != record.getLong("deaths")) {
                                    currentStats.deaths = record.getLong("deaths")
                                    updated = true
                                }

                                if (currentStats.recovered != record.getLong("recovered")) {
                                    currentStats.recovered = record.getLong("recovered")
                                    updated = true
                                }
                                if (updated) db.StatsDao().update(currentStats)
                            } else {
                                val entry = Stats(
                                    null,
                                    country,
                                    formatter.time,
                                    record.getLong("confirmed"),
                                    record.getLong("deaths"),
                                    record.getLong("recovered")
                                )
                                db.StatsDao().insert(entry)
                            }
                            db.close()
                        }
                    }
                    applicationContext.sendBroadcast(Intent(EncounterHome.ACTION_NEW_DATA))
                    applicationContext.startActivity(Intent(applicationContext, EncounterPublisher::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            },
            Response.ErrorListener {
                println("Error ${it.message}")
            }
        )

        requestQueue.add(serverRequest)
        return Result.success()
    }
}