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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
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
import com.awareframework.encounter.ui.StatsFragment
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.imageBitmap
import org.jetbrains.anko.uiThread
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.ArrayList

class EncounterHome : AppCompatActivity() {

    private val RC_SIGN_IN = 12345
    private val PERMISSIONS_ENCOUNTER = 1212

    companion object {
        val ACTION_NEW_DATA = "ACTION_NEW_DATA"
        val ACTION_ENCOUNTER_PUBLISH = "ACTION_ENCOUNTER_PUBLISH"

        val VIEW_ENCOUNTERS = "VIEW_ENCOUNTERS"

        val ENCOUNTER_LOCATION = 1111
        val ENCOUNTER_BLUETOOTH = 1112
        val ENCOUNTER_BATTERY = 1113

        lateinit var viewManager: FragmentManager
        lateinit var user: User

        lateinit var messageListener: MessageListener
        lateinit var encounterContext: Context
        lateinit var message: Message

        val PUBLISH_INTERVAL : Long = 1000*60*2 //two minutes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewManager = supportFragmentManager

        if (intent?.action.equals(VIEW_ENCOUNTERS)) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tab_view_container, EncountersFragment())
                .commit()
        } else {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tab_view_container, StatsFragment())
                .commit()
        }

        user_photo.setOnClickListener {
            message = Message(user.uuid.toByteArray())
            Nearby.getMessagesClient(this@EncounterHome)
                .publish(
                    message,
                    PublishOptions.Builder()
                        .setStrategy(
                            Strategy.Builder()
                                .setTtlSeconds(Strategy.TTL_SECONDS_DEFAULT)
                                .setDistanceType(Strategy.DISTANCE_TYPE_DEFAULT)
                                .build()
                        )
                        .build()
                )
        }

        val guiRefresh = IntentFilter()
        guiRefresh.addAction(ACTION_NEW_DATA)
        registerReceiver(guiUpdateReceiver, guiRefresh)

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
            if (users.isNotEmpty()) {
                user = users.first()

                message = Message(user.uuid.toByteArray())

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
            db.close()
        }

        val handler = Handler()
        handler.postDelayed(object : Runnable {
            override fun run() {
                publish()
                handler.postDelayed(this, PUBLISH_INTERVAL)
            }
        }, PUBLISH_INTERVAL)

        startService(Intent(applicationContext, EncounterService::class.java))
    }

    fun publish() {
        Nearby.getMessagesClient(encounterContext)
            .publish(
                message,
                PublishOptions.Builder()
                    .setStrategy(
                        Strategy.Builder()
                            .setTtlSeconds(Strategy.TTL_SECONDS_DEFAULT)
                            .setDistanceType(Strategy.DISTANCE_TYPE_DEFAULT)
                            .build()
                    )
                    .build()
            )
    }

    override fun onStart() {
        super.onStart()

        encounterContext = this
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

                sendNotification(getString(R.string.encounter_detected).format(String(message.content)))
            }
        }

        Nearby.getMessagesClient(
            this,
            MessagesOptions.Builder().setPermissions(NearbyPermissions.DEFAULT).build()
        ).subscribe(messageListener)
    }

    fun sendNotification(message: String) {
        val foregroundIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(
                applicationContext,
                EncounterHome::class.java
            ).setAction(EncounterHome.VIEW_ENCOUNTERS),
            0
        )
        val notification = NotificationCompat.Builder(applicationContext, "ENCOUNTER")
        notification.setSmallIcon(R.drawable.ic_stat_encounter_warning)
        notification.setContentIntent(foregroundIntent)
        notification.priority = NotificationCompat.PRIORITY_DEFAULT
        notification.setContentTitle(getString(R.string.app_name))
        notification.setContentText(message)
        notification.setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.setChannelId("ENCOUNTER")

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(EncounterService.ENCOUNTER_WARNING, notification.build())
    }

    override fun onResume() {
        super.onResume()

        checkPermissions()
        checkBluetooth()
        checkLocation()
        checkDoze()
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

    private fun checkLocation() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )
        if (!isLocationEnabled) {
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

            val locationIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingLocation = PendingIntent.getActivity(
                applicationContext,
                0,
                locationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(applicationContext, "ENCOUNTER")
                .setSmallIcon(R.drawable.ic_stat_encounter_limited)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.enable_location))
                .setContentIntent(pendingLocation)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            notificationManager.notify(ENCOUNTER_LOCATION, builder.build())
        }
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

                val batteryIntent =
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                val pendingBattery = PendingIntent.getActivity(
                    applicationContext,
                    0,
                    batteryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
                val builder = NotificationCompat.Builder(applicationContext, "ENCOUNTER")
                    .setSmallIcon(R.drawable.ic_stat_encounter_battery)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.enable_doze))
                    .setContentIntent(pendingBattery)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)

                notificationManager.notify(ENCOUNTER_BATTERY, builder.build())
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
                        "encounters"
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

                checkPermissions()
                checkLocation()
                checkBluetooth()
                checkDoze()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(guiUpdateReceiver)

        //Start app again so that we can continue broadcasting UUID
        startActivity(Intent(applicationContext, EncounterHome::class.java))
    }

    private val guiUpdateReceiver = UIUpdate()

    class UIUpdate : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(ACTION_ENCOUNTER_PUBLISH)) {
                Nearby.getMessagesClient(encounterContext)
                    .publish(
                        message,
                        PublishOptions.Builder()
                            .setStrategy(
                                Strategy.Builder()
                                    .setTtlSeconds(Strategy.TTL_SECONDS_DEFAULT)
                                    .setDistanceType(Strategy.DISTANCE_TYPE_DEFAULT)
                                    .build()
                            )
                            .build()
                    )
            }

            if (intent?.action.equals(ACTION_NEW_DATA)) {
                when (context?.defaultSharedPreferences?.getString("active", "")) {
                    "stats" -> {
                        Snackbar.make(
                            StatsFragment.frameContainer,
                            context.getString(R.string.data_updated).toString(),
                            Snackbar.LENGTH_SHORT
                        ).show()
                        viewManager.beginTransaction()
                            .replace(R.id.tab_view_container, StatsFragment()).commit()
                    }
                    "encounters" -> {
                        viewManager.beginTransaction()
                            .replace(R.id.tab_view_container, EncountersFragment()).commit()
                    }
                }
            }
        }
    }
}
