package com.notekeep.local.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    @Query("SELECT * FROM labels ORDER BY name ASC")
    fun observeAll(): Flow<List<Label>>

    @Query("SELECT * FROM labels ORDER BY name ASC")
    suspend fun getAllOnce(): List<Label>

    @Insert
    suspend fun insert(label: Label): Long

    @Update
    suspend fun update(label: Label)

    @Delete
    suspend fun delete(label: Label)

    @Query("DELETE FROM note_label_cross_ref WHERE labelId = :labelId")
    suspend fun clearAssignmentsForLabel(labelId: Long)

    @Query("SELECT labelId FROM note_label_cross_ref WHERE noteId = :noteId")
    suspend fun labelIdsForNote(noteId: Long): List<Long>

    @Query("SELECT * FROM note_label_cross_ref")
    suspend fun getAllCrossRefsOnce(): List<NoteLabelCrossRef>

    @Query("SELECT * FROM labels WHERE id IN (SELECT labelId FROM note_label_cross_ref WHERE noteId = :noteId) ORDER BY name ASC")
    suspend fun labelsForNote(noteId: Long): List<Label>

    @Query(
        "SELECT labels.id AS id, labels.name AS name, COUNT(note_label_cross_ref.noteId) AS noteCount " +
            "FROM labels LEFT JOIN note_label_cross_ref ON labels.id = note_label_cross_ref.labelId " +
            "AND note_label_cross_ref.noteId IN (SELECT id FROM notes WHERE archived = 0 AND deletedAt IS NULL) " +
            "GROUP BY labels.id ORDER BY labels.name ASC"
    )
    suspend fun getLabelsWithCounts(): List<LabelWithCount>

    @Query(
        "SELECT notes.* FROM notes " +
            "INNER JOIN note_label_cross_ref ON notes.id = note_label_cross_ref.noteId " +
            "WHERE note_label_cross_ref.labelId = :labelId AND notes.archived = 0 AND notes.deletedAt IS NULL " +
            "ORDER BY notes.pinned DESC, notes.createdAt ASC"
    )
    fun observeNotesForLabel(labelId: Long): Flow<List<Note>>

    @Query("DELETE FROM note_label_cross_ref WHERE noteId = :noteId")
    suspend fun clearLabelsForNote(noteId: Long)

    @Insert
    suspend fun assignLabels(refs: List<NoteLabelCrossRef>)

    /** Replaces the full set of labels assigned to a note. */
    suspend fun setLabelsForNote(noteId: Long, labelIds: List<Long>) {
        clearLabelsForNote(noteId)
        if (labelIds.isNotEmpty()) {
            assignLabels(labelIds.map { NoteLabelCrossRef(noteId, it) })
        }
    }
}
