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
        val expected = BookDbTracker.addBooks(db,2564621L, "AddBooks Delete", 20)
        val bookDao = db.getBookDao()

        var count = 0
        for (b in ArrayList<BookAndAuthors>().apply {
            addAll(expected.tables.bookEntities.entities)
        }) {
            if (b.book.isSelected) {
                expected.unlinkBook(b)
                ++count
            }
        }
        assertWithMessage("Delete Selected").that(bookDao.deleteSelectedWithUndo(null,null)).isEqualTo(count)
        expected.checkDatabase("Delete Selected")

        var occurrence = 0
        while (expected.tables.bookEntities.size > 0) {
            ++occurrence
            count = expected.random.nextInt(4).coerceAtMost(expected.tables.bookEntities.size)
            val bookIds = Array<Any>(count) { 0L }
            repeat (count) {
                val i = expected.random.nextInt(expected.tables.bookEntities.size)
                val book = expected.tables.bookEntities[i]
                bookIds[it] = book.book.id
                expected.unlinkBook(book)
            }
            assertWithMessage("Deleted $occurrence").that(bookDao.deleteSelectedWithUndo(null, bookIds)).isEqualTo(bookIds.size)
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
        val expected = BookDbTracker.addBooks(db,9922621L, "AddBooks Empty Filter", 20)
        val filter = BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, emptyArray())
        )).buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
        val bookDao = db.getBookDao()

        var count = 0
        for (b in ArrayList<BookAndAuthors>().apply {
            addAll(expected.tables.bookEntities.entities)
        }) {
            if (b.book.isSelected) {
                expected.unlinkBook(b)
                ++count
            }
        }
        assertWithMessage("Delete Selected Empty Filter").that(bookDao.deleteSelectedWithUndo(filter,null)).isEqualTo(count)
        expected.checkDatabase("Delete Selected Empty Filter")

        var occurrence = 0
        while (expected.tables.bookEntities.size > 0) {
            ++occurrence
            count = expected.random.nextInt(4).coerceAtMost(expected.tables.bookEntities.size)
            val bookIds = Array<Any>(count) { 0L }
            repeat (count) {
                val i = expected.random.nextInt(expected.tables.bookEntities.size)
                val book = expected.tables.bookEntities[i]
                bookIds[it] = book.book.id
                expected.unlinkBook(book)
            }
            assertWithMessage("Deleted $occurrence Empty Filter").that(bookDao.deleteSelectedWithUndo(filter, bookIds)).isEqualTo(bookIds.size)
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
        val expected = BookDbTracker.addBooks(db,8964521L, "AddBooks Filter", 20)
        val bookFilter = expected.makeFilter()
        val filter = bookFilter.buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
        val bookDao = db.getBookDao()
        val keptCount = expected.tables.bookEntities.size - bookFilter.filterList[0].values.size

        var count = 0
        for (b in ArrayList<BookAndAuthors>().apply {
            addAll(expected.tables.bookEntities.entities)
        }) {
            if (b.book.isSelected && bookFilter.filterList[0].values.contains(b.book.title)) {
                expected.unlinkBook(b)
                ++count
            }
        }
        assertWithMessage("Delete Selected Filtered").that(bookDao.deleteSelectedWithUndo(filter,null)).isEqualTo(count)
        expected.checkDatabase("Delete Selected Filtered")

        var occurrence = 0
        while (expected.tables.bookEntities.size > keptCount) {
            ++occurrence
            count = expected.random.nextInt(4).coerceAtMost(expected.tables.bookEntities.size)
            val bookIds = Array<Any>(count) { 0L }
            var size = 0
            repeat (count) {
                val i = expected.random.nextInt(expected.tables.bookEntities.size)
                val book = expected.tables.bookEntities[i]
                bookIds[it] = book.book.id
                if (bookFilter.filterList[0].values.contains(book.book.title)) {
                    expected.unlinkBook(book)
                    ++size
                }
            }
            assertWithMessage("Deleted $occurrence Filtered").that(bookDao.deleteSelectedWithUndo(filter, bookIds)).isEqualTo(size)
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
        val expected = BookDbTracker.addBooks(db,554321L, "AddBooks Update", 20)

        repeat (5) {
            val i = expected.random.nextInt(expected.tables.bookEntities.size)
            val old = expected.tables.bookEntities[i]
            val new = expected.tables.bookEntities.new()
            val x = expected.random.nextInt(3)
            if ((x and 1) == 0)
                new.book.ISBN = old.book.ISBN
            if (x > 0) {
                new.book.sourceId = old.book.sourceId
                new.book.volumeId = old.book.volumeId
            }
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
        val expected = BookDbTracker.addBooks(db,56542358L, "GetBooks", 20)
        assertWithMessage("GetBooks").apply {
            val source = db.getBookDao().getBooks()
            val result = source.load(
                PagingSource.LoadParams.Refresh(
                    key = 0,
                    loadSize = expected.tables.bookEntities.size,
                    placeholdersEnabled = false
                )
            )
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
        val expected = BookDbTracker.addBooks(db,565199823L, "GetBooks Filtered", 20)
        assertWithMessage("GetBooksFiltered").apply {
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
        val expected = BookDbTracker.addBooks(db,56542358L, "GetBookList", 20)
        assertWithMessage("GetBookList").apply {
            val source = db.getBookDao().getBookList().getLive()
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
        val expected = BookDbTracker.addBooks(db,565199823L, "GetBookList Filtered", 20)
        assertWithMessage("GetBookListFiltered").apply {
            val filter = expected.makeFilter()
            val source = db.getBookDao().getBookList(filter, context).getLive()
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
        val expected = BookDbTracker.addBooks(db,2564621L, "AddBooks Delete", 20)
        val bookDao = db.getBookDao()

        val ids = ArrayList<Long>().apply {
            addAll(expected.tables.bookEntities.entities.filter { it.book.isSelected }.map { it.book.id })
        }

        assertWithMessage("QueryBookIds Selected").apply {
            bookDao.queryBookIds(null, null)?.let {
                that(it).containsExactlyElementsIn(ids)
            }?: that(ids).isEmpty()
        }

        var array = emptyArray<Any>()   // Test with empty array
        ids.clear()
        repeat(5) {
            assertWithMessage("QueryBookIds Array").apply {
                bookDao.queryBookIds(array, null)?.let {
                    that(it).containsExactlyElementsIn(ids)
                }?: that(ids).isEmpty()
            }
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
        val expected = BookDbTracker.addBooks(db,2564621L, "AddBooks Delete", 20)
        val filter = BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, emptyArray())
        )).buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
        val bookDao = db.getBookDao()

        val ids = ArrayList<Long>().apply {
            addAll(expected.tables.bookEntities.entities.filter { it.book.isSelected }.map { it.book.id })
        }

        assertWithMessage("QueryBookIds Selected").apply {
            bookDao.queryBookIds(null, filter)?.let {
                that(it).containsExactlyElementsIn(ids)
            }?: that(ids).isEmpty()
        }

        var array = emptyArray<Any>()   // Test with empty array
        ids.clear()
        repeat(5) {
            assertWithMessage("QueryBookIds Array").apply {
                bookDao.queryBookIds(array, filter)?.let {
                    that(it).containsExactlyElementsIn(ids)
                }?: that(ids).isEmpty()
            }
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
        val expected = BookDbTracker.addBooks(db,2564621L, "AddBooks Delete", 20)
        val bookFilter = expected.makeFilter()
        val filter = bookFilter.buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
        val bookDao = db.getBookDao()

        val ids = ArrayList<Long>().apply {
            addAll(expected.tables.bookEntities.entities
                .filter { it.book.isSelected && bookFilter.filterList[0].values.contains(it.book.title) }
                .map { it.book.id })
        }

        assertWithMessage("QueryBookIds Selected").apply {
            bookDao.queryBookIds(null, filter)?.let {
                that(it).containsExactlyElementsIn(ids)
            }?: that(ids).isEmpty()
        }

        var array = emptyArray<Any>()   // Test with empty array
        ids.clear()
        repeat(5) {
            assertWithMessage("QueryBookIds Array").apply {
                bookDao.queryBookIds(array, filter)?.let {
                    that(it).containsExactlyElementsIn(ids)
                }?: that(ids).isEmpty()
            }
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


    private suspend fun BookDbTracker.testBitChanges(message: String, bookFilter: BookFilter?) {
        val bookDao = db.getBookDao()
        val filter = bookFilter?.let { it.buildFilter(context, arrayOf(BOOK_ID_COLUMN), true) }

        suspend fun StandardSubjectBuilder.checkCountBits(bits: Int, value: Int, include: Boolean, id: Long?) {
            var s = tables.bookEntities.entities
            if (bookFilter != null)
                s = s.filter { bookFilter.filterList[0].values.contains(it.book.title) }
            if (id != null)
                s = s.filter { it.book.id == id }
            s = if (include)
                s.filter { (it.book.flags and bits) == value }
            else
                s.filter { (it.book.flags and bits) != value }
            that(bookDao.countBits(bits, value, include, id, filter)).isEqualTo(s.count())
        }

        suspend fun checkCount(bits: Int, value: Int, id: Long?) {
            assertWithMessage("checkCount$message value: %s id: %s", value, id).apply {
                checkCountBits(bits, value, true, null)
                checkCountBits(bits, value, false, null)
                checkCountBits(bits, value, true, id)
                checkCountBits(bits, value, false, id)
            }
        }

        checkCount(0b11, 0b01, null)
        checkCount(0b11, 0b11, null)

        fun nextBook(): BookAndAuthors {
            return tables.bookEntities[random.nextInt(tables.bookEntities.size)]
        }
        repeat(10) {
            checkCount(0b11, 0b11, nextBook().book.id)
            checkCount(0b11, 0b01, nextBook().book.id)
        }

        suspend fun checkChange(all: String, operation: Boolean?, mask: Int, book: BookAndAuthors?) {
            assertWithMessage(
                "changeBits$message %s %s op: %s, mask: %s", book?.book?.flags, all, operation, mask
            ).apply {
                var s = tables.bookEntities.entities
                if (bookFilter != null)
                    s = s.filter { bookFilter.filterList[0].values.contains(it.book.title) }
                if (book != null)
                    s = s.filter { it.book.id == book.book.id }
                when (operation) {
                    true -> { s = s.filter { (it.book.flags and mask) != mask }.map { it.book.flags = it.book.flags or mask; it } }
                    false -> { s = s.filter { (it.book.flags and mask) != 0 }.map { it.book.flags = it.book.flags and mask.inv(); it } }
                    else -> { s = s.map { it.book.flags = it.book.flags xor mask; it } }
                }
                val expCount = s.count()
                val count = bookDao.changeBits(operation, mask, book?.book?.id, filter)
                that(count).isEqualTo(expCount)
                checkDatabase("changeBits$message $all op: $operation, mask: $mask")
            }
        }


        repeat( 10) {
            checkChange("single $it", true,  0b11000, nextBook())
            checkChange("single $it", false, 0b01000, nextBook())
            checkChange("single $it", null,  0b10010, nextBook())
        }

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
