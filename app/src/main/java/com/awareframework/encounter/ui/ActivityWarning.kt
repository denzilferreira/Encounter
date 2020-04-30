package com.awareframework.encounter.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.awareframework.encounter.R
import kotlinx.android.synthetic.main.activity_warning.*

class ActivityWarning : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warning)
    }

    override fun onResume() {
        super.onResume()

        val instructions = intent.extras?.getString("instructions")
        warning_instructions.text = instructions

        btn_ok.setOnClickListener {
            finish()
        }
    }
}