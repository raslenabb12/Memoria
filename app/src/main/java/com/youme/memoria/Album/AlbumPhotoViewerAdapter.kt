package com.youme.memoria.Album

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.card.MaterialCardView
import com.youme.inkdex.roomCach.AlbumEntity
import com.youme.inkdex.roomCach.AlbumPhotoEntity
import com.youme.inkdex.roomCach.AlbumsList
import com.youme.memoria.R
import kotlin.collections.mutableListOf

class AlbumPhotoViewerAdapter(
    private var items: List<AlbumPhotoEntity>,
    private var onItemSelected : (selected: MutableList<String>,photos:List<AlbumPhotoEntity>) -> Unit,
    private var onItemClick : (uri: String) -> Unit,
) : RecyclerView.Adapter<AlbumPhotoViewerAdapter.MyViewHolder>() {

    val selected =mutableListOf<String>()

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView13)
        val checkMark: ImageView = itemView.findViewById(R.id.imageView14)
        val box: MaterialCardView = itemView.findViewById(R.id.box)

    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.album_photo_view_item, parent, false)

        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val photo = items[position]

        Glide.with(holder.itemView.context)
            .load(photo.uri)
            .transition(DrawableTransitionOptions.withCrossFade(300)).into(holder.imageView)

        val isSelected = selected.contains(photo.uri)
        holder.checkMark.isVisible = isSelected

        holder.box.strokeWidth = if (isSelected) 10 else 0


        holder.itemView.setOnLongClickListener {
            if (selected.isEmpty()){
                selected.add(photo.uri)
                onItemSelected(selected,items)
                notifyItemChanged(position)

            }
            true
        }
        holder.itemView.setOnClickListener {
            if (selected.isNotEmpty()){
                if (selected.contains(photo.uri)) selected.remove(photo.uri) else  selected.add(photo.uri)
                onItemSelected(selected,items)
                notifyItemChanged(position)
            }else{
                onItemClick(photo.uri)
            }

        }
    }

    fun selectAll(){
        selected.clear()
        selected.addAll(items.map { it.uri })
        onItemSelected(selected,items)
        notifyDataSetChanged()
    }

    fun removeSelected(){
        selected.clear()
        onItemSelected(selected,items)
        notifyDataSetChanged()
    }
    fun deselectAll(){
        selected.clear()
        selected.removeAll(items.map { it.uri })
        onItemSelected(selected,items)
        notifyDataSetChanged()
    }
    fun submitData(albums : List<AlbumPhotoEntity>){
        items = albums
        notifyDataSetChanged()
    }
    override fun onViewRecycled(holder: MyViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView.context).clear(holder.imageView)
    }

    override fun getItemCount(): Int {
        return items.size
    }
}