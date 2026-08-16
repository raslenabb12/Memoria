package com.youme.memoria.Gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.youme.memoria.ImageLoading.ImageUriItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GalleryPagingSource(
    private val contentResolver: ContentResolver,
) : PagingSource<Int, ImageUriItem>() {

    private var totalCount: Int? = null

    override fun getRefreshKey(state: PagingState<Int, ImageUriItem>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ImageUriItem> {
        val page = params.key ?: 0
        val pageSize = params.loadSize

        return withContext(Dispatchers.IO) {
            try {
                val count = totalCount ?: queryCount().also { totalCount = it }
                val items = queryPage(page, 60)
                val itemsBefore = page * pageSize
                val itemsAfter = (count - itemsBefore - items.size).coerceAtLeast(0)

                LoadResult.Page(
                    data = items,
                    prevKey = if (page == 0) null else page - 1,
                    nextKey = if (items.isEmpty() || itemsAfter == 0) null else page + 1,
                    itemsBefore = itemsBefore,
                    itemsAfter = itemsAfter
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
    }

    private fun queryCount(): Int {
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null, null, null
        )?.use { return it.count }
        return 0
    }

    private fun queryPage(page: Int, pageSize: Int): List<ImageUriItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val result = mutableListOf<ImageUriItem>()

        // The absolute safest exact-sort order
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bundle = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize)
                putInt(ContentResolver.QUERY_ARG_OFFSET, page * pageSize)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            }
            contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, bundle, null)
        } else {
            val sortWithLimit = "$sortOrder LIMIT $pageSize OFFSET ${page * pageSize}"
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortWithLimit
            )
        }

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val widthCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val width = it.getInt(widthCol)
                val height = it.getInt(heightCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                result.add(ImageUriItem(id, uri, width, height))
            }
        }
        return result
    }
}