package com.youme.memoria.Album.data

import android.content.Context
import com.youme.memoria.Encoder.MemoriaEncoder
import com.youme.memoria.roomCach.AlbumEntity
import com.youme.memoria.roomCach.AlbumPhotoEntity
import com.youme.memoria.roomCach.PhotosDatabase

class AlbumRepo (context: Context) {

    private val dao = PhotosDatabase.getInstance(context).AlbumDao()
    private val memoriaEncoder = MemoriaEncoder(context)

    suspend fun addAlbum(albumId:String,title: String, autoUpdate: Boolean){
        dao.insertAlbum(
            AlbumEntity(
                id = albumId,
                name = title,
                centroidEmbedding = ByteArray(0),
                autoUpdate = autoUpdate
            )
        )
    }

    suspend fun addPhotosToAlbum(images: List<AlbumPhotoEntity>){
        dao.insertPhotos(images)

    }
    fun computeCentroid(embeddings: List<FloatArray>): FloatArray {
        val avg = FloatArray(MemoriaEncoder.EMBED_DIM)
        embeddings.forEach { emb -> emb.forEachIndexed { i, v -> avg[i] += v } }
        for (i in avg.indices) avg[i] /= embeddings.size
        return memoriaEncoder.l2Normalize(avg)
    }

}