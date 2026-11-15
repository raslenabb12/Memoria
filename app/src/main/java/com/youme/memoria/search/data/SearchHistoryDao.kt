package com.youme.memoria.search.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.youme.memoria.roomCach.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao{

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item : SearchHistoryEntity)

    @Query("SELECT * FROM searchHistory order by pinned DESC , timestamp DESC ")
    fun getSearchHistroy(): Flow<List<SearchHistoryEntity>>



    @Query("select pinned from searchHistory where `query`=:query")
    fun getPinnedStatus(query: String): Boolean?



    @Query("update searchHistory set pinned =:pinned where `query`=:query  ")
    suspend fun setPinned(query: String,pinned: Boolean)

    @Query("delete from searchHistory")
    suspend fun deleteAll()
    @Query("delete from searchHistory where `query`=:query")
    suspend fun deleteItem(query: String)


}