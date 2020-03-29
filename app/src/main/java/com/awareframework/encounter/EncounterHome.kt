package com.awareframework.encounter

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ContextMenu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.awareframework.encounter.database.CovidDatabase
import com.awareframework.encounter.database.User
import com.awareframework.encounter.services.EncounterService
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.Message
import com.google.android.gms.nearby.messages.MessageListener
import com.google.android.material.badge.BadgeDrawable
import com.google.firebase.auth.FirebaseAuth
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.imageBitmap
import org.jetbrains.anko.uiThread
import java.io.ByteArrayOutputStream
import java.util.*

class EncounterHome : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

    lateinit var googleApiClient: GoogleApiClient
    lateinit var encounterCounter: BadgeDrawable
    lateinit var db: CovidDatabase
    lateinit var user: User

    val RC_SIGN_IN = 12345

    companion object {
        val ACTION_NEW_DATA = "ACTION_NEW_DATA"
        var selectedTab = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectedTab = "stats"

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
                Room.databaseBuilder(applicationContext, CovidDatabase::class.java, "covid").build()
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

    override fun onResume() {
        super.onResume()
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
                        CovidDatabase::class.java,
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

    class UIUpdate : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(ACTION_NEW_DATA)) {

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
