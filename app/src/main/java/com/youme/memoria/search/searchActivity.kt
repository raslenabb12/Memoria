package com.youme.memoria.search

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.PagingData
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.loadingindicator.LoadingIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.google.android.material.textfield.TextInputEditText
import com.youme.memoria.ImageLoading.ImagePagingAdapter
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.PhotoRepository
import com.youme.memoria.R
import com.youme.memoria.imageViewer.imageViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class searchActivity : AppCompatActivity() {
    private lateinit var Adapter: ImagePagingAdapter
    private val searchViewModuel : SearchViewModuel by viewModels()
    private lateinit var repo: PhotoRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_layout)
        setupPadding()
        Adapter = ImagePagingAdapter(){imageview ,position->


            val intent = Intent(this@searchActivity, imageViewerActivity::class.java)

            intent.putExtra("pos",position)
            intent.putExtra("from_search",true)
            //intent.putExtra("matchContext",findViewById<TextInputEditText>(R.id.textedit).text.toString())


            startActivity(intent)


        }
        repo = PhotoRepository(this@searchActivity)

        val searchUri = intent.getStringExtra("uri")
        searchUri?.let {
            searchViewModuel.setSearchImage(it.toUri())
        }

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

        setupSearch()

        setupFilterObserver()
        setupImageSearch()
        setupTopBar()

    }

    private fun setupFilterObserver(){
        val filtersTagsContiner = findViewById<ChipGroup>(R.id.chipGroup2)
        lifecycleScope.launch {
            searchViewModuel.searchfilters.collectLatest {searchFilters ->
                filtersTagsContiner.removeAllViews()
                searchFilters.folders.forEach { folder->
                    val chip = Chip(this@searchActivity).apply {
                        text  = folder.trimEnd('/').substringAfterLast("/")
                        isCloseIconVisible=true
                        setOnCloseIconClickListener {
                            val folderlist = searchFilters.folders.toMutableList()
                            folderlist.remove(folder)
                            searchViewModuel.setSearchFilters(searchViewModuel.searchfilters.value.copy(folders = folderlist))

                        }
                    }
                    filtersTagsContiner.addView(chip)
                }
            }
        }
    }
    private fun setupImageSearch() {
        val container = findViewById<LinearLayout>(R.id.linearLayout2)
        val searchToolBar = findViewById<AppBarLayout>(R.id.app_bar_layout)

        lifecycleScope.launch {
            searchViewModuel.searchImage.collectLatest { image ->
                image?.let {
                    searchToolBar.isVisible = false

                    val imageToolbar =
                        layoutInflater.inflate(R.layout.image_search_topbar, container, false)
                    val closeButton = imageToolbar.findViewById<ImageButton>(R.id.imageButton5)
                    val imageview = imageToolbar.findViewById<ImageView>(R.id.imageView7)


                    Glide.with(this@searchActivity).load(image).into(imageview)

                    container.addView(imageToolbar)

                    closeButton.setOnClickListener {
                        container.removeView(imageToolbar)
                        searchToolBar.isVisible = true
                        searchViewModuel.setSearchImage(null)
                    }
                }
            }

        }
    }

    private fun setupTopBar(){
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finishAfterTransition() }
        val pickedImage = registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                searchViewModuel.setSearchImage(uri)
            }
        }

        toolbar.setOnMenuItemClickListener { item ->
            when(item.itemId){
                R.id.image_search->{ pickedImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
                R.id.filter->{FilterBottomFrag().show(supportFragmentManager,"") }
                else -> false
            }
            true
        }
    }

    private fun setupSearch(){
        val recyclerView = findViewById<RecyclerView>(R.id.rec)
        val searchBar = findViewById<SearchBar>(R.id.search_bar)
        val searchView = findViewById<SearchView>(R.id.search_view)
        val progressbar = findViewById<LoadingIndicator>(R.id.loading)

        searchView.setupWithSearchBar(searchBar)


        lifecycleScope.launch {
            progressbar.isVisible=false
                searchViewModuel.results.collectLatest { state ->
                    when (state) {
                        is SearchState.Loading -> progressbar.isVisible = true
                        is SearchState.Success -> {
                            progressbar.isVisible = false
                            val mappedList = state.results.map { it.first }.map {
                                ImageUriItem(uri = it.uri.toUri(), height = it.height, width = it.width, id = it.uri.hashCode().toLong())
                            }
                            SearchResultCache.searchResults = mappedList
                            try {
                                (recyclerView.layoutManager as StaggeredGridLayoutManager).invalidateSpanAssignments()
                            } catch (e: Exception) {
                                ""
                            }
                            Adapter.submitData(lifecycle,PagingData.from(mappedList))

                        }
                        is SearchState.Error -> {
                            progressbar.isVisible = false
                        }
                    }
                }
        }
        Adapter.addOnPagesUpdatedListener {
            recyclerView.post {
                recyclerView.scrollToPosition(0)
            }

        }


        searchView.getEditText().setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchViewModuel.setSearchQuery(searchView.getEditText().text.toString())
                searchBar.setText(searchView.getEditText().text.toString())
                searchView.hide()
                true
            } else false
        }
    }

    override fun onDestroy() {
        repo.unloadModel()
        SearchResultCache.searchResults = null
        super.onDestroy()
    }
    private fun setupPadding(){
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.linearLayout2)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = systemBars.top
            view.layoutParams = params

            insets
        }
    }
}