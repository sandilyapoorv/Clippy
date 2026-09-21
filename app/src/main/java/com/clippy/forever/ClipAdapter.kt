package com.clippy.forever

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clippy.forever.databinding.ItemClipBinding
import java.io.File
import java.text.DateFormat
import java.util.Date

class ClipAdapter(
    private val onCopy: (ClipRecord) -> Unit,
    private val onEdit: (ClipRecord) -> Unit,
    private val onDelete: (ClipRecord) -> Unit,
) : ListAdapter<ClipRecord, ClipAdapter.Holder>(Diff) {

    object Diff : DiffUtil.ItemCallback<ClipRecord>() {
        override fun areItemsTheSame(oldItem: ClipRecord, newItem: ClipRecord) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ClipRecord, newItem: ClipRecord) = oldItem == newItem
    }

    class Holder(val binding: ItemClipBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemClipBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val isImage = item.kind == "IMAGE"
        holder.binding.kind.text = context.getString(
            if (isImage) R.string.kind_image else R.string.kind_text,
        )
        holder.binding.whenLabel.text = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
        ).format(Date(item.createdAt))
        holder.binding.body.isVisible = !item.text.isNullOrBlank()
        holder.binding.body.text = item.text.orEmpty()
        holder.binding.image.isVisible = isImage && !item.imagePath.isNullOrBlank()
        if (isImage && item.imagePath != null) {
            holder.binding.image.setImageURI(android.net.Uri.fromFile(File(item.imagePath)))
        } else {
            holder.binding.image.setImageDrawable(null)
        }
        holder.binding.editButton.isVisible = !isImage
        holder.binding.copyButton.setOnClickListener { onCopy(item) }
        holder.binding.editButton.setOnClickListener { onEdit(item) }
        holder.binding.deleteButton.setOnClickListener { onDelete(item) }
    }
}
