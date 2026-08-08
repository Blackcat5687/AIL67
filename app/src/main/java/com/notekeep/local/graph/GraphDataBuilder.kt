package com.notekeep.local.graph

import com.notekeep.local.data.Label
import com.notekeep.local.data.Note
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the JSON payload sent to the HTML/JS graph (window.NotesLinkGraph.loadData) from real
 * app data. This is the single source of truth for turning notes into graph nodes/edges — the
 * HTML/JS side only renders and simulates what it's given here, per the "Kotlin owns data,
 * JavaScript owns rendering" split.
 *
 * Node id = the note's real database id (as a string), never its title — titles can change or
 * collide, but the id is stable, matching the requirement that node identity must not depend on
 * a mutable display name.
 *
 * A note's #tags and its assigned Labels (categories/groups) both travel in `tags[]`, since the
 * HTML's own filter/group-color query language only exposes a single `tag:` predicate that
 * matches anything in that array — it has no separate concept for "label" vs "hashtag". A note's
 * first label name (if any) is also sent as `path`, so a `path:` rule can target notes by
 * category the same way the query language's own hint text describes.
 */
object GraphDataBuilder {

    /**
     * @param positions previously-saved node positions (note id -> x/y), forwarded as-is so the
     * JS side can restore them for notes it already knows and only randomize brand-new ones.
     */
    fun buildPayload(
        notes: List<Note>,
        labels: List<Label>,
        noteLabelPairs: List<Pair<Long, Long>>,
        positions: Map<String, Pair<Float, Float>> = emptyMap()
    ): String {
        val labelById = labels.associateBy { it.id }
        val labelIdsByNote = noteLabelPairs.groupBy({ it.first }, { it.second })

        // index real notes by resolvable title so [[wiki-links]] can find their target, exactly
        // like the previous native GraphModel resolved links (case-insensitive, trimmed).
        val noteIdByTitle = HashMap<String, Long>()
        for (note in notes) {
            val key = note.title.trim().lowercase()
            if (key.isNotEmpty()) noteIdByTitle[key] = note.id
        }

        val nodesJson = JSONArray()
        val edgesJson = JSONArray()
        val seenEdgeKeys = HashSet<String>()
        val ghostIds = HashMap<String, String>() // lowercase title -> synthetic ghost id

        fun addEdgeOnce(sourceId: String, targetId: String) {
            val key = if (sourceId < targetId) "$sourceId|$targetId" else "$targetId|$sourceId"
            if (!seenEdgeKeys.add(key)) return
            edgesJson.put(
                JSONObject().apply {
                    put("source", sourceId)
                    put("target", targetId)
                    // Undirected in this app's data model today (wiki-links, tags, and labels
                    // are all symmetric relations) - "none" tells the HTML not to draw an arrow,
                    // matching spec section 5's rule to only show direction when the underlying
                    // relationship actually has one.
                    put("direction", "none")
                }
            )
        }

        for (note in notes) {
            val tags = note.extractTags().map { it.removePrefix("#") }
            val noteLabelNames = labelIdsByNote[note.id].orEmpty().mapNotNull { labelById[it]?.name }
            val allTags = JSONArray().apply {
                tags.forEach { put(it) }
                noteLabelNames.forEach { put(it) }
            }

            nodesJson.put(
                JSONObject().apply {
                    put("id", note.id.toString())
                    put("title", note.title.ifBlank { note.content.take(18).ifBlank { "بدون عنوان" } })
                    put("tags", allTags)
                    put("path", noteLabelNames.firstOrNull() ?: "")
                    put("file", "")
                    put("properties", JSONObject())
                    put("noteId", note.id)
                    put("isGhost", false)
                }
            )
        }

        for (note in notes) {
            val thisId = note.id.toString()
            for (linkTitle in note.extractWikiLinks()) {
                val key = linkTitle.trim().lowercase()
                val targetId = noteIdByTitle[key]
                if (targetId != null) {
                    if (targetId == note.id) continue // ignore self-links
                    addEdgeOnce(thisId, targetId.toString())
                } else {
                    // Unresolved [[wiki-link]] becomes a ghost node, same as the previous native
                    // renderer did, so links pointing at not-yet-created notes stay visible.
                    val ghostId = ghostIds.getOrPut(key) { "ghost_$key" }
                    addEdgeOnce(thisId, ghostId)
                }
            }
        }

        for ((key, ghostId) in ghostIds) {
            nodesJson.put(
                JSONObject().apply {
                    put("id", ghostId)
                    put("title", key)
                    put("tags", JSONArray())
                    put("path", "")
                    put("file", "")
                    put("properties", JSONObject())
                    put("noteId", JSONObject.NULL)
                    put("isGhost", true)
                }
            )
        }

        val positionsJson = JSONObject()
        for ((id, xy) in positions) {
            positionsJson.put(id, JSONObject().apply { put("x", xy.first); put("y", xy.second) })
        }

        val root = JSONObject()
        root.put("nodes", nodesJson)
        root.put("edges", edgesJson)
        root.put("positions", positionsJson)
        return root.toString()
    }
}
