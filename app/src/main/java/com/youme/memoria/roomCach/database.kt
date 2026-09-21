package com.youme.inkdex.roomCach

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.youme.memoria.roomCach.MIGRATION_3_4

@Database(entities = [PhotoEntity::class, AlbumPhotoEntity::class, AlbumEntity::class], version = 4)
abstract class PhotosDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun AlbumDao(): AlbumDao
    companion object {
        @Volatile private var INSTANCE: PhotosDatabase? = null

        fun getInstance(context: Context): PhotosDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photo_db"
                ).addMigrations(MIGRATION_3_4).build().also { INSTANCE = it }
            }
        }
    }
}