package com.github.cleveard.bibliotech.db

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.paging.PagingSource
import com.github.cleveard.bibliotech.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import java.util.*

/**
 * Application interface to the book database
 */
class BookRepository private constructor(context: Context) {
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
        suspend fun changeBits(operation: Boolean?, mask: Int, id: Long?, filter: BookFilter.BuiltFilter? = null): Int
        /**
         * Count bits in the flag column
         * @param bits The bits to count
         * @param value The value to count
         * @param include True to count values that match, false to include values that don't match
         * @param id The id of the row to change. Null to change all
         * @param filter A filter to restrict the rows
         * @return The number of rows matched
         */
        suspend fun countBits(bits: Int, value: Int, include: Boolean, id: Long?, filter: BookFilter.BuiltFilter?): Int
        /**
         * Count bits in the flag column
         * @param bits The bits to count
         * @param value The value to count
         * @param include True to count values that match, false to include values that don't match
         * @param id The id of the row to change. Null to change all
         * @param filter A filter to restrict the rows
         * @return The number of rows changed in a LiveData
         */
        fun countBitsLive(bits: Int, value: Int, include: Boolean, id: Long?, filter: BookFilter.BuiltFilter?): LiveData<Int>
    }

    /** Holder for filters and counters for a fragment */
    interface FiltersAndCounters {
        /** Count of item selected and filtered */
        val selectedCount: LiveData<Int>
        /** Count of items filtered */
        val itemCount: LiveData<Int>
        /** The name of the current view */
        val viewName: String?
        /** The current view entity */
        val viewEntity: LiveData<ViewEntity?>
        /**
         * Set the view name and build the new filters
         * @param name The new view name
         * @param context A context used to build the built filter
         */
        fun setViewName(name: String?, context: Context)
        /** Treatment of selection by the type ids and built filters */
        val selectedType: BookFilter.SelectionType
        /**
         * Set the filtering of selected items
         * @param value The filtering to use
         * @param context A context used to build the built filter
         */
        fun setSelectedType(value: BookFilter.SelectionType, context: Context)
    }

    /**
     * Holder for filters and counters for a fragment
     */
    inner class FilteredBookCount(
        /** The flags for the table */
        val flags: FlagsInterface,
        /** The selected mask for the table */
        val mask: Int,
        /** A scope to launch child jobs */
        val scope: CoroutineScope
    ): FiltersAndCounters {
        /** Generation of most recent filter */
        private var lastGeneration: Long = 0
        /** MutableStateFlow that contains the generation and book filter */
        private val _generationFlow: MutableStateFlow<Pair<Long, BookFilter?>> = MutableStateFlow(Pair(0, null))
        /** StateFlow that contains the generation and book filter */
        val generationFlow: StateFlow<Pair<Long, BookFilter?>> = _generationFlow
        /** The built filter for the current filter and selection type */
        private val _builtFilter: MutableLiveData<BookFilter.BuiltFilter?> = MutableLiveData<BookFilter.BuiltFilter?>()

        /** @inheritDoc */
        override val selectedCount: LiveData<Int> = _builtFilter.switchMap {
            // Count rows with selection mask set
            flags.countBitsLive(mask, mask, true, null, it)
        }

        /** @inheritDoc */
        override val itemCount: LiveData<Int> = _builtFilter.switchMap {
            // Count all rows
            flags.countBitsLive(0, 0, true, null, it)
        }

        /** The  built filter for the current filter and selection type */
        val builtFilter: LiveData<BookFilter.BuiltFilter?> = _builtFilter
        /** @inheritDoc */
        override var viewName: String? = null
            private set
        /** The current viewEntity */
        private val _viewEntity: MutableLiveData<ViewEntity?> = MutableLiveData()
        /** @inheritDoc */
        override val viewEntity: LiveData<ViewEntity?> = _viewEntity
        /** @inheritDoc */
        override fun setViewName(name: String?, context: Context) {
            viewName = name
            buildFilter(context, ++lastGeneration)
        }
        /** @inheritDoc */
        override var selectedType: BookFilter.SelectionType = BookFilter.SelectionType.EITHER
            private set
        /** @inheritDoc */
        override fun setSelectedType(value: BookFilter.SelectionType, context: Context) {
            selectedType = value
            buildFilter(context, ++lastGeneration)
        }

        init {
            // Force the counter switch maps to initialize
            _builtFilter.value = null
        }

        /**
         * Transform a generation flow
         */
        fun <T, R> transform(input: Flow<Pair<Long, T>>, trans: suspend CoroutineScope.(T) -> R): Flow<Pair<Long, R>> {
            // Job for the transform
            var deferred: Deferred<Pair<Long, R>>? = null
            // Cache for most recent value
            var cache: Pair<T, Pair<Long, R>>? = null
            // Transform the flow
            return input.transform inner@ {result ->
                // Only emit a value when the generations match
                if (result.first == lastGeneration) {
                    // If the cached value is the most recent value use it
                    cache?.let {c ->
                        // Cache is more recent than this result, use the cash
                        if (c.second.first >= result.first) {
                            emit(c.second)
                            return@inner
                        }
                        // If the value to be transformed is the same use the cache
                        if (c.first == result.second) {
                            Pair(c.first, Pair(result.first, c.second.second)).also {
                                // Cache it
                                cache = it
                                emit(it.second)
                                return@inner
                            }
                        }
                    }

                    // need to recreate the cache, cancel any running job
                    deferred?.cancel()
                    val scope = CoroutineScope(currentCoroutineContext())
                    // Start a new job
                    deferred = scope.async {
                        // Create a new cache with a transformed value
                        Pair(result.second, Pair(result.first, scope.trans(result.second))).let {
                            // If this value isn't the most recent value, emit the cache
                            if (it.second.first < (cache?.second?.first ?: Long.MIN_VALUE))
                                return@let cache!!.second
                            // Cache the new value
                            cache = it
                            // Emit the new value
                            it.second
                        }
                    }

                    try {
                        // Wait for the value
                        deferred?.await()
                    } catch (_: CancellationException) {
                        // Canceled - Don't emit anything
                        null
                    } finally {
                        // Cleared the deferred
                        deferred = null
                    }?.let {
                        // Emit the value
                        emit(it)
                    }
                }
            }
        }

        /**
         * Wait for a generation flow to be done
         * @param input The generation flow
         */
        suspend fun <T> awaitForResult(input: Flow<Pair<Long, T>>): T? {
            // Place to put the result
            var result: T? = null
            // Take while the result is out of date
            input.takeWhile {
                // Are we at the latest generation
                if (it.first == lastGeneration) {
                    // Good result. Set it and stop the collection
                    result = it.second
                    false
                } else
                    true    // Continue collecting
            }.collect()
            // Return the result
            return result
        }

        /**
         * Build the filters from the current viewEntity and selection type
         * @param context Context with locale used to parse dates
         * @param generation The generation of the filter we are building
         */
        private fun buildFilter(context: Context, generation: Long) {
            scope.launch {
                // Get the viewEntity and set it to the LiveData
                val filter = viewName?.let {name ->
                    findViewByName(name).also { _viewEntity.value = it}
                }
                    // Get the view entity filter
                    ?.filter
                    // Modify it based on the selectedType
                    .filterForSelectionType(selectedType)
                    ?.let { filter ->
                        // If the new filter is the same as the last one use the last one
                        if (filter.isSameQuery(generationFlow.value.second))
                            generationFlow.value.second
                        else
                            filter
                    }
                // Emit if this is the latest filter
                if (generation == lastGeneration) {
                    // If the filter changed, make a new built filter
                    if (generationFlow.value.second != filter) {
                        _builtFilter.value = filter.buildFilter(context, arrayOf(BOOK_ID_COLUMN), true)
                    }
                    // Emit the filter
                    _generationFlow.tryEmit(Pair(generation, filter))
                }
            }
        }
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
         * @param testing True to create a database for testing
         * @param name The name of the database. Use null for the default name or in-memory if testing is true
         */
        fun initialize(context: Context, testing: Boolean = false, name: String? = null) {
            // Only initialize if it has been closed
            if (mRepo == null) {
                // Initialize the database
                BookDatabase.initialize(context, testing, name)
                // Create the repository
                mRepo = BookRepository(context)
            }
        }

        /**
         * Close the BookRepository
         * @param context The context for testing to use to delete the database
         */
        fun close(context: Context) {
            mRepo?.also{
                mRepo = null
            }
            BookDatabase.close(context)
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
        ): Int {
            return db.getBookDao().changeBits(operation, mask, id, filter)
        }

        /** inheritDoc **/
        override suspend fun countBits(
            bits: Int,
            value: Int,
            include: Boolean,
            id: Long?,
            filter: BookFilter.BuiltFilter?
        ): Int {
            return db.getBookDao().countBits(bits, value, include, id, filter)
        }

        /** inheritDoc **/
        override fun countBitsLive(
            bits: Int,
            value: Int,
            include: Boolean,
            id: Long?,
            filter: BookFilter.BuiltFilter?
        ): LiveData<Int> {
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
        ): Int {
            return db.getTagDao().changeBits(operation, mask, id)
        }

        /** inheritDoc **/
        override suspend fun countBits(
            bits: Int,
            value: Int,
            include: Boolean,
            id: Long?,
            filter: BookFilter.BuiltFilter?
        ): Int {
            return db.getTagDao().countBits(bits, value, include, id)
        }

        /** inheritDoc **/
        override fun countBitsLive(
            bits: Int,
            value: Int,
            include: Boolean,
            id: Long?,
            filter: BookFilter.BuiltFilter?
        ): LiveData<Int> {
            return db.getTagDao().countBitsLive(bits, value, include, id)
        }
    }

    // Undo description strings
    private val addBook = context.resources.getString(R.string.addBookUndo)
    private val deleteBooks = context.resources.getString(R.string.deleteBooksUndo)
    private val addTag = context.resources.getString(R.string.addTagUndo)
    private val deleteTags = context.resources.getString(R.string.deleteTagsUndo)
    private val tagBooks = context.resources.getString(R.string.tagBooksUndo)
    private val untagBooks = context.resources.getString(R.string.untagBooksUndo)
    private val addView = context.resources.getString(R.string.addViewUndo)
    private val deleteView = context.resources.getString(R.string.deleteViewUndo)
    private val locale = context.resources.configuration.locales[0]

    private fun format(format: String, vararg args: Any): String {
        return Formatter(locale).format(format, *args).toString()
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
     * Get books from the data base
     * @return A PagingSource containing the books
     */
    suspend fun getBookList(): LiveData<List<BookAndAuthors>> {
        return db.getBookDao().getBookList()
    }

    /**
     * Get books from the data base
     * @param filter Description of a filter that filters and orders the books
     * @param context A context used to interpret dates in the filter
     * @return A PagingSource containing the books
     */
    suspend fun getBookList(filter: BookFilter, context: Context): LiveData<List<BookAndAuthors>> {
        return db.getBookDao().getBookList(filter, context)
    }

    /**
     * Add a book to the database or update a book already there
     * @param book The book to add or update
     * @param callback Callback used to resolve conflicts
     * The book is added and selected tags
     */
    suspend fun addOrUpdateBook(book: BookAndAuthors, callback: (suspend CoroutineScope.(conflict: BookAndAuthors) -> Boolean)? = null): Long {
        return withUndo(format(addBook, book.book.title)) {
            db.getBookDao().addOrUpdateWithUndo(book, null, callback)
        }
    }

    /**
     * Delete books from the book database
     * @param filter The current filter
     */
    suspend fun deleteSelectedBooks(filter: BookFilter.BuiltFilter?, bookIds: Array<Any>? = null): Int {
        return withUndo(deleteBooks) {
            db.getBookDao().deleteSelectedWithUndo(filter, bookIds)
        }
    }

    suspend fun getBooksWithoutSeriesUpdate(): List<BookAndAuthors>? {
        return db.getBookDao().getBooksWithoutSeriesUpdate()
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
        return withUndo(format(addTag, tag.name)) {
            db.getTagDao().addWithUndo(tag, callback)
        }
    }

    /**
     * Delete selected tags
     */
    suspend fun deleteSelectedTags(): Int {
        return withUndo(deleteTags) {
            db.getTagDao().deleteSelectedWithUndo()
        }
    }

    /**
     * Add multiple tags to multiple books
     * @param bookIds An array of book ids. Null means use the selected books.
     * @param tagIds An array of tag ids. Null means use the selected tags
     * @return The number of links added
     */
    suspend fun addTagsToBooks(bookIds: Array<Any>?, tagIds: Array<Any>?, filter: BookFilter.BuiltFilter?): Int {
        return withUndo(tagBooks) {
            db.getBookTagDao().addTagsToBooksWithUndo(bookIds, tagIds, filter)
        }
    }

    /**
     * Remove multiple tags from multiple books
     * @param bookIds An array of book ids. Null means use the selected books.
     * @param tagIds An array of tag ids. Null means use the selected tags
     * @param invert True to remove the tags that match tagIds. False to remove the tags not matched by tagIds.
     * @return The number of links removed
     */
    suspend fun removeTagsFromBooks(bookIds: Array<Any>?, tagIds: Array<Any>?, filter: BookFilter.BuiltFilter?, invert: Boolean = false): Int {
        return withUndo(untagBooks) {
            db.getBookTagDao().deleteSelectedTagsForBooksWithUndo(bookIds, filter, tagIds, invert)
        }
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
    fun getViewNames(): LiveData<List<String>> {
        return db.getViewDao().getViewNames()
    }

    /**
     * Add or update a view to the database
     * @param view The view to add
     * @param onConflict A lambda function to respond to a conflict when adding. Return
     *                   true to accept the conflict or false to abort the add
     * @return The id of the view in the database, or 0L if the add was aborted
     */
    suspend fun addOrUpdateView(view: ViewEntity, onConflict: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)? = null): Long {
        return withUndo(format(addView, view.name)) {
            db.getViewDao().addOrUpdateWithUndo(view, onConflict)
        }
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
     * Find a view by name
     * @param name The name of the view
     * @return LiveData of a list containing the view
     */
    fun findViewByNameLive(name: String): LiveData<List<ViewEntity>> {
        return db.getViewDao().findByNameLive(name)
    }

    /**
     * Delete view
     * @param name The name of the view
     * @return The number of views deleted
     */
    suspend fun removeView(name: String): Int {
        return withUndo(deleteView) {
            db.getViewDao().deleteWithUndo(name)
        }
    }

    /**
     * Find a series in the database using the series id
     * @param seriesId The series id
     * @return The SeriesEntity or null if not found
     */
    suspend fun findSeriesBySeriesId(seriesId: String): SeriesEntity? {
        return db.getSeriesDao().findSeriesBySeriesId(seriesId)
    }

    /** The current maximum undo levels kept in the database */
    val maxUndoLevels
        get() = db.getUndoRedoDao().maxUndoLevels

    /**
     * Set the maximum undo levels kept in the data base
     * @param level The new maximum
     * @param reset Undo id where ids are reset to start at 1
     */
    suspend fun setMaxUndoLevels(level: Int, reset:Int = 0) =
        db.getUndoRedoDao().setMaxUndoLevels(level, reset)

    /**
     * Start recording undo
     * @param desc A localized description of the operation for the UI
     * @param operation The operation to run while recording undo
     * WithUndo can be nested. Recording stops when the outermost call returns.
     */
    suspend fun <T> withUndo(desc: String, operation: suspend () -> T): T {
        return db.getUndoRedoDao().withUndo(desc, null, operation)
    }

    /** Is there an undo recorded that can be undone */
    fun canUndo(): Boolean = db.getUndoRedoDao().canUndo()
    /** Is there an undo recorded that can be redone */
    fun canRedo(): Boolean = db.getUndoRedoDao().canRedo()

    /** Get the list of undo transaction */
    fun getUndoList(): LiveData<List<UndoTransactionEntity>> {
        return db.getUndoRedoDao().getTransactionsLive()
    }

    /** Clear all undo */
    suspend fun clearUndo() {
        db.getUndoRedoDao().clear()
    }

    /**
     * Undo a recording
     * @return True if there was a recording that could be undone
     */
    suspend fun undo(): Boolean {
        return db.getUndoRedoDao().undo()
    }

    /**
     * Redo a recording
     * @return True if there was a recording that could be redone
     */
    suspend fun redo(): Boolean {
        return db.getUndoRedoDao().redo()
    }

    /**
     * Get all shelves
     * @return PagingSource containing the shelves
     */
    fun getShelves(): PagingSource<Int, BookshelfAndTag> {
        return db.getBookshelvesDao().getShelvesAndTags()
    }

    suspend fun getShelfAndTag(id: Long): BookshelfAndTag? {
        return db.getBookshelvesDao().getShelfAndTag(id)
    }

    suspend fun updateBookshelf(bookshelf: BookshelfEntity): Boolean {
        return db.getBookshelvesDao().updateBookshelf(bookshelf)
    }

    suspend fun getBookShelves(): List<BookshelfAndTag> {
        return db.getBookshelvesDao().getShelves()
    }

    suspend fun addBookshelf(bookshelf: BookshelfEntity): Long {
        return db.getBookshelvesDao().addWithUndo(bookshelf)
    }

    suspend fun removeBookshelf(bookshelf: BookshelfAndTag): Int {
        return db.getBookshelvesDao().deleteWithUndo(bookshelf)
    }
}
