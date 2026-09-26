package com.youme.memoria.Gallery


import android.Manifest
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.youme.memoria.ImageLoading.ImagePagingAdapter
import com.youme.memoria.PhotoRepository
import com.youme.memoria.R
import com.youme.memoria.imageViewer.imageViewerActivity
import com.youme.memoria.search.searchActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
            loadGallery()
            indexingState()


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

    private fun indexingState(){
        val toolbar = requireView().findViewById<MaterialToolbar>(R.id.toolbar)

        val largeIndicatorBox = requireView().findViewById<MaterialCardView>(R.id.big_indicator)
        val logText = requireView().findViewById<TextView>(R.id.textView2)
        val processButton = requireView().findViewById<MaterialButton>(R.id.button)
        val progressbar  = requireView().findViewById<LinearProgressIndicator>(R.id.progressbar)

        val smallIndicatorBox = requireView().findViewById<MaterialCardView>(R.id.small_indicator)
        val smallIndicatorText = requireView().findViewById<TextView>(R.id.textView12)
        val smallIndicatorProgress = requireView().findViewById<LinearProgressIndicator>(R.id.prog2)

        val collapseButton  = requireView().findViewById<Button>(R.id.button5)

        collapseButton.setOnClickListener {
            animateIndicator(largeIndicatorBox,smallIndicatorBox)
            toolbar.title=""
        }
        smallIndicatorBox.setOnClickListener {
            animateIndicator(smallIndicatorBox,largeIndicatorBox,toolbar)
        }






        lifecycleScope.launch {
            indexingViewModel.state.collectLatest {state->
                when(state){
                    is IndexingViewModel.IndexingState.Ready -> {
                        processButton.setIconResource(R.drawable.baseline_play_arrow_24)
                        logText.text = "Indexed : ${state.processed}/${state.total}"

                        smallIndicatorText.text = "Paused"

                        progressbar.apply {
                            max = state.total
                            progress  =state.processed
                            isIndeterminate=false
                        }
                        smallIndicatorProgress.apply {
                            max = state.total
                            progress  =state.processed
                        }

                        processButton.isVisible=true
                        processButton.setOnClickListener {
                            indexingViewModel.startIndexing()
                            logText.text="Loading"
                            progressbar.isIndeterminate=true
                        }

                    }
                    is IndexingViewModel.IndexingState.Running ->{
                        logText.text = "Indexing: ${state.processed}/${state.total}"
                        smallIndicatorText.text = "${state.processed}/${state.total}"

                        processButton.setIconResource(R.drawable.baseline_pause_24)
                        processButton.isVisible=true
                        processButton.setOnClickListener {
                            indexingViewModel.pause()
                            logText.text = "Indexed : ${state.processed}/${state.total}"
                        }

                        progressbar.isVisible=true
                        progressbar.isIndeterminate=false


                        progressbar.apply {
                            max =  state.total
                            progress = state.processed
                        }
                        smallIndicatorProgress.apply {
                            max = state.total
                            progress  =state.processed
                        }
                    }
                    is IndexingViewModel.IndexingState.Completed ->{
                        processButton.isVisible=false
                        logText.text = "Indexing completed: ${state.total}"
                        progressbar.isIndeterminate=false


                        progressbar.apply {
                            max =  state.total
                            progress = state.total
                        }
                        smallIndicatorProgress.apply {
                            max = state.total
                            progress  =state.total
                        }


                        smallIndicatorText.text = "Completed"
                    }
                    else -> {}
                }
            }
        }


    }
    private fun animateIndicator(visibleView : View,hiddenView : View,toolbar: MaterialToolbar?=null){
        visibleView.animate().scaleX(0f).scaleY(0f).setDuration(200).withEndAction {
            visibleView.isVisible=false
            hiddenView.apply {
                isVisible=true
                scaleX=0f
                scaleY=0f
            }
            toolbar?.apply {
                title="Memoria"
            }
            hiddenView.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
        }.start()
    }

    private fun navigateToSearch(){
        val toolbar = requireView().findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            when(item.itemId){
                R.id.search->{
                    indexingViewModel.pause()
                    val intent = Intent(requireContext(), searchActivity::class.java)
                    startActivity(intent)
                }
            }
            true

        }
    }


}