package com.github.cleveard.bibliotech.ui.bookshelves

import android.app.Application
import android.icu.util.Calendar
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.github.cleveard.bibliotech.BookCredentials
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.BookDatabase
import com.github.cleveard.bibliotech.db.BookRepository
import com.github.cleveard.bibliotech.db.BookshelfAndTag
import com.github.cleveard.bibliotech.db.BookshelfEntity
import com.github.cleveard.bibliotech.db.ShelfVolumeIdsEntity
import com.github.cleveard.bibliotech.db.TagEntity
import com.github.cleveard.bibliotech.db.UndoTransactionEntity
import com.github.cleveard.bibliotech.gb.GoogleBookLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal class BookshelvesViewModel(private val app: Application) : AndroidViewModel(app) {
    /**
     * Repository with the tag data
     */
    val repo: BookRepository = BookRepository.repo

    /** Lookup to use with this view model */
    val lookup: GoogleBookLookup = GoogleBookLookup()

    /**
     * Undo list
     */
    val undoList: LiveData<List<UndoTransactionEntity>> = repo.getUndoList()

    /**
     * Toggle whether a bookshelf is linked to a tag
     * @param shelf The the bookshelf amd tag to be updated
     */
    suspend fun toggleTagAndBookshelfLink(shelf: BookshelfAndTag) {
        repo.toggleTagAndBookshelfLink(shelf)
    }

    /**
     * Refresh the bookshelves table from google books
     * @param auth The credentials to access google books
     */
    suspend fun refreshBookshelves(
        auth: BookCredentials,
        handleConflicts: suspend BookshelfEntity.(conflicts: List<BookAndAuthors>, addedToShelf: Int) -> Boolean
    ) {
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

        BookDatabase.db.withTransaction {
            // This operation is undoable
            repo.withUndo(app.applicationContext.getString(R.string.refresh)) {
                // Current shelf from google. Remove it from the list
                var gbs: BookshelfEntity? = googleShelfList?.removeLastOrNull()
                // Current shelf from the database. Remove it from the list
                var lbs: BookshelfAndTag? = localShelfList.removeLastOrNull()

                // Loop until the end of the list
                while (lbs != null || gbs != null) {
                    // compare the current book shelf ids
                    when {
                        lbs?.bookshelf?.bookshelfId == gbs?.bookshelfId -> {
                            // Update local bookshelf with data from google
                            refreshShelfAndBooks(auth, lbs!!, gbs!!, handleConflicts)
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

    private suspend fun refreshBooksOnShelves(
        auth: BookCredentials,
        shelf: BookshelfEntity,
        tag: TagEntity,
        handleConflicts: suspend BookshelfEntity.(conflicts: List<BookAndAuthors>, addedToShelfCount: Int) -> Boolean
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
        val currentShelfMap: Map<String, BookAndAuthors> = lookup.getBookshelfBooksMap(auth, shelf.bookshelfId)
        // Get the set of volumes ids when we last update the books on the shelf
        val lastShelfVolumes = repo.getShelfVolumeIds(shelf.id)
        // Get the set of volume id from google on the shelf
        val lastShelfSet = mutableSetOf<String>().apply {
            lastShelfVolumes?.commonVolumes?.let { addAll(it) }
            lastShelfVolumes?.volumesOnlyOnShelf?.let { addAll(it) }
        }
        // The volume ids of the books tagged by the linked tag the last time we merged
        val lastDbSet = mutableSetOf<String>().apply {
            lastShelfVolumes?.commonVolumes?.let { addAll(it) }
            lastShelfVolumes?.volumesOnlyTagged?.let { addAll(it) }
        }
        // The current books tagged by the linked tag. The map maps volume id to the book
        // Filter out books that aren't from google books and don't have a volume id
        val curDbMap: Map<String, BookAndAuthors> = repo.getTaggedBooks(tag.id)
            .filter { it.book.sourceId == GoogleBookLookup.kSourceId && it.book.volumeId != null }
            .associateBy { it.book.volumeId!! }

        // Figure out what changed
        // Map of books currently on the shelf that were not there the last time we merged
        val newInShelf = currentShelfMap - lastShelfSet
        // Volume ids of books not on the shelf that were the last time we merged
        val missingFromShelf = lastShelfSet - currentShelfMap.keys

        // Figure out what changed
        // Map of books currently on the shelf that were not there the last time we merged
        val tagInDb = newInShelf.toMutableMap()
        // Volume ids of books not on the shelf that were the last time we merged
        val untagInDb = missingFromShelf.toMutableSet()
        // Map of books tagged by the linked tag since the last merge
        val addToShelf = (curDbMap - lastDbSet).toMutableMap()
        // Volume ids of books tagged when we merged last that are no longer tagged
        val deleteFromShelf = (lastDbSet - curDbMap.keys).toMutableSet()

        // If the books downloaded from haven't changed in a while, then we will
        // assume that google has finished any updates it was working on
        val now = Calendar.getInstance().timeInMillis
        if (tagInDb.isEmpty() && untagInDb.isEmpty()) {
            val time = shelf.booksDownloadLastChanged ?: now.also { shelf.booksDownloadLastChanged = it }
            if (now - time >= UNCHANGED_TIME_LIMIT) {
                currentShelfMap.values.forEach { book ->
                    if (!curDbMap.containsKey(book.book.volumeId!!))
                        tagInDb[book.book.volumeId!!] = book
                }
                curDbMap.values.forEach {book ->
                    if (!currentShelfMap.containsKey(book.book.volumeId!!))
                        untagInDb.add(book.book.volumeId!!)
                }
            }
        } else
            shelf.booksDownloadLastChanged = now


        // Conflicts arise when books added in the shelf were untagged in the db
        // or books tagged in the db were removed from the shelf. The books currently
        // in the shelf are not selected by default and We select all the
        // books tagged in the db, which will make the conflict resolution favor
        // the db changes over the changed on google books.
        val addedToShelf = addToShelf.filterKeys { it in untagInDb }.values.toList()
        val conflicts = tagInDb.filterKeys { it in deleteFromShelf }.values.toList() + addedToShelf
        // If we have some conflicts, then show them to the user and let
        // them decide which ones to keep.
        if (conflicts.isNotEmpty()) {
            // Show conflicts and let user decide what to do
            if (!shelf.handleConflicts(conflicts, addedToShelf.size))
                return false

            // Process the user response and adjust the books
            conflicts.forEach {
                if (it.book.isSelected) {
                    // Selected means we want to keep the book
                    // so remove it from the delete and untag lists
                    untagInDb.remove(it.book.volumeId)
                    deleteFromShelf.remove(it.book.volumeId)
                } else {
                    // Unselected means we don't want to keep the book
                    // so remove it from the add and tag list
                    tagInDb.remove(it.book.volumeId)
                    addToShelf.remove(it.book.volumeId)
                }
            }
        }

        // Temp storage for book and tag id
        val bookId = arrayOf<Any>(0)
        val tagId = arrayOf<Any>(tag.id)

        // Tag new volumes from the bookshelf
        tagInDb.values.forEach {book ->
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
        }

        // Untag volumes removed from the bookshelf
        untagInDb.forEach {volumeId ->
            // Make sure the volume came from google books, we don't mess with
            // books not in google books
            repo.getBookIdByVolumeId(GoogleBookLookup.kSourceId, volumeId)?.let {
                // Ok we got the id. If it is tagged, then remove the tag
                if (curDbMap.containsKey(volumeId)) {
                    bookId[0] = it
                    repo.removeTagsFromBooks(bookId, tagId, null)
                }
            }
        }

        // Remove volumes from the shelf
        deleteFromShelf.forEach {
            lookup.deleteVolumeFromShelf(auth, shelf.bookshelfId, it)
        }

        addToShelf.keys.forEach {
            lookup.addVolumeToShelf(auth, shelf.bookshelfId, it)
        }

        // If the set of volumes changes, then we will update them
        if (lastDbSet != curDbMap.keys || lastShelfSet != currentShelfMap.keys) {
            // Both current sets are empty, just make the volumes Id null
            if (curDbMap.isEmpty() && currentShelfMap.isEmpty())
                shelf.volumesId = null
            else {
                val common = currentShelfMap.keys.intersect(curDbMap.keys)
                shelf.volumesId = repo.addShelfVolumeIds(
                    ShelfVolumeIdsEntity(
                        id = 0L,
                        commonVolumes = common,
                        volumesOnlyOnShelf = currentShelfMap.keys - common,
                        volumesOnlyTagged = curDbMap.keys - common
                    )
                )
            }
        }

        return true
    }

    suspend fun refreshShelfAndBooks(
        auth: BookCredentials,
        shelf: BookshelfAndTag,
        forceBookRefresh: Boolean,
        handleConflicts: suspend BookshelfEntity.(conflicts: List<BookAndAuthors>, addedToShelfCount: Int) -> Boolean
    ) {
        // Get the list from google. Run this on an IO thread
        withContext(Dispatchers.IO) {
            lookup.getBookShelf(auth, shelf.bookshelf.bookshelfId)
        }?.let {
            repo.withUndo(app.applicationContext.getString(R.string.refresh_one_book, shelf.bookshelf.title)) {
                refreshShelfAndBooks(auth, shelf, it, handleConflicts, forceBookRefresh)
            }
        }
    }

    /**
     * Refresh a single book shelf
     * @param shelf The bookshelf to refresh
     * @param from The bookshelf from google books
     * @param forceBookRefresh Set to true to refresh volumes on shelf even if they haven't changed
     */
    private suspend fun refreshShelfAndBooks(
        auth: BookCredentials,
        shelf: BookshelfAndTag,
        from: BookshelfEntity,
        handleConflicts: suspend BookshelfEntity.(conflicts: List<BookAndAuthors>, addedToShelfCount: Int) -> Boolean,
        forceBookRefresh: Boolean = false
    ) {
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
            if (forceBookRefresh || from.booksModified > shelf.bookshelf.booksLastUpdate) {
                refreshBooksOnShelves(auth, shelf.bookshelf, tag, handleConflicts)
                shelf.bookshelf.booksLastUpdate = from.booksModified
            }
        }

        // Update the database
        // If the name of the bookshelf changes, then unlink the tag
        // which updates the database, or just update it
        shelf.tag?.let {tag ->
            if (shelf.bookshelf.title != from.title) {
                repo.toggleTagAndBookshelfLink(shelf)
            }
        } ?: repo.updateBookshelf(shelf.bookshelf)
    }

    companion object {
        private const val UNCHANGED_TIME_LIMIT = 3600000    // If google doesn't change for an hour
        // Check for changes. Don't use == because the tag id and
        // some flag bits don't matter, and the modified and booksModified
        // times are not reliable
        fun BookshelfEntity.changed(from: BookshelfEntity): Boolean {
            return this.title != from.title
                || this.description != from.description
                || this.selfLink != from.selfLink
                || this.bookshelfId != from.bookshelfId
        }
    }
}