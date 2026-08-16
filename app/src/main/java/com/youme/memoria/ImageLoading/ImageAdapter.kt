package  com.youme.memoria.ImageLoading

import android.net.Uri
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.youme.memoria.R
import kotlinx.parcelize.Parcelize
@Parcelize
data class ImageUriItem(
    val id: Long,
    val uri: Uri,
    val width: Int,
    val height: Int
) : Parcelable {
    val ratio: Float get() = if (width > 0) height.toFloat() / width.toFloat() else 1f
}
class ImageDiffCallback : DiffUtil.ItemCallback<ImageUriItem>() {
    override fun areItemsTheSame(old: ImageUriItem, new: ImageUriItem) = old.id == new.id
    override fun areContentsTheSame(old: ImageUriItem, new: ImageUriItem) = old == new
}

class ImagePagingAdapter(val onImageClick :(ImageView,Int) -> Unit) : PagingDataAdapter<ImageUriItem, ImagePagingAdapter.VH>(ImageDiffCallback()) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.image_layout, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position) ?: return
        val baseWidth = holder.itemView.resources.displayMetrics.widthPixels / 2
        holder.imageView.layoutParams.height = (baseWidth * item.ratio).toInt()

        Glide.with(holder.itemView.context)
            .load(item.uri)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            onImageClick(holder.imageView,position)
        }
    }
}