package com.notekeep.local.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.notekeep.local.R
import com.notekeep.local.data.AppDatabase
import com.notekeep.local.data.Note
import com.notekeep.local.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: NoteAdapter
    private var allNotes: List<Note> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        adapter = NoteAdapter(
            onClick = { note ->
                val intent = Intent(this, NoteEditActivity::class.java)
                intent.putExtra(NoteEditActivity.EXTRA_NOTE_ID, note.id)
                startActivity(intent)
            },
            onTogglePin = { note -> togglePin(note) }
        )

        binding.recyclerNotes.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.recyclerNotes.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, NoteEditActivity::class.java))
        }

        val dao = AppDatabase.getInstance(applicationContext).noteDao()
        lifecycleScope.launch {
            dao.observeAll().collectLatest { notes ->
                allNotes = notes
                submit(notes)
            }
        }
    }

    private fun togglePin(note: Note) {
        lifecycleScope.launch {
            AppDatabase.getInstance(applicationContext).noteDao().setPinned(note.id, !note.pinned)
        }
    }

    private fun submit(notes: List<Note>) {
        adapter.submitList(buildListItems(notes))
        binding.emptyView.visibility =
            if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun buildListItems(notes: List<Note>): List<NoteListItem> {
        val pinned = notes.filter { it.pinned }
        val others = notes.filter { !it.pinned }
        val items = mutableListOf<NoteListItem>()
        if (pinned.isNotEmpty()) {
            items.add(NoteListItem.Header(getString(R.string.section_pinned)))
            items.addAll(pinned.map { NoteListItem.Item(it) })
            if (others.isNotEmpty()) {
                items.add(NoteListItem.Header(getString(R.string.section_others)))
            }
        }
        items.addAll(others.map { NoteListItem.Item(it) })
        return items
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.hint_search)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filter(newText.orEmpty())
                return true
            }
        })
        return true
    }

    private fun filter(query: String) {
        if (query.isBlank()) {
            submit(allNotes)
            return
        }
        val q = query.trim()
        submit(
            allNotes.filter {
                it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true)
            }
        )
    }
}
