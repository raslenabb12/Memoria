package com.youme.memoria.settings.FoldersManager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.youme.memoria.Gallery.FolderInfo
import com.youme.memoria.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FoldersAdapter(
    private var items: List<FolderInfo>
) : RecyclerView.Adapter<FoldersAdapter.MyViewHolder>() {

    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFoldersFlow: StateFlow<Set<String>> = _selectedFolders.asStateFlow()


    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textView26)
        val photosCount: TextView = itemView.findViewById(R.id.textView27)
        val checkBox : MaterialCheckBox = itemView.findViewById<MaterialCheckBox>(R.id.checkBox)
        val box: MaterialCardView = itemView.findViewById(R.id.box)



    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.folders_box_item, parent, false)

        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val folder = items[position]
        val currentSelection = _selectedFolders.value.toMutableSet()

        holder.title.text= folder.bucketName
        holder.photosCount.text = "${folder.itemCount} photo"
        holder.checkBox.isChecked = currentSelection.contains(folder.bucketId)

        holder.box.setOnClickListener {
            holder.checkBox.isChecked = !holder.checkBox.isChecked

            _selectedFolders.update { currentSet ->
                if (holder.checkBox.isChecked) currentSet +folder.bucketId else currentSet - folder.bucketId
            }
        }


    }
    fun setSelected(ids: Set<String>){
        _selectedFolders.value=ids
        notifyDataSetChanged()
    }
    override fun onViewRecycled(holder: MyViewHolder) {
        super.onViewRecycled(holder)
    }
    fun submitData(folders : List<FolderInfo>){
        items = folders
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return items.size
    }
}