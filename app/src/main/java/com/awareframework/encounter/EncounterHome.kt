package com.awareframework.encounter

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.DatabaseUtils
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.awareframework.encounter.database.EncounterDatabase
import com.awareframework.encounter.database.User
import com.awareframework.encounter.services.EncounterService
import com.firebase.ui.auth.AuthUI
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.formatter.LargeValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.Message
import com.google.android.gms.nearby.messages.MessageListener
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_stats.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.find
import org.jetbrains.anko.imageBitmap
import org.jetbrains.anko.uiThread
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.log10
import kotlin.math.pow

class EncounterHome : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

    lateinit var googleApiClient: GoogleApiClient
    lateinit var encounterCounter: BadgeDrawable
    lateinit var db: EncounterDatabase
    lateinit var user: User

    lateinit var tabView: ViewStub
    lateinit var spread_char : LineChart

    val RC_SIGN_IN = 12345
    val guiUpdateReceiver = UIUpdate()

    companion object {
        val ACTION_NEW_DATA = "ACTION_NEW_DATA"
        lateinit var mainContainer : View
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainContainer = main_container

        tabView = find(R.id.tab_view_container)
        tabView.inflate()

        bottom_nav.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.bottom_stats -> {
                    tabView.layoutResource = R.layout.layout_stats
                    tabView.visibility = View.VISIBLE
                }
                R.id.bottom_encounters -> {
                    tabView.layoutResource = R.layout.layout_encounters
                    tabView.visibility = View.VISIBLE
                }
                R.id.bottom_symptoms -> {
                    tabView.layoutResource = R.layout.layout_symptoms
                    tabView.visibility = View.VISIBLE
                }
            }
            true
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

                doAsync {
                    db =
                        Room.databaseBuilder(
                            applicationContext,
                            EncounterDatabase::class.java,
                            "covid"
                        ).build()

                    val data = db.StatsDao().getCountryData(selectedCountry)
                    uiThread {
                        count_confirmed.text = getString(R.string.count_number, data.last().confirmed)
                        count_recovered.text = getString(R.string.count_number, data.last().recovered)
                        count_deaths.text = getString(R.string.count_number, data.last().deaths)
                    }

                    val weeklyData = db.StatsDao().getWeekly(selectedCountry)
                    val weeklyCounts = HashMap<Int, ArrayList<Int>>()
                    weeklyData.moveToFirst()
                    do {
                        val weekNumber = weeklyData.getString(weeklyData.getColumnIndex("week")).toInt()
                        val confirmedAmount = weeklyData.getString(weeklyData.getColumnIndex("confirmed")).toInt()
                        if (weeklyCounts.containsKey(weekNumber)) {
                            weeklyCounts[weekNumber]?.add(confirmedAmount)
                        } else {
                            weeklyCounts[weekNumber] = ArrayList<Int>().apply {
                                add(confirmedAmount)
                            }
                        }
                    } while(weeklyData.moveToNext())
                    weeklyData.close()

                    val weeklyDelta = ArrayList<Float>()
                    val weeklyTotals = ArrayList<Float>()
                    for(week in weeklyCounts) {
                        val min = week.value.min()
                        val max = week.value.max()
                        weeklyTotals.add(max!!.toFloat())
                        weeklyDelta.add(max.toFloat()-min!!.toFloat())
                    }

                    val plotDataPoints = ArrayList<Entry>()
                    for (i in 0 until weeklyDelta.size) {
                        plotDataPoints.add(Entry(scaleCbr(weeklyTotals[i]), scaleCbr(weeklyDelta[i]))) //convert both axis data points to logarithmic scale
                    }

                    val lineData = LineDataSet(plotDataPoints, selectedCountry)
                    lineData.setCircleColor(Color.BLUE)
                    lineData.setDrawCircleHole(true)

                    val dataset = ArrayList<ILineDataSet>()
                    dataset.add(lineData)

                    val lineChart = LineData(dataset)

                    uiThread {
                        spread_char = findViewById<LineChart>(R.id.spread_chart)

                        title = getString(R.string.country_trajectory)
                        spread_char.axisRight.isEnabled = false

                        spread_char.animateX(1500)

                        spread_char.axisLeft.valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val format = DecimalFormat("##.###")
                                return format.format(unScaleCbr(value))
                            }
                        }
                        spread_char.xAxis.valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val format = DecimalFormat("#.#")
                                return format.format(unScaleCbr(value))
                            }
                        }

                        spread_char.axisLeft.axisMinimum = scaleCbr(10.toFloat())
                        spread_char.axisLeft.axisMaximum = scaleCbr(1000000.toFloat())
                        spread_char.axisLeft.setLabelCount(5, true)

                        spread_char.xAxis.axisMinimum = scaleCbr(10.toFloat())
                        spread_char.xAxis.axisMaximum = scaleCbr(1000000.toFloat())
                        spread_char.xAxis.setLabelCount(5, true)

                        spread_char.description.isEnabled = false
                        spread_char.setTouchEnabled(true)
                        spread_char.setDrawGridBackground(true)
                        spread_char.isDragEnabled = true
                        spread_char.setScaleEnabled(true)
                        spread_char.setPinchZoom(true)
                        spread_char.data = lineChart
                        spread_char.invalidate()

                        val legend = spread_char.legend
                        legend.isEnabled = false
                    }

                    db.close()
                }
            }
        }

        val messageListener = object : MessageListener() {
            override fun onFound(p0: Message?) {
                super.onFound(p0)
            }

            override fun onLost(p0: Message?) {
                super.onLost(p0)
            }
        }

        getGoogleClient()

        doAsync {
            db =
                Room.databaseBuilder(applicationContext, EncounterDatabase::class.java, "covid")
                    .build()
            val users = db.UserDao().getUser()
            if (users.isNotEmpty()) {
                user = users.first()
                uiThread {
                    user_photo.imageBitmap =
                        BitmapFactory.decodeByteArray(user.photo, 0, user.photo?.size!!)
                    user_name.text = getString(R.string.greeting).format(user.name)
                }
            } else {
                val providers = arrayListOf(
                    AuthUI.IdpConfig.GoogleBuilder().build(),
                    AuthUI.IdpConfig.FacebookBuilder().build(),
                    AuthUI.IdpConfig.TwitterBuilder().build()
                )
                startActivityForResult(
                    AuthUI.getInstance().createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build(), RC_SIGN_IN
                )
            }

            val countries = db.StatsDao().getCountries()
            val countryAdapter = ArrayList<String>()
            for (country in countries) {
                countryAdapter.add(country.country)
            }
            Collections.sort(countryAdapter, String.CASE_INSENSITIVE_ORDER)
            uiThread {
                country_selector.adapter =
                    ArrayAdapter(applicationContext, R.layout.spinner_country, countryAdapter)
                //TODO: auto select the last selection
                //country_selector.setSelection(countryIndex, true)
                //country_selector.dispatchSetSelected(true)
            }

            db.close()
        }

        startService(Intent(applicationContext, EncounterService::class.java))
    }

    fun getGoogleClient() {
        if (::googleApiClient.isInitialized) {
            return
        }
        googleApiClient = GoogleApiClient.Builder(this)
            .addApi(Nearby.MESSAGES_API)
            .addConnectionCallbacks(this)
            .enableAutoManage(this, this)
            .build()
    }

    fun scaleCbr(value: Float) : Float {
        return log10(value)
    }

    fun unScaleCbr(value: Float) : Float {
        return 10.0.pow(value.toDouble()).toFloat()
    }

    override fun onResume() {
        super.onResume()

        val guiRefresh = IntentFilter(ACTION_NEW_DATA)
        registerReceiver(guiUpdateReceiver, guiRefresh)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser

                doAsync {

                    val photoBitmap = Picasso.get().load(user?.photoUrl).get()
                    uiThread {
                        user_photo.imageBitmap = photoBitmap
                        user_name.text = getString(R.string.greeting).format(user?.displayName)
                    }

                    val db = Room.databaseBuilder(
                        applicationContext,
                        EncounterDatabase::class.java,
                        "covid"
                    ).build()

                    val blobStream = ByteArrayOutputStream()
                    photoBitmap.compress(Bitmap.CompressFormat.PNG, 100, blobStream)

                    val userDB = User(
                        null,
                        UUID.randomUUID().toString(),
                        System.currentTimeMillis(),
                        user?.displayName!!,
                        user.toString(),
                        blobStream.toByteArray()
                    )
                    blobStream.close()

                    db.UserDao().insert(userDB)
                    db.close()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(guiUpdateReceiver)
    }

    class UIUpdate : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(ACTION_NEW_DATA)) {
                Snackbar.make(mainContainer, context?.getString(R.string.data_updated).toString(), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onConnected(p0: Bundle?) {
        println("Connected GoogleApiClient")
    }

    override fun onConnectionSuspended(errorCode: Int) {
        println("Connection stopped: error $errorCode")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        println("Connection failed: ${connectionResult.errorMessage}")
    }
}
