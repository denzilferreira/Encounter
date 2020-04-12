package com.awareframework.encounter.ui

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.room.Room
import com.awareframework.encounter.R
import com.awareframework.encounter.database.Encounter
import com.awareframework.encounter.database.EncounterDatabase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import kotlinx.android.synthetic.main.fragment_encounters.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.runOnUiThread
import org.jetbrains.anko.uiThread
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class EncountersFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_encounters, container, false)
    }

    lateinit var startDate: Date
    lateinit var endDate: Date

    override fun onResume() {
        super.onResume()

        defaultSharedPreferences.edit().putString("active", "encounters").apply()

        if (!::startDate.isInitialized) {
            val startCalendar = Calendar.getInstance()
            startCalendar.add(Calendar.DATE, -7)
            startDate = startCalendar.time
        }
        if (!::endDate.isInitialized) {
            val endCalendar = Calendar.getInstance()
            endDate = endCalendar.time
        }

        encounter_previous.setOnClickListener {
            endDate = startDate

            val startCalendar = Calendar.getInstance()
            startCalendar.time = startDate
            startCalendar.add(Calendar.DATE, -7)
            startDate = startCalendar.time

            encounter_dates.text = getString(R.string.encounter_dates).format(
                DateFormat.getMediumDateFormat(context).format(startDate),
                DateFormat.getMediumDateFormat(context).format(endDate)
            )

            doAsync {
                val db =
                    Room.databaseBuilder(context!!, EncounterDatabase::class.java, "encounters")
                        .build()
                val encountersData = db.EncounterDao().getWindow(startDate.time, endDate.time)
                drawBarChart(encountersData)
                db.close()
            }
        }

        encounter_next.setOnClickListener {
            startDate = endDate

            val endCalendar = Calendar.getInstance()
            endCalendar.time = startDate
            endCalendar.add(Calendar.DATE, +7)
            endDate = endCalendar.time

            encounter_dates.text = getString(R.string.encounter_dates).format(
                DateFormat.getMediumDateFormat(context).format(startDate),
                DateFormat.getMediumDateFormat(context).format(endDate)
            )

            doAsync {
                val db =
                    Room.databaseBuilder(context!!, EncounterDatabase::class.java, "encounters")
                        .build()
                val encountersData = db.EncounterDao().getWindow(startDate.time, endDate.time)
                drawBarChart(encountersData)
                db.close()
            }
        }

        doAsync {
            val db =
                Room.databaseBuilder(context!!, EncounterDatabase::class.java, "encounters")
                    .build()

            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            val encounters = db.EncounterDao().getToday(today.timeInMillis)
            uiThread {
                it.counter_encounters.text =
                    getString(R.string.count_number).format(encounters.size)
                it.encounter_dates.text = getString(R.string.encounter_dates).format(
                    DateFormat.getMediumDateFormat(context).format(startDate),
                    DateFormat.getMediumDateFormat(context).format(endDate)
                )
            }

            val encountersData = db.EncounterDao().getWindow(startDate.time, endDate.time)
            drawBarChart(encountersData)

            db.close()
        }
    }

    fun drawBarChart(encounters: Array<Encounter>) {
        runOnUiThread {
            encounters_chart.description.isEnabled = false
            encounters_chart.setPinchZoom(false)

            encounters_chart.animateXY(200, 300)
            encounters_chart.legend.isEnabled = false
            encounters_chart.axisRight.isEnabled = false
            encounters_chart.axisRight.setDrawGridLines(false)

            encounters_chart.axisLeft.granularity = 1f
            encounters_chart.axisLeft.isGranularityEnabled = true
            encounters_chart.axisLeft.setDrawGridLines(false)
            encounters_chart.axisLeft.textColor = Color.GRAY

            encounters_chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            encounters_chart.xAxis.textColor = Color.GRAY
            encounters_chart.xAxis.setDrawGridLines(false)
        }

        val barEntries = ArrayList<BarEntry>()
        val weeklyDistribution = HashMap<Int, ArrayList<String>>()
        weeklyDistribution.put(1, ArrayList()) //Monday
        weeklyDistribution.put(2, ArrayList())
        weeklyDistribution.put(3, ArrayList())
        weeklyDistribution.put(4, ArrayList())
        weeklyDistribution.put(5, ArrayList())
        weeklyDistribution.put(6, ArrayList())
        weeklyDistribution.put(7, ArrayList()) //Sunday

        for (encounter in encounters) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = encounter.timestamp
            if (!weeklyDistribution.get(cal.get(Calendar.DAY_OF_WEEK))?.contains(encounter.uuid_detected)!!) {
                (weeklyDistribution.get(cal.get(Calendar.DAY_OF_WEEK)) as ArrayList<String>).add(encounter.uuid_detected)
            }
        }

        for (i in 1..weeklyDistribution.size) {
            barEntries.add(BarEntry(i.toFloat(), weeklyDistribution.get(i)?.size?.toFloat()!!))
        }

        val barDataSet = BarDataSet(barEntries, "Weekly Encounters")
        barDataSet.setDrawValues(false)
        barDataSet.color = resources.getColor(R.color.colorAccent)

        val barSet = ArrayList<IBarDataSet>()
        barSet.add(barDataSet)

        val barData = BarData(barSet)
        runOnUiThread {
            encounters_chart.data = barData
            encounters_chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return when(value) {
                        1f -> getString(R.string.sunday)
                        2f -> getString(R.string.monday)
                        3f -> getString(R.string.tuesday)
                        4f -> getString(R.string.wednesday)
                        5f -> getString(R.string.thursday)
                        6f -> getString(R.string.friday)
                        7f -> getString(R.string.saturday)
                        else -> super.getFormattedValue(value)
                    }
                }
            }
            encounters_chart.invalidate()
        }
    }
}
