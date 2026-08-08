package com.notekeep.local.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.notekeep.local.data.AppDatabase
import com.notekeep.local.databinding.ActivityArchiveBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Shows the notes under a single label, using the exact same grid/empty-state presentation as
 * the archived-notes screen. */
class LabelNotesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveBinding
    private lateinit var adapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = intent.getStringExtra(EXTRA_LABEL_NAME).orEmpty()

        val labelId = intent.getLongExtra(EXTRA_LABEL_ID, -1L)

        adapter = NoteAdapter(
            onClick = { note ->
                val editIntent = Intent(this, NoteEditActivity::class.java)
                editIntent.putExtra(NoteEditActivity.EXTRA_NOTE_ID, note.id)
                startActivity(editIntent)
            },
            onTogglePin = { }
        )
        binding.recyclerNotes.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.recyclerNotes.adapter = adapter

        val labelDao = AppDatabase.getInstance(applicationContext).labelDao()
        lifecycleScope.launch {
            labelDao.observeNotesForLabel(labelId).collectLatest { notes ->
                adapter.submitList(notes.map { NoteListItem.Item(it) })
                binding.emptyView.visibility =
                    if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    companion object {
        const val EXTRA_LABEL_ID = "extra_label_id"
        const val EXTRA_LABEL_NAME = "extra_label_name"
    }
}
