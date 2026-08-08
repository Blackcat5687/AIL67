package com.notekeep.local.graph

import android.webkit.JavascriptInterface
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch

/**
 * The Kotlin side of the WebView JavaScript bridge (spec section 3). Every method here is called
 * FROM JavaScript, always on a background WebView thread — never assume this runs on the main
 * thread, which is why every callback is dispatched through [scope]. Kotlin only reacts to what
 * JS reports; it never reaches into the page's DOM or JS state directly, keeping the two sides'
 * responsibilities separate as specified.
 *
 * @param scope a lifecycle-aware coroutine scope (the hosting Activity's lifecycleScope) so any
 * work this triggers is automatically cancelled if the screen is destroyed mid-callback.
 * @param onReady invoked once the page has finished its own setup and is ready to receive
 * loadData/applySettings calls.
 * @param onNoteTapped invoked with the tapped note's real database id, already parsed — never
 * invoked for ghost nodes (JS itself filters those out before calling).
 * @param onStateChanged invoked with the full graph state JSON (settings/filterState/
 * groupRules/positions/view) whenever JS reports a change worth persisting durably.
 */
class GraphBridge(
    private val scope: LifecycleCoroutineScope,
    private val onReady: () -> Unit,
    private val onNoteTapped: (Long) -> Unit,
    private val onStateChanged: (String) -> Unit
) {

    @JavascriptInterface
    fun onGraphReady(unused: String) {
        scope.launch { onReady() }
    }

    @JavascriptInterface
    fun onNoteTapped(noteIdString: String) {
        val noteId = noteIdString.toLongOrNull() ?: return
        scope.launch { onNoteTapped(noteId) }
    }

    @JavascriptInterface
    fun onGraphStateChanged(stateJson: String) {
        scope.launch { onStateChanged(stateJson) }
    }
}
