package com.youme.memoria.Album

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.youme.inkdex.roomCach.AlbumPhotoEntity
import com.youme.inkdex.roomCach.AlbumsList
import com.youme.inkdex.roomCach.PhotosDatabase
import com.youme.inkdex.roomCach.toByteArray
import com.youme.inkdex.roomCach.toFloatArray
import com.youme.memoria.Album.AlbumViewer.AlbumViewerAdapter
import com.youme.memoria.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID


class AlbumViewModel(application: Application): AndroidViewModel(application){

    val albumRepo = AlbumRepo(application)
    val photoRepo = PhotoRepository(application)

    private val dao = PhotosDatabase.getInstance(application).AlbumDao()
    private val photoDao = PhotosDatabase.getInstance(application).photoDao()

    val albums = dao.getAlbumsWithPhotos()
        .map { list -> list.map { AlbumsList(it.album.id,it.album.name, it.photos.take(4).map { p -> p.uri.toUri() }, size = it.photos.size) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albumPhotos = MutableStateFlow<List<AlbumPhotoEntity>>(emptyList())




    fun getAlbumPhotos(albumId : String) = dao.getAlbumsPhotos(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())



    fun deleteAlbumPhotos(photos: List<String>, Adapter: AlbumViewerAdapter, photosList : List<AlbumPhotoEntity>){
        viewModelScope.launch {

            val newlist = photosList.filter { it.uri !in photos }
            val albumId = photosList.getOrNull(0)?.albumId

            val images=  photoDao.getPhotosByUri(newlist.map { it.uri })
            albumId?.let {
                if (newlist.isNotEmpty()){
                    dao.updateCentroidEmbedding(albumId,albumRepo.computeCentroid(images.map { it.embedding.toFloatArray() }).toByteArray())
                }else{
                    dao.updateCentroidEmbedding(albumId, ByteArray(0))
                }
            }
            dao.removeAlbumPhotos(photos)
            Adapter.removeSelected()

        }

    }

    suspend fun updatedB(){
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllAlbums().filter { it.autoUpdate }.forEach { album->
                    if (album.centroidEmbedding.isNotEmpty()){
                        viewModelScope.launch {
                            val photosList = photoRepo.searchByEmbd(album.centroidEmbedding.toFloatArray(),photoDao.getAll(),0.80f)
                            albumRepo.addPhotosToAlbum(photosList.map { AlbumPhotoEntity(album.id,it.first.uri) })
                            dao.updateCentroidEmbedding(album.id,albumRepo.computeCentroid(photosList.map { it.first.embedding.toFloatArray() }).toByteArray())
                        }
                    }
                    else{
                        updateEmbeddingsByNameSearch(album.name,album.id)
                }
            }
        }
    }

    private fun updateEmbeddingsByNameSearch(albumName:String,albumId: String){
        viewModelScope.launch {
            photoRepo.initializeTextModel()
            val photosList = photoRepo.search(albumName,photoDao.getAll(),0.20f)
            albumRepo.addPhotosToAlbum(photosList.map { AlbumPhotoEntity(albumId,it.first.uri) })
            dao.updateCentroidEmbedding(albumId,albumRepo.computeCentroid(photosList.map { it.first.embedding.toFloatArray() }).toByteArray())
        }
    }

    fun deleteAlbum(albumIds: List<String>, Adapter: AlbumAdapter){
        viewModelScope.launch {
            dao.deleteAlbum(albumIds)
            dao.deleteAllAlbumPhotos(albumIds)
            Adapter.removeSelected()
        }
    }

    fun createAlbum(title: String, startMode: Int, autoUpdate: Boolean){
        viewModelScope.launch {
            val albumId = UUID.randomUUID().toString()
            albumRepo.addAlbum(albumId,title,autoUpdate)
            if (startMode==0){
                viewModelScope.launch {
                    updateEmbeddingsByNameSearch(title,albumId)
                }
            }
        }
    }
    override fun onCleared() {
        viewModelScope.launch(NonCancellable) {
            photoRepo.unloadModel()
        }
        super.onCleared()
    }

}