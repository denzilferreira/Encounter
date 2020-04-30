package com.awareframework.encounter.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.room.Room
import com.awareframework.encounter.R
import com.awareframework.encounter.database.EncounterDatabase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.android.synthetic.main.fragment_stats.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.find
import org.jetbrains.anko.uiThread
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

class StatsFragment : Fragment() {

    val countryAdapter = ArrayList<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        defaultSharedPreferences.edit().putString("active", "stats").apply()

        val countrySelector = view.findViewById<Spinner>(R.id.country_selector)

        doAsync {
            val db =
                Room.databaseBuilder(context!!, EncounterDatabase::class.java, "encounters").build()
            val countries = db.StatsDao().getCountries()
            for (country in countries) {
                countryAdapter.add(country.country)
            }
            db.close()
            Collections.sort(countryAdapter, String.CASE_INSENSITIVE_ORDER)

            uiThread {

                countrySelector.adapter = ArrayAdapter(context!!, R.layout.spinner_country, countryAdapter)

                countrySelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
                        val selectedCountry = countrySelector.selectedItem.toString()

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

                            val logScales = ArrayList<String>()
                            logScales.add("10")
                            logScales.add("100")
                            logScales.add("1000")
                            logScales.add("10K")
                            logScales.add("100K")
                            logScales.add("1M")
                            logScales.add("10M")
                            logScales.add("100M")

                            val maxGrowth = 10.0.pow(ceil(log10(growth.max()!!)).toDouble())
                            val maxConfirmed = 10.0.pow(ceil(log10(confirmed.max()!!)).toDouble())

                            uiThread {
                                spread_chart.axisRight.isEnabled = false
                                spread_chart.animateX(1500)

                                spread_chart.axisLeft.valueFormatter = object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        return logScales.get(value.toInt() - 1)
                                    }
                                }
                                spread_chart.xAxis.valueFormatter = object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        if (value.toInt() == 0) return logScales.get(0)
                                        return logScales.get(value.toInt() - 1)
                                    }
                                }

                                spread_chart.axisLeft.axisMinimum = scaleCbr(10f)
                                spread_chart.axisLeft.axisMaximum = scaleCbr(maxGrowth.toFloat())

                                spread_chart.axisLeft.isGranularityEnabled = true

                                val countY = log10(maxGrowth)
                                spread_chart.axisLeft.setLabelCount(countY.toInt(), true)

                                spread_chart.axisLeft.textColor = Color.GRAY

                                spread_chart.xAxis.axisMinimum = scaleCbr(10f)
                                spread_chart.xAxis.axisMaximum = scaleCbr(maxConfirmed.toFloat())

                                spread_chart.xAxis.isGranularityEnabled = true

                                val countX = log10(maxConfirmed)
                                spread_chart.xAxis.setLabelCount(countX.toInt(), true)

                                spread_chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
                                spread_chart.xAxis.textColor = Color.GRAY

                                spread_chart.description.isEnabled = false
                                spread_chart.setTouchEnabled(true)
                                spread_chart.setDrawGridBackground(false)
                                spread_chart.isDragEnabled = true
                                spread_chart.setScaleEnabled(false)
                                spread_chart.setPinchZoom(false)
                                spread_chart.data = lineChart
                                spread_chart.invalidate()

                                val legend = spread_chart.legend
                                legend.isEnabled = false
                            }

                            encounterDatabase.close()
                        }
                    }
                }

                if (defaultSharedPreferences.contains("country")) {
                    countrySelector.setSelection(
                        countryAdapter.indexOf(
                            defaultSharedPreferences.getString(
                                "country",
                                ""
                            )
                        ), true
                    )
                    countrySelector.dispatchSetSelected(true)
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
