package com.example.cleve.bibliotech.ui.modes

import android.app.AlertDialog
import android.content.Context
import android.util.Range
import androidx.appcompat.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.db.BookAndAuthors
import com.example.cleve.bibliotech.db.BookRepository

/**
 * Modal Action used to delete books
 * @param fragment The Fragment starting the modal action
 * @param repo The BookRepository holding the books
 * Inherits from Observer<List<Range<Int>>> to observe selection changes
 */
class DeleteModalAction(val fragment: Fragment, val repo: BookRepository): ModalAction(
    fragment.context?.resources?.getString(R.string.delete)?: "",          // action title
    fragment.context?.resources?.getString(R.string.delete_books)?: "", // Action subtitle
    R.menu.delete_mode,                                                         // delete menu
    arrayOf(Action(R.id.action_delete, DeleteModalAction::delete),              // Action functions
            Action(R.id.action_select_all, DeleteModalAction::selectAll),
            Action(R.id.action_select_none, DeleteModalAction::selectAll))
), Observer<List<Range<Int>>> {
    // Menu items enabled and disabled based on the selection count
    var deleteItem: MenuItem? = null
    var allItem: MenuItem? = null
    var noneItem: MenuItem? = null

    /**
     * Delete the selected books
     * @param item The MenuItem that initiated this action
     */
    fun delete(@Suppress("UNUSED_PARAMETER") item: MenuItem): Boolean {
        // Get the list of books and selection count
        val list: List<BookAndAuthors>? = repo.books.value
        val count = repo.selectCount
        // Do we have anything to do
        if (list != null && count > 0) {
            // Make sure we really want to delete the books
            val context = fragment.context!!
            val builder = AlertDialog.Builder(context)
            builder.setMessage(
                context.resources.getString(
                    if (count > 1) R.string.delete_n else R.string.delete_1,
                    count)
                )
                // Set the action buttons
                .setPositiveButton(
                    R.string.ok
                ) { _, _ ->
                    // OK pressed delete the boooks
                    for (book in list) {
                        if (book.selected)
                            repo.delete(book)
                    }
                    // Finish the acton
                    finish()
                }
                .setNegativeButton(
                    R.string.cancel
                ) { _, _ ->
                    // Cancel pressed, do nothing
                }
                .show()
        }
        return true;
    }

    /**
     * Selete All or None
     * @param item The MenuItem used to start the action. Used to choose select All of None
     */
    fun selectAll(item: MenuItem): Boolean {
        repo.selectAll(item.itemId == R.id.action_select_all)
        return true
    }

    /**
     * {@inheritdoc}
     * Update the menu when the selection changes
     */
    override fun onChanged(t: List<Range<Int>>?) {
        updateMenu()
    }

    /**
     * {@inheritdoc}
     * Get the menu items we update on selection changes and observe selection changes
     */
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val actionMode = super.onCreateActionMode(mode, menu)
        deleteItem = menu.findItem(R.id.action_delete)
        allItem = menu.findItem(R.id.action_select_all)
        noneItem = menu.findItem(R.id.action_select_none)
        repo.selectChanges.observe(fragment, this)
        return actionMode
    }

    /**
     * {@inheritdoc}
     * Update the action menu to start the action mode
     */
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        updateMenu()
        return true
    }

    /**
     * {@inheritdoc}
     * Clear the menu items and remove the selection observer.
     */
    override fun onDestroyActionMode(mode: ActionMode) {
        deleteItem = null
        allItem = null
        noneItem = null
        repo.selectChanges.removeObserver(this)
        super.onDestroyActionMode(mode)
    }

    /**
     * Update the menu based on the selection count
     */
    fun updateMenu() {
        val count = repo.selectCount
        val list = repo.books.value;
        val listCount = if (list == null) 0 else list.size
        // Only delete if something is selected
        deleteItem?.setEnabled(count != 0)
        // Only select all if the list isn't empty and all are not already selected
        allItem?.setEnabled(count != listCount && listCount != 0)
        // Only select none if something is selected
        noneItem?.setEnabled(count != 0)
    }
}