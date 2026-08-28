package com.youme.memoria.imageViewer

import android.content.Intent
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.viewpager2.widget.ViewPager2
import com.youme.memoria.Gallery.GalleryRepository
import com.youme.memoria.Gallery.GalleryViewModel
import com.youme.memoria.R
import com.youme.memoria.search.SearchResultCache
import com.youme.memoria.search.searchActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class imageViewerActivity : AppCompatActivity(){
    private lateinit var Adatper : ImagePagerAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.imageview_activity)
        Adatper = ImagePagerAdapter()

        findViewById<ImageButton>(R.id.imageButton2).setOnClickListener {
            finishAfterTransition()
        }


        setupViewPager()
        setupUi()
        setupPadding()
    }

    private fun setupUi(){
        val viewpager2= findViewById<ViewPager2>(R.id.viewpager)
        val isFromSearch = intent.getBooleanExtra("from_search",false)
        val openAppGalleryButton = findViewById<Button>(R.id.button2)

        val matchContextCard = findViewById<CardView>(R.id.cardView2)
        val matchContextText = findViewById<TextView>(R.id.textView3)
        if (isFromSearch){
            matchContextCard.isVisible=true
            matchContextText.text = "Matched: '${intent.getStringExtra("matchContext")}'"
        }

        openAppGalleryButton.setOnClickListener {
            val currentItem = Adatper.peek(viewpager2.currentItem)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(currentItem?.uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(intent)
        }
        findViewById<ImageButton>(R.id.imageButton7).setOnClickListener {
            val intent = Intent(this@imageViewerActivity, searchActivity::class.java)
            intent.putExtra("uri",Adatper.peek(viewpager2.currentItem)?.uri.toString())
            startActivity(intent)
        }

    }
    private fun setupViewPager(){

        val viewpager2= findViewById<ViewPager2>(R.id.viewpager)
        val viewModel = GalleryViewModel(GalleryRepository(contentResolver))

        viewpager2.adapter = Adatper

        val tappedPosition = intent.getIntExtra("pos",0)
        val isFromSearch = intent.getBooleanExtra("from_search",false)


        lifecycleScope.launch {
            if (isFromSearch) {
                val searchData = SearchResultCache.searchResults
                if (searchData != null) {
                    Adatper.submitData(PagingData.from(searchData))
                }
            } else {
                val viewModel = GalleryViewModel(GalleryRepository(contentResolver))
                viewModel.imageViewerFlow(tappedPosition).collectLatest {
                    Adatper.submitData(it)
                }
            }

        }
        var isPositionSet = false
        Adatper.addOnPagesUpdatedListener {
            if (!isPositionSet && Adatper.itemCount > tappedPosition) {
                isPositionSet = true
                viewpager2.post {
                    viewpager2.setCurrentItem(tappedPosition, false)
                }
            }
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