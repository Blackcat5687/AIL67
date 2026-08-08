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

class ArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveBinding
    private lateinit var adapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = NoteAdapter(
            onClick = { note ->
                val intent = Intent(this, NoteEditActivity::class.java)
                intent.putExtra(NoteEditActivity.EXTRA_NOTE_ID, note.id)
                startActivity(intent)
            },
            onTogglePin = { }
        )
        binding.recyclerNotes.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.recyclerNotes.adapter = adapter

        val dao = AppDatabase.getInstance(applicationContext).noteDao()
        lifecycleScope.launch {
            dao.observeArchived().collectLatest { notes ->
                adapter.submitList(notes.map { NoteListItem.Item(it) })
                binding.emptyView.visibility =
                    if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }
}
