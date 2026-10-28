package com.youme.memoria.roomCach

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumDaoTest {

    private lateinit var db: PhotosDatabase
    private lateinit var dao: AlbumDao
    private lateinit var photoDao: PhotoDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PhotosDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.AlbumDao()
        photoDao = db.photoDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun photo(uri: String) =
        PhotoEntity(uri, floatArrayOf(0.1f, 0.2f).toByteArray(), 100, 100)

    private fun album(id: String, name: String = id, autoUpdate: Boolean = true) =
        AlbumEntity(id, name, floatArrayOf(0.5f).toByteArray(), autoUpdate)

    private suspend fun albumWithPhotos(albumId: String, vararg uris: String) {
        dao.insertAlbum(album(albumId))
        dao.insertPhotos(uris.map { AlbumPhotoEntity(albumId, it) })
    }

    @Test
    fun insertedAlbumsAreReturned() = runBlocking {
        dao.insertAlbum(album("a", name = "Dogs"))
        dao.insertAlbum(album("b", name = "Beach", autoUpdate = false))

        val albums = dao.getAllAlbums().associateBy { it.id }

        assertEquals(setOf("a", "b"), albums.keys)
        assertEquals("Dogs", albums.getValue("a").name)
        assertTrue(albums.getValue("a").autoUpdate)
        assertFalse(albums.getValue("b").autoUpdate)
    }

    @Test
    fun insertingTheSameAlbumIdAgainKeepsTheFirstRow() = runBlocking {
        dao.insertAlbum(album("a", name = "Dogs"))
        dao.insertAlbum(album("a", name = "Cats"))

        assertEquals(listOf("Dogs"), dao.getAllAlbums().map { it.name })
    }

    @Test
    fun albumPhotosAreListedPerAlbum() = runBlocking {
        photoDao.insertAll(listOf(photo("p1"), photo("p2"), photo("p3")))
        albumWithPhotos("a", "p1", "p2")
        albumWithPhotos("b", "p3")

        assertEquals(setOf("p1", "p2"), dao.getAlbumsPhotos("a").first().map { it.uri }.toSet())
        assertEquals(3, dao.albumPhotoUris().size)
    }

    @Test
    fun addingTheSamePhotoTwiceKeepsOneRow() = runBlocking {
        photoDao.insert(photo("p1"))
        albumWithPhotos("a", "p1")
        dao.insertPhotos(listOf(AlbumPhotoEntity("a", "p1")))

        assertEquals(1, dao.getAlbumsPhotos("a").first().size)
    }

    @Test
    fun addingAPhotoThatIsNotIndexedFails() = runBlocking {
        // The foreign key needs the photo row first; IGNORE does not cover that.
        dao.insertAlbum(album("a"))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertPhotos(listOf(AlbumPhotoEntity("a", "missing"))) }
        }
        Unit
    }

    @Test
    fun removeAlbumPhotosOnlyTouchesTheGivenAlbum() = runBlocking {
        photoDao.insertAll(listOf(photo("p1"), photo("p2")))
        albumWithPhotos("a", "p1", "p2")
        albumWithPhotos("b", "p1")

        dao.removeAlbumPhotos(listOf("p1"), "a")

        assertEquals(listOf("p2"), dao.getAlbumsPhotos("a").first().map { it.uri })
        assertEquals(listOf("p1"), dao.getAlbumsPhotos("b").first().map { it.uri })
        assertEquals(2, photoDao.count())
    }

    @Test
    fun deletingAnAlbumRemovesItsPhotoLinksButNotThePhotos() = runBlocking {
        photoDao.insertAll(listOf(photo("p1"), photo("p2")))
        albumWithPhotos("a", "p1", "p2")
        albumWithPhotos("b", "p2")

        // Only the album row: the cascade should take its links with it.
        dao.deleteAlbum(listOf("a"))

        assertEquals(listOf("b"), dao.getAllAlbums().map { it.id })
        assertEquals(listOf(AlbumPhotoEntity("b", "p2")), dao.albumPhotoUris())
        assertEquals(2, photoDao.count())
    }

    @Test
    fun deleteAllAlbumPhotosEmptiesTheAlbumsButKeepsThem() = runBlocking {
        photoDao.insertAll(listOf(photo("p1"), photo("p2")))
        albumWithPhotos("a", "p1")
        albumWithPhotos("b", "p2")

        dao.deleteAllAlbumPhotos(listOf("a", "b"))

        assertTrue(dao.albumPhotoUris().isEmpty())
        assertEquals(2, dao.getAllAlbums().size)
    }

    @Test
    fun deletingAPhotoRemovesItFromEveryAlbum() = runBlocking {
        photoDao.insertAll(listOf(photo("p1"), photo("p2")))
        albumWithPhotos("a", "p1", "p2")
        albumWithPhotos("b", "p1")

        // Deletes the listed URIs (see PhotoDaoTest for the name).
        photoDao.deleteWhereUriNotIn(listOf("p1"))

        assertEquals(listOf(AlbumPhotoEntity("a", "p2")), dao.albumPhotoUris())
    }

    @Test
    fun centroidEmbeddingCanBeUpdatedAndCleared() = runBlocking {
        dao.insertAlbum(album("a"))
        val centroid = floatArrayOf(0.25f, -1f, 3.5f)

        dao.updateCentroidEmbedding("a", centroid.toByteArray())
        assertArrayEquals(centroid, dao.getAllAlbums().single().centroidEmbedding.toFloatArray(), 0f)

        dao.updateCentroidEmbedding("a", ByteArray(0))
        assertEquals(0, dao.getAllAlbums().single().centroidEmbedding.size)
    }

    @Test
    fun albumsWithPhotosGroupsLinksUnderEachAlbum() = runBlocking {
        photoDao.insertAll(listOf(photo("p1"), photo("p2")))
        albumWithPhotos("a", "p1", "p2")
        dao.insertAlbum(album("empty"))

        val grouped = dao.getAlbumsWithPhotos().first().associate { it.album.id to it.photos.map { p -> p.uri }.toSet() }

        assertEquals(mapOf("a" to setOf("p1", "p2"), "empty" to emptySet<String>()), grouped)
    }
}
