package com.notekeep.local.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.notekeep.local.databinding.ActivitySettingsBinding
import com.notekeep.local.graph.GraphActivity

/** The app's main side menu (opened from the home screen's hamburger icon). */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowGraph.setOnClickListener {
            startActivity(Intent(this, GraphActivity::class.java))
        }

        binding.rowLabels.setOnClickListener {
            startActivity(Intent(this, LabelsOverviewActivity::class.java))
        }

        binding.rowArchive.setOnClickListener {
            startActivity(Intent(this, ArchiveActivity::class.java))
        }

        binding.rowAppSettings.setOnClickListener {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }
    }
}
