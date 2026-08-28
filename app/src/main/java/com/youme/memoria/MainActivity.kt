package com.youme.memoria


import android.os.Bundle
import android.view.ViewGroup

import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.youme.memoria.Gallery.GalleryFragement
import com.youme.memoria.settings.settings


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupPadding()
        setupBottomNav()
    }

    private fun setupBottomNav(){
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnItemSelectedListener { item->
            when(item.itemId){
                R.id.photos->{
                    replaceFrag(GalleryFragement())
                    true
                }
                R.id.settings->{
                    replaceFrag(settings())
                    true
                }
            }
            true
        }
    }
    private fun replaceFrag(frag: Fragment){
        supportFragmentManager.commit {
            replace(R.id.fragmentContainerView,frag)
            addToBackStack(null)
        }
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