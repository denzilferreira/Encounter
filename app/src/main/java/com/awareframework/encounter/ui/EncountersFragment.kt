package com.awareframework.encounter.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.room.Room
import com.awareframework.encounter.R
import com.awareframework.encounter.database.EncounterDatabase
import kotlinx.android.synthetic.main.fragment_encounters.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.runOnUiThread
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.uiThread
import java.util.*

class EncountersFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_encounters, container, false)
    }

    override fun onResume() {
        super.onResume()

        defaultSharedPreferences.edit().putString("active", "encounters").apply()

        doAsync {
            val db =
                Room.databaseBuilder(context!!, EncounterDatabase::class.java, "encounters")
                    .build()

            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY,0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            val encounters = db.EncounterDao().getToday(today.timeInMillis)
            uiThread {
                counter_encounters.text = getString(R.string.count_number).format(encounters.size)
            }

            db.close()
        }
    }
}
