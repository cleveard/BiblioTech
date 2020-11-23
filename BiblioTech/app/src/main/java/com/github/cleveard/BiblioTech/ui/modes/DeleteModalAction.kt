package com.github.cleveard.BiblioTech.ui.modes

import android.app.AlertDialog
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
), Observer<Boolean> {
    // Menu items enabled and disabled based on the selection count
    private var deleteItem: MenuItem? = null

    /**
     * Delete the selected books
     * @param item The MenuItem that initiated this action
     */
    @Suppress("UNUSED_PARAMETER")
    fun delete(item: MenuItem): Boolean {
        delete(fragment.context!!, viewModel) {
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
        viewModel.selection.selectAll(item.itemId == R.id.action_select_all)
        return true
    }

    /**
     * Invert selection
     * @param item The MenuItem used to start the action. Used to choose select All of None
     */
    @Suppress("UNUSED_PARAMETER")
    fun selectInvert(item: MenuItem): Boolean {
        viewModel.selection.invert()
        return true
    }

    /**
     * {@inheritDoc}
     * Update the menu when the selection changes
     */
    override fun onChanged(t: Boolean?) {
        updateMenu()
    }

    /**
     * {@inheritDoc}
     * Get the menu items we update on selection changes and observe selection changes
     */
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val actionMode = super.onCreateActionMode(mode, menu)
        deleteItem = BaseViewModel.setupIcon(fragment.context, menu, R.id.action_delete)
        viewModel.selection.hasSelection.observe(fragment, this)
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
        viewModel.selection.hasSelection.removeObserver(this)
        super.onDestroyActionMode(mode)
    }

    /**
     * Update the menu based on the selection count
     */
    private fun updateMenu() {
        val hasSelection = viewModel.selection.hasSelection.value!!
        // Only delete if something is selected
        deleteItem?.isEnabled = hasSelection
    }

    companion object {
        /**
         * Delete selected books
         * @param fragment The fragment requesting the delete
         * If no books are selected, start the delete modal action
         */
        fun doDelete(fragment: Fragment) {
            val activity = fragment.activity!!
            val viewModel: BooksViewModel =
                MainActivity.getViewModel(activity, BooksViewModel::class.java)
            if (viewModel.selection.hasSelection.value == true) {
                delete(fragment.context!!, viewModel)
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
            val bookIds = viewModel.selection.selection
            val inverted = viewModel.selection.inverted
            if (inverted || bookIds.isNotEmpty()) {
                // Make sure we really want to delete the books
                val builder = AlertDialog.Builder(context)
                builder.setMessage(
                    if (inverted)
                        context.resources.getString(R.string.delete_unknown_books)
                    else
                        context.resources.getQuantityString(R.plurals.ask_delete_books, bookIds.size, bookIds.size)
                )
                    // Set the action buttons
                    .setPositiveButton(
                        R.string.ok
                    ) { _, _ ->
                        // OK pressed delete the books
                        viewModel.viewModelScope.launch {
                            viewModel.selection.selectAll(false)
                            viewModel.repo.deleteBooks(bookIds, inverted)
                            // Finish the acton
                            onFinished?.run()
                        }
                    }
                    .setNegativeButton(
                        R.string.cancel
                    ) { _, _ ->
                        // Cancel pressed, do nothing
                    }
                    .show()
            }
            return true
        }
    }
}