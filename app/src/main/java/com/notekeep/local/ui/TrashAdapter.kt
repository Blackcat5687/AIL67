package com.notekeep.local.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.notekeep.local.R
import com.notekeep.local.data.Note
import com.notekeep.local.databinding.ItemTrashNoteBinding
import java.util.concurrent.TimeUnit

class TrashAdapter(
    private val onRestore: (Note) -> Unit,
    private val onDeleteForever: (Note) -> Unit
) : ListAdapter<Note, TrashAdapter.TrashViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val binding = ItemTrashNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrashViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) = holder.bind(getItem(position))

    inner class TrashViewHolder(private val binding: ItemTrashNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            binding.textTitle.text = note.previewTitle().ifBlank {
                note.previewContent().take(24).ifBlank { binding.root.context.getString(R.string.hint_title) }
            }
            binding.textContent.text = note.previewContent()
            binding.textTitle.visibility =
                if (note.previewTitle().isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            val deletedAt = note.deletedAt ?: System.currentTimeMillis()
            val elapsedDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - deletedAt)
            val daysLeft = (RETENTION_DAYS - elapsedDays).coerceAtLeast(0)
            binding.textDaysLeft.text = binding.root.context.resources.getQuantityString(
                R.plurals.trash_days_left, daysLeft.toInt(), daysLeft.toInt()
            )

            binding.buttonRestore.setOnClickListener { onRestore(note) }
            binding.buttonDeleteForever.setOnClickListener { onDeleteForever(note) }
        }
    }

    companion object {
        const val RETENTION_DAYS = 30L

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
        }
    }
}
