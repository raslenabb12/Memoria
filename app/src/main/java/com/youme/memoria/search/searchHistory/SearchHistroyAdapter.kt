package com.youme.memoria.search.searchHistory


import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.youme.memoria.ImageLoading.ImageUriItem
import com.youme.memoria.R
import com.youme.memoria.roomCach.SearchHistoryEntity
import java.util.concurrent.TimeUnit


class HistoryItemDiffCallback : DiffUtil.ItemCallback<SearchHistoryEntity>() {
    override fun areItemsTheSame(old: SearchHistoryEntity, new: SearchHistoryEntity) = old.query == new.query
    override fun areContentsTheSame(old: SearchHistoryEntity, new: SearchHistoryEntity) = old == new
}
class SearchHistroyAdapter(
    private val onPinnedClicked: (String, Boolean)-> Unit,
    private val onClick: (String)-> Unit,
    private val onLongClick : (String) -> Unit
) : ListAdapter<SearchHistoryEntity, SearchHistroyAdapter.ViewHolder>(
    HistoryItemDiffCallback()) {

    private var items: List<SearchHistoryEntity> = emptyList()
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textView41)
        val timeStampText: TextView = view.findViewById(R.id.textView42)


        val pinnedImage: ImageView = view.findViewById(R.id.imageView20)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_history_item, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item  = items[position]
        holder.textView.text =item.query

        holder.timeStampText.text = item.timestamp.toTimeAgo()
        holder.pinnedImage.apply {
            setImageResource(if (item.pinned) R.drawable.baseline_push_pin_24 else R.drawable.outline_push_pin_24)
            rotation  = if (item.pinned) 40f else 0f
            imageTintList  = ColorStateList.valueOf(if (item.pinned) Color.RED else Color.WHITE)
            setOnClickListener {
                onPinnedClicked(item.query,item.pinned)
            }
        }

        holder.itemView.setOnClickListener {
            onClick(item.query)
        }
        holder.itemView.setOnLongClickListener {
            onLongClick(item.query)
            true
        }

    }

    override fun getItemCount(): Int = items.size


    fun  submitData(list : List<SearchHistoryEntity>){
        items  =list
        notifyDataSetChanged()
    }
    fun Long.toTimeAgo(): String {
        val now = System.currentTimeMillis()
        val diff = (now - this).coerceAtLeast(0L)

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            seconds < 10 -> "Just now"
            seconds < 60 -> "${seconds}s ago"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            days < 30 -> "${days / 7}w ago"
            days < 365 -> "${days / 30}mo ago"
            else -> "${days / 365}y ago"
        }
    }
}