package com.awareframework.encounter

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.room.Room
import com.awareframework.encounter.database.Encounter
import com.awareframework.encounter.database.EncounterDatabase
import com.awareframework.encounter.database.User
import com.awareframework.encounter.services.EncounterService
import com.awareframework.encounter.ui.EncountersFragment
import com.awareframework.encounter.ui.InfoFragment
import com.awareframework.encounter.ui.SharingFragment
import com.awareframework.encounter.ui.StatsFragment
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.*
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*
import kotlin.collections.ArrayList

class EncounterHome : AppCompatActivity() {

    private val PERMISSIONS_ENCOUNTER = 1212

    companion object {
        val VIEW_ENCOUNTERS = "VIEW_ENCOUNTERS"
        val ACTION_UPDATE_STARTED = "ACTION_UPDATE_STARTED"
        val ACTION_UPDATE_FINISHED = "ACTION_UPDATE_FINISHED"
        val ACTION_UPDATE_COUNTRY = "ACTION_UPDATE_COUNTRY"
        val EXTRA_COUNTRY = "EXTRA_COUNTRY"
        val ENCOUNTER_BLUETOOTH = 1112

        lateinit var viewManager: FragmentManager
        lateinit var messageListener: MessageListener
        lateinit var progressBar: ProgressBar
        lateinit var progressCountry: TextView
    }

    private val timer = Timer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewManager = supportFragmentManager

