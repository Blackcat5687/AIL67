package com.notekeep.local.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.notekeep.local.data.AppDatabase
import com.notekeep.local.databinding.ActivityLabelsOverviewBinding
import kotlinx.coroutines.launch

/** Shows every label together with how many notes carry it; tapping one opens its notes,
 * shown the same way the archive screen shows archived notes. */
class LabelsOverviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLabelsOverviewBinding
    private lateinit var adapter: LabelOverviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLabelsOverviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = LabelOverviewAdapter(onClick = { label ->
            val intent = Intent(this, LabelNotesActivity::class.java)
            intent.putExtra(LabelNotesActivity.EXTRA_LABEL_ID, label.id)
            intent.putExtra(LabelNotesActivity.EXTRA_LABEL_NAME, label.name)
            startActivity(intent)
        })
        binding.recyclerLabelsOverview.layoutManager = LinearLayoutManager(this)
        binding.recyclerLabelsOverview.adapter = adapter

        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val labels = AppDatabase.getInstance(applicationContext).labelDao().getLabelsWithCounts()
            adapter.submitList(labels)
            binding.emptyView.visibility = if (labels.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
}
