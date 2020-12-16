package com.github.cleveard.BiblioTech.ui.widget

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.children
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.cleveard.BiblioTech.R
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * ChipBox subclass of FlexBox that contains an edit text and chips
 */
open class ChipBox: FlexboxLayout {
    /**
     * @inheritDoc
     */
    constructor(context: Context) :
        super(context)

    /**
     * @inheritDoc
     */
    constructor(context: Context, attrs: AttributeSet?) :
        super(context, attrs) {
        attrs?.let { applyAttributes(it) }
    }

    /**
     * @inheritDoc
     */
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr) {
        attrs?.let { applyAttributes(it) }
    }

    /**
     * The edit text
     */
    private lateinit var _textView: EditText
    val textView: EditText
        get() {
            if (this::_textView.isInitialized)
                return _textView
            return setupChipBox()
        }

    /**
     * The Main Action assigned to the edit text
     */
    private var mainAction: Int = 0

    /**
     * The action used to trigger a new chip
     */
    private var imeAction: Int = EditorInfo.IME_ACTION_DONE

    /**
     * True if the edit text action is the main action
     */
    private var isMainAction: Boolean = true

    /**
     * True to make the chip box keep text in chips unique
     */
    private var uniqueChipText: Boolean = true

    /**
     * Scope for coroutines
     */
    var coroutineScope: CoroutineScope = MainScope()
    
    /**
     * Lambda used to make a chip.
     * If this is null, a default function creates a Chip
     */
    var onCreateChip: (suspend (scope: CoroutineScope, text: String) -> Chip?)? = null

    /**
     * Lambda called when a chip is created
     */
    var onChipCreated: (suspend (scope: CoroutineScope, chip: Chip) -> Unit)? = null

    init {
        coroutineScope.launch {
            if (!this@ChipBox::_textView.isInitialized)
                setupChipBox()
        }
    }

    /**
     * Get sequence of strings from the chip box
     */
    private val _values: MutableLiveData<Sequence<String>> = MutableLiveData(emptySequence())
    val values: LiveData<Sequence<String>>
        get() = _values

    /**
     * Get the number of values in the chip box
     */    
    val valueCount: Int
        get() {
            var count = 0
            for (v in children) {
                if (v is Chip)
                    ++count
            }
            return count
        }

    /**
     * Set the values sequence
     */
    private fun setValueSequence() {
        _values.value = sequence {
            for (v in this@ChipBox.children) {
                if (v is Chip)
                    yield(v.text.toString())
            }
        }
    }

    /**
     * Action listener for the edit text
     */
    var onEditorActionListener: TextView.OnEditorActionListener? = null

    /**
     * Focus listener for the edit text
     */
    var onEditorFocusListener: OnFocusChangeListener? = null

    private var textWatcher: TextWatcher = object: TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable?) {
            setAction(textView)
        }
    }

    private fun applyAttributes(attrs: AttributeSet, defStyleAttr: Int = 0, defStyleRes: Int = 0) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.ChipBox, defStyleAttr, defStyleRes)
            .apply {
                try {
                    uniqueChipText = getBoolean(R.styleable.ChipBox_uniqueChipText, uniqueChipText)
                    imeAction = getInt(R.styleable.ChipBox_imeAction, imeAction) and EditorInfo.IME_MASK_ACTION
                } finally {
                    recycle()
                }
            }
    }

    /**
     * Setup and return the edit text
     */
    private fun setupChipBox(): EditText {
        // Find an edit text in the flex box if there is one
        // Pick the last one
        var edit: EditText? = null
        for (v in children) {
            if (v is EditText)
                edit = v
        }
        // Create an edit text if we didn't find one
        edit = edit ?: EditText(context)
        _textView = edit
        // Set a focus listener to change the soft keyboard action
        edit.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus)
                setAction(view as EditText)
            onEditorFocusListener?.onFocusChange(view, hasFocus)
        }
        // Don't allow the edit text to use InputType null
        if (edit.inputType == InputType.TYPE_NULL)
            edit.inputType = InputType.TYPE_CLASS_TEXT
        // Move the edit text to the end of the chip box
        moveView(edit)
        edit.addTextChangedListener(textWatcher)
        // Set the Editable for the EditText
        edit.text = SpannableStringBuilder()
        // Set the action for the edit text
        setAction(edit)
        // Set the action listener for the edit text
        edit.setOnEditorActionListener { v, action, keyEvent ->
            // If the action is done, then that means create a chip
            // Otherwise call the client action listener
            if (action == imeAction)
                onCreateChipAction()
            else
                onEditorActionListener?.onEditorAction(v, action, keyEvent) == true
        }

        return edit
    }

    /**
     * Call this to simulate the editor action that creates a chip
     */
    fun onCreateChipAction(): Boolean {
        coroutineScope.launch {
            val text = textView.text.toString()
            val trim = text.trim { it <= ' ' }
            if (text != trim) {
                textView.text.clear()
                @Suppress("BlockingMethodInNonBlockingContext")
                textView.text.append(trim)
            }
            createChip(this, trim)?.let {
                onChipCreated?.invoke(this, it)
            }
        }
        return true
    }
    /**
     * Set the action of an edit text
     * @param edit The edit text that is changed
     * This sets the action to imeAction and saves the
     * previous action. When the imeAction is handled
     * the action is reset to the saved action
     */
    private fun setAction(edit: EditText) {
        // Set the action for the edit text. If the text
        val options = edit.imeOptions
        var newOptions = options
        if (isMainAction)
            mainAction = newOptions and EditorInfo.IME_MASK_ACTION
        newOptions = newOptions and EditorInfo.IME_MASK_ACTION.inv()
        if (edit.text.toString().isEmpty()) {
            newOptions += mainAction
            isMainAction = true
        } else {
            newOptions += imeAction
            isMainAction = false
        }

        if (options != newOptions) {
            val inputType = edit.inputType
            edit.inputType = InputType.TYPE_NULL
            edit.imeOptions = newOptions
            edit.inputType = inputType
        }
    }

    /**
     * Move a view to a new position
     * @param view The View. If view is not a child of the chip box
     *             it is add at the new position
     * @param inPos The new position. If inPos is < 0 or > childCount, the
     *              view is moved/added to the end position
     */
    protected open fun moveView(view: View, inPos: Int = -1) {
        // Get the current position
        val pos = indexOfChild(view)
        // Get the new position
        var newPos = if (inPos in 0..childCount) inPos else childCount
        // If the positions are different or the view is not a child the move/add it
        if (pos != newPos || pos < 0) {
            // If the view is a child, remove it
            if (pos >= 0) {
                removeViewAt(pos)
                // Adjust the new position if needed
                if (pos < newPos)
                    --newPos
            }
            // Add it at the new position
            addView(view, newPos)
        }
    }

    private fun chipIndexWithText(text: CharSequence): Int {
        for (i in 0 until childCount) {
            val v = getChildAt(i) as? Chip
            if (v != null) {
                if (v.text == text)
                    return i
            }
        }
        return -1
    }

    /**
     * Create a chip
     * @param scope CoroutineScope to use
     * @param text The text for the chip
     */
    protected open suspend fun createChip(scope: CoroutineScope, text: String?, isBatched: Boolean = false): Chip? {
        // Don't create empty chips
        val trim = text?.trim { it <= ' ' }
        if (trim.isNullOrEmpty())
            return null
        var chip: Chip? = null
        // If we want unique chip text, then make sure this text isn't already there
        if (uniqueChipText) {
            val index = chipIndexWithText(trim)
            if (index >= 0) {
                // Chip text already exits, move it
                // to the position where it edit text is
                chip = getChildAt(index) as Chip
                removeView(chip)
            }
        }

        // Create the chip if we need to
        chip = chip ?: onCreateChip?.invoke(scope, trim)
        if (chip != null) {
            // Add the chip where the edit text is
            var index = indexOfChild(textView)
            if (index < 0)
                index = childCount
            // Last check that the chip index is unique
            // because onCreateChip can change the text string
            if (uniqueChipText) {
                val i = chipIndexWithText(chip.text)
                if (i >= 0) {
                    // Not unique, remove existing one and use the new one
                    index = i
                    removeViewAt(i)
                }
            }
            // Add the chip
            addView(chip, index)
            // Move the edit text back to the end of the chip box
            // and clear its text
            moveView(textView)
            textView.text.clear()
            textView.requestFocus()
            if (!isBatched)
                setValueSequence()
            // We processed the chip
            return chip
        }
        // Didn't do anything
        return null
    }

    /**
     * Edit a chip
     * @param chip The Chip to edit
     * This method moves the edit text to the position of the chip,
     * sets the text in the edit text to the chip text, and removes the chip
     */
    open fun editChip(chip: Chip): Boolean {
        // Get the position of the chip and stop if it isn't a child
        val index = indexOfChild(chip)
        if (index < 0)
            return false

        // Move the edit text after the chip
        moveView(textView, index + 1)
        // Set the edit text to the chip text
        textView.text.clear()
        textView.text.append(chip.text)
        setAction(textView)
        textView.requestFocus()
        textView.selectAll()
        // Remove the chip
        removeViewAt(index)
        setValueSequence()
        return true
    }

    /**
     * Set the chips from a sequence of string
     * @param text The sequence of strings
     * If chips are not set to be unique, then they are just added to the end
     * If chips are set to be unique:
     * Chips that have the same text as strings in the sequence are left alone
     * Chips that have text not in the sequence are removed
     * Strings in sequence that don't already have a chip are added at the end
     */
    open fun setChips(scope: CoroutineScope, text: Sequence<String>?) {
        // If the chip text is unique, filter the existing text out of the sequence
        val seq = if (uniqueChipText) {
            // Keep a set of the names in the sequence
            val names = HashSet<String>()
            if (text != null) {
                for (n in text)
                    names.add(n)
            }
            // Remove all chips in the chip box whose text isn't
            // in the sequence and remove exiting names from the set.
            for (i in childCount - 1 downTo 0) {
                (getChildAt(i) as? Chip)?.let { v ->
                    if (names.contains(v.text))
                        names.remove(v.text)
                    else
                        removeView(v)
                }
            }
            // Setup the filter to only add names in the set
            text?.filter { names.contains(it) }
        } else {
            // Not unique, use the list as is
            text
        }

        // Add any names in the sequence
        if (seq != null) {
            scope.launch {
                for (n in seq)
                    createChip(this, n, true)
            }
        }
        setValueSequence()
    }
}
