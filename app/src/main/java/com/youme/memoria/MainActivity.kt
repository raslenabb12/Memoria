package com.youme.memoria


import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.view.ViewGroup

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.youme.memoria.Album.AlbumFragment
import com.youme.memoria.Gallery.GalleryFragement
import com.youme.memoria.onboarding.OnboardingPrefs
import com.youme.memoria.onboarding.SetupScreen
import com.youme.memoria.settings.settings
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_YES
        )
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            OnboardingPrefs.isOnboardingDone(this@MainActivity).collect { done ->
                if (!done) {
                    navigateToOnboarding()
                }else{
                    setupScreen()
                }
            }
        }




    }
    private fun setupScreen(){
        setContentView(R.layout.activity_main)
        setupPadding()
        setupBottomNav()
    }
    private fun navigateToOnboarding(){
        val intent = Intent(this@MainActivity,SetupScreen::class.java)
        intent.flags=Intent.FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun setupBottomNav(){
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnItemSelectedListener { item->
            when(item.itemId){
                R.id.photos->{
                    replaceFrag(GalleryFragement())
                }
                R.id.settings->{
                    replaceFrag(settings())
                }
                R.id.album->{
                    replaceFrag(AlbumFragment())
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