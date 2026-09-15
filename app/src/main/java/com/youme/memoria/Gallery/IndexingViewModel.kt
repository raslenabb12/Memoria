package com.youme.memoria.Gallery

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.youme.memoria.Gallery.IndexingViewModel.IndexingState
import com.youme.memoria.ImageSizeUtil
import com.youme.memoria.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val imglistMutex = Mutex()
    val imglist  =  mutableListOf<Uri>()


    private val _state = MutableStateFlow<IndexingState>(IndexingState.Idle)
    val state: StateFlow<IndexingState> = _state.asStateFlow()

    private val _Folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _Folders.asStateFlow()
    val dbSize = MutableStateFlow<String>("0")

    val batteryTemp  = MutableStateFlow<Float>(0f)

    init {
        viewModelScope.launch {
            scanGallery()
            getBatteryTemperature()
        }
    }
    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Memoria::IndexingWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
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

    suspend fun scanGallery() {
        imglistMutex.withLock {
            imglist.clear()


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
                val toDelete=alreadyExists.filter { it !in imglist }

                if (toDelete.isNotEmpty() && imglist.isNotEmpty()) {
                    Toast.makeText(appContext, "Found ${toDelete.size} missing image(s). Deleting them now.", Toast.LENGTH_SHORT).show()
                    viewModelScope.launch {
                        repo.pruneDeletedPhotos(toDelete)
                    }
                }


                if (alreadyExists.filter { it !in toDelete }.size == imglist.size){
                    _state.value = IndexingState.Completed(imglist.size)
                }else{
                    if (imglist.isNotEmpty())  _state.value = IndexingState.Ready(alreadyExists.filter { it !in toDelete }.size,imglist.size)
                }


            }catch (e: Exception){

            }
        }


    }
    }
    enum class ThermalStatus { GREEN, ORANGE, RED }

    fun getThermalStatus(tempCelsius: Float): ThermalStatus {
        return when {
            tempCelsius < 35f -> ThermalStatus.GREEN
            tempCelsius < 42f -> ThermalStatus.ORANGE
            else -> ThermalStatus.RED
        }
    }
    fun getBatteryTemperature() {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = appContext.registerReceiver(null, intentFilter)

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        batteryTemp.value =  tempTenths / 10.0f
    }

    fun startIndexing(){
        if (indexingJob?.isActive == true) return
        acquireWakeLock()
        var etaMin = 0f
        var processedSize= 0
        indexingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val alreadyExists = repo.alreadyExistsList().map { it.uri.toUri() }.toMutableList()
                val toProcess = imglist.filter { it !in alreadyExists }

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

                    } catch (e: Throwable) {
                        Log.e("ImageEncoding", "Failed: $uri", e)
                        repo.markAsFailed(uri.toString())
                    }
                    if (index%10 == 0 || index == toProcess.size-1){
                        val endTime = System.currentTimeMillis()

                        val etaMs = (endTime - startTime).toFloat() * (toProcess.size - index - 1)
                        etaMin = etaMs

                        //update ui info
                        getBatteryTemperature()
                        getFormattedDatabaseSize("photo_db")


                        when (getThermalStatus(batteryTemp.value)) {
                            ThermalStatus.RED -> {
                                while (isActive) {
                                    delay(5000)
                                    getBatteryTemperature()
                                    if (getThermalStatus(batteryTemp.value) != ThermalStatus.RED) break
                                }
                            }
                            ThermalStatus.ORANGE -> {
                                delay(800)
                            }
                            ThermalStatus.GREEN -> {
                            }
                        }
                    }



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
                releaseWakeLock()
            }
        }
    }

    fun pause() = indexingJob?.cancel()

    override fun onCleared() {
        viewModelScope.launch(NonCancellable) {
            repo.unloadModel()
        }
        super.onCleared()
    }
    sealed class IndexingState {
        object Idle : IndexingState()

        data class Ready(val processed: Int, val total: Int) : IndexingState()
        data class Running(val processed: Int, val total: Int, val etaMinutes: Float) : IndexingState()
        data class Completed(val total: Int) : IndexingState()
    }

}