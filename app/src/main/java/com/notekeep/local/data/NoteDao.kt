package com.notekeep.local.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** Active (non-archived, non-deleted) notes: pinned first, then newest-created first (editing never reorders them). */
    @Query("SELECT * FROM notes WHERE archived = 0 AND deletedAt IS NULL ORDER BY pinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE archived = 0 AND deletedAt IS NULL ORDER BY pinned DESC, createdAt DESC")
    suspend fun getAllOnce(): List<Note>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    suspend fun getAllIncludingArchivedOnce(): List<Note>

    @Query("SELECT * FROM notes WHERE archived = 1 AND deletedAt IS NULL ORDER BY pinned DESC, createdAt DESC")
    fun observeArchived(): Flow<List<Note>>

    /** Notes in the trash (soft-deleted), most recently deleted first. */
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Note>>

    @Query("UPDATE notes SET deletedAt = :deletedAt WHERE id = :noteId")
    suspend fun moveToTrash(noteId: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :noteId")
    suspend fun restoreFromTrash(noteId: Long)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Long)

    /** Permanently removes anything that's been sitting in the trash for more than 30 days. */
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun purgeTrashOlderThan(cutoff: Long)

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getById(noteId: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Insert
    suspend fun insertAll(notes: List<Note>): List<Long>

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :noteId")
    suspend fun setPinned(noteId: Long, pinned: Boolean)

    @Query("UPDATE notes SET archived = :archived WHERE id = :noteId")
    suspend fun setArchived(noteId: Long, archived: Boolean)

    /** Distinct background-image URIs used across active notes, most recently used first. */
    @Query(
        "SELECT backgroundImageUri FROM notes " +
            "WHERE backgroundImageUri IS NOT NULL AND deletedAt IS NULL " +
            "GROUP BY backgroundImageUri ORDER BY MAX(updatedAt) DESC LIMIT :limit"
    )
    suspend fun recentBackgroundImages(limit: Int = 9): List<String>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
