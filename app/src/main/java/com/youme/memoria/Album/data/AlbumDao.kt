package com.youme.memoria.Album.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.youme.memoria.roomCach.AlbumEntity
import com.youme.memoria.roomCach.AlbumPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao{

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbum(album: AlbumEntity)


    @Query("SELECT * FROM album_photo where albumId=:albumId")
    fun getAlbumsPhotos(albumId: String): Flow<List<AlbumPhotoEntity>>

    @Query("""
    SELECT uri FROM album_photo 
    WHERE albumId = :albumId 
    LIMIT 3
""")
    suspend fun getCoverUris(albumId: Long): List<String>


    @Query("SELECT * FROM album ")
    fun getAllAlbums(): List<AlbumEntity>

    @Query("DELETE FROM album_photo WHERE uri IN (:photoUris) and albumId=:albumId")
    suspend fun removeAlbumPhotos(photoUris: List<String>,albumId: String)


    @Query("select * from album_photo")
    suspend fun albumPhotoUris() : List<AlbumPhotoEntity>


    @Query("DELETE FROM album_photo WHERE albumId in (:albumIds)")
    suspend fun deleteAllAlbumPhotos(albumIds: List<String>)
    @Query("DELETE FROM album WHERE id in (:albumIds)")
    suspend fun deleteAlbum(albumIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhotos(photos: List<AlbumPhotoEntity>)





    data class AlbumWithPhotos(
        @Embedded val album: AlbumEntity,
        @Relation(
            parentColumn = "id",
            entityColumn = "albumId"
        )
        val photos: List<AlbumPhotoEntity>
    )

    @Query(""" update album set centroidEmbedding=:embeddings where id=:albumId """)
    suspend fun updateCentroidEmbedding(albumId: String,embeddings: ByteArray)

    @Transaction
    @Query("SELECT * FROM album")
    fun getAlbumsWithPhotos(): Flow<List<AlbumWithPhotos>>

}