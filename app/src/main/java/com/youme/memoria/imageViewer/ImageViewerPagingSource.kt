package com.youme.memoria.imageViewer

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

class ImageViewerPagingSource(
    private val contentResolver: ContentResolver,
    private val totalCount: Int
) : PagingSource<Int, ImageUriItem>() {

    override fun getRefreshKey(state: PagingState<Int, ImageUriItem>): Int? =
        state.anchorPosition?.let { (it - state.config.pageSize / 2).coerceAtLeast(0) }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ImageUriItem> =
        withContext(Dispatchers.IO) {
            try {
                val offset = (params.key ?: 0).coerceIn(0, maxOf(totalCount - 1, 0))
                val limit = if (params is LoadParams.Prepend)
                    minOf(params.loadSize, offset) else params.loadSize
                val start = if (params is LoadParams.Prepend) offset - limit else offset

                val items = query(start, limit)
                LoadResult.Page(
                    data = items,
                    prevKey = if (start == 0) null else start,
                    nextKey = if (start + items.size >= totalCount) null else start + items.size,
                    itemsBefore = start,
                    itemsAfter = (totalCount - start - items.size).coerceAtLeast(0)
                )
            } catch (e: Exception) { LoadResult.Error(e) }
        }

    private fun query(offset: Int, limit: Int): List<ImageUriItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT
        )
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC"
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
            }
            contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, b, null)
        } else {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
                "$sort LIMIT $limit OFFSET $offset"
            )
        }
        val out = ArrayList<ImageUriItem>()
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                out.add(ImageUriItem(id,
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    it.getInt(1), it.getInt(2)))
            }
        }
        return out
    }
}