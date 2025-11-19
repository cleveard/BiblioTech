package com.github.cleveard.bibliotech.utils

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.*
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.BookFilter
import com.github.cleveard.bibliotech.db.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Interface used by adapters to access the app
 */
internal interface ParentAccess {
    /**
     * Toggle the selection for and id
     * @param id The id to toggle
     */
    fun toggleSelection(id: Long, editable: Boolean, position: Int)

    /**
     * Toggle the open/close state of the book
     */
    fun toggleExpanded(id: Long) {}

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
    interface SelectionInterface {
        /**
         * LiveData holding the last item selected
         */
        val lastSelection: LiveData<Long?>

        /**
         * The count of all items
         */
        val itemCount: LiveData<Int>

        /**
         * LiveData holding the selected item count
         */
        val selectedCount: LiveData<Int>

        /**
         * Lister for selection changed
         */
        val onSelectionChanged: MutableSet<() -> Unit>

        /**
         * Return true if there is a selection
         */
        val hasSelection: Boolean
            get() = (selectedCount.value?:0) > 0

        /**
         * Clear the last item selected
         */
        fun clearLastSelection()

        /**
         * Select all items. May return before completion
         * @param select True to select all, False to clear all selections
         */
        fun selectAllAsync(select: Boolean)

        /**
         * Select all items
         * @param select True to select all, False to clear all selections
         */
        suspend fun selectAll(select: Boolean) = selectAllAsync(select)

        /**
         * Select an item. May return before completion
         * @param id The id of the item to select
         * @param editable True if the selected item is editable
         * @param select True to select. False to clear selection
         */
        fun selectAsync(id: Long, editable: Boolean, select: Boolean)

        /**
         * Select an item
         * @param id The id of the item to select
         * @param editable True if the selected item is editable
         * @param select True to select. False to clear selection
         */
        suspend fun select(id: Long, editable: Boolean, select: Boolean) = selectAsync(id, editable, select)

        /**
         * Toggle an item selection. May return before completion
         * @param id The id of the item
         */
        fun toggleAsync(id: Long, editable: Boolean)

        /**
         * Toggle an item selection
         * @param id The id of the item
         */
        suspend fun toggle(id: Long, editable: Boolean) = toggleAsync(id, editable)

        /**
         * Toggle all oll items' selection. May return before completion
         */
        fun invertAsync()

        /**
         * Toggle all oll items' selection
         */
        suspend fun invert() = invertAsync()

        /**
         * Is an item selected
         * @param id The item to test
         * @return True if the item is selected
         */
        suspend fun isSelected(id: Long): Boolean
    }

    /**
     * Class to hold the last selected item
     */
    abstract class LastSelection: SelectionInterface {
        /**
         * Live data where the last selected id is kept
         */
        @Suppress("PropertyName")
        protected var _lastSelection: MutableLiveData<Long?> = MutableLiveData(null)

        /**
         * Lister for selection changed
         */
        override val onSelectionChanged: MutableSet<() -> Unit> = HashSet()

        /**
         * The last id that was last selected. Only notify when changed
         */
        override val lastSelection: LiveData<Long?> = _lastSelection.distinctUntilChanged()

        /**
         * Clear the last selected id
         */
        override fun clearLastSelection() {
            _lastSelection.value = null
        }
    }

    /**
     * Class to manage selection bits in a database
     * @param counter The FiltersAndCounters object used for this selection set
     */
    class DataBaseSelectionSet(val counter: BookRepository.FilteredBookCount): LastSelection() {
        private var _filter = MutableLiveData<BookFilter.BuiltFilter?>(null)
        var filter: BookFilter.BuiltFilter?
            get() = _filter.value
            set(f) { _filter.value = f }

        /** @inheritDoc **/
        override val selectedCount: LiveData<Int>
            get() = counter.selectedCount

        /** @inheritDoc **/
        override val itemCount: LiveData<Int>
            get() = counter.itemCount

        /** @inheritDoc **/
        override fun selectAllAsync(select: Boolean) {
            counter.scope.launch {
                selectAll(select)
            }
        }

        /** @inheritDoc **/
        override suspend fun selectAll(select: Boolean) {
            clearLastSelection()
            counter.flags.changeBits(select, counter.mask, null, filter)
        }

        /** @inheritDoc **/
        override suspend fun select(id: Long, editable: Boolean, select: Boolean) {
            if (select && editable)
                _lastSelection.value = id
            else
                clearLastSelection()
            counter.flags.changeBits(select, counter.mask, id, filter)
        }

        /** @inheritDoc **/
        override fun selectAsync(id: Long, editable: Boolean, select: Boolean) {
            counter.scope.launch {
                select(id, editable, select)
            }
        }

        /** @inheritDoc **/
        override fun toggleAsync(id: Long, editable: Boolean) {
            counter.scope.launch {
                toggle(id, editable)
            }
        }

        /** @inheritDoc **/
        override suspend fun toggle(id: Long, editable: Boolean) {
            counter.flags.changeBits(null, counter.mask, id, filter)
            if (isSelected(id) && editable)
                _lastSelection.value = id
            else
                clearLastSelection()
        }

        /** @inheritDoc **/
        override fun invertAsync() {
            counter.scope.launch {
                invert()
            }
        }

        /** @inheritDoc **/
        override suspend fun invert() {
            clearLastSelection()
            counter.flags.changeBits(null,counter. mask, null, filter)
        }

        /** @inheritDoc **/
        override suspend fun isSelected(id: Long): Boolean {
            return (counter.flags.countBits(counter.mask, counter.mask, true, id, filter)) > 0
        }
    }

    /**
     * This is the selection object for the view model
     */
    abstract val selection: SelectionInterface

    /**
     * @inheritDoc
     */
    override fun toggleSelection(id: Long, editable: Boolean, position: Int) {
        selection.toggleAsync(id, editable)
    }

    /**
     * @inheritDoc
     */
    override val context: Context
        get() = getApplication<Application>().applicationContext

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