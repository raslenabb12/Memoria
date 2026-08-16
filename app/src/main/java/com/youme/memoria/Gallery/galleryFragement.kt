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
                    scanGallery()
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
                    scanGallery()
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
    suspend fun scanGallery() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        requireContext().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->

            val idCol =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

            val nameCol =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {

                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)

                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                imglist.add(uri)

            }
        }
        encodeImages()


        withContext(Dispatchers.Main){
            //viewModel.loadImages(requireContext(),imglist)
        }
    }
    private fun navigateToSearch(){
        val searchButton =  requireView().findViewById<CardView>(R.id.searchButton)
        searchButton.setOnClickListener {

            if (imageEncodingJob?.isActive == true) {
                imageEncodingJob?.cancel()
            }

            val intent = Intent(requireContext(), searchActivity::class.java)
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                searchButton,
                "search_transition"
            )

            startActivity(intent,options.toBundle())
        }
    }

    private fun encodeImages() {
        val logText = requireView().findViewById<TextView>(R.id.textView2)
        val processButton = requireView().findViewById<Button>(R.id.button)
        val progressbar  = requireView().findViewById<ProgressBar>(R.id.progressBar)
        lifecycleScope.launch {
            val alreadyExists = repo.alreadyExistsList().map { it.uri.toUri() }.toMutableList()
            val toProcess = imglist.filter { !alreadyExists.contains(it) }

            var processedSize = alreadyExists.size

            if (toProcess.isEmpty()) {
                logText.text = "All ${imglist.size} images indexed "
                processButton.isVisible = false
                progressbar.isVisible = false
                return@launch
            }

            logText.text = "Indexed: ${alreadyExists.size}/${imglist.size}"
            processButton.isVisible=true
            progressbar.isVisible=false

            processButton.setOnClickListener {
                if (toProcess.isEmpty()) return@setOnClickListener

                if (imageEncodingJob?.isActive == true) {
                    imageEncodingJob?.cancel()
                    return@setOnClickListener
                }

                imageEncodingJob = lifecycleScope.launch {
                    try {
                        processButton.text = "Pause"
                        processButton.isEnabled = false

                        logText.text = "Initializing AI model..."
                        progressbar.isVisible = true
                        progressbar.isIndeterminate = true


                        repo.initializeImageModel()

                        ensureActive()

                        progressbar.isIndeterminate = false
                        progressbar.max = toProcess.size
                        progressbar.progress = processedSize

                        processButton.isEnabled = true

                        toProcess.forEachIndexed { index, uri ->

                            ensureActive()
                            val startTime = System.currentTimeMillis()

                            try {
                                val encodedImage =
                                    repo.encodeImage(requireContext(), uri)
                                val sizeData = ImageSizeUtil.getImageDimensions(requireContext(),uri)
                                repo.saveEmbedding(
                                    uri.toString(),
                                    encodedImage,
                                    sizeData?.second?:0,
                                    sizeData?.first?:0
                                )
                                alreadyExists.add(uri)

                            } catch (e: CancellationException) {
                                throw e

                            } catch (e: Exception) {
                                Log.e("ImageEncoding", "Failed: $uri", e)
                            }


                            val endTime = System.currentTimeMillis()

                            val etaMs = (endTime - startTime).toFloat() * (imglist.size - index - 1)
                            val etaMin = (etaMs / 1000f) / 60

                            processedSize++

                            logText.text = "Indexing: $processedSize/${toProcess.size} ETA: ${"%.1f".format(etaMin)}Min"

                            progressbar.progress = processedSize
                        }

                        logText.text = "Indexing completed ($processedSize / ${imglist.size})"
                        if (alreadyExists.size == imglist.size){
                            processButton.isVisible=false
                        }
                        processButton.text = "Start"

                    } catch (e: CancellationException) {

                        logText.text = "Cancelled"

                        processButton.text = "Start"
                        processButton.isEnabled=false

                    } catch (e: Exception) {
                        Log.e("ImageEncoding", "Encoding failed", e)

                        logText.text =
                            "Failed to initialize image encoder: ${e.message}"

                        processButton.text = "Start"
                    } finally {
                        lifecycleScope.launch {
                            logText.text = "Releasing resources..."


                            repo.unloadModel()


                            logText.text =  "Indexed: ${processedSize}/${imglist.size}"
                        }


                        progressbar.isVisible = false
                        processButton.isEnabled = true
                    }
                }
            }

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