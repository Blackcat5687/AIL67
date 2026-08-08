package com.notekeep.local.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.notekeep.local.R
import com.notekeep.local.data.Note
import com.notekeep.local.databinding.ItemNoteBinding

class NoteAdapter(
    private val onClick: (Note) -> Unit,
    private val onTogglePin: (Note) -> Unit
) : ListAdapter<NoteListItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is NoteListItem.Header -> VIEW_TYPE_HEADER
        is NoteListItem.Item -> VIEW_TYPE_NOTE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_note_header, parent, false)
            HeaderViewHolder(view as TextView)
        } else {
            val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            NoteViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NoteListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is NoteListItem.Item -> (holder as NoteViewHolder).bind(item.note)
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        val params = holder.itemView.layoutParams
        if (params is StaggeredGridLayoutManager.LayoutParams) {
            params.isFullSpan = holder is HeaderViewHolder
        }
    }

    inner class HeaderViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(header: NoteListItem.Header) {
            textView.text = header.text
        }
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            binding.textTitle.text = note.previewTitle()
            binding.textContent.text = note.previewContent()
            binding.textTitle.visibility =
                if (note.previewTitle().isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            val colorRes = NoteColors.palette.getOrElse(note.color) { R.color.note_0 }
            binding.cardRoot.setCardBackgroundColor(
                androidx.core.content.ContextCompat.getColor(binding.root.context, colorRes)
            )

            val bgUri = note.backgroundImageUri
            if (bgUri != null) {
                binding.imageBackground.visibility = android.view.View.VISIBLE
                binding.imageScrim.visibility = android.view.View.VISIBLE
                try {
                    // the image fills the whole card as a background behind the text only;
                    // it sizes itself to whatever height the text content already gives the
                    // card, so it never changes the note's shape or size.
                    binding.imageBackground.setImageURI(Uri.parse(bgUri))
                } catch (e: Exception) {
                    binding.imageBackground.visibility = android.view.View.GONE
                    binding.imageScrim.visibility = android.view.View.GONE
                }
            } else {
                binding.imageBackground.visibility = android.view.View.GONE
                binding.imageScrim.visibility = android.view.View.GONE
            }

            binding.imagePin.visibility = if (note.pinned) android.view.View.VISIBLE else android.view.View.GONE
            binding.imagePin.setOnClickListener { onTogglePin(note) }

            binding.root.setOnClickListener { onClick(note) }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_NOTE = 1

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NoteListItem>() {
            override fun areItemsTheSame(oldItem: NoteListItem, newItem: NoteListItem) =
                oldItem.stableKey == newItem.stableKey
            override fun areContentsTheSame(oldItem: NoteListItem, newItem: NoteListItem) =
                oldItem == newItem
        }
    }
}
