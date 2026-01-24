package com.github.cleveard.bibliotech.ui.bookshelves

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.github.cleveard.bibliotech.BookCredentials
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.BookDatabase
import com.github.cleveard.bibliotech.db.BookRepository
import com.github.cleveard.bibliotech.db.BookshelfAndTag
import com.github.cleveard.bibliotech.db.Bookshelf
import com.github.cleveard.bibliotech.db.TagEntity
import com.github.cleveard.bibliotech.db.UndoTransactionEntity
import com.github.cleveard.bibliotech.gb.GoogleBookLookup
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * View model for the BookshelvesFragment
 */
internal class BookshelvesViewModel(private val app: Application) : AndroidViewModel(app) {
    /**
     * Interface for progress dialog when refreshing shelves
     */
    interface Progress {
        /** Number of books being updated on a shelf. Update at the start of each shelf. */
        var bookCount: Int
        /** Number of shelves being updated. Update at the start of the refresh */
        var shelfCount: Int
        /** Flag indicating whether the refresh has been canceled. */
        val canceled: Boolean

        /**
         * Show the progress bar while running a code block
         */
        suspend fun run(block: suspend (progress: Progress) -> Unit)
        /**
         * Show the progress dialog
         */
        suspend fun show()
        /**
         * Dismiss the progress dialog
         */
        fun dismiss()
        /**
         * Cancel the progress dialog
         */
        fun cancel()
        /**
         * Start processing next shelf
         */
        fun nextShelf(shelf: String)
        /**
         * Start processing next book
         */
        fun nextBook()
        /**
         * Run code in the main thread with a context
         */
        suspend fun runForUI(block: (context: Context) -> Unit)
    }

    /** Flag for refreshing books on a shelf, when refreshing shelves */
    enum class RefreshBooks {
        // Don't update the books
        NEVER,
        // Always update the books
        ALWAYS,
        // Only update if the google reports the books have changed.
        IF_OUT_OF_DATE
    }

    /**
     * Repository with the bookshelf data
     */
    val repo: BookRepository = BookRepository.repo

    /** Lookup to use with this view model */
    val lookup: GoogleBookLookup = GoogleBookLookup()

    /**
     * Undo list
     * Used to maintain undo/redo menu items.
     */
    val undoList: LiveData<List<UndoTransactionEntity>> = repo.getUndoList()

    /**
     * Toggle whether a bookshelf is linked to a tag
     * @param auth The credentials to access google books
     * @param shelf The the bookshelf amd tag to be updated
     * @param clearShelf Set to true to clear the bookshelf when we unlink
     * @return True if the toggle completed. False if it was canceled.
     */
    suspend fun toggleTagAndBookshelfLink(
        auth: BookCredentials,
        shelf: BookshelfAndTag,
        clearShelf: Boolean,
        progress: Progress?
    ): Boolean {
        // Keep track of whether we finish the clearing the shelf
        var finished = true
        // Don't try to clear the shelf, if we are making the link
        if (shelf.tag != null && clearShelf) {
            // Get the books on the shelf - we only need the volume id
            val books = lookup.getBookshelfBooksMap(auth, shelf.bookshelf.bookshelfId).values.mapNotNull { it.book.volumeId }
            val lastVolumes = repo.getLastShelfVolumes(shelf.bookshelf.id).toMutableSet()
            if (books.isNotEmpty()) {
                // We have some books to clear
                try {
                    // Show the progress dialog and set it up
                    progress?.show()
                    progress?.shelfCount = 1
                    progress?.nextShelf(shelf.bookshelf.title)
                    progress?.bookCount = books.size
                    // Delete books until we are canceled, or finished
                    // We are finished when firstOrNull returns null
                    finished = books.firstOrNull { volume ->
                        (progress?.canceled == true).apply {
                            // Delete the next book
                            progress?.nextBook()
                            lookup.deleteVolumeFromShelf(auth, shelf.bookshelf.bookshelfId, volume)
                            lastVolumes.remove(volume)
                        }

                    } == null
                } finally {
                    // Dismiss the progress dialog
                    progress?.dismiss()
                }
                // Update the lastShelfVolumes
                repo.updateLastShelfVolumes(shelf.bookshelf.id, shelf.bookshelf.booksLastUpdate, lastVolumes)
            }
        }
        // If we finished unlink the shelf
        if (finished)
            repo.toggleTagAndBookshelfLink(shelf)
        return finished
    }

