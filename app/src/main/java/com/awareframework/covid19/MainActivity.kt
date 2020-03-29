package com.awareframework.covid19

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import androidx.room.RoomDatabase
import com.awareframework.covid19.database.CovidDatabase
import com.awareframework.covid19.database.User
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
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

class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

    lateinit var googleApiClient: GoogleApiClient
    lateinit var encounterCounter: BadgeDrawable
    lateinit var db: CovidDatabase
    lateinit var user: User

    val RC_SIGN_IN = 12345

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
            db = Room.databaseBuilder(applicationContext, CovidDatabase::class.java, "covid").build()
            val users = db.UserDao().getUser()
            if (users.isNotEmpty()) {
                user = users.first()
                uiThread {
                    user_photo.imageBitmap = BitmapFactory.decodeByteArray(user.photo,0,user.photo?.size!!)
                    user_name.text = getString(R.string.greeting).format(user.name)
                }
            } else {
                val providers = arrayListOf(
                    AuthUI.IdpConfig.GoogleBuilder().build(),
                    AuthUI.IdpConfig.FacebookBuilder().build(),
                    AuthUI.IdpConfig.MicrosoftBuilder().build(),
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
