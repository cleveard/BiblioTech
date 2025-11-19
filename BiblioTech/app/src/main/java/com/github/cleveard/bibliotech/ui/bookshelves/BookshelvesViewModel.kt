package com.github.cleveard.bibliotech.ui.bookshelves

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.cleveard.bibliotech.BookCredentials
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookRepository
import com.github.cleveard.bibliotech.db.BookshelfAndTag
import com.github.cleveard.bibliotech.db.BookshelfEntity
import com.github.cleveard.bibliotech.db.TagEntity
import com.github.cleveard.bibliotech.gb.GoogleBookLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class BookshelvesViewModel(private val app: Application) : AndroidViewModel(app) {
    /**
     * Repository with the tag data
     */
    val repo: BookRepository = BookRepository.repo

    /**
     * Tag recycler adapter
     */
    internal val adapter = object: BookshelvesAdapter(viewModelScope) {
        override suspend fun toggleTagAndBookshelfLink(bookshelfId: Long) {
            this@BookshelvesViewModel.toggleTagAndBookshelfLink(bookshelfId)
        }
    }

    /** Lookup to use with this view model */
    val lookup: GoogleBookLookup = GoogleBookLookup()

    /**
     * Toggle whether a bookshelf is linked to a tag
     * @param id The database id of the bookshelf
     */
    suspend fun toggleTagAndBookshelfLink(id: Long) {
        // This operation is undoable
        repo.withUndo(app.applicationContext.resources.getString(R.string.toggle_bookshelf_link)) {
            // Get the bookshelf and tag
            val shelf = repo.getShelfAndTag(id)?: return@withUndo
            shelf.tag?.let { tag ->
                // We have a tag, so unlink it
                tag.hasBookshelf = false
                shelf.bookshelf.tagId = 0L
                shelf.tag = null
                // update the bookshelf
                repo.updateBookshelf(shelf.bookshelf)
                // update the tag
                repo.addOrUpdateTag(tag) { true }
            } ?: run {
                // We don't have a tag, so link to a tag. Create the tag if needed
                shelf.tag = TagEntity(0L, shelf.bookshelf.title, shelf.bookshelf.description, TagEntity.HAS_BOOKSHELF).also {
                    // Update the tag
                    it.id = repo.addOrUpdateTag(it) { true }
                    shelf.bookshelf.tagId = it.id
                }
                // Update the bookshelf
                repo.updateBookshelf(shelf.bookshelf)
            }
        }
    }

    /**
     * Refresh the bookshelves table from google books
     * @param auth The credentials to access google books
     */
    suspend fun refreshBookshelves(auth: BookCredentials) {
        // List of shelves from google books sorted by title
        val googleShelfList: MutableList<BookshelfEntity>?
        // List of shelves from the database sorted by title
        val localShelfList: MutableList<BookshelfAndTag>

        // Get the two lists asynchronously
        coroutineScope {
            // Get the list from google. Run this on an IO thread
            val google = async(Dispatchers.IO) {
                lookup.getBookShelves(auth)?.toMutableList()?.also {list ->
                    list.sortBy { it.bookshelfId }
                }
            }
            // Get the list from the database. Room will run this on a different thread
            val local = async {
                repo.getBookShelves().toMutableList().also { list ->
                    list.sortBy { it.bookshelf.bookshelfId }
                }
            }
            // Get the two lists when they are done
            googleShelfList = google.await()
            localShelfList = local.await()
        }

        // This operation is undoable
        repo.withUndo(app.applicationContext.getString(R.string.refresh)) {
            // Current shelf from google. Remove it from the list
            var gbs: BookshelfEntity? = googleShelfList?.removeLastOrNull()
            // Current shelf from the database. Remove it from the list
            var lbs: BookshelfAndTag? = localShelfList.removeLastOrNull()

            // Check for changes. Don't use == because the tag id and
            // some flag bits don't matter
            fun BookshelfEntity.changed(from: BookshelfEntity): Boolean {
                return this.title != from.title
                    || this.description != from.description
                    || this.selfLink != from.selfLink
                    || this.bookshelfId != from.bookshelfId
                    || this.modified != from.modified
                    || this.booksModified != from.booksModified
            }

            // Loop until the end of the list
            while (lbs != null || gbs != null) {
                // compare the current book shelf ids
                when {
                    lbs?.bookshelf?.bookshelfId == gbs?.bookshelfId -> {
                        // Update local bookshelf with data from google
                        if (lbs!!.bookshelf.changed(gbs!!)) {
                            // Something change update the local shelf
                            lbs.bookshelf.title = gbs.title
                            lbs.bookshelf.description = gbs.description
                            lbs.bookshelf.bookshelfId = gbs.bookshelfId
                            lbs.bookshelf.selfLink = gbs.selfLink
                            lbs.bookshelf.modified = gbs.modified
                            lbs.bookshelf.booksModified = gbs.booksModified
                        }
                        // Update the database
                        repo.updateBookshelf(lbs.bookshelf)
                        // Pop next shelves from the list
                        gbs = googleShelfList?.removeLastOrNull()
                        lbs = localShelfList.removeLastOrNull()
                    }

                    lbs == null || lbs.bookshelf.bookshelfId < gbs!!.bookshelfId -> {
                        // Add google bookshelf to database
                        repo.addBookshelf(gbs!!)
                        // Pop next google shelf from the list
                        gbs = googleShelfList?.removeLastOrNull()
                    }

                    else -> {
                        // Update the shelf
                        repo.removeBookshelf(lbs)
                        // Pop next local shelf from the list
                        lbs = localShelfList.removeLastOrNull()
                    }
                }
            }
        }
    }
}