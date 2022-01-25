package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.DisableOnAndroidDebug
import com.github.cleveard.bibliotech.makeBookAndAuthors
import com.github.cleveard.bibliotech.testutils.UndoTracker
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
class IsbnDaoTest {
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
    val timeout = DisableOnAndroidDebug(Timeout(5L, TimeUnit.SECONDS))

    @Test fun testAddUpdateDelete() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestAddUpdateDelete()
        }
    }

    @Test fun testAddUpdateDeleteWithUndo() {
        runBlocking {
            undo.record("TestAddUpdateDeleteWithUndo") { doTestAddUpdateDelete() }
        }
    }

    private suspend fun doTestAddUpdateDelete() {
        val isbnDao = db.getIsbnDao()
        val isbns = listOf(
            IsbnEntity(id = 0L, isbn = "isbn1"),
            IsbnEntity(id = 0L, isbn = "isbn\\"),
            IsbnEntity(id = 0L, isbn = "isbn%"),
        )
        val books = arrayOf(
            makeBookAndAuthors(1),
            makeBookAndAuthors(2)
        )
        val filter = BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, arrayOf("title2")))
        ).buildFilter(context, arrayOf(BOOK_ID_COLUMN), true)

        // Add the books
        db.getBookDao().addOrUpdateWithUndo(books[0])
        db.getBookDao().addOrUpdateWithUndo(books[1])

        // Add isbns for the books
        suspend fun addIsbns() {
            // Add some isbns and books
            var idx = 0
            for (c in isbns) {
                assertWithMessage("Add %s", c.isbn).apply {
                    // Alternate isbns between the two books
                    val book = books[idx]
                    idx = idx xor 1
                    // Add an isbn
                    c.id = 0L
                    isbnDao.addWithUndo(book.book.id, c)
                    // Check the id
                    that(c.id).isNotEqualTo(0L)
                    // Look it up by name and check that
                    val foundIsbn = isbnDao.findByName(c.isbn)
                    that(foundIsbn.size).isEqualTo(1)
                    that(foundIsbn[0]).isEqualTo(c)
                    // Look it the book-isbn link by id and check that
                    val foundBookAndIsbn = isbnDao.findById(c.id)
                    that(foundBookAndIsbn.size).isEqualTo(1)
                    that(foundBookAndIsbn[0].isbnId).isEqualTo(c.id)
                    that(foundBookAndIsbn[0].bookId).isEqualTo(book.book.id)
                }
            }

            // Make sure adding an existing isbn doesn't do anything
            val c = isbns[2].copy(id = 0L)
            isbnDao.addWithUndo(books[0].book.id, c)
            assertWithMessage("Update %s", isbns[2].isbn).that(c).isEqualTo(isbns[2])
        }

        var isbnList: List<IsbnEntity>?
        // Add the isbns for the books
        addIsbns()
        assertWithMessage("Delete keep isbns").apply {
            // Delete the isbns for books[0]. Check that two book-isbn links are deleted
            that(isbnDao.deleteWithUndo(arrayOf(books[0].book.id), false)).isEqualTo(2)
            // Verify that the isbns were kept
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
            // Do it again and verify that nothing was deleted
            that(isbnDao.deleteWithUndo(arrayOf(books[0].book.id), false)).isEqualTo(0)
            // Verify that the isbns were kept
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
            // Delete the isbns for books[0]. Check that one book-isbn links are deleted
            that(isbnDao.deleteWithUndo(arrayOf(books[1].book.id), false)).isEqualTo(1)
            // Verify that the isbns were kept
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
        }

        // Add the isbns for the books, again
        addIsbns()
        assertWithMessage("Delete delete isbns").apply {
            // Delete the isbns for books[0]. Check that two book-isbn links are deleted
            that(isbnDao.deleteWithUndo(arrayOf(books[0].book.id))).isEqualTo(2)
            // Verify that the isbns were deleted
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(1)
            that(isbnList!![0]).isEqualTo(isbns[1])
            // Do it again and verify that nothing was deleted
            that(isbnDao.deleteWithUndo(arrayOf(books[0].book.id))).isEqualTo(0)
            // Verify that the isbns were deleted
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(1)
            that(isbnList!![0]).isEqualTo(isbns[1])
            // Delete the isbns for books[0]. Check that one book-isbn links are deleted
            that(isbnDao.deleteWithUndo(arrayOf(books[1].book.id))).isEqualTo(1)
            isbnList = isbnDao.get()
            // Verify that the isbns were deleted
            that(isbnList?.size).isEqualTo(0)
        }

        // Add the isbns for the books, again
        addIsbns()
        assertWithMessage("Delete filtered keep isbns").apply {
            // Delete the isbns for books[0]. Check that nothing is delete, because the filter doesn't match
            that(isbnDao.deleteWithUndo(arrayOf(books[0].book.id), false, filter))
                .isEqualTo(0)
            // Verify that the isbns were kept
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
            that(isbnDao.deleteWithUndo(arrayOf(books[1].book.id), false, filter))
                .isEqualTo(1)
            // Verify that the isbns were kept
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
            // Delete the isbns for books[0]. Check that one book-isbn links are deleted, because the filter doesn't match
            that(isbnDao.deleteWithUndo(arrayOf(books[0].book.id), false)).isEqualTo(2)
            // Verify that the isbns were kept
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
        }

        addIsbns()
        assertWithMessage("Delete filtered delete isbns").apply {
            // Delete the isbns for books[0]. Check that nothing is delete, because the filter doesn't match
            that(isbnDao.deleteWithUndo(arrayOf(books[0].book.id), true, filter))
                .isEqualTo(0)
            // Verify that the isbns were kept
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
            // Delete the isbns for books[0]. Check that one book-isbn links are deleted, because the filter doesn't match
            that(isbnDao.deleteWithUndo(arrayOf(books[1].book.id), true, filter))
                .isEqualTo(1)
            // Verify that the isbns were deleted
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(2)
            that(isbnList!![0]).isAnyOf(isbns[0], isbns[2])
            that(isbnList!![1]).isAnyOf(isbns[0], isbns[2])
            // Delete the isbns for books[0] and check the delete count
            that(isbnDao.deleteWithUndo(arrayOf(books[0].book.id))).isEqualTo(2)
            // Verify that the isbns were deleted
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(0)
        }

        // Add the isbns as a list
        isbnDao.addWithUndo(books[0].book.id, isbns)
        assertWithMessage("Add isbns from array").apply {
            // Verify that they are there
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
            // Add the isbns as a list for the other book
            isbnDao.addWithUndo(books[1].book.id, isbns)
            // Verify that they are there
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
            // Delete books[0] isbns
            that(isbnDao.deleteWithUndo(arrayOf(books[0].book.id))).isEqualTo(3)
            // Isbns are still there, because books[1] is referencing them
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
            // Do it again and verify that nothing changed
            that(isbnDao.deleteWithUndo(arrayOf(books[0].book.id))).isEqualTo(0)
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(3)
            // Delete books[1] isbns
            that(isbnDao.deleteWithUndo(arrayOf(books[1].book.id))).isEqualTo(3)
            // Verify that they are gone
            isbnList = isbnDao.get()
            that(isbnList?.size).isEqualTo(0)
        }
    }
}
