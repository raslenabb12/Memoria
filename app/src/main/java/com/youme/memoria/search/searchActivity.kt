package com.youme.memoria.search

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.DialogCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.room.util.query
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.loadingindicator.LoadingIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.google.android.material.textfield.TextInputEditText
import com.yalantis.ucrop.UCrop
import com.youme.memoria.Album.deleteAlert
import com.youme.memoria.Gallery.DateImageBlocks
import com.youme.memoria.Gallery.GalleryDateAdapter
import com.youme.memoria.ImageLoading.ImagePagingAdapter
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.PhotoRepository
import com.youme.memoria.R
import com.youme.memoria.imageViewer.imageViewerActivity
import com.youme.memoria.roomCach.PhotoEntity
import com.youme.memoria.search.searchHistory.SearchHistroyAdapter
import com.youme.memoria.search.searchHistory.SearchHistroyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class searchActivity : AppCompatActivity() {
    private lateinit var adapterDateBlocks: GalleryDateAdapter
    private lateinit var adapterStaggeredGrid: ImagePagingAdapter
    private val searchViewModuel : SearchViewModuel by viewModels()
    private val searchHistroyViewModuel : SearchHistroyViewModel by viewModels()
    private lateinit var historyAdapter: SearchHistroyAdapter

    private val cropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
               RESULT_OK -> {
                    val croppedUri: Uri? = result.data?.let { UCrop.getOutput(it) }

                   searchViewModuel.setSearchImage(null)
                   searchViewModuel.setSearchImage(croppedUri)
                }
                UCrop.RESULT_ERROR -> {
                    val error = result.data?.let { UCrop.getError(it) }
                    error?.printStackTrace()
                }
            }
        }
    private lateinit var repo: PhotoRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_layout)
        setupPadding()
        adapterDateBlocks = GalleryDateAdapter(){ uri->
            val intent = Intent(this@searchActivity, imageViewerActivity::class.java)

            intent.putExtra("pos", SearchResultCache.searchResults?.indexOfFirst { it.uri == uri })
            intent.putExtra("from_search",true)

            startActivity(intent)

        }
        adapterStaggeredGrid = ImagePagingAdapter(){_,pos->
            val intent = Intent(this@searchActivity, imageViewerActivity::class.java)

            intent.putExtra("pos", pos)
            intent.putExtra("from_search",true)

            startActivity(intent)

        }


        repo = PhotoRepository(this@searchActivity)

        val searchUri = intent.getStringExtra("uri")
        searchUri?.let {
            searchViewModuel.setSearchImage(it.toUri())
        }

        setupRecyclerView()
        setupSearch()

        setupFilterObserver()
        setupImageSearch()
        setupTopBar()
        setupSearchHistory()
        handleShareIntent(intent)
    }

    private fun setupRecyclerView(){

        val recyclerView = findViewById<RecyclerView>(R.id.rec)
        val staggeredLayoutManager = StaggeredGridLayoutManager(
            2,
            StaggeredGridLayoutManager.VERTICAL
        )
        staggeredLayoutManager.gapStrategy =
            StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS

        lifecycleScope.launch {

            searchViewModuel.searchLayout.collectLatest { layout ->
                when(layout){
                    SearchLayout.STAGGERED->{
                        recyclerView.apply {
                            this.layoutManager = staggeredLayoutManager
                            adapter = adapterStaggeredGrid
                            itemAnimator = null
                        }
                    }
                    SearchLayout.DATEBLOCKS->{
                        recyclerView.apply {
                            this.layoutManager = LinearLayoutManager(this@searchActivity, LinearLayoutManager.VERTICAL,false)
                            adapter = adapterDateBlocks
                            itemAnimator = null
                        }
                    }
                }

            }

        }





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

    private fun showSortDialog(ancher : View){
        val popupView = layoutInflater.inflate(R.layout.dialog_sort, null)
        val popupWindow = PopupWindow(popupView,250,300)


        popupWindow.isOutsideTouchable = true

        val radioGroup = popupView.findViewById<RadioGroup>(R.id.radio_group)

        lifecycleScope.launch {
            when(searchViewModuel.searchLayout.first()){
                SearchLayout.STAGGERED->{ radioGroup.check(R.id.staggerd)}
                SearchLayout.DATEBLOCKS->{ radioGroup.check(R.id.dateBlocks)}
            }
        }

        radioGroup.setOnCheckedChangeListener { _, id ->
            when(id){
                R.id.staggerd->{searchViewModuel.setSearchLayout(SearchLayout.STAGGERED)}
                R.id.dateBlocks->{ searchViewModuel.setSearchLayout(SearchLayout.DATEBLOCKS) }
            }
        }

        popupWindow.showAsDropDown(ancher)
    }
    private fun setupImageSearch() {
        val container = findViewById<AppBarLayout>(R.id.app_bar_layout)
        val searchBar = findViewById<View>(R.id.search_bar)
        val imageToolbar =
            layoutInflater.inflate(R.layout.image_search_topbar, container, false)
        val closeButton = imageToolbar.findViewById<ImageButton>(R.id.imageButton5)
        val imageview = imageToolbar.findViewById<ImageView>(R.id.imageView7)

        lifecycleScope.launch {
            searchViewModuel.searchImage.collectLatest { image ->
                if (image!=null){
                    searchBar.isVisible = false

                    Glide.with(this@searchActivity)
                        .load(image)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(imageview)

                    container.addView(imageToolbar,1)

                    closeButton.setOnClickListener {

                        //delete cached image
                        File(cacheDir,"cropped.jpg").delete()
                        searchViewModuel.setSearchImage(null)

                    }
                }else{
                    searchBar.isVisible = true
                    container.removeView(imageToolbar)

                }
            }

        }
    }

    private fun cropImage(image: Uri){
        val destinUri = Uri.fromFile(
            File(cacheDir,"cropped.jpg")
        )
        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setFreeStyleCropEnabled(true)
        }

        val intent = UCrop.of(image,destinUri)
            .withOptions(options)
            .useSourceImageAspectRatio()
            .getIntent(this)
        cropLauncher.launch(intent)
    }

    private fun setupTopBar(){
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finishAfterTransition() }
        val pickedImage = registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                cropImage(uri)
            }
        }

        toolbar.setOnMenuItemClickListener { item ->
            when(item.itemId){
                R.id.image_search->{ pickedImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
                R.id.filter->{FilterBottomFrag().show(supportFragmentManager,"") }
                R.id.sort_view->{showSortDialog(toolbar.findViewById<View>(R.id.sort_view))}
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
                    progressbar.isVisible = state is SearchState.Loading
                    when (state) {
                        is SearchState.Loading -> {}
                        is SearchState.Success -> {
                            progressbar.isVisible = false


                            findViewById<View>(R.id.emptyState).isVisible= state.results.isEmpty()



                            val fmt = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                            val dateBlocks =state.results.groupBy { (it.second*100).toInt() }.map { Pair(it.key,it.value.map {item-> item.first }
                                .groupBy {item-> fmt.format(Date(item.dateTaken?:0)) }.map { DateImageBlocks(it.key,it.value.map {  ImageUriItem(uri = it.uri.toUri(), height = it.height, width = it.width, id = it.uri.hashCode().toLong()) }.toMutableList()) }) }
                                .flatMap {it.second}



                            val mappedList = state.results.map { it.first }.map {
                                ImageUriItem(uri = it.uri.toUri(), height = it.height, width = it.width, id = it.uri.hashCode().toLong())
                            }

                            SearchResultCache.searchResults = dateBlocks.flatMap { it.images }
                            try {
                                (recyclerView.layoutManager as StaggeredGridLayoutManager).invalidateSpanAssignments()
                            } catch (e: Exception) {
                                ""
                            }
                            adapterStaggeredGrid.submitData(lifecycle,PagingData.from(mappedList))
                            adapterDateBlocks.submitData(lifecycle,PagingData.from(dateBlocks))


                            lifecycleScope.launch {
                                searchViewModuel.searchLayout.collectLatest { layout ->
                                    when(layout){
                                        SearchLayout.STAGGERED->{ SearchResultCache.searchResults = mappedList }
                                        SearchLayout.DATEBLOCKS->{ SearchResultCache.searchResults = dateBlocks.flatMap { it.images } }
                                    }
                                }
                            }



                        }
                        is SearchState.Error -> {
                            progressbar.isVisible = false
                            Log.d("searcherror", "setupSearch: ${state.message}")
                            Toast.makeText(this@searchActivity, "Search failed. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
        adapterStaggeredGrid.addOnPagesUpdatedListener {
            recyclerView.post {
                recyclerView.scrollToPosition(0)
            }
        }
        adapterDateBlocks.addOnPagesUpdatedListener {
            recyclerView.post {
                recyclerView.scrollToPosition(0)
            }
        }


        searchView.getEditText().setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchView.getEditText().text.toString()
                lifecycleScope.launch {
                    adapterDateBlocks.submitData(PagingData.from(emptyList()))
                    adapterStaggeredGrid.submitData(PagingData.from(emptyList()))
                }

                searchViewModuel.setSearchQuery(query)
                searchHistroyViewModuel.addToHistroy(query)
                searchBar.setText(query)
                searchView.hide()
                true
            } else false
        }

    }

    private fun setupSearchHistory(){
        val searchBar = findViewById<SearchBar>(R.id.search_bar)
        val searchView = findViewById<SearchView>(R.id.search_view)
        val clearHistoryButton = findViewById<Button>(R.id.button19)


        historyAdapter = SearchHistroyAdapter(
            onPinnedClicked = { query,isPineed ->
                searchHistroyViewModuel.setPinned(query,!isPineed)
            },
            onClick = {query->
                searchViewModuel.setSearchQuery(query)
                searchHistroyViewModuel.addToHistroy(query)
                searchBar.setText(query)
                searchView.hide()
            },
            onLongClick = { query->
                deleteAlert(this@searchActivity,"Delete Search Item","Are you sure you want to delete \"$query\" from your search history? This action cannot be undone."){
                    searchHistroyViewModuel.deleteHistoryItem(query)
                }
            }
        )


        val recyclerView = findViewById<RecyclerView>(R.id.historyRec)
        recyclerView.apply {
            adapter  = historyAdapter
            layoutManager= LinearLayoutManager(this@searchActivity, LinearLayoutManager.VERTICAL,false)
        }

        clearHistoryButton.setOnClickListener {
            deleteAlert(this@searchActivity,"Clear Search History","Are you sure you want to delete all your search history? This action cannot be undone."){
                searchHistroyViewModuel.deleteHistory()
            }
        }



        lifecycleScope.launch {
            searchHistroyViewModuel.searchHistory.collectLatest {history->
                historyAdapter.submitData(history)
            }
        }
    }

    override fun onDestroy() {
        SearchResultCache.searchResults = null
        super.onDestroy()
    }

    private fun handleShareIntent(intent: Intent){
        if (intent.action== Intent.ACTION_SEND &&  intent.type?.startsWith("image/") == true){
            val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            imageUri?.let { cropImage(imageUri) }

        }
    }
    private fun setupPadding(){}

//    private fun setupPadding(){
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.linearLayout2)) { view, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//
//            val params = view.layoutParams as ViewGroup.MarginLayoutParams
//            params.topMargin = systemBars.top
//            view.layoutParams = params
//
//            insets
//        }
//    }
}