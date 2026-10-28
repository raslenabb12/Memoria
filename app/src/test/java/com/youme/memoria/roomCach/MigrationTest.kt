package com.youme.memoria.roomCach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Opens an old database file with Room. Room checks every table against the entities
 * after migrating, so a migration that leaves a wrong layout fails here with
 * "Migration didn't properly handle ...".
 *
 * The version 3 layout is the photo table as it is now: the later photo columns were
 * added while the version stayed at 3, so older version 3 layouts are not covered.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val dbName = "migration-test"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createOldDatabase(version: Int) {
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `photo` (
                    `uri` TEXT NOT NULL,
                    `embedding` BLOB NOT NULL,
                    `width` INTEGER NOT NULL,
                    `height` INTEGER NOT NULL,
                    `dateTaken` INTEGER,
                    `folderPath` TEXT,
                    `cameraMake` TEXT,
                    `cameraModel` TEXT,
                    PRIMARY KEY(`uri`)
                )
                """
            )
            db.execSQL(
                "INSERT INTO photo VALUES ('content://p1', x'00000000', 100, 200, 5, 'Camera', 'Make', 'Model')"
            )
            if (version >= 4) {
                // As Room creates them on a fresh version 4 install (3 to 4 is covered above).
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `album` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `centroidEmbedding` BLOB NOT NULL,
                        `autoUpdate` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `album_photo` (
                        `albumId` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        PRIMARY KEY(`albumId`, `uri`),
                        FOREIGN KEY(`albumId`) REFERENCES `album`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`uri`) REFERENCES `photo`(`uri`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_photo_albumId` ON `album_photo` (`albumId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_photo_uri` ON `album_photo` (`uri`)")
                db.execSQL("INSERT INTO album VALUES ('a', 'Dogs', x'', 0)")
                db.execSQL("INSERT INTO album_photo VALUES ('a', 'content://p1')")
            }
            db.version = version
        }
    }

    private fun openWithRoom() = Room.databaseBuilder(context, PhotosDatabase::class.java, dbName)
        .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
        .allowMainThreadQueries()
        .build()

    @Test
    fun migrates3To5AndKeepsPhotos() = runBlocking {
        createOldDatabase(3)

        val db = openWithRoom()
        try {
            val photo = db.photoDao().getAll().single()
            assertEquals("content://p1", photo.uri)
            assertEquals(200, photo.height)
            assertEquals("Model", photo.cameraModel)

            // New tables work after the migration, including the foreign keys.
            db.AlbumDao().insertAlbum(AlbumEntity("a", "Dogs", ByteArray(0)))
            db.AlbumDao().insertPhotos(listOf(AlbumPhotoEntity("a", "content://p1")))
            db.SearchHistoryDao().insert(SearchHistoryEntity("dog", timestamp = 1))
            assertEquals(1, db.AlbumDao().albumPhotoUris().size)
            assertEquals(1, db.SearchHistoryDao().getSearchHistroy().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrates4To5AndKeepsAlbums() = runBlocking {
        createOldDatabase(4)

        val db = openWithRoom()
        try {
            val album = db.AlbumDao().getAllAlbums().single()
            assertEquals("Dogs", album.name)
            assertEquals(false, album.autoUpdate)
            assertEquals(listOf(AlbumPhotoEntity("a", "content://p1")), db.AlbumDao().albumPhotoUris())
            assertEquals(1, db.photoDao().count())
        } finally {
            db.close()
        }
    }
}
