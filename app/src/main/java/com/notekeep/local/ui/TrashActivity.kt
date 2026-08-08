package com.notekeep.local.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.notekeep.local.R
import com.notekeep.local.data.AppDatabase
import com.notekeep.local.data.Note
import com.notekeep.local.databinding.ActivityTrashBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private lateinit var adapter: TrashAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val dao = AppDatabase.getInstance(applicationContext).noteDao()

        adapter = TrashAdapter(
            onRestore = { note ->
                lifecycleScope.launch { dao.restoreFromTrash(note.id) }
            },
            onDeleteForever = { note -> confirmDeleteForever(note) }
        )
        binding.recyclerTrash.layoutManager = LinearLayoutManager(this)
        binding.recyclerTrash.adapter = adapter

        lifecycleScope.launch {
            // sweep anything past the 30-day retention window before showing the list
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(TrashAdapter.RETENTION_DAYS)
            dao.purgeTrashOlderThan(cutoff)
        }

        lifecycleScope.launch {
            dao.observeTrash().collectLatest { notes ->
                adapter.submitList(notes)
                binding.emptyView.visibility =
                    if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun confirmDeleteForever(note: Note) {
        AlertDialog.Builder(this)
            .setTitle(R.string.trash_delete_forever_confirm_title)
            .setMessage(R.string.trash_delete_forever_confirm_message)
            .setPositiveButton(R.string.delete_confirm_positive) { _, _ ->
                lifecycleScope.launch {
                    AppDatabase.getInstance(applicationContext).noteDao().deleteById(note.id)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
