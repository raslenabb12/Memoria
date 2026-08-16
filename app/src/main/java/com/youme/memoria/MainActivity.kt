package com.youme.memoria


import android.os.Bundle
import android.view.ViewGroup

import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupPadding()
    }
    private fun setupPadding(){
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = systemBars.top
            view.layoutParams = params

            insets
        }
    }
}