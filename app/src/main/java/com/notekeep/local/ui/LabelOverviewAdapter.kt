package com.notekeep.local.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.notekeep.local.data.LabelWithCount
import com.notekeep.local.databinding.ItemLabelOverviewRowBinding

class LabelOverviewAdapter(
    private val onClick: (LabelWithCount) -> Unit
) : ListAdapter<LabelWithCount, LabelOverviewAdapter.RowViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemLabelOverviewRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) = holder.bind(getItem(position))

    inner class RowViewHolder(private val binding: ItemLabelOverviewRowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LabelWithCount) {
            binding.textLabelName.text = item.name
            binding.textLabelCount.text = item.noteCount.toString()
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LabelWithCount>() {
            override fun areItemsTheSame(oldItem: LabelWithCount, newItem: LabelWithCount) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: LabelWithCount, newItem: LabelWithCount) = oldItem == newItem
        }
    }
}
