package com.awareframework.encounter

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.room.Room
import com.awareframework.encounter.database.EncounterDatabase
import com.awareframework.encounter.database.User
import com.awareframework.encounter.services.EncounterService
import com.awareframework.encounter.ui.EncountersFragment
import com.awareframework.encounter.ui.StatsFragment
import com.awareframework.encounter.ui.SymptomsFragment
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.dynamic.SupportFragmentWrapper
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

class EncounterHome : AppCompatActivity() {

    private val RC_SIGN_IN = 12345

    companion object {
        val ACTION_NEW_DATA = "ACTION_NEW_DATA"
        lateinit var mainContainer: View
        lateinit var viewManager: FragmentManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainContainer = main_container

        viewManager = supportFragmentManager
        viewManager.beginTransaction().replace(R.id.tab_view_container, StatsFragment())
            .commit()

        val guiRefresh = IntentFilter(EncounterHome.ACTION_NEW_DATA)
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
                R.id.bottom_symptoms -> {
                    selectedFragment = SymptomsFragment()
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.tab_view_container, selectedFragment).commit()
            true
        }

        doAsync {
            val db =
                Room.databaseBuilder(applicationContext, EncounterDatabase::class.java, "covid")
                    .build()
            val users = db.UserDao().getUser()
            if (users.isNotEmpty()) {
                val user = users.first()
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

        startService(Intent(applicationContext, EncounterService::class.java))
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

    private val guiUpdateReceiver = UIUpdate()
    class UIUpdate : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(EncounterHome.ACTION_NEW_DATA)) {
                if (context?.defaultSharedPreferences?.getString("active","").equals("stats")) {
                    Snackbar.make(
                        StatsFragment.frameContainer,
                        context?.getString(R.string.data_updated).toString(),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    viewManager.beginTransaction().replace(R.id.tab_view_container, StatsFragment()).commit()
                }
            }
        }
    }
}
