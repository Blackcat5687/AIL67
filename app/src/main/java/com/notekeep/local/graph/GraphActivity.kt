package com.notekeep.local.graph

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.notekeep.local.data.AppDatabase
import com.notekeep.local.databinding.ActivityGraphBinding
import com.notekeep.local.ui.NoteEditActivity
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Hosts the HTML/JS force-directed graph (assets/graph/graph.html) inside a WebView. Per the
 * integration spec: Kotlin owns notes/links/tags/groups/settings persistence and opening notes;
 * the HTML/JS page owns rendering, physics, and all in-graph interaction (drag/zoom/pan/filter/
 * group colors/settings UI). This Activity's only jobs are: load real data in, receive taps and
 * state-change reports out, and persist that state durably (see GraphSettingsStore).
 *
 * Local Graph mode (the old long-press-to-focus feature) does not exist in the new HTML graph
 * and has been intentionally dropped rather than ported, per an explicit decision to rely only
 * on what the HTML provides instead of extending its behavior.
 */
class GraphActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGraphBinding
    private lateinit var bridge: GraphBridge

    /** True once the page has confirmed it's ready (onGraphReady) - loadData/applySettings calls
     * made before this point would silently hit a page that hasn't finished setting up yet. */
    private var pageReady = false

    /** True right after onCreate's own initial load, so the very next onResume (which Android
     * always fires immediately after onCreate on a fresh launch) doesn't reload data a
     * split-second after it was already sent — matching the same guard the previous native
     * implementation used, for the same reason (avoids a redundant, wasteful reload/jolt). */
    private var justCreated = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGraphBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setUpWebView()
        loadGraphData()
    }

    override fun onResume() {
        super.onResume()
        if (justCreated) {
            justCreated = false
        } else {
            // A real return to this screen (e.g. after editing a note) - notes may have changed,
            // so refresh from the DB. The page itself preserves node positions/zoom/pan across
            // this kind of reload (see graph.html's buildGraphFromData), so this doesn't jolt
            // the layout the way a full WebView reload would.
            loadGraphData()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        val webView = binding.graphWebView
        webView.settings.javaScriptEnabled = true
        // Everything the page needs (HTML/CSS/JS) is bundled locally under assets/graph/ - the
        // app has no INTERNET permission and this page must never attempt to reach the network.
        webView.settings.allowFileAccess = true
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        webView.setBackgroundColor(android.graphics.Color.parseColor("#0a0e14"))

        bridge = GraphBridge(
            scope = lifecycleScope,
            onReady = {
                pageReady = true
                sendSavedSettingsToPage()
                sendCurrentDataToPage()
            },
            onNoteTapped = { noteId ->
                val intent = Intent(this, NoteEditActivity::class.java)
                intent.putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId)
                startActivity(intent)
            },
            onStateChanged = { stateJson ->
                GraphSettingsStore.saveStateJson(applicationContext, stateJson)
            }
        )
        webView.addJavascriptInterface(bridge, "Android")
        webView.webViewClient = android.webkit.WebViewClient()
        webView.loadUrl("file:///android_asset/graph/graph.html")
    }

    /** Holds the most recently built data payload so a resume-triggered refresh can compare
     * against what's already on the page rather than always re-sending, once pageReady is true. */
    private var pendingDataJson: String? = null

    private fun loadGraphData() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val notes = db.noteDao().getAllOnce()
            val labels = db.labelDao().getAllOnce()
            val crossRefs = db.labelDao().getAllCrossRefsOnce()
            val noteLabelPairs = crossRefs.map { it.noteId to it.labelId }

            val savedState = GraphSettingsStore.loadStateJson(applicationContext)
            val positions = extractPositions(savedState)

            val dataJson = GraphDataBuilder.buildPayload(notes, labels, noteLabelPairs, positions)
            pendingDataJson = dataJson
            binding.emptyView.visibility =
                if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

            if (pageReady) sendCurrentDataToPage()
            // if the page isn't ready yet, onReady's callback will pick up pendingDataJson
        }
    }

    private fun extractPositions(savedState: JSONObject?): Map<String, Pair<Float, Float>> {
        val positionsObj = savedState?.optJSONObject("positions") ?: return emptyMap()
        val result = HashMap<String, Pair<Float, Float>>()
        val keys = positionsObj.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val point = positionsObj.optJSONObject(id) ?: continue
            val x = point.optDouble("x", Double.NaN)
            val y = point.optDouble("y", Double.NaN)
            if (!x.isNaN() && !y.isNaN()) result[id] = x.toFloat() to y.toFloat()
        }
        return result
    }

    private fun sendCurrentDataToPage() {
        val json = pendingDataJson ?: return
        callJs("window.NotesLinkGraph && window.NotesLinkGraph.loadData(${JSONObject.quote(json)})")
    }

    private fun sendSavedSettingsToPage() {
        val saved = GraphSettingsStore.loadStateJson(applicationContext) ?: return
        val json = saved.toString()
        callJs("window.NotesLinkGraph && window.NotesLinkGraph.applySettings(${JSONObject.quote(json)})")
    }

    private fun callJs(script: String) {
        binding.graphWebView.evaluateJavascript(script, null)
    }

    override fun onDestroy() {
        // Stop the physics/render loop and release the WebView's internal state promptly instead
        // of leaving a requestAnimationFrame loop running in a detached WebView.
        binding.graphWebView.apply {
            loadUrl("about:blank")
            clearHistory()
            (parent as? android.view.ViewGroup)?.removeView(this)
            destroy()
        }
        super.onDestroy()
    }
}
