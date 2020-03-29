package com.awareframework.encounter.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

class EncounterCovidDataWorker(appContext: Context, workerParameters: WorkerParameters) :
    Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        println("Syncing data...")

        val data_source = "https://pomber.github.io/encounter/timeseries.json"

        val requestQueue = Volley.newRequestQueue(applicationContext)
        val serverRequest = object : JsonObjectRequest(Method.GET, data_source, null,
            Response.Listener { dataObj ->
                println(dataObj)
            },
            Response.ErrorListener {

            }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json"
                return params
            }
        }

        requestQueue.add(serverRequest)
        return Result.success()
    }
}