        bottom_nav.setOnNavigationItemSelectedListener { menuItem ->
            lateinit var selectedFragment: Fragment
            when (menuItem.itemId) {
                R.id.bottom_stats -> {
                    selectedFragment = StatsFragment()
                }
                R.id.bottom_encounters -> {
                    selectedFragment = EncountersFragment()
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.tab_view_container, selectedFragment).commit()
            true
        }

        doAsync {
            val db =
                Room.databaseBuilder(
                    applicationContext,
                    EncounterDatabase::class.java,
                    "encounters"
                )
                    .build()
            val users = db.UserDao().getUser()
            if (users.isEmpty()) {
                val userDB = User(
                    null,
                    UUID.randomUUID().toString(), //assign a random UUID to this device
                    System.currentTimeMillis()
                )
                db.UserDao().insert(userDB)
            }
            db.close()
        }

        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                publish()
            }
        }, 15 * 1000, 60 * 1000)

        messageListener = object : MessageListener() {
            override fun onFound(message: Message?) {
                super.onFound(message)

                val uuidDetected = String(message?.content!!)
                doAsync {
                    val db = Room.databaseBuilder(
                        applicationContext,
                        EncounterDatabase::class.java,
                        "encounters"
                    ).build()

                    val user = db.UserDao().getUser().first()
                    val encounter = Encounter(
                        uid = null,
                        timestamp = System.currentTimeMillis(),
                        uuid = user.uuid,
                        uuid_detected = uuidDetected
                    )
                    db.EncounterDao().insert(encounter)
                    db.close()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.share -> {
                viewManager.beginTransaction()
                    .replace(R.id.tab_view_container, SharingFragment()).commit()
                true
            }
            R.id.encounter_info -> {
                viewManager.beginTransaction()
                    .replace(R.id.tab_view_container, InfoFragment()).commit()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()

        registerReceiver(guiUpdate, IntentFilter().apply {
            addAction(ACTION_UPDATE_STARTED)
            addAction(ACTION_UPDATE_FINISHED)
            addAction(ACTION_UPDATE_COUNTRY)
        })

        progressBar = encounter_progress
        progressCountry = progress_country

        when (intent?.action) {
            VIEW_ENCOUNTERS -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.tab_view_container, EncountersFragment())
                    .commit()
            }
            else -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.tab_view_container, StatsFragment())
                    .commit()
            }
        }

        startService(Intent(applicationContext, EncounterService::class.java))
    }

    override fun onStart() {
        super.onStart()

        checkPermissions()
        checkBluetooth()
        checkDoze()

        Nearby.getMessagesClient(
            this@EncounterHome,
            MessagesOptions.Builder().setPermissions(NearbyPermissions.DEFAULT).build()
        ).subscribe(messageListener)
    }

    override fun onStop() {
        super.onStop()

        Nearby.getMessagesClient(
            this@EncounterHome,
            MessagesOptions.Builder().setPermissions(NearbyPermissions.DEFAULT).build()
        ).unsubscribe(messageListener)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(guiUpdate)
    }

    fun publish() {
        doAsync {
            val db =
                Room.databaseBuilder(
                    applicationContext,
                    EncounterDatabase::class.java,
                    "encounters"
                ).build()

            val users = db.UserDao().getUser()
            if (users.isNotEmpty()) {
                uiThread { activity ->
                    val message = Message(users.first().uuid.toByteArray())
                    Nearby.getMessagesClient(activity)
                        .publish(
                            message,
                            PublishOptions.Builder()
                                .setStrategy(
                                    Strategy.Builder()
                                        .setTtlSeconds(59) //we send a broadcast every 1-min so each will be live for 59 seconds
                                        .setDistanceType(Strategy.DISTANCE_TYPE_EARSHOT) //approx. 1.5 meters proximity
                                        .build()
                                )
                                .build()
                        ).addOnFailureListener {
                            println("Failed to publish: ${it.message}")
                        }.addOnSuccessListener {
                            println("Published success!")
                        }
                }
            }
            db.close()
        }
    }

    private fun checkPermissions() {
        val permissions: MutableList<String> = ArrayList()
        permissions.add(Manifest.permission.BLUETOOTH)

        var permissionsGranted = true
        for (permission in permissions) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                permission
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionsGranted = false
                break
            }
        }
        if (!permissionsGranted) ActivityCompat.requestPermissions(
            this@EncounterHome,
            permissions.toTypedArray(),
            PERMISSIONS_ENCOUNTER
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.contains(PackageManager.PERMISSION_DENIED)) checkPermissions()
    }

    private fun checkBluetooth() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBT = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBT, ENCOUNTER_BLUETOOTH)
        }
    }

    private fun checkDoze() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            val powerManager =
                applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager

            val isIgnore = powerManager.isIgnoringBatteryOptimizations(packageName)
            if (!isIgnore) {
                val whitelisting =
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                startActivity(whitelisting)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ENCOUNTER_BLUETOOTH) {
            if (resultCode != Activity.RESULT_OK) {
                val notificationManager =
                    applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val name = applicationContext.getString(R.string.app_name)
                    val descriptionText = applicationContext.getString(R.string.app_name)
                    val channel = NotificationChannel(
                        "ENCOUNTER",
                        name,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = descriptionText
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val bluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                val pendingBluetooth = PendingIntent.getActivity(
                    applicationContext,
                    0,
                    bluetoothIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
                val builder = NotificationCompat.Builder(applicationContext, "ENCOUNTER")
                    .setSmallIcon(R.drawable.ic_stat_encounter_limited)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.enable_bluetooth))
                    .setContentIntent(pendingBluetooth)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                notificationManager.notify(ENCOUNTER_BLUETOOTH, builder.build())
            }
        }
    }

    private val guiUpdate = GUIUpdate()

    class GUIUpdate : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_UPDATE_STARTED -> {
                    progressBar.visibility = View.VISIBLE
                    progressCountry.visibility = View.VISIBLE
                }
                ACTION_UPDATE_FINISHED -> {
                    progressBar.visibility = View.INVISIBLE
                    progressCountry.visibility = View.INVISIBLE
                    viewManager.beginTransaction().replace(R.id.tab_view_container, StatsFragment())
                        .commit()
                }
                ACTION_UPDATE_COUNTRY -> {
                    progressCountry.text = context?.getString(R.string.text_placeholder)?.format(
                        intent.extras?.getString(
                            EXTRA_COUNTRY
                        )
                    )
                }
            }
        }
    }
}
