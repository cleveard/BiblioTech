package com.github.cleveard.bibliotech.testutils

import com.github.cleveard.bibliotech.db.*
import com.github.cleveard.bibliotech.utils.getLive
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CoroutineScope
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.random.Random

/**
 * Compare two books
 * @param actual The book from the database
 * @param expected The expected value
 */
fun StandardSubjectBuilder.compareBooks(actual: BookAndAuthors?, expected: BookAndAuthors?) {
    // If either are null, just use isEqualTo
    if (actual == null || expected == null)
        that(actual).isEqualTo(expected)
    else {
        // The book entities need to be the same, and so do the lists, but not
        // necessarily in the same order
        that(actual.book).isEqualTo(expected.book)
        that(actual.authors).containsExactlyElementsIn(expected.authors)
        that(actual.categories).containsExactlyElementsIn(expected.categories)
        that(actual.tags).containsExactlyElementsIn(expected.tags)
        that(actual.isbns).containsExactlyElementsIn(expected.isbns)
        that(actual.series).isEqualTo(expected.series)
    }

}

/**
 * Class to track the contents of the database
 * @param db The database
 * @param seed Seed for a random number generator
 */
abstract class BookDbTracker(val db: BookDatabase, seed: Long) {
    /**
     * Get a tag by id
     * @param tagId The tag id
     */
    protected abstract suspend fun getTag(tagId: Long): TagEntity?

    /**
     * Get the list of books that aren't hidden
     */
    protected abstract suspend fun getBooks(): List<BookAndAuthors>

    /**
     * Get the list of tags that aren't hidden
     */
    protected abstract suspend fun getTags(): List<TagEntity>

    /**
     * Add or update a book
     * @param book The book to be added or updated
     * @param tags Ids for additional tags to add to the book
     * @return The additional tags
     */
    protected abstract suspend fun addOrUpdate(book: BookAndAuthors, tags: Array<Any>?): Array<Any>?

