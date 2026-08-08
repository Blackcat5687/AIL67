package com.notekeep.local.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.notekeep.local.R
import com.notekeep.local.data.AppDatabase
import com.notekeep.local.data.Label
import com.notekeep.local.data.Note
import com.notekeep.local.databinding.ActivityNoteEditBinding
import com.notekeep.local.databinding.BottomsheetNoteMoreBinding
import com.notekeep.local.databinding.BottomsheetNoteStyleBinding
import com.notekeep.local.databinding.DialogLabelsBinding
import kotlinx.coroutines.launch

class NoteEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditBinding
    private var noteId: Long = -1
    private var currentNote: Note? = null
    private var selectedColor: Int = 0
    private var backgroundImageUri: String? = null
    private var isPinned: Boolean = false
    private var isArchived: Boolean = false

    /** Set while the style bottom sheet is open, so a freshly picked image can refresh its image row. */
    private var onBackgroundImagePicked: (() -> Unit)? = null

    private val tagRegex = Regex("#[\\p{L}0-9_]+")

    // ---- undo / redo history for the title + content text ----
    private val undoStack = ArrayDeque<Pair<String, String>>()
    private val redoStack = ArrayDeque<Pair<String, String>>()
    private var lastCommittedState: Pair<String, String> = "" to ""
    private var isApplyingHistory = false
    /** True as soon as the text differs from lastCommittedState, before the debounce turns it into a real undo step. */
    private var hasPendingEdit = false
    private val historyHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val historyDebounceRunnable = Runnable { commitHistoryCheckpoint() }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // some providers don't support persistable permissions; the uri may still work this session
                }
                // copy into the app's own storage right away, so the background survives even if
                // the picker's content:// permission is later revoked (and so it round-trips
                // correctly through backup/restore).
                val persisted = com.notekeep.local.data.ImageStore.persist(applicationContext, uri)
                backgroundImageUri = persisted ?: uri.toString()
                applyBackgroundPreview()
                onBackgroundImagePicked?.invoke()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1)
        applyBackgroundPreview()
        updateArchivePinIcons()
        attachTagHighlighter(binding.editTitle)
        attachTagHighlighter(binding.editContent)

        binding.buttonSaveClose.setOnClickListener { saveAndFinish() }
        binding.buttonArchive.setOnClickListener {
            isArchived = !isArchived
            updateArchivePinIcons()
        }
        binding.buttonPin.setOnClickListener {
            isPinned = !isPinned
            updateArchivePinIcons()
        }
        binding.buttonNoteStyle.setOnClickListener { openStyleSheet() }
        binding.buttonMoreOptions.setOnClickListener { openMoreOptionsSheet() }
        binding.buttonUndo.setOnClickListener { undo() }
        binding.buttonRedo.setOnClickListener { redo() }

        setupBottomBarFollowsKeyboard()

        // baseline for a brand-new note: nothing typed yet, so undo/redo has nothing to show
        lastCommittedState = currentState()
        updateUndoRedoUi()

        if (noteId != -1L) {
            lifecycleScope.launch {
                val note = AppDatabase.getInstance(applicationContext).noteDao().getById(noteId)
                if (note != null) {
                    currentNote = note
                    selectedColor = note.color
                    backgroundImageUri = note.backgroundImageUri
                    isPinned = note.pinned
                    isArchived = note.archived
                    isApplyingHistory = true
                    binding.editTitle.setText(note.title)
                    binding.editContent.setText(note.content)
                    isApplyingHistory = false
                    // baseline for an opened note: no edit has happened yet, so hide undo/redo
                    undoStack.clear()
                    redoStack.clear()
                    hasPendingEdit = false
                    lastCommittedState = currentState()
                    updateUndoRedoUi()
                    applyBackgroundPreview()
                    updateArchivePinIcons()
                    refreshLabelChips()
                }
            }
        }
    }

    /** Applies the currently selected color / background image to the whole editor screen, live. */
    private fun applyBackgroundPreview() {
        val uri = backgroundImageUri
        if (uri != null) {
            binding.imageEditBackground.visibility = View.VISIBLE
            binding.imageEditScrim.visibility = View.VISIBLE
            try {
                binding.imageEditBackground.setImageURI(Uri.parse(uri))
            } catch (e: Exception) {
                binding.imageEditBackground.visibility = View.GONE
                binding.imageEditScrim.visibility = View.GONE
            }
        } else {
            binding.imageEditBackground.visibility = View.GONE
            binding.imageEditScrim.visibility = View.GONE
        }
        val colorRes = NoteColors.palette.getOrElse(selectedColor) { R.color.note_0 }
        binding.rootFrame.setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }

    /**
     * Makes the bottom actions bar (style / undo-redo / more-options) ride up above the keyboard
     * when it opens, and slide back down to its normal spot when it closes.
     *
     * The screen uses windowSoftInputMode="adjustPan", so the root view itself never resizes or
     * moves - the keyboard just slides on top of it. That's what keeps the background image from
     * shrinking, but it also means bottomActionsBar would otherwise stay pinned at the very bottom
     * of the screen, hidden behind the keyboard. We fix that by listening for IME (keyboard)
     * WindowInsets and translating the bar up by exactly the keyboard's height while it's shown.
     */
    private fun setupBottomBarFollowsKeyboard() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val topBarBasePadding = binding.topBar.paddingTop
        val bottomBarBasePadding = binding.bottomActionsBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            // the app now draws edge-to-edge, so re-apply the space the system bars used to
            // reserve automatically: status bar padding on top, nav bar padding at the bottom.
            binding.topBar.setPadding(
                binding.topBar.paddingLeft, topBarBasePadding + systemBars.top,
                binding.topBar.paddingRight, binding.topBar.paddingBottom
            )
            binding.bottomActionsBar.setPadding(
                binding.bottomActionsBar.paddingLeft, binding.bottomActionsBar.paddingTop,
                binding.bottomActionsBar.paddingRight, bottomBarBasePadding + systemBars.bottom
            )
            // only lift by the portion of the keyboard that actually overlaps the bar (subtract
            // the nav bar height already reserved below it via the padding above), and never a
            // negative amount.
            val liftBy = (imeHeight - systemBars.bottom).coerceAtLeast(0)
            binding.bottomActionsBar.translationY = -liftBy.toFloat()
            insets
        }
    }

    /** Swaps the archive/pin icons (and their descriptions) to reflect the current toggle state. */
    private fun updateArchivePinIcons() {
        binding.buttonArchive.setImageResource(if (isArchived) R.drawable.ic_unarchive else R.drawable.ic_archive)
        binding.buttonArchive.contentDescription =
            getString(if (isArchived) R.string.action_unarchive else R.string.action_archive)
        binding.buttonPin.setImageResource(if (isPinned) R.drawable.ic_pin else R.drawable.ic_pin_outline)
        binding.buttonPin.contentDescription =
            getString(if (isPinned) R.string.action_unpin else R.string.action_pin)
    }

    private fun attachTagHighlighter(editText: android.widget.EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                val spans = s.getSpans(0, s.length, ForegroundColorSpan::class.java)
                spans.forEach { s.removeSpan(it) }
                val color = ContextCompat.getColor(this@NoteEditActivity, R.color.tag_highlight)
                tagRegex.findAll(s).forEach { match ->
                    s.setSpan(
                        ForegroundColorSpan(color),
                        match.range.first,
                        match.range.last + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                if (!isApplyingHistory) {
                    // show the undo bar the instant a change happens, don't wait for the debounce
                    if (currentState() != lastCommittedState) {
                        hasPendingEdit = true
                        updateUndoRedoUi()
                    }
                    // coalesce a burst of typing into a single undo step, committed once typing pauses
                    historyHandler.removeCallbacks(historyDebounceRunnable)
                    historyHandler.postDelayed(historyDebounceRunnable, 600)
                }
            }
        })
    }

    // ---- undo / redo ----

    private fun currentState(): Pair<String, String> =
        binding.editTitle.text.toString() to binding.editContent.text.toString()

    private fun applyState(state: Pair<String, String>) {
        isApplyingHistory = true
        binding.editTitle.setText(state.first)
        binding.editContent.setText(state.second)
        binding.editTitle.setSelection(binding.editTitle.text?.length ?: 0)
        binding.editContent.setSelection(binding.editContent.text?.length ?: 0)
        isApplyingHistory = false
        lastCommittedState = state
    }

    private fun commitHistoryCheckpoint() {
        val current = currentState()
        if (current != lastCommittedState) {
            undoStack.addLast(lastCommittedState)
            lastCommittedState = current
            redoStack.clear()
        }
        hasPendingEdit = false
        updateUndoRedoUi()
    }

    private fun undo() {
        historyHandler.removeCallbacks(historyDebounceRunnable)
        commitHistoryCheckpoint() // flush any pending typing burst first, so it isn't silently lost
        if (undoStack.isEmpty()) return
        redoStack.addLast(lastCommittedState)
        val previous = undoStack.removeLast()
        applyState(previous)
        updateUndoRedoUi()
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(lastCommittedState)
        val next = redoStack.removeLast()
        applyState(next)
        updateUndoRedoUi()
    }

    private fun updateUndoRedoUi() {
        // the bar appears the instant there's a pending edit, not only once it's committed to the stack
        val hasHistory = undoStack.isNotEmpty() || redoStack.isNotEmpty() || hasPendingEdit
        binding.undoRedoBar.visibility = if (hasHistory) View.VISIBLE else View.GONE
        val canUndo = undoStack.isNotEmpty() || hasPendingEdit
        binding.buttonUndo.isEnabled = canUndo
        binding.buttonUndo.alpha = if (canUndo) 1f else 0.35f
        binding.buttonRedo.isEnabled = redoStack.isNotEmpty()
        binding.buttonRedo.alpha = if (redoStack.isNotEmpty()) 1f else 0.35f
    }

    // ---- bottom-right button: note style (color / background image) bottom sheet ----

    private fun openStyleSheet() {
        val sb = BottomsheetNoteStyleBinding.inflate(layoutInflater)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(sb.root)

        val density = resources.displayMetrics.density

        fun buildColors() {
            sb.styleColorRow.removeAllViews()
            val size = (34 * density).toInt()
            val margin = (10 * density).toInt()
            NoteColors.palette.forEachIndexed { index, colorRes ->
                val circle = View(this)
                val params = LinearLayout.LayoutParams(size, size)
                params.marginEnd = margin
                circle.layoutParams = params
                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.OVAL
                drawable.setColor(ContextCompat.getColor(this, colorRes))
                if (index == selectedColor) {
                    drawable.setStroke((2 * density).toInt(), ContextCompat.getColor(this, R.color.white))
                }
                circle.background = drawable
                circle.setOnClickListener {
                    selectedColor = index
                    applyBackgroundPreview()
                    buildColors()
                }
                sb.styleColorRow.addView(circle)
            }
        }

        fun buildImages() {
            sb.styleImageRow.removeAllViews()
            val size = (44 * density).toInt()
            val margin = (10 * density).toInt()
            val radius = 8 * density

            val addBtn = ImageView(this)
            addBtn.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
            addBtn.background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(ContextCompat.getColor(this@NoteEditActivity, R.color.surface_dark))
                setStroke((1 * density).toInt(), ContextCompat.getColor(this@NoteEditActivity, R.color.on_surface_dark))
            }
            addBtn.setImageResource(R.drawable.ic_image)
            addBtn.setPadding(size / 4, size / 4, size / 4, size / 4)
            addBtn.scaleType = ImageView.ScaleType.FIT_CENTER
            addBtn.contentDescription = getString(R.string.content_desc_background_image)
            addBtn.setOnClickListener { pickImageLauncher.launch(arrayOf("image/*")) }
            sb.styleImageRow.addView(addBtn)

            if (backgroundImageUri != null) {
                val removeBtn = ImageView(this)
                removeBtn.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
                removeBtn.background = GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(ContextCompat.getColor(this@NoteEditActivity, R.color.surface_dark))
                }
                removeBtn.setImageResource(R.drawable.ic_close)
                removeBtn.setPadding(size / 4, size / 4, size / 4, size / 4)
                removeBtn.scaleType = ImageView.ScaleType.FIT_CENTER
                removeBtn.contentDescription = getString(R.string.content_desc_remove_background_image)
                removeBtn.setOnClickListener {
                    backgroundImageUri = null
                    applyBackgroundPreview()
                    buildImages()
                }
                sb.styleImageRow.addView(removeBtn)
            }

            lifecycleScope.launch {
                val recents = AppDatabase.getInstance(applicationContext).noteDao().recentBackgroundImages(9)
                recents.forEach { uriString ->
                    val thumb = ImageView(this@NoteEditActivity)
                    val params = LinearLayout.LayoutParams(size, size)
                    params.marginEnd = margin
                    thumb.layoutParams = params
                    thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                    thumb.background = GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(ContextCompat.getColor(this@NoteEditActivity, R.color.surface_dark))
                        if (uriString == backgroundImageUri) {
                            setStroke((2 * density).toInt(), ContextCompat.getColor(this@NoteEditActivity, R.color.white))
                        }
                    }
                    thumb.clipToOutline = true
                    try {
                        thumb.setImageURI(Uri.parse(uriString))
                    } catch (e: Exception) {
                        return@forEach
                    }
                    thumb.setOnClickListener {
                        backgroundImageUri = uriString
                        applyBackgroundPreview()
                        buildImages()
                    }
                    sb.styleImageRow.addView(thumb)
                }
            }
        }

        buildColors()
        buildImages()
        onBackgroundImagePicked = { buildImages() }
        sheet.setOnDismissListener { onBackgroundImagePicked = null }
        sheet.show()
    }

    // ---- bottom-left button: "more options" bottom sheet (dates, labels, delete) ----

    private fun openMoreOptionsSheet() {
        val sb = BottomsheetNoteMoreBinding.inflate(layoutInflater)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(sb.root)

        val created = currentNote?.createdAt ?: System.currentTimeMillis()
        val updated = currentNote?.updatedAt ?: System.currentTimeMillis()
        sb.textCreatedAt.text = getString(R.string.note_created_label) + ": " + formatTimestamp(created)
        sb.textUpdatedAt.text = getString(R.string.note_updated_label) + ": " + formatTimestamp(updated)

        sb.rowLabels.setOnClickListener {
            sheet.dismiss()
            showLabelsDialog()
        }
        sb.rowDelete.setOnClickListener {
            sheet.dismiss()
            confirmDelete()
        }

        sheet.show()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("d MMMM yyyy، h:mm a", java.util.Locale("ar"))
        return sdf.format(java.util.Date(timestamp))
    }

    override fun onBackPressed() {
        saveAndFinish()
    }

    override fun onDestroy() {
        historyHandler.removeCallbacks(historyDebounceRunnable)
        super.onDestroy()
    }

    private fun saveAndFinish() {
        val title = binding.editTitle.text.toString().trim()
        val content = binding.editContent.text.toString().trim()

        if (title.isEmpty() && content.isEmpty()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).noteDao()
            val existing = currentNote
            if (existing != null) {
                dao.update(
                    existing.copy(
                        title = title,
                        content = content,
                        color = selectedColor,
                        updatedAt = System.currentTimeMillis(),
                        pinned = isPinned,
                        archived = isArchived,
                        backgroundImageUri = backgroundImageUri
                    )
                )
            } else {
                dao.insert(
                    Note(
                        title = title,
                        content = content,
                        color = selectedColor,
                        pinned = isPinned,
                        archived = isArchived,
                        backgroundImageUri = backgroundImageUri
                    )
                )
            }
            finish()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete_confirm_positive) { _, _ -> deleteAndFinish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteAndFinish() {
        val existing = currentNote ?: run { finish(); return }
        lifecycleScope.launch {
            AppDatabase.getInstance(applicationContext).noteDao().moveToTrash(existing.id)
            finish()
        }
    }

    // ---- labels ----

    private fun refreshLabelChips() {
        val id = noteId
        if (id == -1L) return
        lifecycleScope.launch {
            val labels = AppDatabase.getInstance(applicationContext).labelDao().labelsForNote(id)
            binding.labelChipRow.removeAllViews()
            if (labels.isEmpty()) {
                binding.labelsScroll.visibility = View.GONE
                return@launch
            }
            binding.labelsScroll.visibility = View.VISIBLE
            for (label in labels) {
                val chip = layoutInflater.inflate(R.layout.item_label_chip, binding.labelChipRow, false) as android.widget.TextView
                chip.text = label.name
                binding.labelChipRow.addView(chip)
            }
        }
    }

    private fun showLabelsDialog() {
        lifecycleScope.launch {
            if (noteId == -1L) {
                // note not saved yet; insert it now so it has an id to attach labels to
                val dao = AppDatabase.getInstance(applicationContext).noteDao()
                val newId = dao.insert(
                    Note(
                        title = binding.editTitle.text.toString().trim(),
                        content = binding.editContent.text.toString().trim(),
                        color = selectedColor,
                        pinned = isPinned,
                        archived = isArchived,
                        backgroundImageUri = backgroundImageUri
                    )
                )
                noteId = newId
                currentNote = dao.getById(newId)
            }
            openLabelsDialog()
        }
    }

    private fun openLabelsDialog() {
        val dialogBinding = DialogLabelsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        val labelDao = AppDatabase.getInstance(applicationContext).labelDao()

        lateinit var adapter: LabelSelectAdapter

        fun reload() {
            lifecycleScope.launch {
                val all = labelDao.getAllOnce()
                val assigned = labelDao.labelIdsForNote(noteId).toSet()
                dialogBinding.textLabelsEmpty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(all.map { LabelRow(it, assigned.contains(it.id)) })
            }
        }

        adapter = LabelSelectAdapter(
            onToggle = { label, checked ->
                lifecycleScope.launch {
                    val current = labelDao.labelIdsForNote(noteId).toMutableSet()
                    if (checked) current.add(label.id) else current.remove(label.id)
                    labelDao.setLabelsForNote(noteId, current.toList())
                    refreshLabelChips()
                }
            },
            onDelete = { label ->
                lifecycleScope.launch {
                    labelDao.clearAssignmentsForLabel(label.id)
                    labelDao.delete(label)
                    reload()
                    refreshLabelChips()
                }
            }
        )
        dialogBinding.recyclerLabels.adapter = adapter
        dialogBinding.recyclerLabels.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        dialogBinding.buttonCreateLabel.setOnClickListener {
            val name = dialogBinding.editNewLabel.text.toString().trim()
            if (name.isNotEmpty()) {
                lifecycleScope.launch {
                    labelDao.insert(Label(name = name))
                    dialogBinding.editNewLabel.setText("")
                    reload()
                }
            }
        }
        dialogBinding.buttonLabelsDone.setOnClickListener { dialog.dismiss() }

        reload()
        dialog.show()
    }

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }
}
