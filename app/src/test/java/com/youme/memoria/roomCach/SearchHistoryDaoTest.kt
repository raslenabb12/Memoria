package com.youme.memoria.roomCach

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchHistoryDaoTest {

    private lateinit var db: PhotosDatabase
    private lateinit var dao: SearchHistoryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PhotosDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.SearchHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun historyListsPinnedFirstThenNewestFirst() = runBlocking {
        dao.insert(SearchHistoryEntity("old", timestamp = 1))
        dao.insert(SearchHistoryEntity("new", timestamp = 3))
        dao.insert(SearchHistoryEntity("pinned old", pinned = true, timestamp = 0))
        dao.insert(SearchHistoryEntity("pinned new", pinned = true, timestamp = 2))

        val queries = dao.getSearchHistroy().first().map { it.query }

        assertEquals(listOf("pinned new", "pinned old", "new", "old"), queries)
    }

    @Test
    fun searchingTheSameQueryAgainReplacesTheRow() = runBlocking {
        dao.insert(SearchHistoryEntity("dog", timestamp = 1))
        dao.insert(SearchHistoryEntity("dog", timestamp = 5))

        val history = dao.getSearchHistroy().first()

        assertEquals(1, history.size)
        assertEquals(5L, history.single().timestamp)
    }

    @Test
    fun pinnedStatusCanBeSetAndRead() = runBlocking {
        dao.insert(SearchHistoryEntity("dog", timestamp = 1))

        assertEquals(false, dao.getPinnedStatus("dog"))
        dao.setPinned("dog", true)
        assertEquals(true, dao.getPinnedStatus("dog"))
        assertNull(dao.getPinnedStatus("missing"))
    }

    @Test
    fun deleteItemRemovesOnlyThatQuery() = runBlocking {
        dao.insert(SearchHistoryEntity("dog", timestamp = 1))
        dao.insert(SearchHistoryEntity("cat", timestamp = 2))

        dao.deleteItem("dog")

        assertEquals(listOf("cat"), dao.getSearchHistroy().first().map { it.query })
    }

    @Test
    fun deleteAllClearsTheHistory() = runBlocking {
        dao.insert(SearchHistoryEntity("dog", timestamp = 1))
        dao.insert(SearchHistoryEntity("cat", pinned = true, timestamp = 2))

        dao.deleteAll()

        assertTrue(dao.getSearchHistroy().first().isEmpty())
    }
}
