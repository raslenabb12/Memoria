package com.youme.memoria

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.net.toUri
import androidx.room.Query
import com.youme.inkdex.roomCach.PhotoEntity
import com.youme.inkdex.roomCach.PhotosDatabase
import com.youme.inkdex.roomCach.toByteArray
import com.youme.inkdex.roomCach.toFloatArray
import com.youme.memoria.Encoder.MemoriaEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoRepository(context: Context) {

    private val dao = PhotosDatabase.getInstance(context).photoDao()
    private val memoriaEncoder = MemoriaEncoder(context)

    suspend fun getCountPhotos() = dao.count()
     var initializeedTextModel  = false

    suspend fun initializeImageModel() {
        withContext(Dispatchers.IO){
            memoriaEncoder.initializeImageEncoder()
        }
    }
    suspend fun initializeTextModel() {
        if (initializeedTextModel) return
        withContext(Dispatchers.IO){
            memoriaEncoder.initializeTextEncoder()
            initializeedTextModel = true
        }
    }
    suspend fun alreadyExistsList() = dao.getAll()

    suspend fun encodeImage(context : Context,image: Uri) : FloatArray {
        return withContext(Dispatchers.IO){ memoriaEncoder.encodeImage(uriToBitmap(context,image)) }
    }

    suspend fun search(query: String) : List<Pair<Uri,Float>>{
        return withContext(Dispatchers.IO){
            val encodedText = memoriaEncoder.encodeText(query)
            val allPhotos = dao.getAll()

            allPhotos.map { photo->
                val score  = memoriaEncoder.cosineSimilarity(encodedText,photo.embedding.toFloatArray())
                photo.uri.toUri() to score
            }.sortedByDescending { it.second }

        }
    }

    suspend fun saveEmbedding(uri: String, embedding: FloatArray) {
        if (alreadyExists(uri)) return
        dao.insert(PhotoEntity(uri = uri, embedding = embedding.toByteArray()))
    }
    suspend fun saveEmbeddingsBatch(items: List<Pair<String, FloatArray>>) {
        withContext(Dispatchers.IO) {
            val entities = items
                .filter { dao.existsByUri(it.first) == null }
                .map { PhotoEntity(uri = it.first, embedding = it.second.toByteArray()) }
            if (entities.isNotEmpty()) dao.insertAll(entities)
        }
    }

    suspend fun alreadyExists(uri: String): Boolean = dao.existsByUri(uri)!=null

     fun unloadModel() = memoriaEncoder.close()
    fun unloadImageModel() = memoriaEncoder.freeImageEncoder()

    fun uriToBitmap(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(256, 256)
        }
    }
}