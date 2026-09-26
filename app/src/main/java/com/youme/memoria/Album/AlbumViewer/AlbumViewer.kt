package com.youme.memoria.Album.AlbumViewer

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import androidx.appcompat.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.youme.inkdex.roomCach.AlbumPhotoEntity
import com.youme.memoria.Album.AlbumViewModel
import com.youme.memoria.Album.deleteAlert
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.R
import com.youme.memoria.imageViewer.imageViewerActivity
import com.youme.memoria.search.SearchResultCache
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.getValue

class AlbumPhotoViewer(private val albumId:String,private val title:String) : BottomSheetDialogFragment(R.layout.album_viewer_layout) {
    override fun getTheme(): Int = R.style.Theme_Memoria_BottomSheet

    private val viewModuel: AlbumViewModel by viewModels()


    private lateinit var Adapter: AlbumViewerAdapter

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
        Adapter  = AlbumViewerAdapter(emptyList(), onItemClick = { pos->
            val intent = Intent(requireContext(), imageViewerActivity::class.java)
            intent.putExtra("pos",pos)
            intent.putExtra("from_search",true)

            startActivity(intent)

        },
            onItemSelected = {selected,photos -> setupSelectedUi(selected,photos)})
        val toolbar = view.findViewById<MaterialToolbar>(R.id.materialToolbar2)
        toolbar.apply {
            title= this@AlbumPhotoViewer.title
            setNavigationOnClickListener {
                dismiss()
            }
        }


        setupRecyclerView()
        setupUI()
        setupSelectDeselect()

    }
    private fun  setupUI(){
        lifecycleScope.launch {
            viewModuel.getAlbumPhotos(albumId).collectLatest { photos->
                Adapter.submitData(photos)
                SearchResultCache.searchResults = photos.map {
                    ImageUriItem(id=0,uri=it.uri.toUri(), width = 0, height = 0)
                }
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
        val toolbar = requireView().findViewById<MaterialToolbar>(R.id.materialToolbar2)
        val deleteButton = requireView().findViewById<Button>(R.id.button11)
        val selectionToolBar = requireView().findViewById<CardView>(R.id.cardView10)


        toolbar.title = if (selected.isNotEmpty()) "${selected.size} selected" else title
        selectionToolBar.animateVisibility(selected.isNotEmpty())

        deleteButton.apply {
            text = "Remove (${selected.size})"
            animateVisibility(selected.isNotEmpty())
        }


        deleteButton.setOnClickListener {
            deleteAlert(
                requireContext(),
                "Delete Photos?",
                "Are you sure you want to delete ${selected.size} photo${if (selected.size == 1) "" else "s"} from this album?"
            ) {
                viewModuel.deleteAlbumPhotos(selected, Adapter, photosList)
            }
        }
    }


    private fun setupRecyclerView(){
        val recyclerView = requireView().findViewById<RecyclerView>(R.id.rec)
        recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(),3)
            adapter = Adapter
        }
    }

    override fun onDestroy() {
        SearchResultCache.searchResults = null
        super.onDestroy()
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