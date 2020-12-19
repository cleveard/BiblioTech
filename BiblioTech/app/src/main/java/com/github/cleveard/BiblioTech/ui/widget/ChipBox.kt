package com.github.cleveard.BiblioTech.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
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
@Suppress("MemberVisibilityCanBePrivate")
open class ChipBox: FlexboxLayout {
    /**
     * Delegate for interacting with the ChipBox
     */
    interface Delegate {
        /**
         * Scope to use for coroutine operations
         * Default is MainScope(). You should replace this with your view model scope.
         */
        val scope: CoroutineScope
            get() = MainScope()

        /**
         * Method used to make a chip.
         * Default method just creates the chip
         * @param chipBox The ChipBox making the request
         * @param text The text for the chip
         * @param scope A CoroutineScope used for coroutine operations
         * @return The Chip or null if the text is not valid
         * Default behaviour is to create a Chip and assign the text to it.
         */
        suspend fun onCreateChip(chipBox: ChipBox, text: String, scope: CoroutineScope): Chip? {
            val chip = Chip(chipBox.context)
            chip.text = text
            return chip
        }

        /**
         * Function call after a chip is added to the box
         * @param chipBox The ChipBox making the request
         * @param chip The chip that was added
         * @param scope A CoroutineScope used for coroutine operations
         * Default behavior does nothing. This method is not called from setChips()
         */
        suspend fun onChipAdded(chipBox: ChipBox, chip: View, scope: CoroutineScope) {}

        /**
         * Function call after a chip is removed from the box
         * @param chipBox The ChipBox making the request
         * @param chip The chip that was added
         * @param scope A CoroutineScope used for coroutine operations
         * Default behavior does nothing. This method is not called from setChips()
         */
        suspend fun onChipRemoved(chipBox: ChipBox, chip: View, scope: CoroutineScope) {}

        /**
         * Function call after a chip is removed from the box
         * @param chipBox The ChipBox making the request
         * @param newChip The chip that was added
         * @param oldChip The chip that was replaced
         * @param scope A CoroutineScope used for coroutine operations
         * Default behavior calls onChipRemoved and then onChipAdded
         */
        suspend fun onChipReplaced(chipBox: ChipBox, newChip: View, oldChip: View, scope: CoroutineScope) {
            onChipRemoved(chipBox, oldChip, scope)
            onChipAdded(chipBox, newChip, scope)
        }

        /**
         * Called when the close icon of a chip is clicked
         * @param chipBox The ChipBox making the request
         * @param chip The chip that was clicked
         * @param scope A CoroutineScope used for coroutine operations
         * @return True to execute the default ChipBox behavior. False to ignore the click
         * The default behavior returns true.
         */
        suspend fun onChipCloseClicked(chipBox: ChipBox, chip: View, scope: CoroutineScope): Boolean {
            return true
        }

        /**
         * Called when a chip is clicked
         * @param chipBox The ChipBox making the request
         * @param chip The chip that was clicked
         * @param scope A CoroutineScope used for coroutine operations
         * @return True to execute the default ChipBox behavior. False to ignore the click
         * The default behavior returns true.
         */
        suspend fun onChipClicked(chipBox: ChipBox, chip: View, scope: CoroutineScope): Boolean {
            return true
        }

        /**
         * Called when the box EditText focus changes
         * @param chipBox The ChipBox that owns the EditText
         * @param edit The EditText
         * @param hasFocus: True when the EditText gets focus. False When the EditText loses focus
         * Default behavior does nothing.
         */
        fun onEditorFocusChange(chipBox: ChipBox, edit: View, hasFocus: Boolean) {}

        /**
         * Called when the box EditText generates an action
         * @param chipBox The ChipBox that owns the EditText
         * @param edit The EditText
         * @param actionId The id of the action
         * @param event The event that generated the action
         * @return True if the action was handled. Otherwise, false.
         * Default behavior returns false. This is not called for the actions that create chips
         */
        fun onEditorAction(chipBox: ChipBox, edit: TextView, actionId: Int, event: KeyEvent?): Boolean {
            return false
        }

    }

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
     * Container for the edit text
     */
    private var _editContainer: View? = null
    val editContainer: View?
        get() = _editContainer

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
    protected var uniqueChipText: Boolean = true

    /**
     * True to show the chip close icon
     */
    protected var showChipCloseIcon: Boolean = true

    /**
     * True to show the chip icon
     */
    protected var showChipIcon: Boolean = false

    /**
     * This is the chip we are editing, when we edit a chip
     */
    protected var editingChip: Chip? = null

    /**
     * Default delegate
     */
    var delegate: Delegate = object: Delegate {}

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
     * OnClickListener for chips in the ChipBox
     */
    protected val onChipClickListener: OnClickListener = OnClickListener {
        delegate.scope.launch {
            if (delegate.onChipClicked(this@ChipBox, it, this)) {
                cancelEdit()
                editChip(it as Chip)
            }
        }
    }

