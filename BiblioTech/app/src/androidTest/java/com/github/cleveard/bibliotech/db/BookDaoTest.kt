package com.github.cleveard.bibliotech.db


import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.testutils.BookDbTracker
import com.github.cleveard.bibliotech.testutils.compareBooks
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDaoTest {
    private lateinit var db: BookDatabase
    private lateinit var context: Context

    @Before
    fun startUp() {
        context = ApplicationProvider.getApplicationContext()
        BookDatabase.initialize(context, true)
        db = BookDatabase.db
    }

    @After
    fun tearDown() {
        BookDatabase.close()
    }

    private suspend fun addBooks(seed: Long, message: String, count: Int): BookDbTracker {
        // Updating a book that conflicts with two other books will fail
        val expected = BookDbTracker(db, seed)
        expected.addTag()
        expected.addTag(TagEntity.SELECTED)
        expected.addTag(TagEntity.SELECTED)
        expected.addTag(0)

        expected.addBooks(message, count)
        return expected
    }

    @Test(timeout = 5000L) fun testAddDeleteBookEntity() {
        runBlocking {
            val expected = addBooks(2564621L, "AddBooks Delete", 20)
            val bookDao = db.getBookDao()

            for (b in ArrayList<BookAndAuthors>().apply {
                addAll(expected.bookEntities.entities)
            }) {
                if (b.book.isSelected)
                    expected.unlinkBook(b)
            }
            bookDao.deleteSelected(null,null)
            expected.checkDatabase("Delete Selected")

            var occurrence = 0
            while (expected.bookEntities.size > 0) {
                ++occurrence
                val count = expected.random.nextInt(4).coerceAtMost(expected.bookEntities.size)
                val bookIds = Array<Any>(count) { 0L }
                repeat (count) {
                    val i = expected.random.nextInt(expected.bookEntities.size)
                    val book = expected.bookEntities[i]
                    bookIds[it] = book.book.id
                    expected.unlinkBook(book)
                }
                bookDao.deleteSelected(null, bookIds)
                expected.checkDatabase("Delete $occurrence")
            }
        }
    }

    @Test(timeout = 5000L) fun testAddDeleteBookEntityEmptyFilter() {
        runBlocking {
            val expected = addBooks(9922621L, "AddBooks Empty Filter", 20)
            val filter = BookFilter(emptyArray(), arrayOf(
                FilterField(Column.TITLE, Predicate.ONE_OF, emptyArray())
            )).buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
            val bookDao = db.getBookDao()

            for (b in ArrayList<BookAndAuthors>().apply {
                addAll(expected.bookEntities.entities)
            }) {
                if (b.book.isSelected)
                    expected.unlinkBook(b)
            }
            bookDao.deleteSelected(filter,null)
            expected.checkDatabase("Delete Selected")

            var occurrence = 0
            while (expected.bookEntities.size > 0) {
                ++occurrence
                val count = expected.random.nextInt(4).coerceAtMost(expected.bookEntities.size)
                val bookIds = Array<Any>(count) { 0L }
                repeat (count) {
                    val i = expected.random.nextInt(expected.bookEntities.size)
                    val book = expected.bookEntities[i]
                    bookIds[it] = book.book.id
                    expected.unlinkBook(book)
                }
                bookDao.deleteSelected(filter, bookIds)
                expected.checkDatabase("Delete $occurrence")
            }
        }
    }

    private fun makeFilter(expected: BookDbTracker): BookFilter {
        val values = ArrayList<String>()
        fun getValues(count: Int, selected: Boolean) {
            repeat(count) {
                var book: BookAndAuthors
                do {
                    book = expected.bookEntities[expected.random.nextInt(expected.bookEntities.size)]
                } while (book.book.isSelected != selected && values.contains(book.book.title))
                values.add(book.book.title)
            }
        }
        val selected = expected.bookEntities.entities.filter { it.book.isSelected }.count().coerceAtMost(3)
        getValues(selected, true)
        getValues(5 - selected, false)
        return BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, values.toTypedArray())
        ))
    }

    @Test(timeout = 5000L) fun testAddDeleteBookEntityWithFilter() {
        runBlocking {
            val expected = addBooks(8964521L, "AddBooks Filter", 20)
            val bookFilter = makeFilter(expected)
            val filter = bookFilter.buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
            val bookDao = db.getBookDao()
            val keptCount = expected.bookEntities.size - bookFilter.filterList[0].values.size

            for (b in ArrayList<BookAndAuthors>().apply {
                addAll(expected.bookEntities.entities)
            }) {
                if (b.book.isSelected && bookFilter.filterList[0].values.contains(b.book.title))
                    expected.unlinkBook(b)
            }
            bookDao.deleteSelected(filter,null)
            expected.checkDatabase("Delete Selected Filtered")

            var occurrence = 0
            while (expected.bookEntities.size > keptCount) {
                ++occurrence
                val count = expected.random.nextInt(4).coerceAtMost(expected.bookEntities.size)
                val bookIds = Array<Any>(count) { 0L }
                repeat (count) {
                    val i = expected.random.nextInt(expected.bookEntities.size)
                    val book = expected.bookEntities[i]
                    bookIds[it] = book.book.id
                    if (bookFilter.filterList[0].values.contains(book.book.title))
                        expected.unlinkBook(book)
                }
                bookDao.deleteSelected(filter, bookIds)
                expected.checkDatabase("Delete $occurrence Filtered")
            }
        }
    }

    @Test(timeout = 5000L) fun testUpdateBookEntity() {
        runBlocking {
            val expected = addBooks(554321L, "AddBooks Update", 20)

            repeat (5) {
                val i = expected.random.nextInt(expected.bookEntities.size)
                val old = expected.bookEntities[i]
                val new = expected.bookEntities.new()
                val x = expected.random.nextInt(3)
                if ((x and 1) == 0)
                    new.book.ISBN = old.book.ISBN
                if (x > 0) {
                    new.book.sourceId = old.book.sourceId
                    new.book.volumeId = old.book.volumeId
                }
                expected.addOneBook("Update ${new.book.title}", new)
                expected.updateBook(new)
            }
        }
    }

    @Test(timeout = 1000L, expected = SQLiteConstraintException::class) fun testUpdateFails() {
        runBlocking {
            // Updating a book that conflicts with two other books will fail
            val expected = addBooks(5668721L, "AddBooks Update", 20)
            val book = expected.bookEntities.new()
            book.book.ISBN = expected.bookEntities[3].book.ISBN
            book.book.sourceId = expected.bookEntities[11].book.sourceId
            book.book.volumeId = expected.bookEntities[11].book.volumeId
            expected.addOneBook("Update Fail", book)
        }
    }

    @Test(timeout = 1000L) fun testGetBooks() {
        runBlocking {
            val expected = addBooks(56542358L, "GetBooks", 20)
            assertWithMessage("GetBooks").apply {
                val source = db.getBookDao().getBooks()
                val result = source.load(
                    PagingSource.LoadParams.Refresh(
                        key = 0,
                        loadSize = expected.bookEntities.size,
                        placeholdersEnabled = false
                    )
                )
                that(result is PagingSource.LoadResult.Page).isTrue()
                result as PagingSource.LoadResult.Page<Int, BookAndAuthors>
                that(result.data.size).isEqualTo(expected.bookEntities.size)
                var i = 0
                for (book in result.data)
                    compareBooks(book, expected.bookEntities[i++])
            }
        }
    }

    @Test(timeout = 1000L) fun testGetBooksFiltered() {
        runBlocking {
            val expected = addBooks(565199823L, "GetBooks Filtered", 20)
            assertWithMessage("GetBooks").apply {
                val filter = makeFilter(expected)
                val source = db.getBookDao().getBooks(filter, context)
                val books = ArrayList<BookAndAuthors>()
                for (b in expected.bookEntities.entities) {
                    if (filter.filterList[0].values.contains(b.book.title))
                        books.add(b)
                }
                val result = source.load(
                    PagingSource.LoadParams.Refresh(
                        key = 0,
                        loadSize = expected.bookEntities.size,
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
    }

    @Test(timeout = 1000L) fun testQueryBookIds() {
        runBlocking {
            val expected = addBooks(2564621L, "AddBooks Delete", 20)
            val bookDao = db.getBookDao()

            val ids = ArrayList<Long>().apply {
                addAll(expected.bookEntities.entities.filter { it.book.isSelected }.map { it.book.id })
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
                        id = expected.bookEntities[expected.random.nextInt(expected.bookEntities.size)].book.id
                    } while (array.contains(id))
                    array[i] = id
                    ids.add(id)
                }
            }
        }
    }

    @Test(timeout = 1000L) fun testQueryBookIdsEmptyFilter() {
        runBlocking {
            val expected = addBooks(2564621L, "AddBooks Delete", 20)
            val filter = BookFilter(emptyArray(), arrayOf(
                FilterField(Column.TITLE, Predicate.ONE_OF, emptyArray())
            )).buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
            val bookDao = db.getBookDao()

            val ids = ArrayList<Long>().apply {
                addAll(expected.bookEntities.entities.filter { it.book.isSelected }.map { it.book.id })
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
                        id = expected.bookEntities[expected.random.nextInt(expected.bookEntities.size)].book.id
                    } while (array.contains(id))
                    array[i] = id
                    ids.add(id)
                }
            }
        }
    }

    @Test(timeout = 1000L) fun testQueryBookIdsWithFilter() {
        runBlocking {
            val expected = addBooks(2564621L, "AddBooks Delete", 20)
            val bookFilter = makeFilter(expected)
            val filter = bookFilter.buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
            val bookDao = db.getBookDao()

            val ids = ArrayList<Long>().apply {
                addAll(expected.bookEntities.entities
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
                        book = expected.bookEntities[expected.random.nextInt(expected.bookEntities.size)]
                    } while (array.contains(book.book.id))
                    array[i] = book.book.id
                    if (bookFilter.filterList[0].values.contains(book.book.title))
                        ids.add(book.book.id)
                }
            }
        }
    }
}
