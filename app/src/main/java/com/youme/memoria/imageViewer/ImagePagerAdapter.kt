package com.youme.memoria.imageViewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.R
class ImagePagerAdapter :
    PagingDataAdapter<ImageUriItem, ImagePagerAdapter.ImageViewHolder>(DIFF_CALLBACK) {

    inner class ImageViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView6)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ImageViewHolder,
        position: Int
    ) {
        val item = getItem(position) ?: run {
            Glide.with(holder.imageView).clear(holder.imageView)
            return
        }

        Glide.with(holder.imageView)
            .load(item.uri)
            .into(holder.imageView)
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.imageView).clear(holder.imageView)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ImageUriItem>() {
            override fun areItemsTheSame(oldItem: ImageUriItem, newItem: ImageUriItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ImageUriItem, newItem: ImageUriItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}