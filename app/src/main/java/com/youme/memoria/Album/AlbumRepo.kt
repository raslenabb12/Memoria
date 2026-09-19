package com.youme.memoria.Album

import android.content.Context
import androidx.core.net.toUri
import com.youme.inkdex.roomCach.AlbumEntity
import com.youme.inkdex.roomCach.AlbumPhotoEntity
import com.youme.inkdex.roomCach.AlbumsList
import com.youme.inkdex.roomCach.PhotosDatabase
import com.youme.memoria.Encoder.MemoriaEncoder
import com.youme.memoria.Encoder.MemoriaEncoder.Companion.EMBED_DIM
import kotlinx.coroutines.flow.first


class AlbumRepo (context: Context) {

    private val dao = PhotosDatabase.getInstance(context).AlbumDao()
    private val memoriaEncoder = MemoriaEncoder(context)

    suspend fun addAlbum(albumId:String,title: String, autoUpdate: Boolean){
        dao.insertAlbum(AlbumEntity(id = albumId, name = title, centroidEmbedding = ByteArray(0), autoUpdate = autoUpdate))
    }

    suspend fun addPhotosToAlbum(images: List<AlbumPhotoEntity>){
        dao.insertPhotos(images)

    }
    fun computeCentroid(embeddings: List<FloatArray>): FloatArray {
        val avg = FloatArray(EMBED_DIM)
        embeddings.forEach { emb -> emb.forEachIndexed { i, v -> avg[i] += v } }
        for (i in avg.indices) avg[i] /= embeddings.size
        return memoriaEncoder.l2Normalize(avg)
    }

}