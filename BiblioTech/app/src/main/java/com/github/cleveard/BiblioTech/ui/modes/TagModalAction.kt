package com.github.cleveard.BiblioTech.ui.modes

import android.content.Context
import androidx.appcompat.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.github.cleveard.BiblioTech.MainActivity
import com.github.cleveard.BiblioTech.R
import com.github.cleveard.BiblioTech.ui.books.BooksViewModel
import com.github.cleveard.BiblioTech.ui.tags.TagViewModel
import com.github.cleveard.BiblioTech.utils.BaseViewModel
import com.github.cleveard.BiblioTech.utils.coroutineAlert
import kotlinx.coroutines.launch

/**
 * Modal Action used to tag books
 * @param fragment The Fragment starting the modal action
 * Inherits from Observer<List<Range<Int>>> to observe selection changes
 */
class TagModalAction private constructor(
    private val fragment: Fragment,
    private val booksViewModel: BooksViewModel,
    private val tagViewModel: TagViewModel
): ModalAction(
    fragment.context?.resources?.getString(R.string.tag)?: "",          // action title
    fragment.context?.resources?.getString(R.string.tag_books)?: "", // Action subtitle
    R.menu.tag_mode,                                                         // delete menu
    arrayOf(Action(R.id.action_add_tags, TagModalAction::addTags),              // Action functions
            Action(R.id.action_remove_tags, TagModalAction::removeTags),              // Action functions
            Action(R.id.action_replace_tags, TagModalAction::replaceTags),              // Action functions
            Action(R.id.action_select_all, TagModalAction::selectAll),
            Action(R.id.action_select_none, TagModalAction::selectAll),
            Action(R.id.action_select_invert, TagModalAction::selectInvert))
), Observer<Int?> {
    // Menu items enabled and disabled based on the selection count
    private var addItem: MenuItem? = null
    private var removeItem: MenuItem? = null
    private var replaceItem: MenuItem? = null

    /**
     * Tag the selected books
     * @param item The MenuItem that initiated this action
     */
    @Suppress("UNUSED_PARAMETER")
    fun addTags(item: MenuItem): Boolean {
        addTags(fragment.requireContext(), booksViewModel, tagViewModel)
        return true
    }

    /**
     * Remove tags from the selected books
     * @param item The MenuItem that initiated this action
     */
    @Suppress("UNUSED_PARAMETER")
    fun removeTags(item: MenuItem): Boolean {
        removeTags(fragment.requireContext(), booksViewModel, tagViewModel)
        return true
    }

    /**
     * Replace tags for the selected books
     * @param item The MenuItem that initiated this action
     */
    @Suppress("UNUSED_PARAMETER")
    fun replaceTags(item: MenuItem): Boolean {
        replaceTags(fragment.requireContext(), booksViewModel, tagViewModel)
        return true
    }

    /**
     * Select All or None
     * @param item The MenuItem used to start the action. Used to choose select All of None
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun selectAll(item: MenuItem): Boolean {
        booksViewModel.selection.selectAllAsync(item.itemId == R.id.action_select_all)
        return true
    }

    /**
     * Invert selection
     * @param item The MenuItem used to start the action. Used to choose select All of None
     */
    @Suppress("UNUSED_PARAMETER")
    fun selectInvert(item: MenuItem): Boolean {
        booksViewModel.selection.invertAsync()
        return true
    }

    /**
     * {@inheritDoc}
     * Update the menu when the selection changes
     */
    override fun onChanged(t: Int?) {
        updateMenu()
    }

    /**
     * {@inheritDoc}
     * Get the menu items we update on selection changes and observe selection changes
     */
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val actionMode = super.onCreateActionMode(mode, menu)
        addItem = BaseViewModel.setupIcon(fragment.context, menu, R.id.action_add_tags)
        removeItem = BaseViewModel.setupIcon(fragment.context, menu, R.id.action_remove_tags)
        replaceItem = BaseViewModel.setupIcon(fragment.context, menu, R.id.action_replace_tags)
        booksViewModel.selection.selectedCount.observe(fragment, this)
        tagViewModel.selection.selectedCount.observe(fragment, this)
        return actionMode
    }

    /**
     * {@inheritDoc}
     * Update the action menu to start the action mode
     */
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        updateMenu()
        return true
    }

    /**
     * {@inheritDoc}
     * Clear the menu items and remove the selection observer.
     */
    override fun onDestroyActionMode(mode: ActionMode) {
        removeItem = null
        booksViewModel.selection.selectedCount.removeObserver(this)
        tagViewModel.selection.selectedCount.removeObserver(this)
        super.onDestroyActionMode(mode)
    }

    /**
     * Update the menu based on the selection count
     */
    private fun updateMenu() {
        val hasBookSelection = booksViewModel.selection.hasSelection
        val hasTagSelection = tagViewModel.selection.hasSelection
        // Only add if something is selected
        addItem?.isEnabled = hasBookSelection && hasTagSelection
        // Only delete if something is selected
        removeItem?.isEnabled = hasBookSelection && hasTagSelection
        // Only replace if books are selected
        replaceItem?.isEnabled = hasBookSelection
    }

    companion object {
        /**
         * Tag selected books
         * @param fragment The fragment requesting the delete
         * If no books are selected, start the delete modal action
         */
        fun doAddTags(fragment: Fragment) {
            val activity = fragment.requireActivity()
            val booksViewModel: BooksViewModel =
                MainActivity.getViewModel(activity, BooksViewModel::class.java)
            val tagViewModel: TagViewModel =
                MainActivity.getViewModel(activity, TagViewModel::class.java)
            if (booksViewModel.selection.hasSelection &&
                    tagViewModel.selection.hasSelection) {
                addTags(fragment.requireContext(), booksViewModel, tagViewModel)
            } else {
                TagModalAction(fragment, booksViewModel, tagViewModel).start(activity)
            }
        }

        /**
         * Remove tags from selected books
         * @param fragment The fragment requesting the delete
         * If no books are selected, start the delete modal action
         */
        fun doRemoveTags(fragment: Fragment) {
            val activity = fragment.requireActivity()
            val booksViewModel: BooksViewModel =
                MainActivity.getViewModel(activity, BooksViewModel::class.java)
            val tagViewModel: TagViewModel =
                MainActivity.getViewModel(activity, TagViewModel::class.java)
            if (booksViewModel.selection.hasSelection &&
                    tagViewModel.selection.hasSelection) {
                removeTags(fragment.requireContext(), booksViewModel, tagViewModel)
            } else {
                TagModalAction(fragment, booksViewModel, tagViewModel).start(activity)
            }
        }

        /**
         * Replace tags for selected books
         * @param fragment The fragment requesting the delete
         * If no books are selected, start the delete modal action
         */
        fun doReplaceTags(fragment: Fragment) {
            val activity = fragment.requireActivity()
            val booksViewModel: BooksViewModel =
                MainActivity.getViewModel(activity, BooksViewModel::class.java)
            val tagViewModel: TagViewModel =
                MainActivity.getViewModel(activity, TagViewModel::class.java)
            if (booksViewModel.selection.hasSelection) {
                replaceTags(fragment.requireContext(), booksViewModel, tagViewModel)
            } else {
                TagModalAction(fragment, booksViewModel, tagViewModel).start(activity)
            }
        }

        /**
         * Tag the selected books
         * @param context The context used to build the alert
         * @param booksViewModel The BooksViewModel with the data
         * @param onFinished A runnable called to finish the modal action
         */
        private fun addTags(
            context: Context,
            booksViewModel: BooksViewModel,
            tagViewModel: TagViewModel,
            onFinished: Runnable? = null
        ): Boolean {
            execute(context, booksViewModel, tagViewModel,
                R.plurals.ask_tag_books) {
                booksViewModel.repo.addTagsToBooks(null, null, booksViewModel.idFilter)
                // Finish the acton
                onFinished?.run()
            }
            return true
        }

        /**
         * Remove tags from the selected books
         * @param context The context used to build the alert
         * @param booksViewModel The BooksViewModel with the data
         * @param onFinished A runnable called to finish the modal action
         */
        private fun removeTags(
            context: Context,
            booksViewModel: BooksViewModel,
            tagViewModel: TagViewModel,
            onFinished: Runnable? = null
        ): Boolean {
            execute(context, booksViewModel, tagViewModel,
                R.plurals.ask_untag_books) {
                booksViewModel.repo.removeTagsFromBooks(null, null, booksViewModel.idFilter)
                // Finish the acton
                onFinished?.run()
            }
            return true
        }

        /**
         * Replace tags for the selected books
         * @param context The context used to build the alert
         * @param booksViewModel The BooksViewModel with the data
         * @param onFinished A runnable called to finish the modal action
         */
        private fun replaceTags(
            context: Context,
            booksViewModel: BooksViewModel,
            tagViewModel: TagViewModel,
            onFinished: Runnable? = null
        ): Boolean {
            execute(context, booksViewModel, tagViewModel,
                R.plurals.ask_repl_tag_books, true) {
                booksViewModel.repo.removeTagsFromBooks(null, null, booksViewModel.idFilter, true)
                booksViewModel.repo.addTagsToBooks(null, null, booksViewModel.idFilter)
                // Finish the acton
                onFinished?.run()
            }
            return true
        }

        /**
         * Replace tags for the selected books
         * @param context The context used to build the alert
         * @param booksViewModel The BooksViewModel with the data
         * @param pluralMessage String id for plural message to ask
         * @param callback Lambda to execute once permission is granted
         */
        private fun execute(
            context: Context,
            booksViewModel: BooksViewModel,
            tagViewModel: TagViewModel,
            pluralMessage: Int,
            allowEmptyTags: Boolean = false,
            callback: suspend () -> Unit
        ): Boolean {
            // Get the list of books and selection count
            val selCount = booksViewModel.selection.selectedCount.value?: 0
            if (selCount > 0 && (allowEmptyTags || tagViewModel.selection.hasSelection)
            ) {
                booksViewModel.viewModelScope.launch {
                    val result = coroutineAlert(context, { false }) {alert ->
                        // Make sure we really want to tag the books
                        alert.builder.setMessage(
                            context.resources.getQuantityString(
                                pluralMessage,
                                selCount,
                                selCount
                            )
                        )
                            // Set the action buttons
                            .setPositiveButton(R.string.ok, null)
                            .setNegativeButton(R.string.cancel, null)
                    }.setPosListener {alert, _, _ ->
                        alert.result = true
                        true
                    }.show()
                    // If OK pressed, then do the operation
                    if (result)
                        callback()
                }
            }
            return true
        }
    }
}