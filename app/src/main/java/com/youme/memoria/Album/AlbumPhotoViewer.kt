package com.youme.memoria.Album

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.youme.inkdex.roomCach.AlbumPhotoEntity
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.R
import com.youme.memoria.imageViewer.imageViewerActivity
import com.youme.memoria.search.SearchResultCache
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.getValue

class AlbumPhotoViewer(private val albumId:String,private val title:String) : BottomSheetDialogFragment(R.layout.album_photo_viewer_layout) {
    override fun getTheme(): Int = R.style.Theme_Memoria_BottomSheet

    private val viewModuel: AlbumViewModel by viewModels()
    private lateinit var Adapter: AlbumPhotoViewerAdapter

    @SuppressLint("StringFormatInvalid")
    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet =
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return
        bottomSheet.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.peekHeight = Resources.getSystem().displayMetrics.heightPixels
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            behavior.skipCollapsed = true
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Adapter  = AlbumPhotoViewerAdapter(emptyList(), onItemClick = {uri->
            SearchResultCache.searchResults = listOf(ImageUriItem(id=0,uri=uri.toUri(), width = 0, height = 0))
            val intent = Intent(requireContext(), imageViewerActivity::class.java)
            intent.putExtra("pos",0)
            intent.putExtra("from_search",true)

            startActivity(intent)

        },
            onItemSelected = {selected,photos -> setupSelectedUi(selected,photos)})

        requireView().findViewById<TextView>(R.id.textView24).text= title


        setupRecyclerView()
        setupUIDate()
        setupSelectDeselect()

    }
    private fun  setupUIDate(){
        lifecycleScope.launch {
            viewModuel.getAlbumPhotos(albumId).collectLatest { photos->
                Adapter.submitData(photos)
            }
        }

    }
    private fun setupSelectDeselect(){
        val selectAllButton  = requireView().findViewById<Button>(R.id.button13)
        val deselectAllButton  = requireView().findViewById<Button>(R.id.button12)
        selectAllButton.setOnClickListener {
            Adapter.selectAll()
        }
        deselectAllButton.setOnClickListener {
            Adapter.deselectAll()
        }
    }
    private fun setupSelectedUi(selected: List<String>, photosList: List<AlbumPhotoEntity>){
        val countText = requireView().findViewById<TextView>(R.id.textView25)
        val countBox = requireView().findViewById<CardView>(R.id.count_box)
        val deleteButton = requireView().findViewById<Button>(R.id.button11)


        countBox.animateVisibility( selected.isNotEmpty())
        countText.text= selected.
        size.toString()

        deleteButton.animateVisibility(selected.isNotEmpty())

        deleteButton.setOnClickListener {
            viewModuel.deleteAlbumPhotos(selected,Adapter,photosList)
        }
    }


    private fun setupRecyclerView(){
        val recyclerView = requireView().findViewById<RecyclerView>(R.id.rec)
        recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(),3)
            adapter = Adapter
        }
    }
}
fun View.animateVisibility(visible: Boolean) {
    if (visible==this.isVisible) return
    this.apply {
        if (visible) isVisible= true
        scaleY=if (visible) 0f else 1f
        scaleX=if (visible) 0f else 1f
    }
    this.animate().scaleX(if (visible) 1f else 0f).scaleY(if (visible) 1f else 0f)
        .setDuration(200).withEndAction {
            this.isVisible = visible
        }.start()
}