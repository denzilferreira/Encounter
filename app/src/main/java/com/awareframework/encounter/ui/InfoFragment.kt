package com.awareframework.encounter.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.awareframework.encounter.R
import kotlinx.android.synthetic.main.fragment_info.*

class InfoFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_info, container, false)
    }

    override fun onResume() {
        super.onResume()
        encounter_webview.loadUrl("https://encounter.awareframework.com/home/research")
        encounter_webview.settings.javaScriptEnabled = true
    }
}