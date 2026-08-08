package com.notekeep.local.graph

import android.content.Context
import org.json.JSONObject

/**
 * Persists everything the HTML/JS graph view needs to restore its exact previous state: display
 * settings, force settings, filter state, group color rules, node positions, and the last
 * zoom/pan. Kotlin is the durable source of truth (spec section 19) — JS reports its full state
 * here on every meaningful change (see GraphBridge.onGraphStateChanged), and this store just
 * keeps the latest snapshot as one JSON blob rather than re-modeling every individual field as a
 * typed Kotlin property, since JS itself already owns and defines that shape.
 *
 * Backward compatible with the older native-graph settings (schema version 1, flat
 * center/repel/link-strength keys): those are simply ignored on load if present, since the new
 * HTML graph has an entirely different settings vocabulary and its own built-in defaults.
 */
object GraphSettingsStore {
    private const val PREFS_NAME = "graph_settings"
    private const val KEY_STATE_JSON = "graph_state_json"
    private const val KEY_SCHEMA_VERSION = "graph_schema_version"

    /** Current schema: the full JS-reported state blob {settings, filterState, groupRules,
     * positions, view}. Bump this if the JS-side state shape ever changes incompatibly. */
    const val CURRENT_SCHEMA_VERSION = 2

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The last full state JSON reported by the HTML graph, or null if the graph has never been
     * opened yet (or only ever showed an older, incompatible schema) — callers should treat null
     * as "use the HTML's own built-in defaults" rather than trying to fabricate one. */
    fun loadStateJson(context: Context): JSONObject? {
        val p = prefs(context)
        if (p.getInt(KEY_SCHEMA_VERSION, 0) != CURRENT_SCHEMA_VERSION) return null
        val raw = p.getString(KEY_STATE_JSON, null) ?: return null
        return try { JSONObject(raw) } catch (e: Exception) { null }
    }

    /** Saves the full state blob reported by JS in one write. Called after every debounced
     * onGraphStateChanged callback from the bridge, so nothing needs an explicit "save" step and
     * nothing is lost if the user leaves the graph screen without any special action. */
    fun saveStateJson(context: Context, stateJson: String) {
        prefs(context).edit()
            .putString(KEY_STATE_JSON, stateJson)
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .apply()
    }

    /** Serializes the current state for embedding in a backup file (BackupManager). Returns an
     * explicit null marker when nothing has been saved yet, so restoring on a fresh
     * install/device doesn't write a bogus empty state over the HTML's own defaults. */
    fun toJson(context: Context): JSONObject {
        val state = loadStateJson(context)
        return JSONObject().apply {
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("state", state ?: JSONObject.NULL)
        }
    }

    /** Restores state from a backup file's embedded JSON, if present and of a schema this build
     * understands. Older backups (pre-HTML-graph, or a future/foreign schema version) simply
     * leave whatever is already saved on this device untouched — restore never errors out over
     * graph data, per spec sections 23-24. */
    fun fromJson(context: Context, obj: JSONObject?) {
        if (obj == null) return
        val schemaVersion = obj.optInt("schemaVersion", -1)
        if (schemaVersion != CURRENT_SCHEMA_VERSION) return
        val state = obj.optJSONObject("state") ?: return
        saveStateJson(context, state.toString())
    }
}
