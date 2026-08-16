package com.youme.memoria.search

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.youme.memoria.ImageLoading.ImagePagingAdapter
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.PhotoRepository
import com.youme.memoria.R
import com.youme.memoria.imageViewer.imageViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class searchActivity : AppCompatActivity() {
    private lateinit var Adapter: ImagePagingAdapter

    private lateinit var repo: PhotoRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_layout)
        setupPadding()
        Adapter = ImagePagingAdapter(){imageview ,position->


            val intent = Intent(this@searchActivity, imageViewerActivity::class.java)

            intent.putExtra("pos",position)
            intent.putExtra("from_search",true)
            intent.putExtra("matchContext",findViewById<TextInputEditText>(R.id.textedit).text.toString())


            startActivity(intent)


        }
        repo = PhotoRepository(this@searchActivity)

        val recyclerView = findViewById<RecyclerView>(R.id.rec)
        val layoutManager = StaggeredGridLayoutManager(
            2,
            StaggeredGridLayoutManager.VERTICAL
        )
        layoutManager.gapStrategy =
            StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS

        recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = Adapter
        }

        val backButton = findViewById<ImageButton>(R.id.imageButton)
        backButton.setOnClickListener {
            finishAfterTransition()
        }
        setupSearch()
    }

    private fun setupSearch(){
        val searchInput = findViewById<TextInputEditText>(R.id.textedit)
        val progressbar = findViewById<ProgressBar>(R.id.progressBar3)

        searchInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {

                Log.d("test_Search", "setupSearch: start search")
                lifecycleScope.launch {
                    if (!repo.initializeedTextModel){
                        progressbar.isVisible=true
                        repo.initializeTextModel()

                    }
                    val data = repo.search(searchInput.text.toString())

                    withContext(Dispatchers.Main){
                        val mappedList = data.map { it.first }.map {
                            ImageUriItem(uri = it.uri.toUri(), height = it.height, width = it.width, id = 0)
                        }
                        SearchResultCache.searchResults = mappedList
                        Adapter.submitData(PagingData.from(mappedList))

                        progressbar.isVisible=false
                    }
                }
                true
            } else {
                false
            }
        }
    }

    override fun onDestroy() {
        repo.unloadModel()
        SearchResultCache.searchResults = null
        super.onDestroy()
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