package com.notekeep.local.ui

import com.notekeep.local.data.Note

/** A row in the main notes grid: either a section header ("Pinned" / "Others") or a note card. */
sealed class NoteListItem {
    data class Header(val text: String) : NoteListItem()
    data class Item(val note: Note) : NoteListItem()

    val stableKey: String
        get() = when (this) {
            is Header -> "header_$text"
            is Item -> "note_${note.id}"
        }
}
