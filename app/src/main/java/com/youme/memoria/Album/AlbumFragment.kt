package com.youme.memoria.Album

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.youme.memoria.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.getValue

class AlbumFragment : Fragment(R.layout.album_layout) {

    private val viewModuel: AlbumViewModel by activityViewModels()
    private lateinit var Adapter : AlbumAdapter


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Adapter = AlbumAdapter(emptyList(), onItemClick = {albumId ,title->
            AlbumPhotoViewer(albumId,title).show(parentFragmentManager,"")
        }, onItemSelected = {selected ->
            setupSelectionUI(selected)
        })

        setupRecycView()

        view.findViewById<FloatingActionButton>(R.id.CreateAlbum).setOnClickListener{
            CreateAlbumBottomFrag().show(parentFragmentManager,"")
        }
        setupUIDate()
    }
    private fun setupSelectionUI(selected: List<String>) {
        val deleteButton = requireView().findViewById<MaterialButton>(R.id.button15)
        deleteButton.animateVisibility(selected.isNotEmpty())

        deleteButton.setOnClickListener {
            deleteAlert(requireContext(),"Delete Album?","Are you sure you want to delete this album? This action cannot be undone."){
                viewModuel.deleteAlbum(selected,Adapter)
            }
        }
    }
    private fun setupRecycView(){
        val recyclerView = requireView().findViewById<RecyclerView>(R.id.AlbumRec)

        recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(),3)
            adapter = Adapter
        }
    }
    private fun setupUIDate(){
        lifecycleScope.launch {
            viewModuel.albums.collectLatest {data->
                Adapter.submitData(data)

            }
        }

    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            viewModuel.updatedB()
        }
    }

}
fun deleteAlert(context: Context,title: String,description:String,onClick : ()->Unit){
   MaterialAlertDialogBuilder(context, R.style.MyAlertDialogTheme)
        .setTitle(title)
        .setMessage(description)
        .setPositiveButton("Delete"){_,_->
            onClick()
        }
        .setNegativeButton("Cancel"){dialog,_->
            dialog.dismiss()

        }
        .show()
}