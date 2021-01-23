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
import com.github.cleveard.bibliotech.db.BookRepository.FlagsInterface
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
    fun toggleSelection(id: Long, position: Int)

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
        val lastSelection: LiveData<Long>

        /**
         * The count of all items
         */
        val itemCount: LiveData<Int?>

        /**
         * LiveData holding the selected item count
         */
        val selectedCount: LiveData<Int?>

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
         * @param select True to select. False to clear selection
         */
        fun selectAsync(id: Long, select: Boolean)

        /**
         * Select an item
         * @param id The id of the item to select
         * @param select True to select. False to clear selection
         */
        suspend fun select(id: Long, select: Boolean) = selectAsync(id, select)

        /**
         * Toggle an item selection. May return before completion
         * @param id The id of the item
         */
        fun toggleAsync(id: Long)

        /**
         * Toggle an item selection
         * @param id The id of the item
         */
        suspend fun toggle(id: Long) = toggleAsync(id)

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
        protected var _lastSelection: MutableLiveData<Long> = MutableLiveData<Long>(null)

        /**
         * Lister for selection changed
         */
        override val onSelectionChanged: MutableSet<() -> Unit> = HashSet()

        /**
         * The last id that was last selected. Only notify when changed
         */
        override val lastSelection: LiveData<Long> = _lastSelection.distinctUntilChanged()

        /**
         * Clear the last selected id
         */
        override fun clearLastSelection() {
            _lastSelection.value = null
        }
    }

    /**
     * Class to manage selection bits in a database
     * @param flags The flags interface to the table
     * @param mask The mask for the selected bit
     * @param scope A coroutine scope for coroutine operations
     */
    class DataBaseSelectionSet(
        private val flags: FlagsInterface,
        private val mask: Int,
        private val scope: CoroutineScope
    ): LastSelection() {
        var filter: BookFilter.BuiltFilter? = null
            set(f) {
                field = f
                scope.launch {
                    selectedCount as CascadeLiveData<Int?>
                    selectedCount.sourceValue = flags.countBitsLive(
                        mask, mask, true, null, f
                    ).distinctUntilChanged()
                    itemCount as CascadeLiveData<Int?>
                    itemCount.sourceValue = flags.countBitsLive(
                        0, 0, true, null, f
                    ).distinctUntilChanged()
                }
            }

        /** @inheritDoc **/
        override val selectedCount: LiveData<Int?> = CascadeLiveData()

        /** @inheritDoc **/
        override val itemCount: LiveData<Int?> = CascadeLiveData()

        /** @inheritDoc **/
        override fun selectAllAsync(select: Boolean) {
            scope.launch {
                selectAll(select)
            }
        }

        /** @inheritDoc **/
        override suspend fun selectAll(select: Boolean) {
            clearLastSelection()
            flags.changeBits(select, mask, null, filter)
        }

        /** @inheritDoc **/
        override suspend fun select(id: Long, select: Boolean) {
            if (select)
                _lastSelection.value = id
            else
                clearLastSelection()
            flags.changeBits(select, mask, id, filter)
        }

        /** @inheritDoc **/
        override fun selectAsync(id: Long, select: Boolean) {
            scope.launch {
                select(id, select)
            }
        }

        /** @inheritDoc **/
        override fun toggleAsync(id: Long) {
            scope.launch {
                toggle(id)
            }
        }

        /** @inheritDoc **/
        override suspend fun toggle(id: Long) {
            flags.changeBits(null, mask, id, filter)
            if (isSelected(id))
                _lastSelection.value = id
            else
                clearLastSelection()
        }

        /** @inheritDoc **/
        override fun invertAsync() {
            scope.launch {
                invert()
            }
        }

        /** @inheritDoc **/
        override suspend fun invert() {
            clearLastSelection()
            flags.changeBits(null, mask, null, filter)
        }

        /** @inheritDoc **/
        override suspend fun isSelected(id: Long): Boolean {
            return (flags.countBits(mask, mask, true, id, filter)?: 0) > 0
        }
    }

    /**
     * This is the selection object for the view model
     */
    abstract val selection: SelectionInterface

    /**
     * @inheritDoc
     */
    override fun toggleSelection(id: Long, position: Int) {
        selection.toggleAsync(id)
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