    /**
     * OnClickListener for the close icon of chips in the ChipBox
     */
    protected val onChipCloseClickListener: OnClickListener = OnClickListener {
        delegate.scope.launch {
            if (delegate.onChipCloseClicked(this@ChipBox, it, this)) {
                cancelEdit()
                removeView(it)
                delegate.onChipRemoved(this@ChipBox, it, this)
            }
        }
    }

    init {
        delegate.scope.launch {
            if (!this@ChipBox::_textView.isInitialized)
                setupChipBox()
        }
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
     * Listener for text changes on the EditText
     * This listener changes the IME action of the EditText when the contents is not empty
     */
    private var textWatcher: TextWatcher = object: TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable?) {
            setAction(textView)
        }
    }

    /**
     * Apply the XML attributes for the ChipBox
     * @param attrs The attribute set with the attributes
     * @param defStyleAttr The default style
     * @param defStyleRes The default resource
     */
    private fun applyAttributes(attrs: AttributeSet, defStyleAttr: Int = 0, defStyleRes: Int = 0) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.ChipBox, defStyleAttr, defStyleRes)
            .apply {
                try {
                    uniqueChipText = getBoolean(R.styleable.ChipBox_uniqueChipText, uniqueChipText)
                    imeAction = getInt(R.styleable.ChipBox_imeAction, imeAction) and EditorInfo.IME_MASK_ACTION
                    showChipCloseIcon = getBoolean(R.styleable.ChipBox_showChipCloseIcon, showChipCloseIcon)
                    showChipIcon = getBoolean(R.styleable.ChipBox_showChipIcon, showChipIcon)
                } finally {
                    recycle()
                }
            }
    }

    /**
     * Setup and return the edit text
     */
    @SuppressLint("ClickableViewAccessibility")
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
            else
                cancelEdit()
            delegate.onEditorFocusChange(this, view, hasFocus)
        }
        edit.setOnTouchListener {_, e ->
            if (editingChip != null && e.action == MotionEvent.ACTION_DOWN && e.pointerCount == 1) {
                if (edit.compoundDrawables[0]?.bounds?.contains(e.x.toInt(), e.y.toInt()) == true) {
                    cancelEdit()
                    return@setOnTouchListener true
                }
            }
            false
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
            // If the action is the create chip action, and the text is not empty
            // create a chip, otherwise call the client action listener
            if (action == imeAction) {
                if (textView.text.toString().trim { it <= ' ' }.isNotEmpty())
                    return@setOnEditorActionListener onCreateChipAction()
                else if (cancelEdit())
                    return@setOnEditorActionListener true
            }
            delegate.onEditorAction(this, v, action, keyEvent)
        }

        return edit
    }

    /**
     * Call this to simulate the editor action that creates a chip
     */
    fun onCreateChipAction(): Boolean {
        delegate.scope.launch {
            val text = textView.text.toString()
            val trim = text.trim { it <= ' ' }
            if (text != trim) {
                textView.text.clear()
                @Suppress("BlockingMethodInNonBlockingContext")
                textView.text.append(trim)
            }
            editingChip?.let {
                if (it.text == trim || trim.isEmpty()) {
                    // If we are editing a chip and the new text is the same
                    // cancel the edit
                    cancelEdit()
                    return@launch
                }
            }
            createChip(this, trim)?.let {newChip ->
                editingChip?.let {oldChip ->
                    delegate.onChipReplaced(this@ChipBox, newChip, oldChip, this)
                    editingChip = null
                }?: delegate.onChipAdded(this@ChipBox, newChip, this)
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
        if (edit.text.toString().isEmpty() && editingChip == null) {
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

    /**
     * Get the index of a chip with a text value
     * @param text The text
     * @return The index or -1 if it isn't found
     */
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
        chip = chip ?: delegate.onCreateChip(this, trim, scope)
        if (chip != null) {
            chip.isCloseIconVisible = showChipCloseIcon
            chip.isChipIconVisible = showChipIcon
            chip.setOnCloseIconClickListener(onChipCloseClickListener)
            chip.setOnClickListener(onChipClickListener)

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
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_close_clear_cancel, 0, 0, 0)
        // Set the edit text to the chip text
        textView.text.clear()
        textView.text.append(chip.text)
        setAction(textView)
        textView.requestFocus()
        textView.selectAll()
        // Remove the chip
        removeViewAt(index)
        editingChip = chip
        setValueSequence()
        return true
    }

    /**
     * Cancel a current edit
     * @return True if an edit was canceled. False if there wasn't an edit active
     */
    open fun cancelEdit(): Boolean {
        val oldChip = editingChip?: return false
        editingChip = null

        // Put the old chip where the EditText is
        var index = indexOfChild(textView)
        if (index < 0)
            index = childCount
        addView(oldChip, index)
        // Move the EditText to the end
        moveView(textView)
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        // Clear the text
        textView.text.clear()
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
