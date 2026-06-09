package com.youme.memoria

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.TimeUtils
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.util.Util
import com.google.android.material.textfield.TextInputEditText
import com.youme.memoria.ImageLoading.ImageAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Time
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
class MainActivity : AppCompatActivity() {
    private lateinit var Adapter: ImageAdapter
    private lateinit var repo : PhotoRepository
    private lateinit var observer: GalleryObserver
    private lateinit var imageEncodingJob : Job
    private val imglist  = mutableListOf<Uri>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = PhotoRepository(this@MainActivity)

        setupSearch()
        requestGalleryPermission()

        Adapter = ImageAdapter(emptyList())


        val recyclerView = findViewById<RecyclerView>(R.id.rec)

        recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity,3)
            adapter = Adapter
        }


        observer = GalleryObserver(Handler(Looper.getMainLooper()))


        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        lifecycleScope.launch(Dispatchers.IO) {
            //repo.initializeImageModel()
            //scanGallery()
        }

    }
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {
                lifecycleScope.launch {
                    scanGallery()
                }
            } else {
                Toast.makeText(
                    this,
                    "Gallery permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    private fun requestGalleryPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        when {
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                lifecycleScope.launch {
                    scanGallery()
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
        val progressbarCircule = findViewById<ProgressBar>(R.id.progressBar2)
        val progressbar = findViewById<ProgressBar>(R.id.progressBar)
        val loadingBox  = findViewById<CardView>(R.id.loadingBox)
        val progressText = findViewById<TextView>(R.id.textView)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
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


        withContext(Dispatchers.Main){
            Adapter.sumbit_data(imglist)
        }
        lifecycleScope.launch {
            val alreadyExists = repo.alreadyExistsList().map { it.uri.toUri() }
            progressbarCircule.max=imglist.size
            progressbarCircule.progress = alreadyExists.size

            progressbarCircule.setOnClickListener {
                lifecycleScope.launch {
                    if (::imageEncodingJob.isInitialized && imageEncodingJob.isActive) {
                        progressbarCircule.apply {
                            max = imglist.size
                            progress = alreadyExists.size
                            isIndeterminate = false
                        }

                        imageEncodingJob.cancel()
                        repo.unloadImageModel()
                        loadingBox.isVisible = false
                        return@launch
                    }
                    progressbarCircule.isIndeterminate = true
                    progressbar.isIndeterminate = true
                    loadingBox.isVisible = true
                    progressText.text = "Loading image encoder model..."

                    repo.initializeImageModel()
                    encodeImages()
                }

            }
        }
    }
    private fun encodeImages(){
        val loadingBox  = findViewById<CardView>(R.id.loadingBox)
        val progressText = findViewById<TextView>(R.id.textView)
        val progressbar = findViewById<ProgressBar>(R.id.progressBar)
        progressbar.isIndeterminate=false
        imageEncodingJob = lifecycleScope.launch {
            val alreadyExists = repo.alreadyExistsList().map { it.uri.toUri() }
            val toProcess = imglist.filter { !alreadyExists.contains(it) }


            val allSize = toProcess.size

            withContext(Dispatchers.Main) {
                progressbar.max = allSize
                loadingBox.isVisible = true
            }
            toProcess.forEachIndexed { index, uri ->
                try {
                    Log.d("test_progress", "encodeImages: $index")
                    val startTime = System.currentTimeMillis()
                    val encodedImage = repo.encodeImage(this@MainActivity, uri)


                    repo.saveEmbedding(uri.toString(), encodedImage)
                    val endTime = System.currentTimeMillis()

                    val etaMs = (endTime - startTime).toFloat() * (allSize - index - 1)
                    val etaMin = (etaMs / 1000f) / 60
                    withContext(Dispatchers.Main) {
                        progressText.text =
                            "Encoding Image $index/${allSize} ETA: ${"%.1f".format(etaMin)}Min"
                        progressbar.progress = index
                    }
                } catch (e: Exception) {
                    // to add later
                }


            }
        }
    }
    private fun setupSearch(){
        val search_input = findViewById<TextInputEditText>(R.id.search_input)
        val loadingBox  = findViewById<CardView>(R.id.loadingBox)
        val progressText = findViewById<TextView>(R.id.textView)
        val progressbar = findViewById<ProgressBar>(R.id.progressBar)
        search_input.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {


                lifecycleScope.launch {
                    if (!repo.initializeedTextModel){
                        loadingBox.isVisible=true
                        progressbar.isIndeterminate = true
                        progressText.text = "Loading Text Encoder And Toknizer..."
                        repo.initializeTextModel()
                        loadingBox.isVisible=false
                    }
                    val data = repo.search(search_input.text.toString())
                    Log.d("test_data", "setupSearch: $data")

                    withContext(Dispatchers.Main){
                        Adapter.sumbit_data(data.map { it.first })
                    }
                }


                true
            } else {
                false
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()

        contentResolver.unregisterContentObserver(observer)
        repo.unloadModel()
    }
}