    /**
     * Refresh the bookshelves table from google books
     * @param auth The credentials to access google books
     * @param refreshBooks Flag to indicate when to refresh the books on the shelf
     * @param progress Interface for the progress dialog
     * @param handleConflicts Callback to handle conflicting book updates
     */
    suspend fun refreshBookshelves(
        auth: BookCredentials,
        refreshBooks: RefreshBooks = RefreshBooks.IF_OUT_OF_DATE,
        progress: Progress? = null,
        handleConflicts: suspend Bookshelf.(conflicts: List<BookAndAuthors>, addedToShelf: Int) -> Boolean
    ) {
        // List of shelves from google books sorted by title
        val googleShelfList: MutableList<Bookshelf>?
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
            /// Count shelves
            progress?.shelfCount = mutableSetOf<Int>().apply {
                googleShelfList?.forEach { add(it.bookshelfId) }
                localShelfList.forEach { add(it.bookshelf.bookshelfId) }
            }.size
        }

        // Process all of the updates as a transaction
        BookDatabase.db.withTransaction {
            // This operation is undoable
            repo.withUndo(app.applicationContext.getString(R.string.refresh)) {
                // Current shelf from google. Remove it from the list
                var gbs: Bookshelf? = googleShelfList?.removeLastOrNull()
                // Current shelf from the database. Remove it from the list
                var lbs: BookshelfAndTag? = localShelfList.removeLastOrNull()

                // Loop until the end of the list
                while (lbs != null || gbs != null) {
                    // Stop if the progress dialog is canceled
                    if (progress?.canceled == true)
                        return@withUndo

                    // compare the current book shelf ids
                    when {
                        // If the google book shelf ids are the same
                        lbs?.bookshelf?.bookshelfId == gbs?.bookshelfId -> {
                            // Update local bookshelf with data from google
                            refreshShelfAndBooks(auth, lbs!!, gbs!!, refreshBooks, progress, handleConflicts)
                            // Pop next shelves from the list
                            gbs = googleShelfList?.removeLastOrNull()
                            lbs = localShelfList.removeLastOrNull()
                        }

                        // New shelf from google
                        lbs == null || (gbs != null && lbs.bookshelf.bookshelfId < gbs.bookshelfId) -> {
                            progress?.nextShelf(gbs!!.title)
                            // Add google bookshelf to database
                            repo.addBookshelf(gbs!!)
                            // Pop next google shelf from the list
                            gbs = googleShelfList?.removeLastOrNull()
                        }

                        // Missing shelf from google
                        else -> {
                            progress?.nextShelf(lbs.bookshelf.title)
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

    /**
     * Refresh the books on a single shelf
     * @param auth The credentials to access google books
     * @param shelf The bookshelf to update
     * @param tag The tag linked to the bookshelf
     * @param progress Interface for the progress dialog
     * @param handleConflicts Callback to handle conflicting book updates
     */
    private suspend fun refreshBooksOnShelves(
        auth: BookCredentials,
        shelf: Bookshelf,
        tag: TagEntity,
        progress: Progress? = null,
        handleConflicts: suspend Bookshelf.(conflicts: List<BookAndAuthors>, addedToShelfCount: Int) -> Boolean
    ): Boolean {
        // When we refresh books on shelves we perform a 3-way merge of the books currently on the shelf
        // and the books tagged by the linked tag. But, we also need to account for the fact google books
        // may not update changes to the shelf immediately. So we track changes to the books on
        // the shelf and the books we have tagged and merge them together. There are still cases
        // where the two databased can get out of sync. If you add a book back to a shelf, while
        // it being removed from the shelf or vice-versa then we will miss that. The delay in google
        // books updating shelves was seen by me when I first started trying to connect to
        // shelves. Once we get this working maybe we can find a way to get around the problem.

        // Collect the data we need for the 3 way merge
        // The current volumes on the shelf in google books. The map maps volume id to the book
        val currentShelfMap: Map<String, BookAndAuthors>
        try {
            currentShelfMap = lookup.getBookshelfBooksMap(auth, shelf.bookshelfId)
        } catch(_: GoogleJsonResponseException){
            progress?.runForUI {
                Toast(it).apply {
                    duration = Toast.LENGTH_LONG
                    setText(it.resources.getString(R.string.books_not_available, shelf.title))
                    show()
                }
            }
            return true
        }
        // Get the set of volumes ids when we last update the books on the shelf
        val lastShelfSet = repo.getLastShelfVolumes(shelf.id)
        // The current books tagged by the linked tag. The map maps volume id to the book
        // Filter out books that aren't from google books and don't have a volume id
        val curDbMap: Map<String, BookAndAuthors> = repo.getTaggedBooks(tag.id)
            .filter { it.book.sourceId == GoogleBookLookup.kSourceId && it.book.volumeId != null }
            .associateBy { it.book.volumeId!! }

        // Figure out what changed
        // Map of books currently on the shelf that were not supposed to be there the last time we merged
        val tagInDb = (currentShelfMap - lastShelfSet).toMutableMap()
        // Volume ids of books not on the shelf that were the last time we merged
        val untagInDb = (lastShelfSet - currentShelfMap.keys).intersect(curDbMap.keys).toMutableSet()
        // Map of books tagged by the linked tag since the last merge
        val addToShelf = (curDbMap - lastShelfSet).toMutableMap()
        // Volume ids of books tagged when we merged last that are no longer tagged
        val deleteFromShelf = (lastShelfSet - curDbMap.keys).intersect(currentShelfMap.keys).toMutableSet()

        // Remove the volumes already tagged or on the shelf that we want to tag or add
        // Put those volumes in the nextShelfSet, because they already agree
        val nextShelfSet = lastShelfSet.toMutableSet()
        // Add already tagged volumes to nextShelfSet, and remove them from the
        // books we want to tag
        nextShelfSet.addAll(tagInDb.keys.intersect(curDbMap.keys))
        curDbMap.keys.forEach { tagInDb.remove(it) }
        // Add books already on the shelf to the nextShelfSet and remove them from
        // the volumes we want to add to the shelf
        nextShelfSet.addAll(addToShelf.keys.intersect(currentShelfMap.keys))
        currentShelfMap.keys.forEach { addToShelf.remove(it) }

        // Conflicts arise when books added in the shelf were untagged in the db
        // or books tagged in the db were removed from the shelf. The books currently
        // in the shelf are not selected by default and We select all the
        // books tagged in the db, which will make the conflict resolution favor
        // the db changes over the changed on google books.
        val newForShelf = tagInDb.filterKeys { it in deleteFromShelf }.values.toList()
        val conflicts = newForShelf + addToShelf.filterKeys { it in untagInDb }.values.toList()
        // If we have some conflicts, then show them to the user and let
        // them decide which ones to keep.
        if (conflicts.isNotEmpty()) {
            // Show conflicts and let user decide what to do
            if (!shelf.handleConflicts(conflicts, newForShelf.size))
                return false

            // Process the user response and adjust the books
            conflicts.forEach {
                if (it.book.isSelected) {
                    // Selected means we want to keep the book
                    // so remove it from the delete and untag lists
                    untagInDb.remove(it.book.volumeId!!)
                    deleteFromShelf.remove(it.book.volumeId!!)
                } else {
                    // Unselected means we don't want to keep the book
                    // so remove it from the add and tag list
                    tagInDb.remove(it.book.volumeId!!)
                    addToShelf.remove(it.book.volumeId!!)
                }
            }
        }

        // The number of books being updated
        val totalCount = tagInDb.size + untagInDb.size + addToShelf.size + deleteFromShelf.size
        var finished = true
        // Nothing to do if no books changed
        if (totalCount != 0) {
            progress?.bookCount = totalCount

            // Temp storage for book and tag id
            val bookId = arrayOf<Any>(0)
            val tagId = arrayOf<Any>(tag.id)

            coroutineScope {
                // Run db updates and google shelf updates in parallel
                launch {
                    // Tag new volumes from the bookshelf
                    finished = tagInDb.values.firstOrNull { book ->
                        (progress?.canceled == true).apply {
                            progress?.nextBook()
                            // If we do have the book, then tag it
                            repo.getBookIdByVolumeId(book.book.sourceId!!, book.book.volumeId!!)?.let {
                                // Make sure it isn't already tagged
                                if (!curDbMap.containsKey(book.book.volumeId)) {
                                    bookId[0] = it
                                    repo.addTagsToBooks(bookId, tagId, null)
                                }
                            } ?: run {
                                // We don't have the book, so add it with the book already tagged
                                book.tags = listOf(tag)
                                repo.addOrUpdateBook(book) { true }
                            }
                            // Add the book to the last set of tagged books
                            nextShelfSet.add(book.book.volumeId!!)
                        }
                    } != null && finished

                    // Untag volumes removed from the bookshelf
                    finished = untagInDb.firstOrNull { volumeId ->
                        (progress?.canceled == true).apply {
                            progress?.nextBook()
                            // Make sure the volume came from google books, we don't mess with
                            // books not in google books
                            repo.getBookIdByVolumeId(GoogleBookLookup.kSourceId, volumeId)?.let {
                                // Ok we got the id. If it is tagged, then remove the tag
                                if (curDbMap.containsKey(volumeId)) {
                                    bookId[0] = it
                                    repo.removeTagsFromBooks(bookId, tagId, null)
                                }
                            }
                            // remove volume from the last set of tagged books
                            nextShelfSet.remove(volumeId)
                        }
                    } != null && finished
                }

                launch {
                    // Remove volumes from the shelf that were untagged
                    finished = deleteFromShelf.firstOrNull {
                        (progress?.canceled == true).apply {
                            progress?.nextBook()
                            lookup.deleteVolumeFromShelf(auth, shelf.bookshelfId, it)
                            nextShelfSet.remove(it)
                        }
                    } != null && finished

                    // Add volumes to the shelf that were tagged
                    finished = addToShelf.values.firstOrNull {
                        (progress?.canceled == true).apply {
                            progress?.nextBook()
                            lookup.addVolumeToShelf(auth, shelf.bookshelfId, it.book.volumeId!!)
                            nextShelfSet.add(it.book.volumeId!!)
                        }
                    } != null && finished
                }
            }
        }

        // Only update the last update time, if we finished the update
        if (finished)
            shelf.booksLastUpdate = shelf.booksModified

        // Update the last shelf volumes - needed to resume the refresh if canceled
        repo.updateLastShelfVolumes(shelf.id, shelf.booksLastUpdate, nextShelfSet)

        return finished
    }

    /**
     * Refresh the shelf and books for a single shelf
     * @param auth The credentials to access google books
     * @param shelf The bookshelf and linked tag for the shelf
     * @param refreshBooks Flag to indicate when to refresh the books on the shelf
     * @param progress Interface for the progress dialog
     * @param handleConflicts Callback to handle conflicting book updates
     */
    suspend fun refreshShelfAndBooks(
        auth: BookCredentials,
        shelf: BookshelfAndTag,
        refreshBooks: RefreshBooks,
        progress: Progress? = null,
        handleConflicts: suspend Bookshelf.(conflicts: List<BookAndAuthors>, addedToShelfCount: Int) -> Boolean
    ) {
        // Get the list from google. Run this on an IO thread
        withContext(Dispatchers.IO) {
            lookup.getBookShelf(auth, shelf.bookshelf.bookshelfId)
        }?.let {
            progress?.shelfCount = 1
            repo.withUndo(app.applicationContext.getString(R.string.refresh_one_book, shelf.bookshelf.title)) {
                refreshShelfAndBooks(auth, shelf, it, refreshBooks, progress, handleConflicts)
            }
        }
    }

    /**
     * Refresh a single book shelf
     * @param auth The credentials to access google books
     * @param shelf The bookshelf and linked tag for the shelf
     * @param from The bookshelf from google books
     * @param refreshBooks Flag to indicate when to refresh the books on the shelf
     * @param progress Interface for the progress dialog
     * @param handleConflicts Callback to handle conflicting book updates
     */
    private suspend fun refreshShelfAndBooks(
        auth: BookCredentials,
        shelf: BookshelfAndTag,
        from: Bookshelf,
        refreshBooks: RefreshBooks = RefreshBooks.IF_OUT_OF_DATE,
        progress: Progress? = null,
        handleConflicts: suspend Bookshelf.(conflicts: List<BookAndAuthors>, addedToShelfCount: Int) -> Boolean
    ) {
        // Let progress know we are starting the next shelf
        progress?.nextShelf(shelf.bookshelf.title)

        // Something change update the local shelf
        // Update local bookshelf with data from google
        if (shelf.bookshelf.changed(from)) {
            // Update the bookshelf
            shelf.bookshelf.title = from.title
            shelf.bookshelf.description = from.description
            shelf.bookshelf.bookshelfId = from.bookshelfId
            shelf.bookshelf.selfLink = from.selfLink
            shelf.bookshelf.modified = from.modified
            shelf.bookshelf.booksModified = from.booksModified
        }

        // If the shelf is linked to a tag, and the books on the shelf have been
        // modified since the last time we refreshed, then update the book list
        // and tags for the shelf.
        shelf.tag?.let {tag ->
            if (refreshBooks == RefreshBooks.ALWAYS || (refreshBooks == RefreshBooks.IF_OUT_OF_DATE  && from.booksModified > shelf.bookshelf.booksLastUpdate)) {
                if (refreshBooksOnShelves(auth, shelf.bookshelf, tag, progress, handleConflicts))
                    shelf.bookshelf.booksLastUpdate = from.booksModified
            }
        }

        // Update the database
        // If the name of the bookshelf changes, then unlink the tag
        // which updates the database, or just update it
        if (shelf.bookshelf.title != from.title && shelf.tag != null)
            repo.toggleTagAndBookshelfLink(shelf)

        // Update the shelf
        repo.updateBookshelf(shelf.bookshelf)
    }

    companion object {
        // Check for changes. Don't use == because the tag id and
        // some flag bits don't matter, and the modified and booksModified
        // times are not reliable
        fun Bookshelf.changed(from: Bookshelf): Boolean {
            return this.title != from.title
                || this.description != from.description
                || this.selfLink != from.selfLink
                || this.bookshelfId != from.bookshelfId
        }
    }
}