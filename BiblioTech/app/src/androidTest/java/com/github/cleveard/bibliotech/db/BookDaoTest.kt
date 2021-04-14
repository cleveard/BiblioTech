package com.github.cleveard.bibliotech.db


import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.DisableOnAndroidDebug
import com.github.cleveard.bibliotech.testutils.BookDbTracker
import com.github.cleveard.bibliotech.testutils.UndoTracker
import com.github.cleveard.bibliotech.testutils.compareBooks
import com.github.cleveard.bibliotech.utils.getLive
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BookDaoTest {
    private lateinit var db: BookDatabase
    private lateinit var context: Context
    private lateinit var undo: UndoTracker

    @Before
    fun startUp() {
        context = ApplicationProvider.getApplicationContext()
        BookDatabase.initialize(context, true)
        db = BookDatabase.db
        undo = UndoTracker(db.getUndoRedoDao())
    }

    @After
    fun tearDown() {
        BookDatabase.close(context)
    }

    @get:Rule
    val timeout = DisableOnAndroidDebug(Timeout(50L, TimeUnit.SECONDS))

    @Test fun testAddDeleteBookEntity() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestAddDeleteBookEntity()
        }
    }

    @Test fun testAddDeleteBookEntityWithUndo() {
        runBlocking {
            undo.record("TestAddDeleteBookEntityWithUndo") { doTestAddDeleteBookEntity() }
        }
    }

    private suspend fun doTestAddDeleteBookEntity() {
        // Get some books into the database and get the book dao
        val expected = BookDbTracker.addBooks(db,2564621L, "AddBooks Delete", 20)
        val bookDao = db.getBookDao()

        // Loop through database copy and remove selected books. Keep count of how many were removed.
        var count = 0
        for (b in ArrayList<BookAndAuthors>().apply {
            addAll(expected.tables.bookEntities.entities)
        }) {
            if (b.book.isSelected) {
                expected.unlinkBook(b)
                ++count
            }
        }
        // Delete selected books and make sure the return value is correct.
        assertWithMessage("Delete Selected").that(bookDao.deleteSelectedWithUndo(null,null)).isEqualTo(count)
        // Make sure the books were deleted.
        expected.checkDatabase("Delete Selected")

        // Remove books in random groups of 0 to 4 books until they are all gone
        var occurrence = 0  // Keep track of how many times we delete for error messages
        while (expected.tables.bookEntities.size > 0) {
            ++occurrence
            count = expected.random.nextInt(4).coerceAtMost(expected.tables.bookEntities.size)
            val bookIds = Array<Any>(count) { 0L }
            repeat (count) {
                // Randomly pick a book to delete and remove it from the expected books
                val i = expected.random.nextInt(expected.tables.bookEntities.size)
                val book = expected.tables.bookEntities[i]
                bookIds[it] = book.book.id
                expected.unlinkBook(book)
            }
            // Delete the books and make sure the return value is correct
            assertWithMessage("Deleted $occurrence").that(bookDao.deleteSelectedWithUndo(null, bookIds)).isEqualTo(bookIds.size)
            // Make sure the books were deleted
            expected.checkDatabase("Delete $occurrence")
        }
    }

    @Test fun testAddDeleteBookEntityEmptyFilter() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestAddDeleteBookEntityEmptyFilter()
        }
    }

    @Test fun testAddDeleteBookEntityEmptyFilterWithUndo() {
        runBlocking {
            undo.record("TestAddDeleteBookEntityEmptyFilterWithUndo") { doTestAddDeleteBookEntityEmptyFilter() }
        }
    }

    private suspend fun doTestAddDeleteBookEntityEmptyFilter() {
        // Get some books into the database, a filter and get the book dao
        val expected = BookDbTracker.addBooks(db,9922621L, "AddBooks Empty Filter", 20)
        val filter = BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, emptyArray())
        )).buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
        val bookDao = db.getBookDao()

        // Loop through database copy and remove selected books. Keep count of how many were removed.
        // The filter is empty, so we don't need to filter the books
        var count = 0
        for (b in ArrayList<BookAndAuthors>().apply {
            addAll(expected.tables.bookEntities.entities)
        }) {
            if (b.book.isSelected) {
                expected.unlinkBook(b)
                ++count
            }
        }
        // Delete selected books and make sure the return value is correct.
        assertWithMessage("Delete Selected Empty Filter").that(bookDao.deleteSelectedWithUndo(filter,null)).isEqualTo(count)
        // Make sure the books were deleted.
        expected.checkDatabase("Delete Selected Empty Filter")

        // Remove books in random groups of 0 to 4 books until they are all gone
        var occurrence = 0
        while (expected.tables.bookEntities.size > 0) {
            ++occurrence
            count = expected.random.nextInt(4).coerceAtMost(expected.tables.bookEntities.size)
            val bookIds = Array<Any>(count) { 0L }
            repeat (count) {
                // Randomly pick a book to delete and remove it from the expected books
                val i = expected.random.nextInt(expected.tables.bookEntities.size)
                val book = expected.tables.bookEntities[i]
                bookIds[it] = book.book.id
                expected.unlinkBook(book)
            }
            // Delete the books and make sure the return value is correct
            assertWithMessage("Deleted $occurrence Empty Filter").that(bookDao.deleteSelectedWithUndo(filter, bookIds)).isEqualTo(bookIds.size)
            // Make sure the books were deleted
            expected.checkDatabase("Delete $occurrence Empty Filter")
        }
    }

    @Test fun testAddDeleteBookEntityWithFilter() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestAddDeleteBookEntityWithFilter()
        }
    }

    @Test fun testAddDeleteBookEntityWithFilterWithUndo() {
        runBlocking {
            undo.record("TestAddDeleteBookEntityWithFilterWithUndo") { doTestAddDeleteBookEntityWithFilter() }
        }
    }

    private suspend fun doTestAddDeleteBookEntityWithFilter() {
        // Get some books into the database, a filter and get the book dao
        val expected = BookDbTracker.addBooks(db,8964521L, "AddBooks Filter", 20)
        val bookFilter = expected.makeFilter()
        val filter = bookFilter.buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
        val bookDao = db.getBookDao()
        val keptCount = expected.tables.bookEntities.size - bookFilter.filterList[0].values.size

        // Loop through database copy and remove selected books. Keep count of how many were removed.
        // The filter only uses title, so just check that
        var count = 0
        for (b in ArrayList<BookAndAuthors>().apply {
            addAll(expected.tables.bookEntities.entities)
        }) {
            if (b.book.isSelected && bookFilter.filterList[0].values.contains(b.book.title)) {
                expected.unlinkBook(b)
                ++count
            }
        }
        // Delete selected books and make sure the return value is correct.
        assertWithMessage("Delete Selected Filtered").that(bookDao.deleteSelectedWithUndo(filter,null)).isEqualTo(count)
        // Make sure the books were deleted.
        expected.checkDatabase("Delete Selected Filtered")

        // Remove books in random groups of 0 to 4 books until only the unfiltered remain
        var occurrence = 0
        while (expected.tables.bookEntities.size > keptCount) {
            ++occurrence
            count = expected.random.nextInt(4).coerceAtMost(expected.tables.bookEntities.size)
            val bookIds = Array<Any>(count) { 0L }
            var size = 0
            repeat (count) {
                // Randomly pick a book to delete and remove it from the expected books
                val i = expected.random.nextInt(expected.tables.bookEntities.size)
                val book = expected.tables.bookEntities[i]
                bookIds[it] = book.book.id
                if (bookFilter.filterList[0].values.contains(book.book.title)) {
                    expected.unlinkBook(book)
                    ++size
                }
            }
            // Delete the books and make sure the return value is correct
            assertWithMessage("Deleted $occurrence Filtered").that(bookDao.deleteSelectedWithUndo(filter, bookIds)).isEqualTo(size)
            // Make sure the books were deleted
            expected.checkDatabase("Delete $occurrence Filtered")
        }
    }

    @Test fun testUpdateBookEntity() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestUpdateBookEntity()
        }
    }

    @Test fun testUpdateBookEntityWithUndo() {
        runBlocking {
            undo.record("TestUpdateBookEntityWithUndo") { doTestUpdateBookEntity() }
        }
    }

    private suspend fun doTestUpdateBookEntity() {
        // Get some books
        val expected = BookDbTracker.addBooks(db,554321L, "AddBooks Update", 20)

        repeat (5) {
            // Randomly select books to update
            val i = expected.random.nextInt(expected.tables.bookEntities.size)
            val old = expected.tables.bookEntities[i]
            val new = expected.tables.bookEntities.new()
            // Randomly make the ISBN or source id and volume id match
            val x = expected.random.nextInt(3)
            if ((x and 1) == 0)
                new.book.ISBN = old.book.ISBN
            if (x > 0) {
                new.book.sourceId = old.book.sourceId
                new.book.volumeId = old.book.volumeId
            }
            // Update the book. AddOneBook does the checks
            expected.addOneBook("Update ${new.book.title}", new, true)
        }
    }

    @Test(expected = SQLiteConstraintException::class) fun testUpdateFails() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestUpdateFails()
        }
    }

    @Test(expected = SQLiteConstraintException::class) fun testUpdateFailsWithUndo() {
        runBlocking {
            undo.record("TestGetBooksWithUndo") { doTestUpdateFails() }
        }
    }

    private suspend fun doTestUpdateFails() {
        // Updating a book that conflicts with two other books will fail
        val expected = BookDbTracker.addBooks(db,5668721L, "AddBooks Update", 20)
        val book = expected.tables.bookEntities.new()
        book.book.ISBN = expected.tables.bookEntities[3].book.ISBN
        book.book.sourceId = expected.tables.bookEntities[11].book.sourceId
        book.book.volumeId = expected.tables.bookEntities[11].book.volumeId
        expected.addOneBook("Update Fail", book, true)
    }

    @Test fun testGetBooks() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestGetBooks()
        }
    }

    @Test fun testGetBooksWithUndo() {
        runBlocking {
            undo.record("TestGetBooksWithUndo") { doTestGetBooks() }
        }
    }

    private suspend fun doTestGetBooks() {
        // Get some books
        val expected = BookDbTracker.addBooks(db,56542358L, "GetBooks", 20)
        assertWithMessage("GetBooks").apply {
            // Get the books
            val source = db.getBookDao().getBooks()
            val result = source.load(
                PagingSource.LoadParams.Refresh(
                    key = 0,
                    loadSize = expected.tables.bookEntities.size,
                    placeholdersEnabled = false
                )
            )
            // Make sure it is correct
            that(result is PagingSource.LoadResult.Page).isTrue()
            result as PagingSource.LoadResult.Page<Int, BookAndAuthors>
            that(result.data.size).isEqualTo(expected.tables.bookEntities.size)
            var i = 0
            for (book in result.data)
                compareBooks(book, expected.tables.bookEntities[i++])
        }
    }

    @Test fun testGetBooksFiltered() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestGetBooksFiltered()
        }
    }

    @Test fun testGetBooksFilteredWithUndo() {
        runBlocking {
            undo.record("TestGetBooksFilteredWithUndo") { doTestGetBooksFiltered() }
        }
    }

    private suspend fun doTestGetBooksFiltered() {
        // Get some books
        val expected = BookDbTracker.addBooks(db,565199823L, "GetBooks Filtered", 20)
        assertWithMessage("GetBooksFiltered").apply {
            // Get the books
            val filter = expected.makeFilter()
            val source = db.getBookDao().getBooks(filter, context)
            val books = ArrayList<BookAndAuthors>()
            for (b in expected.tables.bookEntities.entities) {
                if (filter.filterList[0].values.contains(b.book.title))
                    books.add(b)
            }
            val result = source.load(
                PagingSource.LoadParams.Refresh(
                    key = 0,
                    loadSize = expected.tables.bookEntities.size,
                    placeholdersEnabled = false
                )
            )
            // Make sure they are correct
            that(result is PagingSource.LoadResult.Page).isTrue()
            result as PagingSource.LoadResult.Page<Int, BookAndAuthors>
            that(result.data.size).isEqualTo(books.size)
            var i = 0
            for (book in result.data)
                compareBooks(book, books[i++])
        }
    }

    @Test fun testGetBookList() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestGetBookList()
        }
    }

    @Test fun testGetBookListWithUndo() {
        runBlocking {
            undo.record("TestGetBooksWithUndo") { doTestGetBookList() }
        }
    }

    private suspend fun doTestGetBookList() {
        // Get some books
        val expected = BookDbTracker.addBooks(db,56542358L, "GetBookList", 20)
        assertWithMessage("GetBookList").apply {
            // Get the list of books
            val source = db.getBookDao().getBookList().getLive()
            // Make sure they are correct
            that(source).isNotNull()
            source!!
            that(source.size).isEqualTo(expected.tables.bookEntities.size)
            var i = 0
            for (book in source)
                compareBooks(book, expected.tables.bookEntities[i++])
        }
    }

    @Test fun testGetBookListFiltered() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestGetBookListFiltered()
        }
    }

    @Test fun testGetBookListFilteredWithUndo() {
        runBlocking {
            undo.record("TestGetBooksFilteredWithUndo") { doTestGetBookListFiltered() }
        }
    }

    private suspend fun doTestGetBookListFiltered() {
        // Get some books
        val expected = BookDbTracker.addBooks(db,565199823L, "GetBookList Filtered", 20)
        assertWithMessage("GetBookListFiltered").apply {
            // Get the list of books
            val filter = expected.makeFilter()
            val source = db.getBookDao().getBookList(filter, context).getLive()
            // Make sure it is correct
            that(source).isNotNull()
            source!!
            val books = ArrayList<BookAndAuthors>()
            for (b in expected.tables.bookEntities.entities) {
                if (filter.filterList[0].values.contains(b.book.title))
                    books.add(b)
            }
            that(source.size).isEqualTo(books.size)
            var i = 0
            for (book in source)
                compareBooks(book, books[i++])
        }
    }

    @Test fun testQueryBookIds() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestQueryBookIds()
        }
    }

    @Test fun testQueryBookIdsWithUndo() {
        runBlocking {
            undo.record("TestQueryBookIdsWithUndo") { doTestQueryBookIds() }
        }
    }

    private suspend fun doTestQueryBookIds() {
        // Get some books
        val expected = BookDbTracker.addBooks(db,2564621L, "AddBooks Delete", 20)
        val bookDao = db.getBookDao()

        // Get the ids of selected books
        val ids = ArrayList<Long>().apply {
            addAll(expected.tables.bookEntities.entities.filter { it.book.isSelected }.map { it.book.id })
        }

        assertWithMessage("QueryBookIds Selected").apply {
            // Make sure querying the ids gives the correct result
            bookDao.queryBookIds(null, null)?.let {
                that(it).containsExactlyElementsIn(ids)
            }?: that(ids).isEmpty()
        }

        var array = emptyArray<Any>()   // Test with empty array
        ids.clear()
        repeat(5) {
            // Query ids a couple of time
            assertWithMessage("QueryBookIds Array").apply {
                bookDao.queryBookIds(array, null)?.let {
                    that(it).containsExactlyElementsIn(ids)
                }?: that(ids).isEmpty()
            }
            // Make a new array of ids
            val size = expected.random.nextInt(1, 15)
            array = Array(size) { 0L }
            ids.clear()
            for (i in array.indices) {
                var id: Long
                do {
                    id = expected.tables.bookEntities[expected.random.nextInt(expected.tables.bookEntities.size)].book.id
                } while (array.contains(id))
                array[i] = id
                ids.add(id)
            }
        }
    }

    @Test fun testQueryBookIdsEmptyFilter() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestQueryBookIdsEmptyFilter()
        }
    }

    @Test fun testQueryBookIdsEmptyFilterWithUndo() {
        runBlocking {
            undo.record("TestQueryBookIdsEmptyFilterWithUndo") { doTestQueryBookIdsEmptyFilter() }
        }
    }

    private suspend fun doTestQueryBookIdsEmptyFilter() {
        // Get some books and a filter
        val expected = BookDbTracker.addBooks(db,2564621L, "AddBooks Delete", 20)
        val filter = BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, emptyArray())
        )).buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
        val bookDao = db.getBookDao()

        // Get the ids of selected filtered books
        val ids = ArrayList<Long>().apply {
            addAll(expected.tables.bookEntities.entities.filter { it.book.isSelected }.map { it.book.id })
        }

        assertWithMessage("QueryBookIds Selected").apply {
            // Make sure querying the ids gives the correct result
            bookDao.queryBookIds(null, filter)?.let {
                that(it).containsExactlyElementsIn(ids)
            }?: that(ids).isEmpty()
        }

        var array = emptyArray<Any>()   // Test with empty array
        ids.clear()
        repeat(5) {
            assertWithMessage("QueryBookIds Array").apply {
                // Query ids a couple of time
                bookDao.queryBookIds(array, filter)?.let {
                    that(it).containsExactlyElementsIn(ids)
                }?: that(ids).isEmpty()
            }
            // Make a new array of ids
            val size = expected.random.nextInt(1, 15)
            array = Array(size) { 0L }
            ids.clear()
            for (i in array.indices) {
                var id: Long
                do {
                    id = expected.tables.bookEntities[expected.random.nextInt(expected.tables.bookEntities.size)].book.id
                } while (array.contains(id))
                array[i] = id
                ids.add(id)
            }
        }
    }

    @Test fun testQueryBookIdsWithFilter() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestQueryBookIdsWithFilter()
        }
    }

    @Test fun testQueryBookIdsWithFilterWithUndo() {
        runBlocking {
            undo.record("TestQueryBookIdsWithFilterWithUndo") { doTestQueryBookIdsWithFilter() }
        }
    }

    private suspend fun doTestQueryBookIdsWithFilter() {
        // Get some books and a filter
        val expected = BookDbTracker.addBooks(db,2564621L, "AddBooks Delete", 20)
        val bookFilter = expected.makeFilter()
        val filter = bookFilter.buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
        val bookDao = db.getBookDao()

        // Get the ids of selected filtered books
        val ids = ArrayList<Long>().apply {
            addAll(expected.tables.bookEntities.entities
                .filter { it.book.isSelected && bookFilter.filterList[0].values.contains(it.book.title) }
                .map { it.book.id })
        }

        assertWithMessage("QueryBookIds Selected").apply {
            // Make sure querying the ids gives the correct result
            bookDao.queryBookIds(null, filter)?.let {
                that(it).containsExactlyElementsIn(ids)
            }?: that(ids).isEmpty()
        }

        var array = emptyArray<Any>()   // Test with empty array
        ids.clear()
        repeat(5) {
            assertWithMessage("QueryBookIds Array").apply {
                // Query ids a couple of time
                bookDao.queryBookIds(array, filter)?.let {
                    that(it).containsExactlyElementsIn(ids)
                }?: that(ids).isEmpty()
            }
            // Make a new array of ids
            val size = expected.random.nextInt(1, 15)
            array = Array(size) { 0L }
            ids.clear()
            for (i in array.indices) {
                var book: BookAndAuthors
                do {
                    book = expected.tables.bookEntities[expected.random.nextInt(expected.tables.bookEntities.size)]
                } while (array.contains(book.book.id))
                array[i] = book.book.id
                if (bookFilter.filterList[0].values.contains(book.book.title))
                    ids.add(book.book.id)
            }
        }
    }

    /**
     * Test bit changes - filtered or not
     * @param message A message for assertion failures
     * @param bookFilter An optional filter to use for the test
     */
    private suspend fun BookDbTracker.testBitChanges(message: String, bookFilter: BookFilter?) {
        // Get the book do and build the filter
        val bookDao = db.getBookDao()
        val filter = bookFilter?.let { it.buildFilter(context, arrayOf(BOOK_ID_COLUMN), true) }

        // Make sure we can count bit patterns
        suspend fun StandardSubjectBuilder.checkCountBits(bits: Int, value: Int, include: Boolean, id: Long?) {
            // Get the sequence of books
            var s = tables.bookEntities.entities
            // Filter the ones using the filter
            if (bookFilter != null)
                s = s.filter { bookFilter.filterList[0].values.contains(it.book.title) }
            // Filter by id, if not null
            if (id != null)
                s = s.filter { it.book.id == id }
            // Filter by include or exclude the bit pattern
            s = if (include)
                s.filter { (it.book.flags and bits) == value }
            else
                s.filter { (it.book.flags and bits) != value }
            // Make sure the counts match
            that(bookDao.countBits(bits, value, include, id, filter)).isEqualTo(s.count())
        }

        // Check counts, include/exclude for an id
        suspend fun checkCount(bits: Int, value: Int, id: Long?) {
            assertWithMessage("checkCount$message value: %s id: %s", value, id).apply {
                checkCountBits(bits, value, true, id)
                checkCountBits(bits, value, false, id)
            }
        }

        // Check null ids first
        checkCount(0b11, 0b01, null)
        checkCount(0b11, 0b11, null)

        // Now check other ids
        fun nextBook(): BookAndAuthors {
            return tables.bookEntities[random.nextInt(tables.bookEntities.size)]
        }
        repeat(10) {
            checkCount(0b11, 0b11, nextBook().book.id)
            checkCount(0b11, 0b01, nextBook().book.id)
        }

        // Check that we can change bits - set, clear or invert
        suspend fun checkChange(all: String, operation: Boolean?, mask: Int, book: BookAndAuthors?) {
            assertWithMessage(
                "changeBits$message %s %s op: %s, mask: %s", book?.book?.flags, all, operation, mask
            ).apply {
                // Get the sequence of books
                var s = tables.bookEntities.entities
                // Filter if needed
                if (bookFilter != null)
                    s = s.filter { bookFilter.filterList[0].values.contains(it.book.title) }
                // Restrict to a single id
                if (book != null)
                    s = s.filter { it.book.id == book.book.id }
                // Do the operation and update the expected books in place in the map {} lambda
                when (operation) {
                    true -> { s = s.filter { (it.book.flags and mask) != mask }.map { it.book.flags = it.book.flags or mask; it } }
                    false -> { s = s.filter { (it.book.flags and mask) != 0 }.map { it.book.flags = it.book.flags and mask.inv(); it } }
                    else -> { s = s.map { it.book.flags = it.book.flags xor mask; it } }
                }
                // Run the sequence once - count and update the books
                val expCount = s.count()
                // Make sure the count is right
                val count = bookDao.changeBits(operation, mask, book?.book?.id, filter)
                that(count).isEqualTo(expCount)
                // Make sure the bits got changed
                checkDatabase("changeBits$message $all op: $operation, mask: $mask")
            }
        }

        // Change bits for random books
        repeat( 10) {
            checkChange("single $it", true,  0b11000, nextBook())
            checkChange("single $it", false, 0b01000, nextBook())
            checkChange("single $it", null,  0b10010, nextBook())
        }

        // Change all bits
        checkChange("all", null,  0b10011, null)
        checkChange("all", false, 0b01001, null)
        checkChange("all", true,  0b10010, null)
    }

    @Test fun testBitChanges() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestBitChanges()
        }
    }

    @Test fun testBitChangesWithUndo() {
        runBlocking {
            undo.record("TestBitChangesWithUndo") { doTestBitChanges() }
        }
    }

    private suspend fun doTestBitChanges() {
        val expected = BookDbTracker.addBooks(db,552841129L, "Test Bit Change", 20)
        expected.testBitChanges("", null)
    }

    @Test fun testBitChangesFiltered() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestBitChangesFiltered()
        }
    }

    @Test fun testBitChangesFilteredWithUndo() {
        runBlocking {
            undo.record("TestBitChangesFilteredWithUndo") { doTestBitChangesFiltered() }
        }
    }

    private suspend fun doTestBitChangesFiltered() {
        val expected = BookDbTracker.addBooks(db,1165478L, "Test Bit Change filtered", 20)
        expected.testBitChanges(" filtered", expected.makeFilter())
    }
}
