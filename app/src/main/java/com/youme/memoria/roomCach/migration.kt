package com.youme.memoria.roomCach

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `album` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `centroidEmbedding` BLOB NOT NULL,
                `autoUpdate` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`id`)
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `album_photo` (
                `albumId` TEXT NOT NULL,
                `uri` TEXT NOT NULL,
                PRIMARY KEY(`albumId`, `uri`),
                FOREIGN KEY(`albumId`) REFERENCES `album`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`uri`) REFERENCES `photo`(`uri`) ON DELETE CASCADE
            )
        """)

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_photo_albumId` ON `album_photo` (`albumId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_photo_uri` ON `album_photo` (`uri`)")
    }
}