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
import com.awareframework.encounter.database.CovidDatabase
import com.awareframework.encounter.database.Stats
import org.jetbrains.anko.doAsync
import java.text.SimpleDateFormat
import java.util.*

class EncounterCovidDataWorker(appContext: Context, workerParameters: WorkerParameters) :
    Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val data_source = "https://pomber.github.io/covid19/timeseries.json"
        val requestQueue = Volley.newRequestQueue(applicationContext)
        val serverRequest = JsonObjectRequest(Request.Method.GET, data_source, null,
            Response.Listener { dataObj ->
                doAsync {
                    val db =
                        Room.databaseBuilder(applicationContext, CovidDatabase::class.java, "covid")
                            .build()
                    db.StatsDao().clear()

                    val countries = dataObj.keys()
                    countries.forEach {
                        val recordsCountry = dataObj.getJSONArray(it)
                        for (i in 0 until recordsCountry.length()) {
                            val record = recordsCountry.getJSONObject(i)
                            val formatter = SimpleDateFormat("yyyy-M-d", Locale.US).parse(record.getString("date"))
                            val entry = Stats(
                                null,
                                it,
                                formatter.time,
                                record.getLong("confirmed"),
                                record.getLong("deaths"),
                                record.getLong("recovered")
                            )
                            db.StatsDao().insert(entry)
                        }
                    }

                    db.close()

                    applicationContext.sendBroadcast(Intent(EncounterHome.ACTION_NEW_DATA))
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