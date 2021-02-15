package com.github.cleveard.bibliotech.db

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Application interface to the book database
 */
class BookRepository private constructor() {
    /**
     * Interface to deal with flags in database
     */
    interface FlagsInterface {
        /**
         * Change bits in the flag column
         * @param operation The operation to perform. True to set, false to clear, null to toggle
         * @param mask The bits to change
         * @param id The id of the row to change. Null to change all
         * @param filter A filter to restrict the rows
         * @return The number of rows changed
         */
        suspend fun changeBits(operation: Boolean?, mask: Int, id: Long?, filter: BookFilter.BuiltFilter? = null): Int?
        /**
         * Count bits in the flag column
         * @param bits The bits to count
         * @param value The value to count
         * @param include True to count values that match, false to include values that don't match
         * @param id The id of the row to change. Null to change all
         * @param filter A filter to restrict the rows
         * @return The number of rows matched
         */
        suspend fun countBits(bits: Int, value: Int, include: Boolean, id: Long?, filter: BookFilter.BuiltFilter?): Int?
        /**
         * Count bits in the flag column
         * @param bits The bits to count
         * @param value The value to count
         * @param include True to count values that match, false to include values that don't match
         * @param id The id of the row to change. Null to change all
         * @param filter A filter to restrict the rows
         * @return The number of rows changed in a LiveData
         */
        suspend fun countBitsLive(bits: Int, value: Int, include: Boolean, id: Long?, filter: BookFilter.BuiltFilter?): LiveData<Int?>
    }

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
         * @param inMemory Create the book database in memory
         */
        fun initialize(context: Context, inMemory: Boolean = false) {
            // Only initialize if it has been closed
            if (mRepo == null) {
                // Initialize the database
                BookDatabase.initialize(context, inMemory)
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
     * Object for dealing with the bits in the book flags
     */
    val bookFlags: FlagsInterface = object: FlagsInterface {
        /** inheritDoc **/
        override suspend fun changeBits(
            operation: Boolean?,
            mask: Int,
            id: Long?,
            filter: BookFilter.BuiltFilter?
        ): Int? {
            return db.getBookDao().changeBits(operation, mask, id, filter)
        }

        /** inheritDoc **/
        override suspend fun countBits(
            bits: Int,
            value: Int,
            include: Boolean,
            id: Long?,
            filter: BookFilter.BuiltFilter?
        ): Int? {
            return db.getBookDao().countBits(bits, value, include, id, filter)
        }

        /** inheritDoc **/
        override suspend fun countBitsLive(
            bits: Int,
            value: Int,
            include: Boolean,
            id: Long?,
            filter: BookFilter.BuiltFilter?
        ): LiveData<Int?> {
            return db.getBookDao().countBitsLive(bits, value, include, id, filter)
        }
    }

    /**
     * Object for dealing with the bits in the tag flags
     */
    val tagFlags: FlagsInterface = object: FlagsInterface {
        /** inheritDoc **/
        override suspend fun changeBits(
            operation: Boolean?,
            mask: Int,
            id: Long?,
            filter: BookFilter.BuiltFilter?
        ): Int? {
            return db.getTagDao().changeBits(operation, mask, id)
        }

        /** inheritDoc **/
        override suspend fun countBits(
            bits: Int,
            value: Int,
            include: Boolean,
            id: Long?,
            filter: BookFilter.BuiltFilter?
        ): Int? {
            return db.getTagDao().countBits(bits, value, include, id)
        }

        /** inheritDoc **/
        override suspend fun countBitsLive(
            bits: Int,
            value: Int,
            include: Boolean,
            id: Long?,
            filter: BookFilter.BuiltFilter?
        ): LiveData<Int?> {
            return db.getTagDao().countBitsLive(bits, value, include, id)
        }
    }

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
     * The book is added and selected tags
     */
    suspend fun addOrUpdateBook(book: BookAndAuthors) {
        db.getBookDao().addOrUpdate(book)
    }

    /**
     * Delete books from the book database
     * @param filter The current filter
     */
    suspend fun deleteSelectedBooks(filter: BookFilter.BuiltFilter?, bookIds: Array<Any>? = null): Int {
        return db.getBookDao().deleteSelected(filter, bookIds)
    }

    /**
     * Get all tags
     * @return PagingSource containing the tags
     */
    fun getTags(): PagingSource<Int, TagEntity> {
        return db.getTagDao().get()
    }

    /**
     * Get all tags
     * @param selected True to get selected tags. False to get all tags.
     * @return LiveData with the list of tags
     */
    suspend fun getTagsLive(selected: Boolean = false): LiveData<List<TagEntity>> {
        return db.getTagDao().getLive(selected)
    }

    /**
     * Find a tag by name
     * @param tagName The name of the tag
     * @return The TagEntity or null if it wasn't found
     */
    suspend fun findTagByName(tagName: String): TagEntity? {
        return db.getTagDao().findByName(tagName)
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
    suspend fun addOrUpdateTag(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)? = null): Long {
        return db.getTagDao().add(tag, callback)
    }

    /**
     * Delete tag
     */
    suspend fun deleteSelectedTags() {
        db.getTagDao().deleteSelected()
    }

    /**
     * Add multiple tags to multiple books
     * @param bookIds An array of book ids. Null means use the selected books.
     * @param tagIds An array of tag ids. Null means use the selected tags
     */
    suspend fun addTagsToBooks(bookIds: Array<Any>?, tagIds: Array<Any>?, filter: BookFilter.BuiltFilter?) {
        db.getBookTagDao().addTagsToBooks(bookIds, tagIds, filter)
    }

    /**
     * Remove multiple tags from multiple books
     * @param bookIds An array of book ids. Null means use the selected books.
     * @param tagIds An array of tag ids. Null means use the selected tags
     * @param invert True to remove the tags that match tagIds. False to remove the tags not matched by tagIds.
     */
    suspend fun removeTagsFromBooks(bookIds: Array<Any>?, tagIds: Array<Any>?, filter: BookFilter.BuiltFilter?, invert: Boolean = false) {
        db.getBookTagDao().deleteSelectedTagsForBooks(bookIds, filter, tagIds, invert)
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
        return db.getBookDao().getThumbnail(bookId, large)
    }

    /**
     * Get a cursor from the database
     * @param query The SQL query
     * @param args The arguments for the query
     * @return The cursor
     * This method cannot be called from the main thread.
     */
    fun getCursor(query: String, args: Array<Any>? = null): Cursor {
        return db.query(query, args)
    }

    /**
     * Get the list of views from the database
     */
    suspend fun getViewNames(): LiveData<List<String>> {
        return db.getViewDao().getViewNames()
    }

    /**
     * Add or update a view to the database
     * @param view The view to add
     * @param onConflict A lambda function to respond to a conflict when adding. Return
     *                   true to accept the conflict or false to abort the add
     * @return The id of the view in the database, or 0L if the add was aborted
     */
    suspend fun addOrUpdateView(view: ViewEntity, onConflict: suspend CoroutineScope.(conflict: ViewEntity) -> Boolean): Long {
        return db.getViewDao().addOrUpdate(view, onConflict)
    }

    /**
     * Find a view by name
     * @param name The name of the view
     * @return The view or null if it wasn't found
     */
    suspend fun findViewByName(name: String): ViewEntity? {
        return db.getViewDao().findByName(name)
    }

    /**
     * Delete view
     * @param name The name of the view
     * @return The number of views deleted
     */
    suspend fun removeView(name: String): Int {
        return db.getViewDao().delete(name)
    }
}
