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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.util.Util
import com.youme.memoria.Encoder.EncodeService
import com.youme.memoria.ImageLoading.ImageAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Time
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
class MainActivity : AppCompatActivity() {
    private lateinit var Adapter: ImageAdapter
    private lateinit var repo : PhotoRepository
    private lateinit var observer: GalleryObserver
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = PhotoRepository(this@MainActivity)


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
            repo.initializeModel()
            scanGallery()
        }

    }
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {
                //scanGallery()
            } else {
                Toast.makeText(
                    this,
                    "Gallery permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    private fun requestGalleryPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                //scanGallery()
            }

            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
    }


    suspend fun scanGallery() {
        val imglist = mutableListOf<Uri>()
        val loadingBox  = findViewById<CardView>(R.id.loadingBox)
        val progressText = findViewById<TextView>(R.id.textView)
        val progressbar = findViewById<ProgressBar>(R.id.progressBar)

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

//        val intent = Intent(this, EncodeService::class.java)
//        intent.putStringArrayListExtra("uris", ArrayList(imglist.map { it.toString() }.take(100)))
//        ContextCompat.startForegroundService(this, intent)
        lifecycleScope.launch {
            val allSize = imglist.size
            var proccsed = 0
            withContext(Dispatchers.Main) {
                progressbar.max = allSize
                loadingBox.isVisible = true
            }

            imglist.forEachIndexed { index, uri ->

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


            }
//            imglist.chunked(4).forEach {uriBatch->
//
//                val result = uriBatch.map { uri->
//                    async (Dispatchers.IO){
//                        val embeddings = repo.encodeImage(this@MainActivity, uri)
//                        uri.toString() to embeddings
//                    }
//                }.awaitAll()
//
//                repo.saveEmbeddingsBatch(result)
//
//                proccsed+=uriBatch.size
//
//                withContext(Dispatchers.Main){
//                    progressText.text="Encoding Image $proccsed/${allSize}"
//                    progressbar.progress =proccsed
//                }
//            }
//        }
        withContext(Dispatchers.Main){
            Adapter.sumbit_data(imglist)
        }

        }
    }
    override fun onDestroy() {
        super.onDestroy()

        contentResolver.unregisterContentObserver(observer)
        repo.unloadModel()
    }
}