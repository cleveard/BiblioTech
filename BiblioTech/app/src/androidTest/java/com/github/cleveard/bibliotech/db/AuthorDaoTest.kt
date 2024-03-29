package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.DisableOnAndroidDebug
import com.github.cleveard.bibliotech.testutils.makeBookAndAuthors
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
class AuthorDaoTest {
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
        val authorDao = db.getAuthorDao()
        val authors = listOf(
            AuthorEntity(id = 0L, lastName = "last1", remainingName = "first1"),
            AuthorEntity(id = 0L, lastName = "last\\", remainingName = "first%"),
            AuthorEntity(id = 0L, lastName = "last_", remainingName = "first\\"),
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

        // Add authors for the books
        suspend fun addAuthors() {
            // Add some authors and books
            var idx = 0
            for (a in authors) {
                assertWithMessage("Add %s", a.name).apply {
                    // Alternate authors between the two books
                    val book = books[idx]
                    idx = idx xor 1
                    // Add an author
                    a.id = 0L
                    authorDao.addWithUndo(book.book.id, a)
                    // Check the id
                    that(a.id).isNotEqualTo(0L)
                    // Look it up by name and check that
                    val foundAuthor = authorDao.findByName(a.lastName, a.remainingName)
                    that(foundAuthor.size).isEqualTo(1)
                    that(foundAuthor[0]).isEqualTo(a)
                    // Look it the book-author link by id and check that
                    val foundBookAndAuthor = authorDao.findById(a.id)
                    that(foundBookAndAuthor.size).isEqualTo(1)
                    that(foundBookAndAuthor[0].authorId).isEqualTo(a.id)
                    that(foundBookAndAuthor[0].bookId).isEqualTo(book.book.id)
                }
            }

            // Make sure adding an existing author doesn't do anything
            val a = authors[2].copy(id = 0L)
            authorDao.addWithUndo(books[0].book.id, a)
            assertWithMessage("Update %s", authors[2].name).that(a).isEqualTo(authors[2])
        }

        var authorList: List<AuthorEntity>?
        // Add the authors for the books
        addAuthors()
        assertWithMessage("Delete keep authors").apply {
            // Delete the authors for books[0]. Check that two book-author links are deleted
            that(authorDao.deleteWithUndo(arrayOf(books[0].book.id), false)).isEqualTo(2)
            // Verify that the authors were kept
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
            // Do it again and verify that nothing was deleted
            that(authorDao.deleteWithUndo(arrayOf(books[0].book.id), false)).isEqualTo(0)
            // Verify that the authors were kept
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
            // Delete the authors for books[0]. Check that one book-author links are deleted
            that(authorDao.deleteWithUndo(arrayOf(books[1].book.id), false)).isEqualTo(1)
            // Verify that the authors were kept
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
        }

        // Add the authors for the books, again
        addAuthors()
        assertWithMessage("Delete delete authors").apply {
            // Delete the authors for books[0]. Check that two book-author links are deleted
            that(authorDao.deleteWithUndo(arrayOf(books[0].book.id))).isEqualTo(2)
            // Verify that the authors were deleted
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(1)
            that(authorList!![0]).isEqualTo(authors[1])
            // Do it again and verify that nothing was deleted
            that(authorDao.deleteWithUndo(arrayOf(books[0].book.id))).isEqualTo(0)
            // Verify that the authors were deleted
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(1)
            that(authorList!![0]).isEqualTo(authors[1])
            // Delete the authors for books[0]. Check that one book-author links are deleted
            that(authorDao.deleteWithUndo(arrayOf(books[1].book.id))).isEqualTo(1)
            authorList = authorDao.get()
            // Verify that the authors were deleted
            that(authorList?.size).isEqualTo(0)
        }

        // Add the authors for the books, again
        addAuthors()
        assertWithMessage("Delete filtered keep authors").apply {
            // Delete the authors for books[0]. Check that nothing is delete, because the filter doesn't match
            that(authorDao.deleteWithUndo(arrayOf(books[0].book.id), false, filter)).isEqualTo(0)
            // Verify that the authors were kept
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
            that(authorDao.deleteWithUndo(arrayOf(books[1].book.id), false, filter)).isEqualTo(1)
            // Verify that the authors were kept
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
            // Delete the authors for books[0]. Check that one book-author links are deleted, because the filter doesn't match
            that(authorDao.deleteWithUndo(arrayOf(books[0].book.id), false)).isEqualTo(2)
            // Verify that the authors were kept
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
        }

        addAuthors()
        assertWithMessage("Delete filtered delete authors").apply {
            // Delete the authors for books[0]. Check that nothing is delete, because the filter doesn't match
            that(authorDao.deleteWithUndo(arrayOf(books[0].book.id), true, filter)).isEqualTo(0)
            // Verify that the authors were kept
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
            // Delete the authors for books[0]. Check that one book-author links are deleted, because the filter doesn't match
            that(authorDao.deleteWithUndo(arrayOf(books[1].book.id), true, filter)).isEqualTo(1)
            // Verify that the authors were deleted
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(2)
            that(authorList!![0]).isEqualTo(authors[0])
            that(authorList!![1]).isEqualTo(authors[2])
            // Delete the authors for books[0] and check the delete count
            that(authorDao.deleteWithUndo(arrayOf(books[0].book.id))).isEqualTo(2)
            // Verify that the authors were deleted
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(0)
        }

        // Add the authors as a list
        authorDao.addWithUndo(books[0].book.id, authors)
        assertWithMessage("Add authors from list").apply {
            // Verify that they are there
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
            // Add the authors as a list for the other book
            authorDao.addWithUndo(books[1].book.id, authors)
            // Verify that they are there
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
            // Delete books[0] authors
            that(authorDao.deleteWithUndo(arrayOf(books[0].book.id))).isEqualTo(3)
            // Authors are still there, because books[1] is referencing them
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
            // Do it again and verify that nothing changed
            that(authorDao.deleteWithUndo(arrayOf(books[0].book.id))).isEqualTo(0)
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(3)
            // Delete books[1] authors
            that(authorDao.deleteWithUndo(arrayOf(books[1].book.id))).isEqualTo(3)
            // Verify that they are gone
            authorList = authorDao.get()
            that(authorList?.size).isEqualTo(0)
        }
    }
}
