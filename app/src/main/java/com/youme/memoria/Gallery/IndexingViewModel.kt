package com.youme.memoria.Gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.youme.memoria.Gallery.IndexingViewModel.IndexingState
import com.youme.memoria.ImageSizeUtil
import com.youme.memoria.PhotoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException


class IndexingViewModelFactory(
    private val repo: PhotoRepository,
    private val appContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return IndexingViewModel(repo, appContext) as T
    }
}
class IndexingViewModel(
    private val repo: PhotoRepository,
    private val appContext: Context
) : ViewModel() {

    private var indexingJob : Job?= null

    val imglist  =  mutableListOf<Uri>()

    private val _state = MutableStateFlow<IndexingState>(IndexingState.Idle)
    val state: StateFlow<IndexingState> = _state.asStateFlow()

    private val _Folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _Folders.asStateFlow()
    val dbSize = MutableStateFlow<String>("0")

    init {
        scanGallery()
    }
    private fun getDatabaseSizeInBytes(context: Context, dbName: String): Long {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            return 0L
        }
        val walFile = File("${dbFile.path}-wal")
        val shmFile = File("${dbFile.path}-shm")
        var totalSize = dbFile.length()
        if (walFile.exists()) {
            totalSize += walFile.length()
        }
        if (shmFile.exists()) {
            totalSize += shmFile.length()
        }
        return totalSize
    }
    fun getFormattedDatabaseSize(dbName: String) {
        viewModelScope.launch {
            val sizeInBytes = getDatabaseSizeInBytes(appContext, dbName)
            dbSize.value= Formatter.formatFileSize(appContext, sizeInBytes)
        }
    }
    fun scanGallery() {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            appContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->

                val idCol =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

                val nameCol =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    imglist.add(uri)

                }
            }
        viewModelScope.launch {
            try {
                val alreadyExists = repo.alreadyExistsList().map { it.uri.toUri() }.toMutableList()
                val toProcess = imglist.filter { !alreadyExists.contains(it) }

                if (imglist.isNotEmpty())  _state.value = IndexingState.Ready(alreadyExists.size,imglist.size)
            }catch (e: Exception){

            }
        }


    }

    fun startIndexing(){
        if (indexingJob?.isActive == true) return
        var processedSize= 0
        indexingJob = viewModelScope.launch {
            try {
                val alreadyExists = repo.alreadyExistsList().map { it.uri.toUri() }.toMutableList()
                val toProcess = imglist.filter { !alreadyExists.contains(it) }

                processedSize = alreadyExists.size

                repo.initializeImageModel()

                ensureActive()

                toProcess.forEachIndexed { index, uri ->

                    ensureActive()
                    val startTime = System.currentTimeMillis()

                    try {
                        val encodedImage =
                            repo.encodeImage(appContext, uri)
                        val sizeData = ImageSizeUtil.getImageDimensions(appContext,uri)
                        val photoMetaData = ImageSizeUtil.getPhotoMetadata(appContext,uri)
                        val cameraInfo = ImageSizeUtil.getCameraInfo(appContext,uri)
                        repo.saveEmbedding(
                            uri.toString(),
                            encodedImage,
                            sizeData?.second?:0,
                            sizeData?.first?:0,
                            photoMetaData?.first,
                            photoMetaData?.second,
                            cameraInfo.first,
                            cameraInfo.second

                        )
                        alreadyExists.add(uri)

                    } catch (e: CancellationException) {
                        throw e

                    } catch (e: Exception) {
                        Log.e("ImageEncoding", "Failed: $uri", e)
                    }


                    val endTime = System.currentTimeMillis()

                    val etaMs = (endTime - startTime).toFloat() * (toProcess.size - index - 1)
                    val etaMin = (etaMs / 1000f) / 60


                    _state.value = IndexingState.Running(processedSize,imglist.size,etaMin)
                    processedSize++
                }

                _state.value = IndexingState.Completed(processedSize)

            } catch (e: CancellationException) {

                _state.value = IndexingState.Ready(processedSize , imglist.size)

            } catch (e: Exception) {
                Log.e("Indexing", "Fatal error during indexing", e)

            }finally {
                repo.unloadModel()
            }
        }
    }

    fun pause() = indexingJob?.cancel()
    sealed class IndexingState {
        object Idle : IndexingState()

        data class Ready(val processed: Int, val total: Int) : IndexingState()
        data class Running(val processed: Int, val total: Int, val etaMinutes: Float) : IndexingState()
        data class Completed(val total: Int) : IndexingState()
    }

}