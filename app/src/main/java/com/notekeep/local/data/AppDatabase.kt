package com.notekeep.local.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [Note::class, Label::class, NoteLabelCrossRef::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun labelDao(): LabelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN backgroundImageUri TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS labels (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS note_label_cross_ref (noteId INTEGER NOT NULL, labelId INTEGER NOT NULL, PRIMARY KEY(noteId, labelId))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // existing notes didn't track a creation time separately; use updatedAt as a
                // reasonable stand-in so their relative order is preserved going forward.
                db.execSQL("ALTER TABLE notes ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE notes SET createdAt = updatedAt")
                db.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notes_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
        }
    }
}
