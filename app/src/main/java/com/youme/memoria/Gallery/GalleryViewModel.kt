package com.youme.memoria.Gallery

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.ImageSizeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow


class GalleryRepository(private val contentResolver: ContentResolver) {
    fun getGalleryPager(): Pager<Int, ImageUriItem> {
        return Pager(
            config = PagingConfig(
                pageSize = 60,
                enablePlaceholders = false,
                prefetchDistance = 20,
                initialLoadSize = 60
            ),
            pagingSourceFactory = { GalleryPagingSource(contentResolver) }
        )
    }
    fun galleryFlow(startPosition: Int = 0, pageSize: Int = 60): Flow<PagingData<ImageUriItem>> {
        val startPage = startPosition / pageSize
        Log.d("test_post", "galleryFlow: $startPage $startPosition")
        return Pager(
            config = PagingConfig(pageSize = pageSize, enablePlaceholders = true, initialLoadSize = pageSize),
            initialKey = startPage,
            pagingSourceFactory = { GalleryPagingSource(contentResolver) }
        ).flow
    }
}

class GalleryViewModel(private val repository: GalleryRepository) : ViewModel() {
    val galleryFlow: Flow<PagingData<ImageUriItem>> =
        repository.getGalleryPager().flow.cachedIn(viewModelScope)

    fun imageViewerFlow(startPosition: Int) : Flow<PagingData<ImageUriItem>> = repository.galleryFlow(startPosition)
}