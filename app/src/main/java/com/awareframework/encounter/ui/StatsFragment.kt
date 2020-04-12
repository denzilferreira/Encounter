package com.awareframework.encounter.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.room.Room
import com.awareframework.encounter.R
import com.awareframework.encounter.database.EncounterDatabase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.LargeValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.android.synthetic.main.fragment_stats.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.uiThread
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.log10
import kotlin.math.pow

class StatsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onResume() {
        super.onResume()

        defaultSharedPreferences.edit().putString("active", "stats").apply()

        doAsync {
            val db =
                Room.databaseBuilder(context!!, EncounterDatabase::class.java, "encounters").build()
            val countries = db.StatsDao().getCountries()
            val countryAdapter = ArrayList<String>()
            for (country in countries) {
                countryAdapter.add(country.country)
            }
            db.close()

            Collections.sort(countryAdapter, String.CASE_INSENSITIVE_ORDER)

            uiThread {
                country_selector.adapter =
                    ArrayAdapter(context!!, R.layout.spinner_country, countryAdapter)

                if (defaultSharedPreferences.contains("country")) {
                    country_selector.setSelection(
                        countryAdapter.indexOf(
                            defaultSharedPreferences.getString(
                                "country",
                                ""
                            )
                        ), true
                    )
                    country_selector.dispatchSetSelected(true)
                }
            }

            country_selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    count_confirmed.text = getString(R.string.count_number, 0)
                    count_deaths.text = getString(R.string.count_number, 0)
                    count_recovered.text = getString(R.string.count_number, 0)
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedCountry = country_selector.selectedItem.toString()

                    defaultSharedPreferences.edit().putString("country", selectedCountry).apply()

                    doAsync {
                        val encounterDatabase = Room.databaseBuilder(
                            context!!,
                            EncounterDatabase::class.java,
                            "encounters"
                        ).build()
                        val data = encounterDatabase.StatsDao().getCountryData(selectedCountry)
                        uiThread {
                            count_confirmed.text =
                                getString(R.string.count_number, data.last().confirmed)
                            count_recovered.text =
                                getString(R.string.count_number, data.last().recovered)
                            count_deaths.text = getString(R.string.count_number, data.last().deaths)
                        }

                        val dailyData = encounterDatabase.StatsDao().getDailyCounts(selectedCountry)
                        dailyData.moveToFirst()

                        val dataDayCount = ArrayList<Int>()
                        do {
                            val confirmedAmount =
                                dailyData.getString(dailyData.getColumnIndex("confirmed")).toInt()
                            dataDayCount.add(confirmedAmount)
                        } while (dailyData.moveToNext())
                        dailyData.close()

                        val growth = ArrayList<Float>()
                        val confirmed = ArrayList<Float>()
                        for (i in 8 until dataDayCount.size) {
                            val weekGrowth = dataDayCount.get(i) - dataDayCount.get(i - 7)
                            val weekConfirmed = dataDayCount.get(i)
                            growth.add(weekGrowth.toFloat())
                            confirmed.add(weekConfirmed.toFloat())
                        }

                        val plotDataPoints = ArrayList<Entry>()
                        for (i in 0 until growth.size) {
                            plotDataPoints.add(
                                Entry(
                                    scaleCbr(confirmed[i]), //convert axis data points to logarithmic scale
                                    scaleCbr(growth[i]) //convert axis data points to logarithmic scale
                                )
                            )
                        }

                        val lineData = LineDataSet(plotDataPoints, selectedCountry)
                        lineData.setCircleColor(resources.getColor(R.color.colorPrimary))
                        lineData.setDrawCircleHole(true)
                        lineData.enableDashedLine(0f, 1f, 0f)

                        val dataset = ArrayList<ILineDataSet>()
                        dataset.add(lineData)

                        val lineChart = LineData(dataset)
                        lineChart.setDrawValues(false)

                        uiThread {
                            spread_chart.axisRight.isEnabled = false
                            spread_chart.animateX(1500)

                            val logScale = ArrayList<String>().apply {
                                add("10") //0
                                add("100") //1
                                add("1000") //2
                                add("10K") //3
                                add("100K") //4
                                add("1M") //5
                            }

                            spread_chart.axisLeft.valueFormatter = object : LargeValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    return when (unScaleCbr(value).toInt()) {
                                        in 1..10 -> logScale[0]
                                        in 10..100 -> logScale[0]
                                        in 100..1000 -> logScale[1]
                                        in 1000..10000 -> logScale[2]
                                        in 10000..100000 -> logScale[3]
                                        in 100000..10000000 -> logScale[4]
                                        in 1000000..1000000000 -> logScale[5]
                                        else -> "1M+ :(" //OMG
                                    }
                                }
                            }
                            spread_chart.xAxis.valueFormatter = object : LargeValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    return when (unScaleCbr(value).toInt()) {
                                        in 1..10 -> logScale[0]
                                        in 10..100 -> logScale[0]
                                        in 100..1000 -> logScale[1]
                                        in 1000..10000 -> logScale[2]
                                        in 10000..100000 -> logScale[3]
                                        in 100000..10000000 -> logScale[4]
                                        in 1000000..1000000000 -> logScale[5]
                                        else -> "1M+ :(" //OMG
                                    }
                                }
                            }

                            spread_chart.axisLeft.axisMinimum = scaleCbr(10.toFloat())
                            spread_chart.axisLeft.axisMaximum = scaleCbr(1000000.toFloat())
                            spread_chart.axisLeft.isGranularityEnabled = true
                            spread_chart.axisLeft.setLabelCount(5, true)
                            spread_chart.axisLeft.textColor = Color.GRAY

                            spread_chart.xAxis.axisMinimum = scaleCbr(10.toFloat())
                            spread_chart.xAxis.axisMaximum = scaleCbr(1000000.toFloat())
                            spread_chart.xAxis.isGranularityEnabled = true
                            spread_chart.xAxis.setLabelCount(5, true)
                            spread_chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
                            spread_chart.xAxis.textColor = Color.GRAY

                            spread_chart.description.isEnabled = false
                            spread_chart.setTouchEnabled(true)
                            spread_chart.setDrawGridBackground(false)
                            spread_chart.isDragEnabled = true
                            spread_chart.setScaleEnabled(true)
                            spread_chart.setPinchZoom(true)
                            spread_chart.data = lineChart
                            spread_chart.invalidate()

                            val legend = spread_chart.legend
                            legend.isEnabled = false
                        }

                        encounterDatabase.close()
                    }
                }
            }
        }
    }

    /**
     * Convert value to log10
     */
    fun scaleCbr(value: Float): Float {
        return log10(value)
    }

    /**
     * Undo log10 back to value
     */
    fun unScaleCbr(value: Float): Float {
        return 10.0.pow(value.toDouble()).toFloat()
    }
}
