package com.example.cleve.bibliotech.db

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * Application interface to the book database
 */
class BookRepository private constructor() {
    companion object {
        // Singleton BookRepository
        private var mRepo: BookRepository? = null

        /**
         * Single BookRepository instance
         */
        val repo: BookRepository
            get() = mRepo!!

        /**
         * Initialize the BookRepository
         * @param context Application context
         */
        fun initialize(context: Context) {
            // Only initialize if it has been closed
            if (mRepo == null) {
                // Initialize the database
                BookDatabase.initialize(context)
                // Create the repository
                mRepo = BookRepository()
            }
        }

        /**
         * Close the BookRepository
         */
        fun close() {
            mRepo?.also{
                mRepo = null
            }
            BookDatabase.close()
        }
    }

    // The book database
    private val db = BookDatabase.db

    /**
     * Scope for doing queries
     */
    val queryScope: CoroutineScope = CoroutineScope(db.queryExecutor.asCoroutineDispatcher())

    /**
     * Get books from the data base
     * @return A PagingSource containing the books
     */
    fun getBooks(): PagingSource<Int, BookAndAuthors> {
        return db.getBookDao().getBooks()
    }

    /**
     * Get books from the data base
     * @param filter Description of a filter that filters and orders the books
     * @return A PagingSource containing the books
     */
    fun getBooks(filter: BookFilter, context: Context): PagingSource<Int, BookAndAuthors> {
        return db.getBookDao().getBooks(filter, context)
    }

    /**
     * Add a book to the database or update a book already there
     * @param book The book to add or update
     * @param tagIds An array of ids of tags in the Tags table
     * @param invert A flag indicating whether the set of tag ids ar the ids in tagIds
     *               of the tag ids not in tagIds
     * The book is added and tags identified by tagIds and invert are linked to the book
     */
    suspend fun addOrUpdateBook(book: BookAndAuthors, tagIds: Array<Any>? = null, invert: Boolean = false) {
        db.getBookDao().addOrUpdate(book, tagIds, invert)
    }

    /**
     * Delete books from the book database
     * @param bookIds An array of book ids
     * @param invert A flag indicating whether the ids in bookIds are deleted, or the ids
     *               not in bookIds are deleted.
     */
    suspend fun deleteBooks(bookIds: Array<Any>, invert: Boolean = false) {
        db.getBookDao().delete(bookIds, invert)
    }

    /**
     * Get all tags
     * @return PagingSource containing the tags
     */
    fun getTags(): PagingSource<Int, Tag> {
        return db.getTagDao().get()
    }

    /**
     * Get a single tag
     * @param tagId The id of the tag
     * @return The TagEntity for the tag or null if it doesn't exist
     */
    suspend fun getTag(tagId: Long): TagEntity? {
        return db.getTagDao().get(tagId)
    }

    /**
     * Add or update a tag
     * @param tag The TagEntity to be added
     * @param callback A callback call when tag conflicts with an existing tag
     * @return The ids of the added or updated tag. 0, if an update fails
     * Callback can ask the user if it is OK to change an existing tag. This method will also
     * merge two tags into one, when you edit an existing tag and rename it to another existing tag.
     */
    suspend fun addOrUpdateTag(tag: TagEntity, callback: (suspend (conflict: TagEntity) -> Boolean)? = null): Long {
        return db.getTagDao().add(tag, callback)
    }

    /**
     * Delete tag
     * @param tagIds An array of tag ids
     * @param invert A flag indicating whether the ids in tagIds are deleted, or the ids
     *               not in tagIds are deleted.
     */
    suspend fun deleteTags(tagIds: Array<Any>, invert: Boolean = false) {
        db.getTagDao().delete(tagIds, invert)
    }

    /**
     * Add multiple tags to multiple books
     * @param bookIds An array of book ids
     * @param booksInvert A flag indicating whether tags are added to the ids in bookIds,
     *                    or tags are added to the ids not in bookIds
     * @param tagIds An array of tag ids
     * @param tagsInvert A flag indicating whether the ids in tagIds are added, or the ids
     *                   not in tagIds are added.
     */
    suspend fun addTagsToBooks(bookIds: Array<Any>, tagIds: Array<Any>,
                               booksInvert: Boolean = false, tagsInvert: Boolean = false) {
        db.getBookTagDao().addTagsToBooks(bookIds, tagIds, booksInvert, tagsInvert)
    }

    /**
     * Remove multiple tags from multiple books
     * @param bookIds An array of book ids
     * @param booksInvert A flag indicating whether tags are removed from the ids in bookIds,
     *                    or tags are removed from the ids not in bookIds
     * @param tagIds An array of tag ids
     * @param tagsInvert A flag indicating whether the ids in tagIds are removed, or the ids
     *                   not in tagIds are removed.
     */
    suspend fun removeTagsFromBooks(bookIds: Array<Any>, tagIds: Array<Any>,
                                    booksInvert: Boolean = false, tagsInvert: Boolean = false) {
        db.getBookTagDao().deleteTagsForBooks(bookIds, booksInvert, tagIds, tagsInvert)
    }

    /**
     * Get the thumbnail for a book
     * @param bookId The id of the book
     * @param large True to get the large thumbnail. False to get the small thumbnail
     * @return The bitmap, or null if there wasn't one
     */
    suspend fun getThumbnail(
        bookId: Long,
        large: Boolean
    ): Bitmap? {
        try {
            return db.getBookDao().getThumbnail(bookId, large)
        } catch (e: Exception) {}
        // Return null if there is an error
        return null
    }

    fun getCursor(query: String, args: Array<Any>? = null): Cursor {
        return db.query(query, args)
    }
}