    /**
     * Add or update a tag
     * @param tag The tag to be added or updated
     * @param callback Callback to decide whether to update conflicts
     * @return The id of the tag added or updated. 0 if it fails
     */
    protected abstract suspend fun addOrUpdateTag(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)? = null): Long

    /**
     * Add or update a view
     * @param view The view to be added or updated
     * @param onConflict Callback to decide whether to update conflicts
     * @return The id of the view added or updated. 0 if it fails
     */
    protected abstract suspend fun addOrUpdateView(view: ViewEntity, onConflict: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)?): Long

    /**
     * Find a tag by name
     * @param name The name to search for
     * @return The TagEntity or null
     */
    protected abstract suspend fun findTagByName(name: String): TagEntity?

    /**
     * Find a view by name
     * @param name The name to search for
     * @return The ViewEntity or null
     */
    protected abstract suspend fun findViewByName(name: String): ViewEntity?

    /**
     * Undo the last operation
     * @return True if something was undone
     */
    protected abstract suspend fun undo(): Boolean

    /**
     * Redo the last undo
     * @return True if something was redone
     */
    protected abstract suspend fun redo(): Boolean

    /**
     * The maximum number of undo levels
     */
    protected abstract val undoLevels: Int

    /** Undo tracked to track undo transactions */
    val undoTracker: UndoTracker = UndoTracker(db.getUndoRedoDao())
    /** Random number generator */
    val random = Random(seed)

    /** The expected contents of the database tables */
    var tables = Tables()
    /** The saved contents of the database for each undo transaction */
    private val undoneState = UndoTracker.ArrayListRemoveRange<Tables>()
    /** The saved contents of the database for each redo transaction */
    private val redoneState = UndoTracker.ArrayListRemoveRange<Tables>()

    /**
     * Get a book by id
     * @param bookId The id
     * @return The BookAndAuthors or null
     */
    private suspend fun getBook(bookId: Long): BookAndAuthors? {
        return db.getBookDao().getBook(bookId)
    }

    /**
     * Get a list of all authors that aren't hidden
     */
    private suspend fun getAuthors(): List<AuthorEntity> {
        return db.getAuthorDao().get()?: emptyList()
    }

    /**
     * Get a list of all categories that aren't hidden
     */
    private suspend fun getCategories(): List<CategoryEntity> {
        return db.getCategoryDao().get()?: emptyList()
    }

    /**
     * Get a list of all views that aren't hidden
     */
    private suspend fun getViews(): List<ViewEntity> {
        return db.getViewDao().getViews()
    }

    /**
     * Get a list of all isbns that aren't hidden
     */
    private suspend fun getIsbns(): List<IsbnEntity> {
        return db.getIsbnDao().get()?: emptyList()
    }

    /**
     * Get a list of all series that aren't hidden
     */
    private suspend fun getSeries(): List<SeriesEntity> {
        return db.getSeriesDao().get()
    }

    /**
     * Add a tag to the database
     * @param tag The tag to add
     * @param callback A callback to decide whether to update or reject conflicts
     * @return The id of the tag added or updated
     */
    suspend fun addTag(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)? = null): Long {
        // Add or update the tag
        return addOrUpdateTag(tag, callback).also {
            // If it was successful, put it in the expected values
            if (it != 0L) {
                tables.tagEntities.linked(tag)
                tables.tagEntities.unlinked(tag)
            }
        }
    }

    /**
     * Add a tag with specific flags
     * @param flags The flags to assign to the tag
     * @return The id of the tag added
     */
    private suspend fun addTag(flags: Int = 0): Long {
        // Add the tag, and reject conflicts
        return addTag(tables.tagEntities.new(flags)).also {
            // Don't expect a conflict here
            assertWithMessage("AddTag").that(it).isNotEqualTo(0L)
        }
    }

    /**
     * Add a view to the database
     * @param view The view to add
     * @param callback A callback to decide whether to update or reject conflicts
     * @return The id of the view added or updated
     */
    suspend fun addView(view: ViewEntity, callback: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)? = null): Long {
        // Add the view
        return addOrUpdateView(view, callback).also {
            // If it was successful, add it to the expected values
            if (it != 0L) {
                tables.viewEntities.linked(view)
            }
        }
    }

    /**
     * Check some consistency in the expected values
     * @param message A message for assertion failures
     * The tracker keeps a count of links to tags, authors, categories and isbns. This method checks to make sure the counts
     * match the links in the books. It is called when initializing the database with books. When I first wrote this
     * class there were bugs that messed up the count and caused errors later on. This just catches those bugs.
     */
    private fun checkConsistency(message: String) {
        // Get the sequence of books. We map to a sequence of sequences of tag/authors/categories/isbns
        // and count how many times each tag is in the flattened sequence. These counts should equal
        // the link counts we are tracking.
        val bookSequence = tables.bookEntities.entities
        tables.tagEntities.checkCounts("$message: Tags", bookSequence.map { it.tags.asSequence() }.flatten()) { it.name }
        tables.authorEntities.checkCounts("$message: Authors", bookSequence.map { it.authors.asSequence() }.flatten()) { it.name }
        tables.categoryEntities.checkCounts("$message: Categories", bookSequence.map { it.categories.asSequence() }.flatten()) { it.category }
        tables.isbnEntities.checkCounts("$message: Isbns", bookSequence.map { it.isbns.asSequence() }.flatten()) { it.isbn }
        tables.seriesEntities.checkCounts("$message: Series", bookSequence.map { it.series }.filterNotNull()) { it.title }
    }

    /**
     * Compare the expected database with the actual one
     * @param message A message for assertion failures
     * @param args Arguments for %s in message
     */
    suspend fun checkDatabase(message: String, vararg args: Any) {
        assertWithMessage("Check Database: $message", *args).apply {
            // Get the books from the database and sort by id
            val actual = getBooks().sortedBy { it.book.id }
            // Get the expected books and sort by id
            val expected = tables.bookEntities.entities.toList().sortedBy { it.book.id }
            // Make sure they are the same. If the lists are different sizes, fail when the shorter one ends.
            for (i in 0 until expected.size.coerceAtLeast(actual.size)) {
                compareBooks(
                    if (i >= actual.size) null else actual[i],
                    if (i >= expected.size) null else expected[i]
                )
            }
            // Make sure the actual tags, authors, categories and views are the expected values
            that(getTags()).containsExactlyElementsIn(tables.tagEntities.entities.toList())
            that(getAuthors()).containsExactlyElementsIn(tables.authorEntities.entities.toList())
            that(getCategories()).containsExactlyElementsIn(tables.categoryEntities.entities.toList())
            that(getViews()).containsExactlyElementsIn(tables.viewEntities.entities.toList())
            that(getIsbns()).containsExactlyElementsIn(tables.isbnEntities.entities.toList())
            that(getSeries()).containsExactlyElementsIn(tables.seriesEntities.entities.toList())
        }
    }

    /**
     * Add a book with set flags
     * @param message Message for assertion failures
     * @param flags The flags for the book
     * @param isUpdate True if we expect this call to be an update
     */
    private suspend fun addOneBook(message: String, flags: Int = 0, isUpdate: Boolean = false) {
        // Make a new book and add it
        val book = tables.bookEntities.new(flags)
        addOneBook(message, book, isUpdate)
    }

    /**
     * Add a book with set flags
     * @param message Message for assertion failures
     * @param book The book to add
     * @param isUpdate True if we expect this call to be an update
     */
    suspend fun addOneBook(message: String, book: BookAndAuthors, isUpdate: Boolean = false) {
        // Add 0 to 3 random authors
        book.authors = tables.authorEntities.createRelationList(3, random)
        // Add 0 to 3 random categories
        book.categories = tables.categoryEntities.createRelationList(3, random)
        // Add 0 to 4 random additional tags
        val tagCount = random.nextInt(5).coerceAtMost(tables.tagEntities.size)
        var tags: Array<Any>?
        if (tagCount != 0 || random.nextBoolean()) {
            tags = Array<Any>(random.nextInt(5)) { 0L }.also {
                repeat(it.size) { i ->
                    var id: Long
                    do {
                        id = tables.tagEntities[random.nextInt(tables.tagEntities.size)].id
                    } while (id == 0L || it.contains(id))
                    it[i] = id
                }
            }
        } else
            tags = null
        // Add 0 to 2 random tags
        book.tags = tables.tagEntities.createRelationList(2, random)
        // Add 0 to 3 random isbns
        book.isbns = tables.isbnEntities.createRelationList(3, random)
        // Add options series
        book.series = if (!random.nextBoolean()) null else tables.seriesEntities.createRelation(random.nextBoolean(), random)

        assertWithMessage("%s: Add %s", message, book.book.title).apply {
            // Add the book
            tags = addOrUpdate(book, tags)
            // Expect the add/update to work
            that(book.book.id).isNotEqualTo(0L)
            // Expect the series to be consistent
            book.series?.also {
                // If the series is not null, expect that the series id and order number are not null
                that(book.book.seriesId).isEqualTo(it.id)
                that(book.book.seriesOrder).isNotNull()
            }?: run {
                // If the series is null, expect that the series id and order number are null
                that(book.book.seriesId).isNull()
                that(book.book.seriesOrder).isNull()
            }
            // Combine the additional tags with the tags in the book. If tags, is null, then
            // the additional tags are the selected tags
            book.tags = (tags?: tables.tagEntities.entities.filter { it.isSelected }.map { it.id }.toList().toTypedArray()).let {
                // it is either the original tags array, or an array of selected tags
                // Start the books tag list with the tags from the book
                val newTags = ArrayList<TagEntity>(book.tags)
                // Add in any additional tags from it
                for (id in it) {
                    val tag = getTag(id as Long)
                    if (tag != null && !book.tags.contains(tag))
                        newTags.add(tag)
                }
                newTags
            }

            // We need to know whether this is an update or not, to keep the link counts correct
            if (isUpdate)
                updateBook(book)
            else
                linkBook(book)

            // Undo and redo the add and make sure it is all OK
            val label = "$message: Add ${book.book.title}"
            undoAndCheck(label)
            redoAndCheck(label)
            // Compare the book from the database with the book we added.
            compareBooks(getBook(book.book.id), book)
        }
    }

    /**
     * Link all of the related entities for a book
     * @param book The book
     */
    private fun linkBook(book: BookAndAuthors) {
        tables.tagEntities.linkRelation(book.tags)
        tables.categoryEntities.linkRelation(book.categories)
        tables.authorEntities.linkRelation(book.authors)
        tables.isbnEntities.linkRelation(book.isbns)
        book.series?.let { tables.seriesEntities.linked(it) }
        tables.bookEntities.linked(book)
    }

    /**
     * Unlink all of the related entities for a book
     * @param book The book
     */
    fun unlinkBook(book: BookAndAuthors) {
        tables.tagEntities.unlinkRelation(book.tags)
        tables.categoryEntities.unlinkRelation(book.categories)
        tables.authorEntities.unlinkRelation(book.authors)
        tables.isbnEntities.unlinkRelation(book.isbns)
        book.series?.let { tables.seriesEntities.unlinked(it) }
        tables.bookEntities.unlinked(book)
    }

    /**
     * Unlink previous related entities and link the current ones when updating a book
     * @param book The book
     */
    private fun updateBook(book: BookAndAuthors) {
        // Get the previous book
        val prev = tables.bookEntities.linked(book)
        if (prev != null) {
            // Update the links
            tables.tagEntities.updateRelation(prev.tags, book.tags)
            tables.categoryEntities.updateRelation(prev.categories, book.categories)
            tables.authorEntities.updateRelation(prev.authors, book.authors)
            tables.isbnEntities.updateRelation(prev.isbns, book.isbns)
            if (prev.series != book.series) {
                prev.series?.let { tables.seriesEntities.unlinked(it) }
                book.series?.let { tables.seriesEntities.linked(it) }
            }
        } else
            linkBook(book) // No previous, just link in the new book
    }

    /**
     * Test undoes and redoes randomly
     * @param message A message for assertion failures
     * @param count The number of times to test
     */
    suspend fun testRandomUndo(message: String, count: Int) {
        val dao = db.getUndoRedoDao()
        repeat(count) {
            // Randomly undo or redo
            if (random.nextBoolean()) {
                undoAndCheck("%s: min: %s, undo: %s, max: %s", message, dao.minUndoId, dao.undoId, dao.maxUndoId)
            } else {
                redoAndCheck("%s: min: %s, undo: %s, max: %s", message, dao.minUndoId, dao.undoId, dao.maxUndoId)
            }
        }
        // Redo until the database is restored.
        @Suppress("ControlFlowWithEmptyBody")
        while (redoAndCheck("%s: min: %s, undo: %s, max: %s", message, dao.minUndoId, dao.undoId, dao.maxUndoId)) {
        }
    }

    /**
     * Add multiple books to the database
     * @param message A message for assertion failures
     * @param count Number of books to add
     * @return The BookDbTracker
     */
    suspend fun addBooks(message:String, count: Int): BookDbTracker {
        repeat (count) {
            // Add each book randomly setting flags
            addOneBook(message, random.nextFlags(BookEntity.SELECTED or BookEntity.EXPANDED or BookEntity.SERIES), false)
        }
        // Make sure the tracker is consistent
        checkConsistency(message)
        // Randomly do some undoes and redoes
        testRandomUndo(message, 10)
        return this
    }

    /**
     * Add a fixed set of tags to the database
     * @param message A message for assertion failures
     * Also check that we can undo and redo each add
     */
    suspend fun addTags(message: String): BookDbTracker {
        addTag().also {
            undoAndCheck(message)
            redoAndCheck(message)
        }
        addTag(TagEntity.SELECTED).also {
            undoAndCheck(message)
            redoAndCheck(message)
        }
        addTag(TagEntity.SELECTED).also {
            undoAndCheck(message)
            redoAndCheck(message)
        }
        addTag(0).also {
            undoAndCheck(message)
            redoAndCheck(message)
        }
        return this
    }

    /**
     * Create a filter for books
     * The filter filters on a random set of titles
     */
    fun makeFilter(): BookFilter {
        val values = ArrayList<String>()
        // Get a set of selected or unselected titles
        fun getValues(count: Int, selected: Boolean) {
            repeat(count) {
                var book: BookAndAuthors
                do {
                    book = tables.bookEntities[random.nextInt(tables.bookEntities.size)]
                } while (book.book.isSelected != selected || values.contains(book.book.title))
                values.add(book.book.title)
            }
        }
        // Count how many selected books we want to get. The smaller of count of selected books or 3
        val selected = tables.bookEntities.entities.filter { it.book.isSelected }.count().coerceAtMost(3)
        // Get selected books
        getValues(selected, true)
        // Fill in to 5 titles with unselected books
        getValues(5 - selected, false)
        // Return the book filter
        return BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, values.toTypedArray())
        ))
    }

    /**
     * Called to save the expected database state when starting to record undo
     */
    fun undoStarted() {
        if (undoLevels > 0 && !db.getUndoRedoDao().isRecording) {
            // We are undoing and not recording, so save the expected values
            undoneState.add(tables.copy())
            // Clear any expected redo values
            redoneState.clear()
        }
    }

    /**
     * Called to finished up after recording an undo transaction
     * @param message A message for assertion failures
     * @param succeeded True if the operation we were recording succeeded
     */
    suspend fun undoEnded(message: String, succeeded: Boolean) {
        if (undoLevels > 0 && !db.getUndoRedoDao().isRecording) {
            // OK, undo is enable and we aren't recording
            if (!succeeded) {
                // Didn't succeed, so remove the last values saved in undoStarted
                undoneState.removeLast()
            } else {
                // It did work
                val state = undoneState.last()
                // When an entity is deleted, its flags are cleared. Clear these
                // flags in the state we saved
                state.clearFlags(tables)
                // Check the undo transaction in the UndoTracker
                undoTracker.record(message)
                // Remove states that are past the maximum
                if (undoneState.size > undoLevels)
                    undoneState.removeRange(0, undoneState.size - undoLevels)
            }
        }
    }

    /**
     * Respond to changing the maximum number of undo levels
     */
    fun undoLevelsChanged() {
        // Let the tracker know they changed
        undoTracker.undoLevelsChanged(undoLevels)
        if (undoneState.size + redoneState.size > undoLevels) {
            // Remove undoes and redoes as needed
            undoneState.removeRange(0, (undoneState.size + redoneState.size - undoLevels).coerceAtMost(undoneState.size))
            if (redoneState.size > undoLevels) {
                redoneState.removeRange(undoLevels, redoneState.size)
            }
        }
    }

    /**
     * Do an undo and check that the data base is correct
     * @param message A message for assertion errors
     * @param args Arguments to replace %s in message
     * @return True if the undo was successful
     */
    suspend fun undoAndCheck(message: String, vararg args: Any): Boolean {
        // If we are recording undo is not successful
        if (db.getUndoRedoDao().isRecording)
            return false
        // The label used for the check
        val label = "Undo and check $message"
        // Get the expected result of the undo
        val canUndo = undoneState.isNotEmpty()
        assertWithMessage(label, *args).apply {
            // Make sure the undo acts as expected
            that(undo()).isEqualTo(canUndo)
            // Make sure the undo tracker is in sync
            that(undoTracker.undo()).isEqualTo(canUndo)
            if (undoneState.isNotEmpty()) {
                val state = undoneState.removeLast()
                // Move flags from the current state to the undo state
                state.swapTables(tables)
                // Make the current state the redo state
                redoneState.add(tables)
                // Make the undo state the current state
                tables = state
            }
            // Verify that the database is correct
            checkDatabase(label, *args)
            // Verify that the undo tables are correct
            undoTracker.checkUndo(label, *args)
        }
        return canUndo
    }

    /**
     * Do a redo and check that the data base is correct
     * @param message A message for assertion errors
     * @param args Arguments to replace %s in message
     * @return True if the undo was successful
     */
    suspend fun redoAndCheck(message: String, vararg args: Any): Boolean {
        // If we are recording, redo is not successful
        if (db.getUndoRedoDao().isRecording)
            return false
        // Message for assertion errors
        val label = "Redo and check $message"
        // Get expected redo result
        val canRedo = redoneState.isNotEmpty()
        assertWithMessage(label, *args).apply {
            // Redo and make sure it worked as expected
            that(redo()).isEqualTo(canRedo)
            // Make sure the undo tracker is in sync
            that(undoTracker.redo()).isEqualTo(canRedo)
            if (redoneState.isNotEmpty()) {
                val state = redoneState.removeLast()
                // Move current flags to redo state
                state.swapTables(tables)
                // Make the current state the undo state
                undoneState.add(tables)
                // Make the redo state the current state
                tables = state
            }
            // Check the database
            checkDatabase(label, *args)
            // Check the undo tables
            undoTracker.checkUndo(label, *args)
        }
        return canRedo
    }

    companion object {
        /**
         * Create a BookDBTracker and add some books
         * @param _db The database
         * @param seed A seed for the random number generator
         * @param message A message for assertion failures while adding the initial set of books
         * @param count The number of books to add initially to the database
         */
        suspend fun addBooks(_db: BookDatabase, seed: Long, message: String, count: Int): BookDbTracker {
            // Create the tracker and override the abstract methods using implementations
            // that access the database directly
            return object: BookDbTracker(_db, seed) {
                /** @inheritDoc */
                override suspend fun getTag(tagId: Long): TagEntity? {
                    return db.getTagDao().get(tagId)
                }

                /** @inheritDoc */
                override suspend fun getBooks(): List<BookAndAuthors> {
                    return db.getBookDao().getBookList().getLive()?: emptyList()
                }

                /** @inheritDoc */
                override suspend fun getTags(): List<TagEntity> {
                    return db.getTagDao().getLive(false).getLive()?: emptyList()                }

                /** @inheritDoc */
                override suspend fun addOrUpdate(book: BookAndAuthors, tags: Array<Any>?): Array<Any>? {
                    // Enable undo
                    undoStarted()
                    db.getUndoRedoDao().withUndo("Add or Update Book") {
                        db.getBookDao().addOrUpdateWithUndo(book, tags)
                    }.also {
                        undoEnded("Add or Update Book", it != 0L)
                    }
                    return tags
                }

                /** @inheritDoc */
                override suspend fun addOrUpdateTag(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)?): Long {
                    // Enable Undo
                    undoStarted()
                    return db.getUndoRedoDao().withUndo("Add Tag") {
                        db.getTagDao().addWithUndo(tag, callback)
                    }.also {
                        undoEnded("Add Tag", it != 0L)
                    }
                }

                /** @inheritDoc */
                override suspend fun addOrUpdateView(view: ViewEntity, onConflict: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)?): Long {
                    // Enable Undo
                    undoStarted()
                    return db.getUndoRedoDao().withUndo("Add View") {
                        db.getViewDao().addOrUpdateWithUndo(view, onConflict)
                    }.also {
                        undoEnded("Add View", it != 0L)
                    }
                }

                /** @inheritDoc */
                override suspend fun findTagByName(name: String): TagEntity? {
                    return db.getTagDao().findByName(name)
                }

                /** @inheritDoc */
                override suspend fun findViewByName(name: String): ViewEntity? {
                    return db.getViewDao().findByName(name)
                }

                /** @inheritDoc */
                override suspend fun undo(): Boolean {
                    return db.getUndoRedoDao().undo()
                }

                /** @inheritDoc */
                override suspend fun redo(): Boolean {
                    return db.getUndoRedoDao().redo()
                }

                /** @inheritDoc */
                override val undoLevels: Int
                    get() { return db.getUndoRedoDao().maxUndoLevels }
            }.apply {
                // Clear the any undo state in the database
                if (!db.getUndoRedoDao().isRecording)
                    undoTracker.clearUndo(message)
                // If we are adding books, then add some tags and books
                if (count > 0)
                    addTags(message).addBooks(message, count)
            }
        }

        /**
         * Create a BookDBTracker and add some books
         * @param repo The book repository
         * @param seed A seed for the random number generator
         * @param message A message for assertion failures while adding the initial set of books
         * @param count The number of books to add initially to the database
         */
        suspend fun addBooks(repo: BookRepository, seed: Long, message: String, count: Int): BookDbTracker {
            // Create the BookDbTracker and override the abstract methods with implementations
            // that use the book repository
            return object: BookDbTracker(BookDatabase.db, seed) {
                /** @inheritDoc */
                override suspend fun getTag(tagId: Long): TagEntity? {
                    return repo.getTag(tagId)
                }

                /** @inheritDoc */
                override suspend fun getBooks(): List<BookAndAuthors> {
                    return repo.getBookList().getLive()?: emptyList()
                }

                /** @inheritDoc */
                override suspend fun getTags(): List<TagEntity> {
                    return repo.getTagsLive(false).getLive()?: emptyList()
                }

                /** @inheritDoc */
                override suspend fun addOrUpdate(book: BookAndAuthors, tags: Array<Any>?): Array<Any>? {
                    undoStarted()
                    repo.addOrUpdateBook(book).also {
                        undoEnded("Add or Update Book", it != 0L)
                    }
                    return null
                }

                /** @inheritDoc */
                override suspend fun addOrUpdateTag(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)?): Long {
                    undoStarted()
                    return repo.addOrUpdateTag(tag, callback).also {
                        undoEnded("Add Tag", it != 0L)
                    }
                }

                /** @inheritDoc */
                override suspend fun addOrUpdateView(view: ViewEntity, onConflict: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)?): Long {
                    undoStarted()
                    return repo.addOrUpdateView(view, onConflict).also {
                        undoEnded("Add View", it != 0L)
                    }
                }

                /** @inheritDoc */
                override suspend fun findTagByName(name: String): TagEntity? {
                    return repo.findTagByName(name)
                }

                /** @inheritDoc */
                override suspend fun findViewByName(name: String): ViewEntity? {
                    return repo.findViewByName(name)
                }

                /** @inheritDoc */
                override suspend fun undo(): Boolean {
                    return repo.undo()
                }

                /** @inheritDoc */
                override suspend fun redo(): Boolean {
                    return repo.redo()
                }

                /** @inheritDoc */
                override val undoLevels: Int
                    get() = repo.maxUndoLevels
            }.apply {
                // Clear all undo if we can
                if (!db.getUndoRedoDao().isRecording)
                    undoTracker.clearUndo(message)
                // If we want books at the start add some tags and books
                if (count > 0)
                    addTags(message).addBooks(message, count)
            }
        }

        /**
         * Randomly set flags bits
         * @param mask The mask of bits to randomly set
         * @return The random flags
         */
        fun Random.nextFlags(mask: Int): Int {
            // Get the random patter
            var ran = nextInt(1 shl mask.countOneBits()).toUInt()
            var result = mask.toUInt()
            var bit = 1u
            // Loop through the bits in the mask
            while (bit > 0u && result >= bit) {
                // If the current bit is on in the mask
                if ((result and bit) != 0u) {
                    // If the next random bit is off
                    // then clear the bit in the result
                    if ((ran and 1u) == 0u)
                        result = result xor bit
                    // Go to the next random bit
                    ran = ran shr 1
                }
                // Go to the next flag bit
                bit = bit shl 1
            }
            // return the result
            return result.toInt()
        }
    }

    /**
     * Class that keeps track of a single table in the database
     * @param T The entity type for the table
     * @param make A lambda to make a new entity
     * @param setId A lambda to set the id of one entity from another
     * @param sameId A lambda to determine when the ids for two entities are the same
     * @param autoDelete True if entities are deleted from the table when the link count goes to 0
     *
     * The Comparator is used to check for conflicting entities
     */
    abstract class Table<T>(
        protected val make: (flags: Int, next: Int, unique: String) -> T,
        protected val setId: (entity: T, prev: T?) -> Unit,
        protected val sameId: (e1: T, e2: T) -> Boolean,
        private val autoDelete: Boolean = true
    ): Comparator<T> {
        /** Class to hold an entity and link count */
        data class EntityAndCount<T>(var entity: T, var count: Int = 0)

        /** List of the table contents */
        private val list = ArrayList<EntityAndCount<T>>()
        /** Next number for make entity contents unique */
        protected var next = 0
            private set

        /**
         * Create a new entity
         * @param flags The flags to use for the entity
         */
        fun new(flags: Int = 0): T {
            // Bump the unique number
            ++next
            // Return the new entity
            return this.make(flags, next, unique())
        }

        /**
         * Copy another table into this one
         * @param from The other table
         * @param copyMap Lambda used to make copies of the entities for this table
         */
        fun copy(from: Table<T>, copyMap: (entity: T) -> T) {
            // Clear all existing entities
            list.clear()
            // Add the entities from the other table, copying them if needed
            list.addAll(from.list.map { EntityAndCount(copyMap(it.entity), it.count) })
            // Set the next number
            next = from.next
        }

        /**
         * Get an entity by index
         * @param i The index
         */
        operator fun get(i: Int): T {
            return list[i].entity
        }
        /** The number of entities in the table */
        val size: Int
            get() = list.size
        /** The sequence of entities */
        val entities: Sequence<T>
            get() = list.asSequence().map { it.entity }

        /**
         * Inform the table that an entity was linked in
         * @param entity The entity being linked in
         */
        fun linked(entity: T): T? {
            // Find the entity in the table
            var item = list.firstOrNull { sameId(it.entity, entity) }
            val prev = item?.entity
            // Set the id of this entity from the existing entity
            setId(entity, prev)
            // If no existing entity, add it, otherwise update it
            if (item == null)
                item = EntityAndCount(entity).also { list.add(it) }
            else
                item.entity = entity
            // Increment the link count
            ++item.count
            // Return the existing entity
            return prev
        }

        /**
         * Inform the table that an entity was unlinked
         * @param entity The entity unlinked
         * @param delete True to remove the entity, no matter the state of the link count
         */
        fun unlinked(entity: T, delete: Boolean = false) {
            // Find the entity
            val i = list.indexOfFirst { sameId(it.entity, entity) }
            val item = list[i]      // Assume the entity is in the table
            // Decrement the link count
            item.count = (item.count - 1).coerceAtLeast(0)
            // Remove if needed
            if (delete || (autoDelete && item.count == 0)) {
                list.removeAt(i)
            }
        }

        /**
         * Special case for merging links of two tag entities
         * @param entity The merged entity
         * @param delete The entity being replaced
         */
        fun merge(entity: T, delete: T) {
            val e = list.first { sameId(it.entity, entity) }
            val d = list.indexOfFirst { sameId(it.entity, delete) }
            if (d >= 0) {
                e.count += list[d].count
                list.removeAt(d)
            }
        }

        /**
         * Create a list of entities to be linked to a book
         * @param maxCount The maximum number of entities in the list
         * @param random A random number generator
         * @return Mutable list of entities
         */
        fun createRelationList(maxCount: Int, random: Random) : MutableList<T> {
            // Create the list
            val returnList = ArrayList<T>()
            // Get number of entities
            val count = random.nextInt(maxCount + 1)
            // Get number of existing entities for the list
            var existing = random.nextInt(count + 1).coerceAtMost(size)
            repeat (count) {
                val fromList = existing > 0 && random.nextBoolean()
                returnList.add(createRelation(fromList, random, returnList))
                if (fromList)
                    --existing
            }
            return returnList
        }

        /**
         * Create a relation entity
         * @param fromList True to return existing entities from the list
         * @param returnList List of relations already created. Used to prevent duplicates
         * @param random A random number generator
         * @return The entity
         */
        fun createRelation(fromList: Boolean, random: Random, returnList: List<T>? = null) : T {
            // Add another entities
            if (fromList && size > 0) {
                // Add an existing entity
                var entity: T
                // Don't add any entities twice
                do {
                    entity = this[random.nextInt(size)]
                } while (returnList?.contains(entity) == true)
                return entity
            } else {
                // Add a new entity - Tags are the only linked entities with flags
                val flags = if (random.nextInt(5) > 3)
                    TagEntity.SELECTED
                else
                    0
                return new(flags)
            }
        }

        /**
         * Link all entities in a list
         * @param linkList The list of entities
         */
        fun linkRelation(linkList: List<T>) {
            for (e in linkList)
                linked(e)
        }

        /**
         * Unlink all entities in a list
         * @param linkList The list of entities
         */
        fun unlinkRelation(linkList: List<T>) {
            for (e in linkList)
                unlinked(e)
        }

        /**
         * Update the links of entities
         * @param from The linked entities before the update
         * @param to The linked entities after the update
         */
        fun updateRelation(from: List<T>, to: List<T>) {
            // Create a map to keep track of net effect or unlink/link
            val entities = TreeMap<T, Int>(this)
            // Add entities for unlinking
            for (e in from)
                entities[e] = entities[e]?.dec()?: -1
            // Add entities for linking
            for (e in to)
                entities[e] = entities[e]?.inc()?: 1
            for (e in entities.entries) {
                when {
                    // Link in new entities
                    e.value >= 0 -> linked(e.key)
                    // Unlink removed entities
                    e.value < 0 -> unlinked(e.key)
                }
            }
        }

        /**
         * Check that the entities counts are correct
         * @param message A message for assertion failures
         * @param sequence The sequence of all entities for all books
         * @param name Callback to get a name from an entity
         */
        fun checkCounts(message: String, sequence: Sequence<T>, name: (T) -> String) {
            // Map to hold the entities and counts
            val counts = HashMap<T, Int>(size)
            // Count each entities in the sequence
            for (e in sequence)
                counts[e] = (counts[e]?: 0) + 1
            // Make sure each count matches the count in the table
            for (e in list)
                assertWithMessage("%s: CheckCounts %s", message, name(e.entity)).that(counts[e.entity]?: 0).isEqualTo(e.count)
        }

        /**
         * Get a unique string to append to name
         */
        private fun unique(): String {
            return when(next) {
                // Make sure the special characters for SQLite LIKE are used
                1 -> "\\"
                2 -> "_"
                3 -> "%"
                // Otherwise just use a number
                else -> next.toString()
            }
        }
    }

    /**
     * Container for expected values of all tables in the database
     */
    inner class Tables {
        /**
         * The expected values for the tags table
         */
        val tagEntities: Table<TagEntity> =  object: Table<TagEntity>(
            // Create a tag entity
            {flags, _, unique -> TagEntity(0L, "tag$unique", "desc$unique", flags) },
            // Copy id from one tag to another
            {entity, prev ->  prev?.let { entity.id = it.id } },
            // Determine whether two tags have the same id
            {e1, e2 -> e1.id == e2.id },
            // Don't delete tags when the are no longer referenced
            false
        ) {
            /**
             * @inheritDoc
             * Tags conflict when the names are the same
             */
            override fun compare(e1: TagEntity, e2: TagEntity): Int {
                return e1.name.compareTo(e2.name, true)
            }
        }
        /**
         * The expected values for the authors table
         */
        val authorEntities: Table<AuthorEntity> = object: Table<AuthorEntity>(
            // Create an author entity
            {_, _, unique -> AuthorEntity(0L, "last$unique", "first$unique") },
            // Copy id from one author to another
            {entity, prev ->  prev?.let { entity.id = it.id } },
            // Determine whether two authors have the same id
            {e1, e2 -> e1.id == e2.id }
            // Do delete authors when the are no longer referenced
        ) {
            /**
             * @inheritDoc
             * Authors conflict when the names are the same
             */
            override fun compare(e1: AuthorEntity, e2: AuthorEntity): Int {
                val f = e1.lastName.compareTo(e2.lastName, true)
                return if (f != 0) f else e1.remainingName.compareTo(e2.remainingName, true)
            }
        }
        /**
         * The expected values for the categories table
         */
        val categoryEntities: Table<CategoryEntity> = object: Table<CategoryEntity>(
            // Create a category entity
            {_, _, unique -> CategoryEntity(0L, "category$unique") },
            // Copy id from one category to another
            {entity, prev ->  prev?.let { entity.id = it.id } },
            // Determine whether two categories have the same id
            {e1, e2 -> e1.id == e2.id }
            // Do delete categories when the are no longer referenced
        ) {
            /**
             * @inheritDoc
             * Categories conflict when the names are the same
             */
            override fun compare(e1: CategoryEntity, e2: CategoryEntity): Int {
                return e1.category.compareTo(e2.category, true)
            }
        }
        /**
         * The expected values for the isbns table
         */
        val isbnEntities: Table<IsbnEntity> = object: Table<IsbnEntity>(
            // Create an isbn entity
            {_, _, unique -> IsbnEntity(0L, "isbn$unique") },
            // Copy id from one isbn to another
            {entity, prev ->  prev?.let { entity.id = it.id } },
            // Determine whether two isbns have the same id
            {e1, e2 -> e1.id == e2.id }
            // Do delete isbns when they are no longer referenced
        ) {
            /**
             * @inheritDoc
             * Isbns conflict when the values are the same
             */
            override fun compare(e1: IsbnEntity, e2: IsbnEntity): Int {
                return e1.isbn.compareTo(e2.isbn, true)
            }
        }
        val seriesEntities: Table<SeriesEntity> = object: Table<SeriesEntity>(
            // Create a series entity
            {_, _, unique -> SeriesEntity(0L, "series$unique", "title$unique", 0) },
            // Copy id from one series to another
            {entity, prev ->  prev?.let { entity.id = it.id } },
            // Determine whether two isbns have the same id
            {e1, e2 -> e1.id == e2.id }
            // Do delete isbns when they are no longer referenced
        ) {
            /**
             * @inheritDoc
             * Series conflict when the values are the same
             */
            override fun compare(e1: SeriesEntity, e2: SeriesEntity): Int {
                return e1.seriesId.compareTo(e2.seriesId)
            }
        }
        /**
         * The expected values for the books table
         */
        val bookEntities: Table<BookAndAuthors> = object: Table<BookAndAuthors>(
            // Create a book entity
            {flags, next, unique -> makeBookAndAuthors(next, flags, unique) },
            // Copy id from one book to another
            {entity, prev ->  prev?.let { entity.book.id = it.book.id } },
            // Determine whether two books have the same id
            {e1, e2 -> e1.book.id == e2.book.id }
            // Do delete books when unlinked is called
        ) {
            private val comp = nullsFirst(Comparator<String> { o1, o2 -> o1!!.compareTo(o2!!, true) })
                .let { c -> compareBy<BookAndAuthors, String?>(c) { it.book.sourceId }.thenBy(c) { it.book.volumeId } }
            /**
             * @inheritDoc
             * Books conflict if the ISBN or source and volume ids are not null and are the same
             */
            override fun compare(e1: BookAndAuthors, e2: BookAndAuthors): Int {
                return comp.compare(e1, e2)
            }
        }
        /**
         * The expected values for the views table
         */
        val viewEntities: Table<ViewEntity> = object: Table<ViewEntity>(
            // Create a views entity
            {_, _, unique -> ViewEntity(0L, "view$unique", "desc$unique", makeFilter(), 0) },
            // Copy id from one view to another
            {entity, prev ->  prev?.let { entity.id = it.id } },
            // Determine whether two views have the same id
            {e1, e2 -> e1.id == e2.id }
            // Do delete views when unlinked is called
        ) {
            /**
             * @inheritDoc
             * Views conflict when the names are the same
             */
            override fun compare(e1: ViewEntity, e2: ViewEntity): Int {
                return e1.name.compareTo(e2.name, true)
            }
        }

        /**
         * Clear flags for deleted entities
         * @param new The expected tables with the entities deleted
         * Only tags and books have tags to be cleared
         */
        fun clearFlags(new: Tables) {
            // Clear flags for all tags in this table, that are not in the new table
            val tags = new.tagEntities.entities.map { it.id }.toSet()
            for (t in tagEntities.entities) {
                // PRESERVE some flags
                if (!tags.contains(t.id))
                    t.flags = t.flags and TagEntity.PRESERVE     // Clear flags if not in new
            }
            // Clear flags for all books in this table, that are not in the new table
            val books = new.bookEntities.entities.map { it.book.id }.toSet()
            for (b in bookEntities.entities) {
                // PRESERVE some flags
                if (!books.contains(b.book.id))
                    b.book.flags = b.book.flags and BookEntity.PRESERVE    // Clear flags if not in new
            }
        }

        /**
         * Copy flags from one table to another
         * @param old The source table
         * Only books and tags have flags to be copied
         */
        fun swapTables(old: Tables) {
            // Create a map from id to tag in the old table
            val tags = mapOf(*old.tagEntities.entities.map { Pair(it.id, it) }.toList().toTypedArray())
            // Don't copy the flags in PRESERVE
            for (t in tagEntities.entities)
                tags[t.id]?.let { t.flags = (t.flags and TagEntity.PRESERVE) or (it.flags and TagEntity.PRESERVE.inv()) }  // If a tag in this table is in the map, copy the flags
            // Create a map from id to book in the old table
            val books = mapOf(*old.bookEntities.entities.map { Pair(it.book.id, it) }.toList().toTypedArray())
            // Don't copy the flags in PRESERVE
            for (b in bookEntities.entities)
                books[b.book.id]?.let { b.book.flags = (b.book.flags and BookEntity.PRESERVE) or (it.book.flags and BookEntity.PRESERVE.inv()) }  // If a book in this table is in the map, copy the flags
        }

        /**
         * Make a copy of the expected values
         */
        fun copy(): Tables {
            return Tables().also {t ->
                // Copy the tags making copies of the TagEntities
                t.tagEntities.copy(tagEntities) { it.copy() }
                // Create a map of id to tag for the new table
                val tags = mapOf(*t.tagEntities.entities.map { Pair(it.id, it) }.toList().toTypedArray())
                // Copy the authors and share the entities between all tables
                t.authorEntities.copy(authorEntities) { it }
                // Copy the categories and share the entities between all tables
                t.categoryEntities.copy(categoryEntities) { it }
                // Copy the isbns and share the entities between all tables
                t.isbnEntities.copy(isbnEntities) { it }
                // Copy the series making copies of the SeriesEntities
                t.seriesEntities.copy(seriesEntities) { it.copy() }
                val series = mapOf(*t.seriesEntities.entities.map { Pair(it.id, it) }.toList().toTypedArray())
                // Copy the books
                t.bookEntities.copy(bookEntities) {book ->
                    // Make copies of the books
                    book.copy(
                        // And copies of the book entities
                        book = book.book.copy(),
                        // And make sure the tag list has the copied tag entities
                        tags = book.tags.map { tags[it.id]!! },
                        // And make sure the series list has the copied series entities
                        series = series[book.book.seriesId]
                    )
                }
                // Copy the views making copies of the ViewEntities
                t.viewEntities.copy(viewEntities) { it.copy() }
            }
        }
    }}