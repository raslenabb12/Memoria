package com.youme.memoria.roomCach

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoDaoTest {

    private lateinit var db: PhotosDatabase
    private lateinit var dao: PhotoDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PhotosDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.photoDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun photo(
        uri: String,
        embedding: FloatArray = floatArrayOf(0.1f, 0.2f),
        dateTaken: Long? = 0,
        folderPath: String? = "",
        cameraMake: String? = null,
        cameraModel: String? = null
    ) = PhotoEntity(uri, embedding.toByteArray(), 100, 100, dateTaken, folderPath, cameraMake, cameraModel)

    @Test
    fun insertedPhotosAreReturnedAndCounted() = runBlocking {
        dao.insert(photo("a"))
        dao.insertAll(listOf(photo("b"), photo("c")))

        assertEquals(3, dao.count())
        assertEquals(setOf("a", "b", "c"), dao.getAll().map { it.uri }.toSet())
    }

    @Test
    fun insertingTheSameUriAgainKeepsTheFirstRow() = runBlocking {
        dao.insert(photo("a", folderPath = "Camera"))
        dao.insert(photo("a", folderPath = "Downloads"))

        assertEquals(1, dao.count())
        assertEquals("Camera", dao.existsByUri("a")?.folderPath)
    }

    @Test
    fun embeddingSurvivesTheDatabaseRoundTrip() = runBlocking {
        val embedding = floatArrayOf(0.5f, -1.25f, 3.0e-7f, Float.MAX_VALUE)
        dao.insert(photo("a", embedding = embedding))

        val stored = dao.existsByUri("a")!!.embedding.toFloatArray()

        assertArrayEquals(embedding, stored, 0f)
    }

    @Test
    fun lookupByUri() = runBlocking {
        dao.insertAll(listOf(photo("a"), photo("b"), photo("c")))

        assertEquals(setOf("a", "c"), dao.getPhotosByUri(listOf("a", "c", "missing")).map { it.uri }.toSet())
        assertTrue(dao.photoExists("b"))
        assertFalse(dao.photoExists("missing"))
        assertNull(dao.existsByUri("missing"))
    }

    @Test
    fun deleteWhereUriNotInDeletesTheListedUris() = runBlocking {
        dao.insertAll(listOf(photo("a"), photo("b"), photo("c")))

        dao.deleteWhereUriNotIn(listOf("a", "c"))

        assertEquals(listOf("b"), dao.getAll().map { it.uri })
    }

    @Test
    fun availableFoldersAreCountedAndSkipEmptyPaths() = runBlocking {
        dao.insertAll(
            listOf(
                photo("a", folderPath = "Camera"),
                photo("b", folderPath = "Camera"),
                photo("c", folderPath = "Screenshots"),
                photo("d", folderPath = ""),
                photo("e", folderPath = null)
            )
        )

        assertEquals(
            listOf(FolderCount("Camera", 2), FolderCount("Screenshots", 1)),
            dao.getAvailableFolders()
        )
    }

    @Test
    fun availableCamerasJoinMakeAndModelAndSkipIncompleteRows() = runBlocking {
        dao.insertAll(
            listOf(
                photo("a", cameraMake = "Google", cameraModel = "Pixel 10"),
                photo("b", cameraMake = "Google", cameraModel = "Pixel 10"),
                photo("c", cameraMake = "Apple", cameraModel = "iPhone 15"),
                photo("d", cameraMake = "Sony", cameraModel = null),
                photo("e", cameraMake = null, cameraModel = "X100")
            )
        )

        assertEquals(listOf("Apple iPhone 15", "Google Pixel 10"), dao.getAvailableCameras())
    }

    @Test
    fun filteredWithNoFiltersReturnsEverything() = runBlocking {
        dao.insertAll(listOf(photo("a", folderPath = "Camera"), photo("b", folderPath = "Screenshots")))

        val result = dao.getFiltered(null, null, emptyList(), null, foldersEmpty = true)

        assertEquals(setOf("a", "b"), result.map { it.uri }.toSet())
    }

    @Test
    fun filteredByDateRangeIncludesBothEnds() = runBlocking {
        dao.insertAll(
            listOf(
                photo("before", dateTaken = 99),
                photo("start", dateTaken = 100),
                photo("end", dateTaken = 200),
                photo("after", dateTaken = 201)
            )
        )

        val result = dao.getFiltered(100, 200, emptyList(), null, foldersEmpty = true)

        assertEquals(setOf("start", "end"), result.map { it.uri }.toSet())
    }

    @Test
    fun filteredByFolders() = runBlocking {
        dao.insertAll(
            listOf(
                photo("a", folderPath = "Camera"),
                photo("b", folderPath = "Screenshots"),
                photo("c", folderPath = "Downloads")
            )
        )

        val result = dao.getFiltered(null, null, listOf("Camera", "Downloads"), null, foldersEmpty = false)

        assertEquals(setOf("a", "c"), result.map { it.uri }.toSet())
    }

    @Test
    fun filteredByCameraMatchesTheModel() = runBlocking {
        dao.insertAll(
            listOf(
                photo("a", cameraMake = "Google", cameraModel = "Pixel 10"),
                photo("b", cameraMake = "Apple", cameraModel = "iPhone 15")
            )
        )

        val result = dao.getFiltered(null, null, emptyList(), "Pixel 10", foldersEmpty = true)

        assertEquals(listOf("a"), result.map { it.uri })
    }
}
