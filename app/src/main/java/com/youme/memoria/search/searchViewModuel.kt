package com.youme.memoria.search

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Query
import com.youme.inkdex.roomCach.PhotoEntity
import com.youme.memoria.PhotoRepository
import com.youme.memoria.SearchFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed class SearchState {
    object Loading : SearchState()
    data class Success(val results: List<Pair<PhotoEntity, Float>>) : SearchState()
    data class Error(val message: String) : SearchState()
}
class SearchViewModuel(application: Application): AndroidViewModel(application) {
    private val repo = PhotoRepository(application)
    private val searchQuery = MutableStateFlow("")
    val searchImage = MutableStateFlow<Uri?>(null)
    val searchfilters  = MutableStateFlow<SearchFilters>(SearchFilters(null,null,emptyList(),null))

    val results = combine(searchfilters, searchQuery.debounce(100),searchImage) { filters, query,image -> filters to (query to image ) }
        .distinctUntilChanged()
        .flatMapLatest { (filters, query) ->
            flow {
                emit(SearchState.Loading)
                repo.ensureTextModelInitialized()
                val allImages = repo.indexedImagesFiltered(filters)
                val res = if (query.second==null) {
                    repo.search(query.first, allImages)
                }else{
                    repo.searchByImage(application,query.second!!,allImages)
                }
                emit(SearchState.Success(res))
                }
            }.catch { e ->
                emit(SearchState.Error(e.message ?: "search failed"))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchState.Loading)

    fun setSearchQuery(query: String){
        searchQuery.value = query
    }
    fun setSearchFilters(searchFilters: SearchFilters){
        this.searchfilters.value = searchFilters
    }
    fun setSearchImage(image: Uri?){
        searchImage.value = image
    }
}