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
import android.provider.Settings
import android.view.View
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
import com.awareframework.encounter.ui.EncounterPublisher
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
        val VIEW_ENCOUNTERS = "VIEW_ENCOUNTERS"

        val ENCOUNTER_WARNING = 1104
        val ENCOUNTER_LOCATION = 1111
        val ENCOUNTER_BLUETOOTH = 1112

        lateinit var mainContainer: View
        lateinit var viewManager: FragmentManager

        lateinit var user: User

        lateinit var messageListener: MessageListener

        lateinit var encounterHomeContext: Context
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        encounterHomeContext = this

        mainContainer = main_container
        viewManager = supportFragmentManager

        if (intent?.action.equals(VIEW_ENCOUNTERS)) {
            viewManager.beginTransaction().replace(R.id.tab_view_container, EncountersFragment())
                .commit()
        } else {
            viewManager.beginTransaction().replace(R.id.tab_view_container, StatsFragment())
                .commit()
        }

        user_photo.setOnClickListener {
            startActivity(
                Intent(
                    applicationContext,
                    EncounterPublisher::class.java
                ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }

        val guiRefresh = IntentFilter(ACTION_NEW_DATA)
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

        messageListener = object : MessageListener() {
            override fun onFound(message: Message?) {
                super.onFound(message)

                val uuidDetected = String(message?.content!!)
                println("Nearby UUID: $uuidDetected")

                val encounter = Encounter(
                    uid = null,
                    uuid = user.uuid,
                    timestamp = System.currentTimeMillis(),
                    uuid_detected = uuidDetected
                )

                doAsync {
                    val db =
                        Room.databaseBuilder(
                            applicationContext,
                            EncounterDatabase::class.java,
                            "encounters"
                        ).build()

                    db.EncounterDao().insert(encounter)

                    sendBroadcast(
                        Intent(EncountersFragment.ACTION_NEW_ENCOUNTER).putExtra(
                            "message",
                            "Found: ${encounter.uuid_detected}"
                        )
                    )

                    sendNotification("Detected: $uuidDetected")
                }
            }
        }

        Nearby.getMessagesClient(this@EncounterHome)
            .registerStatusCallback(object : StatusCallback() {
                override fun onPermissionChanged(isNearbyAllowed: Boolean) {
                    super.onPermissionChanged(isNearbyAllowed)
                    println("Nearby allowed: $isNearbyAllowed")
                }
            })
            .addOnSuccessListener {
                println("Nearby Encounters started successfully")
            }
            .addOnFailureListener {
                println("Nearby Encounters failed: ${it.message}")
            }

        val subOptions = SubscribeOptions.Builder()
            .setStrategy(
                Strategy.Builder()
                    .setDiscoveryMode(Strategy.DISCOVERY_MODE_DEFAULT)
                    .setDistanceType(Strategy.DISTANCE_TYPE_EARSHOT) //1.5 meters
                    .setTtlSeconds(Strategy.TTL_SECONDS_MAX)
                    .build()
            )
            .setCallback(object : SubscribeCallback() {
                override fun onExpired() {
                    super.onExpired()
                    println("Subscription expired...")
                }
            })
            .build()

        Nearby.getMessagesClient(this@EncounterHome).subscribe(messageListener, subOptions)
        Nearby.getMessagesClient(this).subscribe(
            PendingIntent.getService(
                applicationContext,
                0,
                Intent(
                    applicationContext,
                    EncounterService::class.java
                ).setAction(EncounterService.ACTION_NEW_ENCOUNTER),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        startService(Intent(applicationContext, EncounterService::class.java))
    }

    override fun onResume() {
        super.onResume()

        checkPermissions()
        checkBluetooth()
        checkLocation()
    }

    private fun checkPermissions() {
        val permissions: MutableList<String> = ArrayList()
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE)
        permissions.add(Manifest.permission.BLUETOOTH)
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        permissions.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

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
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(guiUpdateReceiver)

        //NOTE: On purpose, we don't want Google Services to stop notifying us about the Nearby published messages
        //Nearby.getMessagesClient(applicationContext).unsubscribe(getPendingIntent())
    }

    private val guiUpdateReceiver = UIUpdate()

    class UIUpdate : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.setChannelId("ENCOUNTER")

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ENCOUNTER_WARNING, notification.build())
    }
}
