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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryPagingSource(
    private val contentResolver: ContentResolver,
    private val pageSize: Int = 60,
) : PagingSource<Int, DateImageBlocks>() {

    override fun getRefreshKey(state: PagingState<Int, DateImageBlocks>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { it.prevKey?.plus(1) ?: it.nextKey?.minus(1) }
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DateImageBlocks> {
        val page = params.key ?: 0
        return withContext(Dispatchers.IO) {
            try {
                val (blocks, rowCount) = queryPage(page, pageSize)
                LoadResult.Page(
                    data = blocks,
                    prevKey = if (page == 0) null else page - 1,
                    nextKey = if (rowCount < pageSize) null else page + 1   // based on images, not blocks
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
    }

    private fun queryPage(page: Int, pageSize: Int): Pair<List<DateImageBlocks>, Int> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val sortOrder =
            "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC"
        val offset = page * pageSize

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bundle = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            }
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                bundle,
                null
            )
        } else {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
                "$sortOrder LIMIT $pageSize OFFSET $offset"
            )
        }

        val blocks = LinkedHashMap<String, DateImageBlocks>()
        var rows = 0
        val fmt = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val widthCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (it.moveToNext()) {
                rows++
                val id = it.getLong(idCol)
                val uri =
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val date = fmt.format(Date(it.getLong(dateCol)))
                blocks.getOrPut(date) { DateImageBlocks(date, mutableListOf()) }
                    .images.add(ImageUriItem(id, uri, it.getInt(widthCol), it.getInt(heightCol)))
            }
        }
        return blocks.values.toList() to rows
    }
}
data class DateImageBlocks(
    val dataTaken : String,
    val images : MutableList<ImageUriItem>,
)