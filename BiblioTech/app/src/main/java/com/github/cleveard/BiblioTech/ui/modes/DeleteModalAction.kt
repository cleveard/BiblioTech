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
import com.github.cleveard.BiblioTech.utils.BaseViewModel
import com.github.cleveard.BiblioTech.utils.coroutineAlert
import kotlinx.coroutines.launch

/**
 * Modal Action used to delete books
 * @param fragment The Fragment starting the modal action
 * Inherits from Observer<List<Range<Int>>> to observe selection changes
 */
class DeleteModalAction private constructor(private val fragment: Fragment, private val viewModel: BooksViewModel): ModalAction(
    fragment.context?.resources?.getString(R.string.delete)?: "",          // action title
    fragment.context?.resources?.getString(R.string.delete_books)?: "", // Action subtitle
    R.menu.delete_mode,                                                         // delete menu
    arrayOf(Action(R.id.action_delete, DeleteModalAction::delete),              // Action functions
            Action(R.id.action_select_all, DeleteModalAction::selectAll),
            Action(R.id.action_select_none, DeleteModalAction::selectAll),
            Action(R.id.action_select_invert, DeleteModalAction::selectInvert))
), Observer<Int?> {
    // Menu items enabled and disabled based on the selection count
    private var deleteItem: MenuItem? = null
    private var selectAll: MenuItem? = null
    private var selectNone: MenuItem? = null

    /**
     * Delete the selected books
     * @param item The MenuItem that initiated this action
     */
    @Suppress("UNUSED_PARAMETER")
    fun delete(item: MenuItem): Boolean {
        delete(fragment.requireContext(), viewModel) {
            finish()
        }
        return true
    }

    /**
     * Select All or None
     * @param item The MenuItem used to start the action. Used to choose select All of None
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun selectAll(item: MenuItem): Boolean {
        viewModel.selection.selectAllAsync(item.itemId == R.id.action_select_all)
        return true
    }

    /**
     * Invert selection
     * @param item The MenuItem used to start the action. Used to choose select All of None
     */
    @Suppress("UNUSED_PARAMETER")
    fun selectInvert(item: MenuItem): Boolean {
        viewModel.selection.invertAsync()
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
        deleteItem = BaseViewModel.setupIcon(fragment.context, menu, R.id.action_delete)
        selectAll = menu.findItem(R.id.action_select_all)
        selectNone = menu.findItem(R.id.action_select_none)
        viewModel.selection.selectedCount.observe(fragment, this)
        viewModel.selection.itemCount.observe(fragment, this)
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
        deleteItem = null
        selectAll = null
        selectNone = null
        viewModel.selection.selectedCount.removeObserver(this)
        viewModel.selection.itemCount.removeObserver(this)
        super.onDestroyActionMode(mode)
    }

    /**
     * Update the menu based on the selection count
     */
    private fun updateMenu() {
        val selectCount = viewModel.selection.selectedCount.value?: 0
        val itemCount = viewModel.selection.itemCount.value?: 0
        // Only delete if something is selected
        deleteItem?.isEnabled = selectCount > 0
        selectAll?.isEnabled = itemCount < selectCount
        selectNone?.isEnabled = selectCount > 0
    }

    companion object {
        /**
         * Delete selected books
         * @param fragment The fragment requesting the delete
         * If no books are selected, start the delete modal action
         */
        fun doDelete(fragment: Fragment) {
            val activity = fragment.requireActivity()
            val viewModel: BooksViewModel =
                MainActivity.getViewModel(activity, BooksViewModel::class.java)
            if (viewModel.selection.hasSelection) {
                delete(fragment.requireContext(), viewModel)
            } else {
                DeleteModalAction(fragment, viewModel).start(activity)
            }
        }

        /**
         * Delete the selected books
         * @param context The context used to build the alert
         * @param viewModel The BooksViewModel with the data
         * @param onFinished A runnable called to finish the modal action
         */
        private fun delete(context: Context, viewModel: BooksViewModel, onFinished: Runnable? = null): Boolean {
            // Get the list of books and selection count
            val selCount = viewModel.selection.selectedCount.value?: 0
            if (selCount > 0) {
                viewModel.viewModelScope.launch {
                    val result = coroutineAlert(context, { false }) {alert ->
                        // Make sure we really want to delete the books
                        alert.builder.setMessage(
                            context.resources.getQuantityString(
                                R.plurals.ask_delete_books,
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

                    if (result) {
                        // OK pressed delete the books
                        viewModel.repo.deleteSelectedBooks(viewModel.idFilter)
                        // Finish the acton
                        onFinished?.run()
                    }
                }
            }
            return true
        }
    }
}