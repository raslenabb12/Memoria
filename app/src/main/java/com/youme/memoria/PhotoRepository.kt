package com.youme.memoria

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.room.Query
import com.youme.memoria.Encoder.MemoriaEncoder
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.roomCach.FolderCount
import com.youme.memoria.roomCach.PhotoEntity
import com.youme.memoria.roomCach.PhotosDatabase
import com.youme.memoria.roomCach.toByteArray
import com.youme.memoria.roomCach.toFloatArray
import com.youme.memoria.settings.FoldersManager.FolderPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Preset(val score: Float) {
    LOOSE(0.14f), BALANCED(0.18f), STRICT(0.22f)
}
data class SearchFilters(
    val startDate: Long?,
    val endDate: Long?,
    val folders: List<String>,
    val camera: String?,
    val confidence : Preset  = Preset.BALANCED
)

class PhotoRepository(context: Context) {

    private val dao = PhotosDatabase.getInstance(context).photoDao()
    private val memoriaEncoder = MemoriaEncoder(context)

    suspend fun getCountPhotos() = dao.count()

    private val imageModelMutex = Mutex()


    suspend fun initializeImageModel() {
        imageModelMutex.withLock {
            withContext(Dispatchers.IO){
                memoriaEncoder.initializeImageEncoder()
            }
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
    suspend fun markAsFailed(uri: String) {
        if (alreadyExists(uri)) return
        dao.insert(PhotoEntity(
            uri = uri,
            embedding = ByteArray(0),
            width = 0,
            height = 0
        ))
    }

    suspend fun encodeImage(context : Context,image: Uri) : FloatArray {
        return withContext(Dispatchers.IO){
            val bitmap = uriToBitmap(context, image)
            try {
                memoriaEncoder.encodeImage(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
    }
    suspend fun searchByEmbd(embedding: FloatArray,indexedImages : List<PhotoEntity>,confidence: Float) : List<Pair<PhotoEntity,Float>>{
        return withContext(Dispatchers.IO){
            val allPhotos = indexedImages

            allPhotos.filter { it.embedding.isNotEmpty() }.map { photo->
                val score  = memoriaEncoder.cosineSimilarity(embedding,photo.embedding.toFloatArray())
                photo to score
            }.sortedByDescending { it.second }.filter { it.second>=confidence }
        }
    }

    suspend fun encodeAndSaveImage(context : Context,uri: Uri){
        withContext(Dispatchers.IO){
            initializeImageModel()
            val bitmap = uriToBitmap(context, uri)
            try {
                val encodedImage= memoriaEncoder.encodeImage(bitmap)
                val sizeData = ImageSizeUtil.getImageDimensions(context,uri)
                val photoMetaData = ImageSizeUtil.getPhotoMetadata(context,uri)
                val cameraInfo = ImageSizeUtil.getCameraInfo(context,uri)
                saveEmbedding(
                    uri.toString(),
                    encodedImage,
                    sizeData?.second?:0,
                    sizeData?.first?:0,
                    photoMetaData?.first,
                    photoMetaData?.second,
                    cameraInfo.first,
                    cameraInfo.second,

                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun search(query: String, indexedImages : List<PhotoEntity>, confidence: Float) : List<Pair<PhotoEntity,Float>>{
        return withContext(Dispatchers.Default){
            val encodedText = memoriaEncoder.encodeText(query)

            val candidates = indexedImages.filter { it.embedding.isNotEmpty() }
            if (candidates.isEmpty()) return@withContext emptyList()


            val cores = Runtime.getRuntime().availableProcessors()
            val chunkCount = cores.coerceAtMost(candidates.size).coerceAtLeast(1)
            val chunkSize = (candidates.size+chunkCount-1)/chunkCount
            val chunks = candidates.chunked(chunkSize)

            val partialResults: List<List<Pair<PhotoEntity, Float>>> = chunks.map { chunk ->
                async {
                    chunk.map { photo ->
                        photo to memoriaEncoder.cosineSimilarity(encodedText, photo.embedding.toFloatArray())
                    }
                        .filter { it.second >= confidence }
                        .sortedByDescending { it.second }
                }
            }.awaitAll()

            mergeSortedDescending(partialResults)
        }
    }
    private fun mergeSortedDescending(
        lists: List<List<Pair<PhotoEntity, Float>>>
    ): List<Pair<PhotoEntity, Float>> {
        val result = ArrayList<Pair<PhotoEntity, Float>>(lists.sumOf { it.size })
        val indices = IntArray(lists.size)

        while (true) {
            var bestListIdx = -1
            var bestScore = Float.NEGATIVE_INFINITY

            for (i in lists.indices) {
                val idx = indices[i]
                if (idx < lists[i].size) {
                    val score = lists[i][idx].second
                    if (score > bestScore) {
                        bestScore = score
                        bestListIdx = i
                    }
                }
            }

            if (bestListIdx == -1) break

            result.add(lists[bestListIdx][indices[bestListIdx]])
            indices[bestListIdx]++
        }
        return result
    }
    suspend fun pruneDeletedPhotos(currentUris: List<Uri>) {
        val currentUriStrings = currentUris.map { it.toString() }
        dao.deleteWhereUriNotIn(currentUriStrings)
    }
    suspend fun searchByImage(context : Context,image: Uri,indexedImages : List<PhotoEntity>) : List<Pair<PhotoEntity,Float>>{
        return withContext(Dispatchers.IO){
            memoriaEncoder.initializeImageEncoder()
            val encodedImage= memoriaEncoder.encodeImage(uriToBitmap(context,image))
            val allPhotos = indexedImages

            allPhotos.filter { it.embedding.isNotEmpty() }.map { photo->
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

    suspend fun unloadModel() = imageModelMutex.withLock {memoriaEncoder.close()}
    fun unloadImageModel() = memoriaEncoder.freeImageEncoder()

    val folderPrefs = FolderPrefs(context)

    suspend fun getImagesToIndex(context: Context) : List<Uri> {

        val imglist = mutableListOf<Uri>()
        val selectedBuckets = folderPrefs.selectedBuckets.first()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        var selection: String? = null
        var selectionArgs: Array<String>? = null

        if (selectedBuckets.isNotEmpty()) {
            val placeholders = selectedBuckets.joinToString(",") { "?" }
            selection = "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
            selectionArgs = selectedBuckets.toTypedArray()
        }

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
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
        return imglist
    }
    suspend fun getImagePosition(
        contentResolver: ContentResolver,
        image: Uri
    ): Int = withContext(Dispatchers.IO) {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageId = ContentUris.parseId(image)
        val dateTaken = contentResolver.query(
            uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN),
            "${MediaStore.Images.Media._ID} = ?", arrayOf(imageId.toString()), null
        )?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return@withContext -1

        contentResolver.query(
            uri, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DATE_TAKEN} > ? OR " +
                    "(${MediaStore.Images.Media.DATE_TAKEN} = ? AND ${MediaStore.Images.Media._ID} > ?)",
            arrayOf(dateTaken.toString(), dateTaken.toString(), imageId.toString()),
            null
        )?.use { it.count } ?: -1
    }
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