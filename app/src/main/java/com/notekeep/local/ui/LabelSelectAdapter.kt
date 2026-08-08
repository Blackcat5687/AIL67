package com.notekeep.local.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.notekeep.local.data.Label
import com.notekeep.local.databinding.ItemLabelSelectableBinding

data class LabelRow(val label: Label, val checked: Boolean)

class LabelSelectAdapter(
    private val onToggle: (Label, Boolean) -> Unit,
    private val onDelete: (Label) -> Unit,
    private val showCheckbox: Boolean = true
) : ListAdapter<LabelRow, LabelSelectAdapter.RowViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemLabelSelectableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) = holder.bind(getItem(position))

    inner class RowViewHolder(private val binding: ItemLabelSelectableBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: LabelRow) {
            binding.textLabelName.text = row.label.name
            binding.checkLabel.visibility = if (showCheckbox) android.view.View.VISIBLE else android.view.View.GONE
            binding.checkLabel.setOnCheckedChangeListener(null)
            binding.checkLabel.isChecked = row.checked
            binding.checkLabel.setOnCheckedChangeListener { _, isChecked -> onToggle(row.label, isChecked) }
            binding.buttonDeleteLabel.setOnClickListener { onDelete(row.label) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<LabelRow>() {
            override fun areItemsTheSame(oldItem: LabelRow, newItem: LabelRow) = oldItem.label.id == newItem.label.id
            override fun areContentsTheSame(oldItem: LabelRow, newItem: LabelRow) = oldItem == newItem
        }
    }
}
