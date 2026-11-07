package com.youme.memoria.Gallery

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.card.MaterialCardView
import com.youme.memoria.Album.AlbumViewer.AlbumViewerAdapter
import com.youme.memoria.R
import com.youme.memoria.roomCach.AlbumPhotoEntity
import com.youme.memoria.roomCach.AlbumsList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryDateAdapter(private val onClick : (Uri)-> Unit) :
    PagingDataAdapter<DateImageBlocks, GalleryDateAdapter.MyViewHolder>(DIFF_CALLBACK) {

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recyclerView: RecyclerView = itemView.findViewById<RecyclerView>(R.id.rec)
        val date: TextView = itemView.findViewById<TextView>(R.id.textView17)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.data_image_items, parent, false)

        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val dateBlock = getItem(position) ?: return
        val Adapter = AlbumViewerAdapter(emptyList(), { _, _ -> }, {
            onClick(dateBlock.images[it].uri)
        },false)

        holder.date.text = dateBlock.dataTaken

        holder.recyclerView.apply {
            layoutManager = GridLayoutManager(holder.itemView.context, 3)
            adapter = Adapter
        }

        Adapter.submitData(
            dateBlock.images.map {
                AlbumPhotoEntity("", it.uri.toString())
            }
        )
    }

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<DateImageBlocks>() {

                override fun areItemsTheSame(
                    oldItem: DateImageBlocks,
                    newItem: DateImageBlocks
                ): Boolean {
                    return oldItem.dataTaken == newItem.dataTaken
                }

                override fun areContentsTheSame(
                    oldItem: DateImageBlocks,
                    newItem: DateImageBlocks
                ): Boolean {
                    return oldItem == newItem
                }
            }
    }
}