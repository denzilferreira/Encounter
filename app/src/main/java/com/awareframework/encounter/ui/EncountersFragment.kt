package com.awareframework.encounter.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.awareframework.encounter.R
import org.jetbrains.anko.support.v4.defaultSharedPreferences

/**
 * A simple [Fragment] subclass.
 */
class EncountersFragment : Fragment() {

    companion object {
        val ACTION_NEW_ENCOUNTER = "ACTION_NEW_ENCOUNTER"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_encounters, container, false)
    }

    override fun onResume() {
        super.onResume()

        defaultSharedPreferences.edit().putString("active", "encounters").apply()


    }

    class EncounterListener : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent?.action.equals(ACTION_NEW_ENCOUNTER)) {
                println("new encounter!")
            }
        }
    }
}
