package com.youme.memoria.Album

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.card.MaterialCardView
import com.youme.inkdex.roomCach.AlbumEntity
import com.youme.inkdex.roomCach.AlbumsList
import com.youme.memoria.R

class AlbumAdapter(
    private var items: List<AlbumsList>,
    private var onItemClick : (albumId: String,title:String) -> Unit,
    private var onItemSelected : (selected: List<String>) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.MyViewHolder>() {

    val selectedAlbum = mutableListOf<String>()

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textView22)
        val countText: TextView = itemView.findViewById(R.id.textView23)
        val imageViewList = listOf(R.id.imageView8,R.id.imageView9,R.id.imageView10,R.id.imageView11,)
        val box: MaterialCardView = itemView.findViewById(R.id.cardView9)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.album_item_layout, parent, false)

        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val albumlist = items[position]
        holder.countText.text = albumlist.size.toString()
        holder.title.text  = albumlist.name
        albumlist.images.forEachIndexed { i ,item->
            val imageview= holder.itemView.findViewById<ImageView>(holder.imageViewList[i])
            Glide.with(holder.itemView.context)
                .load(item)
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(imageview)
        }
        val isSelected = selectedAlbum.contains(albumlist.albumId)
        holder.box.strokeWidth = if (isSelected) 10 else 0





        holder.itemView.setOnClickListener {
            if(selectedAlbum.isNotEmpty()){
                if (selectedAlbum.contains(albumlist.albumId)) selectedAlbum.remove(albumlist.albumId) else selectedAlbum.add(albumlist.albumId)
                onItemSelected(selectedAlbum)
                notifyItemChanged(position)

            }else{
                onItemClick(albumlist.albumId,albumlist.name)
            }

        }
        holder.itemView.setOnLongClickListener {
            if (selectedAlbum.isEmpty()){
                selectedAlbum.add(albumlist.albumId)
                onItemSelected(selectedAlbum)
                notifyItemChanged(position)
            }
            true
        }
    }
    override fun onViewRecycled(holder: MyViewHolder) {
        super.onViewRecycled(holder)
        holder.imageViewList.forEach { item->
            val imageview = holder.itemView.findViewById<ImageView>(item)
            Glide.with(holder.itemView.context).clear(imageview)
        }
    }
    fun removeSelected(){
        selectedAlbum.clear()
        onItemSelected(selectedAlbum)
        notifyDataSetChanged()
    }
    fun submitData(albums : List<AlbumsList>){
        items = albums
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return items.size
    }
}