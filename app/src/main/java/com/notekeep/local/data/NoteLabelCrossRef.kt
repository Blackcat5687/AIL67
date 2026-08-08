package com.notekeep.local.data

import androidx.room.Entity

@Entity(tableName = "note_label_cross_ref", primaryKeys = ["noteId", "labelId"])
data class NoteLabelCrossRef(
    val noteId: Long,
    val labelId: Long
)
