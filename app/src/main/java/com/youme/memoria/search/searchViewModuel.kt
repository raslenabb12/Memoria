package com.youme.memoria.search

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Query
import com.youme.memoria.PhotoRepository
import com.youme.memoria.SearchFilters
import com.youme.memoria.roomCach.PhotoEntity
import kotlinx.coroutines.flow.Flow
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



enum class SearchLayout{ STAGGERED,DATEBLOCKS }


sealed class SearchState {
    object Loading : SearchState()
    data class Success(val results: List<Pair<PhotoEntity, Float>>) : SearchState()
    data class Error(val message: String) : SearchState()
}
class SearchViewModuel(application: Application): AndroidViewModel(application) {
    private val repo = PhotoRepository(application)
    private val searchQuery = MutableStateFlow("")
    val searchImage = MutableStateFlow<Uri?>(null)

    val searchLayout = MutableStateFlow<SearchLayout>(SearchLayout.STAGGERED)

    val weightsSearch = MutableStateFlow<ByteArray>(ByteArray(0))
    val searchfilters  = MutableStateFlow<SearchFilters>(SearchFilters(null,null,emptyList(),null))




    val results = combine(searchfilters, searchQuery.debounce(100),searchImage,weightsSearch) { filters, query,image,_ -> filters to (query to image ) }
        .distinctUntilChanged()
        .toSearchStates { (filters, query) ->
            repo.ensureTextModelInitialized()
            val allImages = repo.indexedImagesFiltered(filters)
            if (query.second==null) {
                repo.search(query.first, allImages,filters.confidence.score)
            }else{
                repo.searchByImage(application,query.second!!,allImages)
            }
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

    fun setSearchLayout(searchLayout: SearchLayout){
        this.searchLayout.value = searchLayout
    }
}

// Errors are caught per query, inside flatMapLatest, so one failed search
// doesn't end the stream and later searches still run.
internal fun <T> Flow<T>.toSearchStates(
    search: suspend (T) -> List<Pair<PhotoEntity, Float>>
): Flow<SearchState> = flatMapLatest { input ->
    flow<SearchState> {
        emit(SearchState.Loading)
        emit(SearchState.Success(search(input)))
    }.catch { e ->
        emit(SearchState.Error(e.message ?: "search failed"))
    }
}
