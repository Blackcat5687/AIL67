package com.notekeep.local.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labels")
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = ""
)

/** A label together with how many active (non-archived, non-deleted) notes carry it. */
data class LabelWithCount(
    val id: Long,
    val name: String,
    val noteCount: Int
)
