package com.github.cleveard.bibliotech.ui.bookshelves

import android.app.Application
import android.icu.util.Calendar
import androidx.lifecycle.viewModelScope
import com.github.cleveard.bibliotech.db.BookRepository
import com.github.cleveard.bibliotech.db.BookshelfAndTag
import com.github.cleveard.bibliotech.db.BookshelfEntity
import com.github.cleveard.bibliotech.utils.GenericViewModel

class BookshelvesViewModel(private val app: Application) : GenericViewModel<BookshelfAndTag>(app) {
    /**
     * Repository with the tag data
     */
    val repo: BookRepository = BookRepository.repo

    /**
     * Selection set for tags
     */
    override val selection: DataBaseSelectionSet = DataBaseSelectionSet(repo.FilteredBookCount(repo.shelfFlags, BookshelfEntity.SELECTED, viewModelScope))

    /**
     * Tag recycler adapter
     */
    internal val adapter = BookshelvesAdapter(this)

    /**
     * Count of shelves being edited
     */
    val editItems = repo.getEditItems()

    /**
     * Selection change listener
     */
    private val selectChange = {
        adapter.refresh()
    }.also { selection.onSelectionChanged.add(it) }

    /**
     * @inheritDoc
     */
    override fun onCleared() {
        selection.onSelectionChanged.remove(selectChange)
        super.onCleared()
    }


    private fun startNewShelf(): Boolean {
        if ((editItems.value?.size?: 0) > 0)
            return false
        val now = Calendar.getInstance().timeInMillis
        val entity = BookshelfEntity(
            id = null,
            bookshelfId = 0,
            title = "",
            description = "",
            selfLink = "",
            modified = now,
            booksModified = now,
            tagId = null,
            flags = BookshelfEntity.EDITING
        )

        return true
    }

}