package com.notekeep.local.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.notekeep.local.R
import com.notekeep.local.data.AppDatabase
import com.notekeep.local.data.BackupManager
import com.notekeep.local.data.Label
import com.notekeep.local.databinding.ActivityAppSettingsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The app's data-management settings: trash, backup, and restore. */
class AppSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSettingsBinding

    private val createBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) writeBackup(uri)
        }

    private val openBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) confirmRestore(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowTrash.setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }

        binding.rowBackup.setOnClickListener {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            createBackupLauncher.launch("notes_backup_$stamp.json")
        }

        binding.rowRestore.setOnClickListener {
            openBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }

    private fun writeBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val notes = db.noteDao().getAllIncludingArchivedOnce()
                val labels = db.labelDao().getAllOnce().associateBy { it.id }
                val crossRefs = db.labelDao().getAllCrossRefsOnce()
                val labelsByNoteId = crossRefs.groupBy({ it.noteId }, { it.labelId })
                    .mapValues { (_, ids) -> ids.mapNotNull { labels[it]?.name } }
                val json = BackupManager.toJson(applicationContext, notes, labelsByNoteId)
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this@AppSettingsActivity, R.string.backup_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@AppSettingsActivity, R.string.backup_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmRestore(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_choice_title)
            .setPositiveButton(R.string.restore_merge) { _, _ -> performRestore(uri, replace = false) }
            .setNegativeButton(R.string.restore_replace) { _, _ -> performRestore(uri, replace = true) }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun performRestore(uri: Uri, replace: Boolean) {
        lifecycleScope.launch {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("empty")
                val notes = BackupManager.fromJson(applicationContext, text)
                val labelsPerNote = BackupManager.labelsPerNote(text)
                val db = AppDatabase.getInstance(applicationContext)
                val noteDao = db.noteDao()
                val labelDao = db.labelDao()
                if (replace) noteDao.deleteAll()

                val newIds = noteDao.insertAll(notes)

                // any private background files that belonged to notes wiped out by a "replace"
                // restore are now orphaned; clean them up so they don't pile up forever
                if (replace) {
                    val stillInUse = noteDao.getAllIncludingArchivedOnce().mapNotNull { it.backgroundImageUri }
                    com.notekeep.local.data.ImageStore.pruneUnused(applicationContext, stillInUse)
                }

                val nameToId = labelDao.getAllOnce().associate { it.name to it.id }.toMutableMap()
                newIds.forEachIndexed { index, noteId ->
                    val names = labelsPerNote.getOrNull(index).orEmpty()
                    if (names.isEmpty()) return@forEachIndexed
                    val ids = names.map { name ->
                        nameToId[name] ?: labelDao.insert(Label(name = name)).also { nameToId[name] = it }
                    }
                    labelDao.setLabelsForNote(noteId, ids)
                }

                Toast.makeText(this@AppSettingsActivity, R.string.restore_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@AppSettingsActivity, R.string.restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
