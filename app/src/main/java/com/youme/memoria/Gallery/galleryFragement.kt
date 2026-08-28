package com.youme.memoria.Gallery


import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.youme.memoria.ImageLoading.ImagePagingAdapter
import com.youme.memoria.ImageSizeUtil
import com.youme.memoria.PhotoRepository
import com.youme.memoria.R
import com.youme.memoria.imageViewer.imageViewerActivity
import com.youme.memoria.search.searchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.log

class GalleryFragement  : Fragment(R.layout.gallery_layout){

    private lateinit var Adapter: ImagePagingAdapter

    private lateinit var repo: PhotoRepository
    private lateinit var observer: GalleryObserver
    private  var imageEncodingJob: Job?=null
    private val imglist = mutableListOf<Uri>()

    private val indexingViewModel: IndexingViewModel by activityViewModels {
        IndexingViewModelFactory(repo, requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)
        repo = PhotoRepository(requireContext())

        Adapter = ImagePagingAdapter(){imageview ,position->
            val intent = Intent(requireContext(), imageViewerActivity::class.java)

            intent.putExtra("pos",position)



            startActivity(intent,)


        }


        val recyclerView = view.findViewById<RecyclerView>(R.id.rec)
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

        observer = GalleryObserver(Handler(Looper.getMainLooper()))


        requireContext().contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        lifecycleScope.launch {
            requestGalleryPermission()
            indexinState()

        }
        navigateToSearch()



    }
    private fun loadGallery() {
        val viewModel = GalleryViewModel(GalleryRepository(requireContext().contentResolver))
        lifecycleScope.launch {
            viewModel.galleryFlow.collectLatest { pagingData ->
                Adapter.submitData(pagingData)
            }
        }
    }
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                lifecycleScope.launch {
                    if (indexingViewModel.imglist.isEmpty()) indexingViewModel.scanGallery()
                    loadGallery()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Gallery permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        private fun requestGalleryPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                lifecycleScope.launch {
                    if (indexingViewModel.imglist.isEmpty()) indexingViewModel.scanGallery()
                    loadGallery()
                }
            }
            else -> {
                requestPermissionLauncher.launch(
                    permission
                )
            }
        }
    }
    private fun indexinState(){

        val logText = requireView().findViewById<TextView>(R.id.textView2)
        val processButton = requireView().findViewById<Button>(R.id.button)
        val progressbar  = requireView().findViewById<ProgressBar>(R.id.progressBar)


        lifecycleScope.launch {
            indexingViewModel.state.collectLatest {state->
                when(state){
                    is IndexingViewModel.IndexingState.Ready -> {
                        processButton.text="Start"
                        logText.text = "Indexed : ${state.processed}/${state.total}"
                        progressbar.isVisible=false
                        processButton.isVisible=true

                        processButton.setOnClickListener {
                            indexingViewModel.startIndexing()
                            logText.text="Loading"
                            progressbar.isIndeterminate=true
                        }

                    }
                    is IndexingViewModel.IndexingState.Running ->{
                        logText.text = "Indexing: ${state.processed}/${state.total} ETA: ${"%.1f".format(state.etaMinutes)}Min"


                        processButton.text="Pause"
                        processButton.isVisible=true
                        processButton.setOnClickListener {
                            indexingViewModel.pause()
                            logText.text = "Indexed : ${state.processed}/${state.total}"
                        }

                        progressbar.isVisible=true
                        progressbar.isIndeterminate=false


                        progressbar.max = state.total
                        progressbar.progress = state.processed
                    }
                    is IndexingViewModel.IndexingState.Completed ->{
                        processButton.isVisible=false
                        logText.text = "Indexing completed: ${state.total}"
                        progressbar.isVisible=false
                    }
                    else -> {}
                }
            }
        }


    }

    private fun navigateToSearch(){
        val searchButton =  requireView().findViewById<CardView>(R.id.searchButton)
        searchButton.setOnClickListener {
            indexingViewModel.pause()

            val intent = Intent(requireContext(), searchActivity::class.java)
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                searchButton,
                "search_transition"
            )

            startActivity(intent,options.toBundle())
        }
    }
    private fun formatEta(etaMs: Long): String {
        if (etaMs <= 0) return "< 1s"
        val totalSeconds = etaMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return when {
            minutes > 60 -> "> 1h"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    override fun onDestroy() {
        repo.unloadModel()
        super.onDestroy()
    }
}