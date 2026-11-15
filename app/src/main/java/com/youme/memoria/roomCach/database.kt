package com.youme.memoria.roomCach

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.youme.memoria.Album.data.AlbumDao
import com.youme.memoria.roomCach.MIGRATION_3_4
import com.youme.memoria.roomCach.MIGRATION_4_5
import com.youme.memoria.search.data.SearchHistoryDao

@Database(entities = [PhotoEntity::class, AlbumPhotoEntity::class, AlbumEntity::class,SearchHistoryEntity::class], version = 5)
abstract class PhotosDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun AlbumDao(): AlbumDao
    abstract fun SearchHistoryDao(): SearchHistoryDao
    companion object {
        @Volatile private var INSTANCE: PhotosDatabase? = null

        fun getInstance(context: Context): PhotosDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photo_db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
            }
        }
    }
}