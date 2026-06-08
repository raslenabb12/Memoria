package com.youme.inkdex.roomCach

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [PhotoEntity::class], version = 3)
abstract class PhotosDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    companion object {
        @Volatile private var INSTANCE: PhotosDatabase? = null

        fun getInstance(context: Context): PhotosDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photo_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}