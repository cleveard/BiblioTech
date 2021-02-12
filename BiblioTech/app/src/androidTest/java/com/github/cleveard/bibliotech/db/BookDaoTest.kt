package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.makeBookAndAuthors
import com.github.cleveard.bibliotech.testutils.BookDbTracker
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

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

    @Test(timeout = 5000L) fun testAddDeleteBookEntity() {
        runBlocking {
            val expected = BookDbTracker(db,2564621L)
            expected.addTag()
            expected.addTag(TagEntity.SELECTED)
            expected.addTag(TagEntity.SELECTED)
            expected.addTag(0)

            expected.addBooks("AddBooks", 20)
            val bookDao = db.getBookDao()

            for (b in ArrayList<BookAndAuthors>().apply {
                addAll(expected.bookEntities.entities)
            }) {
                if (b.book.isSelected)
                    expected.unlinkBook(b)
            }
            bookDao.deleteSelected(null,null)
            expected.checkDatabase("Delete Selected")

            var occurrance = 0
            while (expected.bookEntities.size > 0) {
                ++occurrance
                val count = expected.random.nextInt(4).coerceAtMost(expected.bookEntities.size)
                val bookIds = Array<Any>(count) { 0L }
                repeat (count) {
                    val i = expected.random.nextInt(expected.bookEntities.size)
                    val book = expected.bookEntities[i]
                    bookIds[it] = book.book.id
                    expected.unlinkBook(book)
                }
                bookDao.deleteSelected(null, bookIds)
                expected.checkDatabase("Delete $occurrance")
            }
        }
    }

    @Test(timeout = 5000L) fun testAddDeleteBookEntityEmptyFilter() {
        runBlocking {
            val expected = BookDbTracker(db,9922621L)
            expected.addTag()
            expected.addTag(TagEntity.SELECTED)
            expected.addTag(TagEntity.SELECTED)
            expected.addTag(0)

            expected.addBooks("AddBooks", 20)
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

            var occurrance = 0
            while (expected.bookEntities.size > 0) {
                ++occurrance
                val count = expected.random.nextInt(4).coerceAtMost(expected.bookEntities.size)
                val bookIds = Array<Any>(count) { 0L }
                repeat (count) {
                    val i = expected.random.nextInt(expected.bookEntities.size)
                    val book = expected.bookEntities[i]
                    bookIds[it] = book.book.id
                    expected.unlinkBook(book)
                }
                bookDao.deleteSelected(filter, bookIds)
                expected.checkDatabase("Delete $occurrance")
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

    @Test/*(timeout = 5000L)*/ fun testAddDeleteBookEntityWithFilter() {
        runBlocking {
            val expected = BookDbTracker(db,8964521L)
            expected.addTag()
            expected.addTag(TagEntity.SELECTED)
            expected.addTag(TagEntity.SELECTED)
            expected.addTag(0)

            expected.addBooks("AddBooks", 20)
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

            var occurrance = 0
            while (expected.bookEntities.size > keptCount) {
                ++occurrance
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
                expected.checkDatabase("Delete $occurrance Filtered")
            }
        }
    }

    @Test(timeout = 5000L) fun testUpdateBookEntity() {
        runBlocking {
            val expected = BookDbTracker(db, 554321L)
            expected.addTag()
            expected.addTag(TagEntity.SELECTED)
            expected.addTag(TagEntity.SELECTED)
            expected.addTag(0)

            expected.addBooks("AddBooks", 20)

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
            }
        }
    }
}
