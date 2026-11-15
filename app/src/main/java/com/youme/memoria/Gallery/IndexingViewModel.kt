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
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OverwritingInputMerger
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.setInputMerger
import com.youme.memoria.Gallery.IndexingViewModel.IndexingState
import com.youme.memoria.ImageSizeUtil
import com.youme.memoria.PhotoRepository
import com.youme.memoria.roomCach.PhotosDatabase
import com.youme.memoria.settings.FoldersManager.FolderPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
enum class ThermalStatus { GREEN, ORANGE, RED }
data class FolderInfo(
    val rank: Int  = 0,
    val bucketId: String,
    val bucketName: String,
    val folderPath : String,
    val itemCount: Int,
    val subFolders : List<FolderInfo> = emptyList()
)
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
    private val dao = PhotosDatabase.getInstance(appContext).AlbumDao()

    val folderPrefs = FolderPrefs(appContext)


    private val _state = MutableStateFlow<IndexingState>(IndexingState.Idle)
    val state: StateFlow<IndexingState> = _state.asStateFlow()

    private val _Folders = MutableStateFlow<List<FolderInfo>>(emptyList())
    val folders: StateFlow<List<FolderInfo>> = _Folders.asStateFlow()
    val dbSize = MutableStateFlow<String>("0")


    val isPermissionGrandted  = MutableStateFlow<Boolean>(false)

    private val wm = WorkManager.getInstance(appContext)
    val batteryTemp  = MutableStateFlow<Float>(0f)



    init {
        viewModelScope.launch {
            launch {
                scanGallery()
                getBatteryTemperature()
                getAvailableFolders()
            }
            launch {
                wm.getWorkInfosForUniqueWorkFlow(IndexingWorker.UNIQUE_NAME).collect { list ->
                    val info = list.firstOrNull() ?: return@collect
                    val done = info.progress.getInt(IndexingWorker.KEY_DONE, 0)
                    val total = info.progress.getInt(IndexingWorker.KEY_TOTAL, imglist.size)
                    val eta = info.progress.getLong(IndexingWorker.KEY_ETA, 0)

                    _state.value = when (info.state) {
                        WorkInfo.State.RUNNING   -> {
                            getBatteryTemperature()
                            getFormattedDatabaseSize("photo_db")
                            IndexingState.Running(done, total, eta.toFloat())
                        }
                        WorkInfo.State.SUCCEEDED -> IndexingState.Completed(info.outputData.getInt(IndexingWorker.KEY_DONE, done))
                        WorkInfo.State.ENQUEUED  -> IndexingState.Ready(done, total)
                        WorkInfo.State.CANCELLED,
                        WorkInfo.State.FAILED    -> IndexingState.Ready(done, total)
                        else -> _state.value
                    }
                }
            }

        }

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
    private fun existsInMediaStore(uri: Uri): Boolean =
        try {
            appContext.contentResolver.query(
                uri, arrayOf(MediaStore.Images.Media._ID), null, null, null
            )?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            false
        }

    suspend fun scanGallery() {
        imglistMutex.withLock {
            imglist.clear()

            val selectedBuckets = folderPrefs.selectedBuckets.first()

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            var selection: String? = null
            var selectionArgs: Array<String>? = null

            if (selectedBuckets.isNotEmpty()) {
                val placeholders = selectedBuckets.joinToString(",") { "?" }
                selection = "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
                selectionArgs = selectedBuckets.toTypedArray()
            }

            appContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
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
                val imgSet = imglist.toHashSet()

                val inAlbums = dao.albumPhotoUris().map { it.uri }.toHashSet()


                val toDelete = alreadyExists.filter { uri ->
                    if (uri in imgSet) return@filter false
                    if (uri.toString() in inAlbums) {
                        !existsInMediaStore(uri)
                    } else {
                        true
                    }
                }

                if (toDelete.isNotEmpty() && imglist.isNotEmpty()) {
                    Toast.makeText(appContext, "Found ${toDelete.size} missing image(s). Deleting them now.", Toast.LENGTH_SHORT).show()
                    viewModelScope.launch {
                        repo.pruneDeletedPhotos(toDelete)
                    }
                }

                val indexedInScope = alreadyExists.count { it in imgSet }
                if (indexedInScope == imglist.size){
                    _state.value = IndexingState.Completed(imglist.size)
                }else{
                    if (imglist.isNotEmpty())  _state.value = IndexingState.Ready(indexedInScope,imglist.size)
                }
            }catch (e: Exception){
                Log.e("Cleanup", "prune failed", e)
            }
        }


    }
    }


    fun getBatteryTemperature() {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = appContext.registerReceiver(null, intentFilter)

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        batteryTemp.value =  tempTenths / 10.0f
    }


    fun startIndexing() {
        val req = OneTimeWorkRequestBuilder<IndexingWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        wm.enqueueUniqueWork(IndexingWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, req)
    }

    fun pause() = wm.cancelUniqueWork(IndexingWorker.UNIQUE_NAME)

    override fun onCleared() {
        viewModelScope.launch(NonCancellable) {
            repo.unloadModel()
        }
        super.onCleared()
    }
    suspend fun getAvailableFolders() = withContext(Dispatchers.IO) {
        val folders = LinkedHashMap<String, FolderInfo>()

        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        appContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val pathId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)


            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(idCol) ?: continue
                val bucketName = cursor.getString(nameCol) ?: "Unknown"
                val path  = cursor.getString(pathId)?:"Unknown"

                val existing = folders[bucketId]
                folders[bucketId] = FolderInfo(0,
                    bucketId,
                    bucketName,
                    path,
                    (existing?.itemCount ?: 0) + 1
                )
            }
        }
        _Folders.value =folders.values.filter { it.itemCount>0 }.sortedByDescending { it.itemCount }
    }

    private fun FolderInfo.segments() = folderPath.split("/").filter { it.isNotEmpty() }

    fun treeFolders(folders: List<FolderInfo>, depth: Int = 0): List<FolderInfo> {
        if (folders.isEmpty()) return emptyList()
        val rootLevel = if (depth == 0)
            folders.filter { it.segments().isEmpty() }.map { it.copy(rank = 0) }
        else emptyList()

        val grouped = folders
            .filter { it.segments().size > depth }
            .groupBy { it.segments()[depth] }

        val nodes = grouped.map { (segment, group) ->
            val exact = group.firstOrNull { it.segments().size == depth + 1 }
            val deeper = group.filter { it.segments().size > depth + 1 }
            val subFolders = treeFolders(deeper, depth + 1)

            val path = group.first().segments().take(depth + 1).joinToString("/") + "/"

            when {

                exact != null && subFolders.isNotEmpty() -> {
                    val self = exact.copy(
                        rank = depth + 1,
                        bucketName = "/",
                        subFolders = emptyList()
                    )
                    val all = listOf(self) + subFolders
                    FolderInfo(
                        rank = depth,
                        bucketId = path,
                        bucketName = segment,
                        folderPath = path,
                        itemCount = all.sumOf { it.itemCount },
                        subFolders = all
                    )
                }

                exact != null -> exact.copy(rank = depth, subFolders = emptyList())
                else -> FolderInfo(
                    rank = depth,
                    bucketId = path,
                    bucketName = segment,
                    folderPath = path,
                    itemCount = subFolders.sumOf { it.itemCount },
                    subFolders = subFolders
                )
            }
        }

        return rootLevel + nodes
    }
    fun setPermission(value: Boolean){
        isPermissionGrandted.value=value
    }
    sealed class IndexingState {
        object Idle : IndexingState()

        data class Ready(val processed: Int, val total: Int) : IndexingState()
        data class Running(val processed: Int, val total: Int, val etaMinutes: Float) : IndexingState()
        data class Completed(val total: Int) : IndexingState()
    }

}