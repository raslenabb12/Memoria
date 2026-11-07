package com.youme.memoria.settings.FoldersManager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Query
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.youme.memoria.Gallery.FolderInfo
import com.youme.memoria.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FoldersAdapter() : RecyclerView.Adapter<FoldersAdapter.MyViewHolder>() {


    private var items: List<FolderInfo> =emptyList()
    private var filteredItems  : List<FolderInfo> = emptyList()

    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFoldersFlow: StateFlow<Set<String>> = _selectedFolders.asStateFlow()


    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textView26)
        val photosCount: TextView = itemView.findViewById(R.id.textView27)
        val folderPath: TextView = itemView.findViewById(R.id.textView24)
        val checkBox : MaterialCheckBox = itemView.findViewById<MaterialCheckBox>(R.id.checkBox)
        val box: MaterialCardView = itemView.findViewById(R.id.box)



    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.folders_box_item, parent, false)

        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val folder = filteredItems[position]
        val currentSelection = _selectedFolders.value.toMutableSet()

        holder.folderPath.text=folder.folderPath
        holder.title.text= folder.bucketName
        holder.photosCount.text = "${folder.itemCount} photo"
        holder.checkBox.isChecked = currentSelection.contains(folder.bucketId)

        holder.box.setCardBackgroundColor(if (currentSelection.contains(folder.bucketId)) "#4ED0BCFF".toColorInt() else
        holder.itemView.resources.getColor(R.color.neutral_17))

        holder.box.setOnClickListener {
            holder.checkBox.isChecked = !holder.checkBox.isChecked

            _selectedFolders.update { currentSet ->
                if (holder.checkBox.isChecked) currentSet +folder.bucketId else currentSet - folder.bucketId
            }
            notifyItemChanged(position)
        }
    }
    fun setAllSelected(){
        _selectedFolders.value = items.map { it.bucketId }.toSet()
        notifyDataSetChanged()
    }
    fun setAllDeselected(){
        _selectedFolders.value = emptySet()
        notifyDataSetChanged()
    }
    fun setSelected(ids: Set<String>){
        _selectedFolders.value=ids
        notifyDataSetChanged()
    }
    override fun onViewRecycled(holder: MyViewHolder) {
        super.onViewRecycled(holder)
    }
    fun search(query: String){
        filteredItems = if (query.isNotEmpty())items.filter { it.bucketName.contains(query,ignoreCase = true) } else items
        notifyDataSetChanged()
    }

    fun submitData(folders : List<FolderInfo>){
        items = folders
        filteredItems = folders
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return filteredItems.size
    }
}