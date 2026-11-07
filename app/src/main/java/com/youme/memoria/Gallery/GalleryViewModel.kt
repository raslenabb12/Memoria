package com.youme.memoria.Gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.ImageSizeUtil
import com.youme.memoria.imageViewer.ImageViewerPagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class GalleryRepository(private val contentResolver: ContentResolver) {
    fun getGalleryPager(): Pager<Int, DateImageBlocks> {
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
    fun galleryFlow(startPosition: Int = 0, pageSize: Int = 60): Flow<PagingData<DateImageBlocks>> {
        // startPosition = image index in the gallery, key = page of `pageSize` images
        val startPage = (startPosition / pageSize).coerceAtLeast(0)
        Log.d("test_post", "galleryFlow: page=$startPage position=$startPosition")

        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize,      // must equal pageSize so keys map 1:1 to pages
                prefetchDistance = 2,            // counted in blocks, not images
                enablePlaceholders = false       // items are date blocks, so image-based placeholders are wrong
            ),
            initialKey = startPage,
            pagingSourceFactory = { GalleryPagingSource(contentResolver, pageSize) }
        ).flow
    }
    fun imageViewerFlow(startPosition: Int, totalCount: Int): Flow<PagingData<ImageUriItem>> =
        Pager(
            config = PagingConfig(pageSize = 30, initialLoadSize = 60, enablePlaceholders = true),
            initialKey = (startPosition - 30).coerceAtLeast(0),
            pagingSourceFactory = { ImageViewerPagingSource(contentResolver, totalCount) }
        ).flow
}

class GalleryViewModel(private val repository: GalleryRepository) : ViewModel() {
    val galleryFlow: Flow<PagingData<DateImageBlocks>> =
        repository.getGalleryPager().flow.cachedIn(viewModelScope)

    fun imageViewerFlow(startPosition:Int,totalCount: Int) : Flow<PagingData<ImageUriItem>> = repository.imageViewerFlow(startPosition ,totalCount).cachedIn(viewModelScope)
    //fun imageViewerFlow(startPosition: Int) : Flow<PagingData<DateImageBlocks>> = repository.galleryFlow(startPosition)
}