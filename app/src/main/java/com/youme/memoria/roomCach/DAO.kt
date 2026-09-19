package com.youme.inkdex.roomCach

import android.R
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Query("SELECT * FROM photo")
    suspend fun getAll(): List<PhotoEntity>

    @Query("SELECT * FROM photo where uri in (:uri)")
    suspend fun getPhotosByUri(uri: List<String>): List<PhotoEntity>


    @Query("SELECT COUNT(*) FROM photo")
    suspend fun count(): Int

    @Query("select * from photo where uri =:uri limit 1")
    suspend fun existsByUri(uri : String) :PhotoEntity?

    @Query("""
    SELECT DISTINCT folderPath,COUNT(*) AS count FROM photo
    WHERE folderPath != '' 
    group by folderPath
   
    ORDER BY folderPath ASC
""")
    suspend fun getAvailableFolders(): List<FolderCount>

    @Query("delete from photo where uri in (:toDelete)")
    suspend fun deleteWhereUriNotIn(toDelete : List<String>)


    @Query("""
    SELECT DISTINCT cameraMake || ' ' || cameraModel AS camera FROM photo 
    WHERE cameraMake IS NOT NULL AND cameraModel IS NOT NULL 
    ORDER BY camera ASC
""")
    suspend fun getAvailableCameras(): List<String>
    @Query("""
    SELECT * FROM photo 
    WHERE (:startDate IS NULL OR dateTaken >= :startDate)
    AND (:endDate IS NULL OR dateTaken <= :endDate)
    AND (:foldersEmpty = 1 OR folderPath IN (:folders))
    AND (:camera IS NULL OR cameraModel = :camera)
""")
    suspend fun getFiltered(
        startDate: Long?,
        endDate: Long?,
        folders: List<String>,
        camera: String?,
        foldersEmpty: Boolean
    ): List<PhotoEntity>

}
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

    @Query("DELETE FROM album_photo WHERE uri IN (:photoUris)")
    suspend fun removeAlbumPhotos(photoUris: List<String>)



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
data class AlbumsList(
    val albumId: String,
    val name : String,
    val images : List<Uri>,
    val size : Int = 0
)

data class FolderCount(
    val folderPath: String,
    val count: Long
)
