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

}
