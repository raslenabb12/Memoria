package com.youme.memoria

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.room.Query
import com.youme.inkdex.roomCach.FolderCount
import com.youme.inkdex.roomCach.PhotoEntity
import com.youme.inkdex.roomCach.PhotosDatabase
import com.youme.inkdex.roomCach.toByteArray
import com.youme.inkdex.roomCach.toFloatArray
import com.youme.memoria.Encoder.MemoriaEncoder
import com.youme.memoria.ImageLoading.ImageUriItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SearchFilters(
    val startDate: Long?,
    val endDate: Long?,
    val folders: List<String>,
    val camera: String?
)

class PhotoRepository(context: Context) {

    private val dao = PhotosDatabase.getInstance(context).photoDao()
    private val memoriaEncoder = MemoriaEncoder(context)

    suspend fun getCountPhotos() = dao.count()
    suspend fun initializeImageModel() {
        withContext(Dispatchers.IO){
            memoriaEncoder.initializeImageEncoder()
        }
    }

    private val initMutex = Mutex()
    @Volatile var initializeedTextModel = false
        private set
    suspend fun initializeTextModel() {
        if (initializeedTextModel) return
        withContext(Dispatchers.IO){
            memoriaEncoder.initializeTextEncoder()
            initializeedTextModel = true
        }
    }
    suspend fun ensureTextModelInitialized() {
        if (initializeedTextModel) return
        initMutex.withLock {
            if (initializeedTextModel) return
            initializeTextModel()
            initializeedTextModel = true
        }
    }
    suspend fun getFoldersList(): List<FolderCount> = dao.getAvailableFolders()

    suspend fun getCameraList() = dao.getAvailableCameras()


    suspend fun alreadyExistsList() = dao.getAll()

    suspend fun indexedImagesFiltered(searchFilters :SearchFilters ) =  dao.getFiltered(
        searchFilters.startDate,
        searchFilters.endDate,
        searchFilters.folders,
        searchFilters.camera,
        foldersEmpty = searchFilters.folders.isEmpty()
    )

    suspend fun encodeImage(context : Context,image: Uri) : FloatArray {
        return withContext(Dispatchers.IO){ memoriaEncoder.encodeImage(uriToBitmap(context,image)) }
    }

    suspend fun search(query: String,indexedImages : List<PhotoEntity>) : List<Pair<PhotoEntity,Float>>{
        return withContext(Dispatchers.IO){
            val encodedText = memoriaEncoder.encodeText(query)
            val allPhotos = indexedImages

            allPhotos.map { photo->
                val score  = memoriaEncoder.cosineSimilarity(encodedText,photo.embedding.toFloatArray())
                photo to score
            }.sortedByDescending { it.second }

        }
    }
    suspend fun searchByImage(context : Context,image: Uri,indexedImages : List<PhotoEntity>) : List<Pair<PhotoEntity,Float>>{
        return withContext(Dispatchers.IO){
            memoriaEncoder.initializeImageEncoder()
            val encodedImage= memoriaEncoder.encodeImage(uriToBitmap(context,image))
            val allPhotos = indexedImages

            allPhotos.map { photo->
                val score  = memoriaEncoder.cosineSimilarity(encodedImage,photo.embedding.toFloatArray())
                photo to score
            }.sortedByDescending { it.second }
        }
    }

    suspend fun saveEmbedding(uri: String, embedding: FloatArray,height: Int,width : Int,
                              dateTaken: Long?,
                              folderPath: String?,
                              cameraMake: String?,
                              cameraModel: String?) {
        if (alreadyExists(uri)) return
        dao.insert(PhotoEntity(
            uri = uri, embedding = embedding.toByteArray(),
            width = width, height = height,
            dateTaken = dateTaken, folderPath = folderPath,
            cameraMake = cameraMake, cameraModel = cameraModel
        ))
    }
    suspend fun alreadyExists(uri: String): Boolean = dao.existsByUri(uri)!=null

     fun unloadModel() = memoriaEncoder.close()
    fun unloadImageModel() = memoriaEncoder.freeImageEncoder()

    fun uriToBitmap(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxDim = maxOf(info.size.width, info.size.height)
            if (maxDim > 1024) {
                val scale = 1024f / maxDim
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt()
                )
            }
        }
    }
}