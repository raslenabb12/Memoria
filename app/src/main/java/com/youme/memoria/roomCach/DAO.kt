package com.youme.inkdex.roomCach

import android.R
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Query("SELECT * FROM photo")
    suspend fun getAll(): List<PhotoEntity>

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
data class FolderCount(
    val folderPath: String,
    val count: Long
)
