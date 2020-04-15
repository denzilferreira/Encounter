package com.awareframework.encounter.services

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import org.jetbrains.anko.defaultSharedPreferences

class EncounterWarning : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        /**
         * remoteMessage.data will contain
         * {
         *      uuid = "positive UUID",
         *      instructions = "Authority dependent instructions on how to proceed"
         * }
         *
         * we start the service to check if we have seen this uuid or not
         */
        startService(
            Intent(applicationContext, EncounterService::class.java).setAction(
                EncounterService.ACTION_CHECK_WARNING
            ).putExtra("data", Gson().toJson(remoteMessage.data))
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        defaultSharedPreferences.edit().putString("firebase_token", token).apply()
    }
}