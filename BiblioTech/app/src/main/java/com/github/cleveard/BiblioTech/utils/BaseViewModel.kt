package com.github.cleveard.BiblioTech.utils

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.cleveard.BiblioTech.R
import com.github.cleveard.BiblioTech.db.BookAndAuthors
import kotlinx.coroutines.CoroutineScope

/**
 * Interface used by adapters to access the app
 */
internal interface ParentAccess {
    /**
     * Toggle the selection for and id
     * @param id The id to toggle
     */
    fun toggleSelection(id: Long, position: Int)

    /**
     * Toggle the open/close state of the boook
     */
    fun toggleOpen(id: Long) {}

    /**
     * Return whether the id is open
     * @param id The id to check
     * @return True if the id is open
     */
    fun isOpen(id: Long): Boolean {
        return false
    }

    /**
     * Context from the parent
     */
    val context: Context

    /**
     * The CoroutineScope used to launch tasks
     */
    val scope: CoroutineScope

    /**
     * Get a thumbnail for a book
     * @param bookId The id of the book
     * @param large True to get the large thumbnail. False to get the small
     */
    suspend fun getThumbnail(bookId: Long, large: Boolean): Bitmap?

    /**
     * Remove a tag from a book by name
     */
    suspend fun removeTag(ctx: Context, book: BookAndAuthors, tagName: String): Boolean {
        return false
    }

    /**
     * Add a tag to a book by name
     */
    suspend fun addTag(ctx: Context, book: BookAndAuthors, tagName: String): Boolean {
        return false
    }
}

/**
 * Base view model for fragments that need a context
 */
abstract class BaseViewModel(app: Application) : AndroidViewModel(app), ParentAccess {
    /**
     * Handler for selection based on database ids
     * This class keeps a list of ids and a flag to indicate
     * whether the ids are the selected ids or the unselected ids
     */
    class SelectionSet {
        private var _inverted: Boolean = false

        /**
         * Lister for selection changed
         */
        var onSelectionChanged: HashSet<() -> Unit> = HashSet()

        /**
         * Flag to indicate whether ids are selected (false) or unselected (true)
         */
        val inverted: Boolean
            get() { return _inverted }
        private var _selection: HashSet<Long> = HashSet()

        /**
         * The ids
         */
        val selection: Array<Any>
            get() { return _selection.toArray() }
        private val _hasSelection = object: MutableLiveData<Boolean>(false) {
            override fun setValue(value: Boolean?) {
                // Only set if the value changes
                if (value != this.value)
                    super.setValue(value)
            }
        }

        /**
         * Live data that gets changed when selection
         * goes between nothing selected and something selected
         * This is only an approximation if the ids are the unselected
         * ids, since we don't keep track of all of the ids
         */
        val hasSelection: LiveData<Boolean>
            get() { return _hasSelection }

        private var _lastSelection: MutableLiveData<Long> = object: MutableLiveData<Long>(null) {
            override fun setValue(value: Long?) {
                // Only set if the value changes
                if (value != this.value)
                    super.setValue(value)
            }
        }

        /**
         * The last id that was last selected
         */
        val lastSelection: LiveData<Long>
            get() { return _lastSelection }

        /**
         * Clear the last selected id
         */
        fun clearLastSelection() {
            _lastSelection.value = null
        }

        /**
         * Set the something vs nothing selected state if it has changed.
         */
        private fun hasSelectChanged() {
            // Get current state
            val value = _inverted || _selection.size > 0
            // Set value
            _hasSelection.value = value
            // Invalidate the UI. This is here because this
            // method is called on all selection changes
            for (c in onSelectionChanged)
                c()
        }

        /**
         * Select/deselect all ids
         * @param select True to select all ids. False to deselect all ids
         */
        fun selectAll(select: Boolean) {
            // Clear the ids
            _selection.clear()
            // Set inverted based on whether we are selecting or deselecting
            _inverted = select
            // Clear the last selected value
            _lastSelection.value = null
            hasSelectChanged()
        }

        /**
         * Select/deselect a single id
         * @param id The id to select/deselect
         * @param select True to select. False to deselect
         */
        fun select(id: Long, select: Boolean) {
            if (select != _inverted) {
                // When select is different from _inverted,
                // We add to the ids to select/deselect
                if (_selection.contains(id))
                    return
                _selection.add(id)
            } else {
                // If select is the same as _inverted
                // We remove ids to select/deselect
                if (!_selection.contains(id))
                    return
                _selection.remove(id)
            }
            // Set the last select value only if selecting
            _lastSelection.value = if (select) id else null
            hasSelectChanged()
        }

        /**
         * Toggle the selection of an id
         * @param id The id to toggle
         */
        fun toggle(id: Long) {
            // To toggle selection we just need to add the id if it isn't
            // in the set of ids, or remove it if it is. But we also want
            // to know whether the toggle selected the id, so we can set
            // the last selected id.
            val select =
                if (_selection.contains(id)) {
                    _selection.remove(id)
                    // If we remove an id, then that selects it
                    // if _inverted is true
                    _inverted
                } else {
                    _selection.add(id)
                    // If we add an id, then that selects it
                    // if _inverted is false
                    !_inverted
                }
            // Set the last select value only if selecting
            _lastSelection.value = if (select) id else null
            hasSelectChanged()
        }

        /**
         * Invert the ids selected
         */
        fun invert() {
            // Just need to clear the last selected id and invert _inverted
            _lastSelection.value = null
            _inverted = !_inverted
            hasSelectChanged()
        }

        /**
         * Is the id selected
         * @param id The id to check
         */
        fun isSelected(id: Long): Boolean {
            return _selection.contains(id) != _inverted
        }
    }

    /**
     * This is the selection object for the view model
     */
    val selection = SelectionSet()

    /**
     * @inheritDoc
     */
    override fun toggleSelection(id: Long, position: Int) {
        selection.toggle(id)
    }

    /**
     * @inheritDoc
     */
    override val context: Context = app.applicationContext

    /**
     * @inheritDoc
     */
    override val scope: CoroutineScope
        get() {
            return viewModelScope
        }

    /**
     * @inheritDoc
     */
    override suspend fun getThumbnail(bookId: Long, large: Boolean): Bitmap? {
        return null
    }

    companion object {
        /**
         * Utility function to set the icon tint for menu items that we can enable/disable
         * @param context The application context
         * @param menu The menu with the item
         * @param id The id of the menu item
         * @return The menu item with the icon tint set
         * I found that setting tint in the XML didn't work, so I added this method
         */
        fun setupIcon(context: Context?, menu: Menu, id: Int): MenuItem {
            // Get the menu item
            val item = menu.findItem(id)
            // Set the tint
            item?.iconTintList = context?.resources?.getColorStateList(R.color.enable_icon_tint, null)
            item?.iconTintMode = PorterDuff.Mode.MULTIPLY
            return item
        }
    }
}