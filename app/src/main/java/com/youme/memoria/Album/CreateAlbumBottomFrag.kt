package com.youme.memoria.Album

import android.content.Context
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.youme.inkdex.roomCach.AlbumPhotoEntity
import com.youme.memoria.Album.AlbumViewer.AlbumViewerAdapter
import com.youme.memoria.Gallery.IndexingViewModel
import com.youme.memoria.ImageLoading.ImagePagingAdapter
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.PhotoRepository
import com.youme.memoria.R
import com.youme.memoria.search.SearchViewModuel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.getValue


class CreateAlbumBottomFrag : BottomSheetDialogFragment(R.layout.create_album_bottomfrag) {
    override fun getTheme(): Int = R.style.Theme_Memoria_BottomSheet


    private val viewModuel: AlbumViewModel by activityViewModels()
    private lateinit var imageAdapter: AlbumViewerAdapter

    private var title : String = ""
    private var startMode: Int = 0
    private var autoUpdate : Boolean=true


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setupTitle()
        setupStartMode()
        setupAutoUpdate()
        setupAddImageRefrence()


        val submitButton =requireView().findViewById<MaterialButton>(R.id.button9)
        val cancelButton = requireView().findViewById<MaterialButton>(R.id.button10)
        submitButton.setOnClickListener {
            if(title.isNotEmpty()){
               viewModuel.createAlbum(title,startMode,autoUpdate)
                dismiss()
            }

        }
        cancelButton.setOnClickListener {
            dismiss()
        }



    }
    private fun setupTitle(){
        val editText = requireView().findViewById<TextInputEditText>(R.id.title_textedit)
        editText.doAfterTextChanged {text->
            title = text.toString()
        }

    }
    private fun setupStartMode(){
        val tablayout = requireView().findViewById<TabLayout>(R.id.startIndixTable)
        val addToImageContainer = requireView().findViewById<View>(R.id.addImageContainer)
        tablayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    startMode=tablayout.selectedTabPosition
                    addToImageContainer.isVisible = (tablayout.selectedTabPosition==1)

                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}

                override fun onTabReselected(tab: TabLayout.Tab) {}
            }
        )
    }
    private fun setupAddImageRefrence(){
        val recyclerView = requireView().findViewById<RecyclerView>(R.id.imagesRec)
        val addImageButton = requireView().findViewById<Button>(R.id.button21)
        val pickedImage = registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                viewModuel.addReferncePhoto(uri)
            }
        }
        addImageButton.setOnClickListener { pickedImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

        imageAdapter = AlbumViewerAdapter(emptyList(),{selected,_->
            selectedRefrenecUi(selected)
        },{})
        recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = imageAdapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModuel.refrenceImages.collectLatest { images->
                    imageAdapter.submitData(images.map { AlbumPhotoEntity("",it.toString()) })
                }
            }

        }
    }
    private fun selectedRefrenecUi(selected: List<String>){
        val deleteButton = requireView().findViewById<Button>(R.id.button22)
        deleteButton.isVisible=selected.isNotEmpty()
        deleteButton.setOnClickListener {
            viewModuel.removeReferncePhotos(selected.map { it.toUri() })
            imageAdapter.removeSelected()
        }
    }
    private fun setupAutoUpdate(){
        val switch = requireView().findViewById<MaterialSwitch>(R.id.autoUpdateBt)
        switch.setOnClickListener {
            autoUpdate = switch.isChecked
        }
    }
}