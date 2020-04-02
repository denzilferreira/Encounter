package com.awareframework.encounter.ui

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
class SymptomsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_symptoms, container, false)
    }

    override fun onResume() {
        super.onResume()

        defaultSharedPreferences.edit().putString("active", "symptoms").apply()
    }
}
