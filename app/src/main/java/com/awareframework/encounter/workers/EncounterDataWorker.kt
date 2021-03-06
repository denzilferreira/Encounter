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
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
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

                    val db =
                        Room.databaseBuilder(
                            applicationContext,
                            EncounterDatabase::class.java,
                            "encounters"
                        ).build()

                    //Randomize UUID every day to a new one
                    val users = db.UserDao().getUser()
                    if (users.isNotEmpty()) {
                        val now = Calendar.getInstance()
                        val registered = Calendar.getInstance()
                        val user = users.first()
                        registered.timeInMillis = user.timestamp
                        if (now.get(Calendar.DAY_OF_YEAR) != registered.get(Calendar.DAY_OF_YEAR)) {
                            //Randomize UUID to a new one today
                            user.uuid = UUID.randomUUID().toString()
                            user.timestamp = System.currentTimeMillis()
                            db.UserDao().update(user)
                        }
                    }
                    db.close()

                    uiThread {
                        applicationContext.sendBroadcast(Intent(EncounterHome.ACTION_UPDATE_STARTED))
                    }

                    val countries = dataObj.keys()
                    countries.forEach { country ->

                        uiThread {
                            applicationContext.sendBroadcast(
                                Intent(EncounterHome.ACTION_UPDATE_COUNTRY).putExtra(
                                    EncounterHome.EXTRA_COUNTRY,
                                    country
                                )
                            )
                        }

                        val recordsCountry = dataObj.getJSONArray(country)
                        for (i in 0 until recordsCountry.length()) {
                            val record = recordsCountry.getJSONObject(i)
                            val formatter = SimpleDateFormat(
                                "yyyy-M-d",
                                Locale.US
                            ).parse(record.getString("date"))!!

                            val database =
                                Room.databaseBuilder(
                                    applicationContext,
                                    EncounterDatabase::class.java,
                                    "encounters"
                                ).build()

                            val existingDayData =
                                database.StatsDao().getCountryDayData(country, formatter.time)
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
                                if (updated) database.StatsDao().update(currentStats)
                            } else {
                                val entry = Stats(
                                    null,
                                    country,
                                    formatter.time,
                                    record.getLong("confirmed"),
                                    record.getLong("deaths"),
                                    record.getLong("recovered")
                                )
                                database.StatsDao().insert(entry)
                            }
                            database.close()
                        }
                    }

                    uiThread {
                        applicationContext.sendBroadcast(Intent(EncounterHome.ACTION_UPDATE_FINISHED))
                    }
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