package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.makeBookAndAuthors
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthorDaoTest {
    private lateinit var db: BookDatabase
    private lateinit var context: Context

    @Before
    fun startUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        BookDatabase.initialize(context, true)
        db = BookDatabase.db
    }

    @After
    fun tearDown() {
        BookDatabase.close()
    }

    @Test(timeout = 1000L) fun testAddUpdateDelete()
    {
        runBlocking {
            val authorDao = db.getAuthorDao()
            val authors = listOf(
                AuthorEntity(id = 0L, lastName = "last1", remainingName = "first1"),
                AuthorEntity(id = 0L, lastName = "last2", remainingName = "first2"),
                AuthorEntity(id = 0L, lastName = "last3", remainingName = "first3"),
            )
            val books = arrayOf(
                makeBookAndAuthors(1),
                makeBookAndAuthors(2)
            )
            val filter = BookFilter(emptyArray(), arrayOf(
                FilterField(Column.TITLE, Predicate.ONE_OF, arrayOf("title2")))
            ).buildFilter(context, arrayOf(BOOK_ID_COLUMN))

            // Add the books
            db.getBookDao().addOrUpdate(books[0])
            db.getBookDao().addOrUpdate(books[1])

            // Add authors for the books
            suspend fun addAuthors() {
                // Add some authors and books
                var idx = 0
                for (a in authors) {
                    // Alternate authors between the two books
                    val book = books[idx]
                    idx = idx xor 1
                    // Add an author
                    a.id = 0L
                    authorDao.add(book.book.id, a)
                    // Check the id
                    assertThat(a.id).isNotEqualTo(0L)
                    // Look it up by name and check that
                    val foundAuthor = authorDao.findByName(a.lastName, a.remainingName)
                    assertThat(foundAuthor.size).isEqualTo(1)
                    assertThat(foundAuthor[0]).isEqualTo(a)
                    // Look it the book-author link by id and check that
                    val foundBookAndAuthor = authorDao.findById(a.id)
                    assertThat(foundBookAndAuthor.size).isEqualTo(1)
                    assertThat(foundBookAndAuthor[0].authorId).isEqualTo(a.id)
                    assertThat(foundBookAndAuthor[0].bookId).isEqualTo(book.book.id)
                }

                // Make sure adding an existing author doesn't do anything
                val a = authors[0].copy(id = 0L)
                authorDao.add(books[0].book.id, a)
                assertThat(a).isEqualTo(authors[0])
            }

            // Add the authors for the books
            addAuthors()
            // Delete the authors for books[0]. Check that two book-author links are deleted
            assertThat(authorDao.delete(arrayOf(books[0].book.id), false)).isEqualTo(2)
            // Verify that the authors were kept
            var authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)
            // Do it again and verify that nothing was deleted
            assertThat(authorDao.delete(arrayOf(books[0].book.id), false)).isEqualTo(0)
            // Verify that the authors were kept
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)
            // Delete the authors for books[0]. Check that one book-author links are deleted
            assertThat(authorDao.delete(arrayOf(books[1].book.id), false)).isEqualTo(1)
            // Verify that the authors were kept
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)

            // Add the authors for the books, again
            addAuthors()
            // Delete the authors for books[0]. Check that two book-author links are deleted
            assertThat(authorDao.delete(arrayOf(books[0].book.id))).isEqualTo(2)
            // Verify that the authors were deleted
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(1)
            assertThat(authorList!![0]).isEqualTo(authors[1])
            // Do it again and verify that nothing was deleted
            assertThat(authorDao.delete(arrayOf(books[0].book.id))).isEqualTo(0)
            // Verify that the authors were deleted
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(1)
            assertThat(authorList!![0]).isEqualTo(authors[1])
            // Delete the authors for books[0]. Check that one book-author links are deleted
            assertThat(authorDao.delete(arrayOf(books[1].book.id))).isEqualTo(1)
            authorList = authorDao.get()
            // Verify that the authors were deleted
            assertThat(authorList?.size).isEqualTo(0)

            // Add the authors for the books, again
            addAuthors()
            // Delete the authors for books[0]. Check that nothing is delete, because the filter doesn't match
            assertThat(authorDao.delete(arrayOf(books[0].book.id), false, filter)).isEqualTo(0)
            // Verify that the authors were kept
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)
            assertThat(authorDao.delete(arrayOf(books[1].book.id), false, filter)).isEqualTo(1)
            // Verify that the authors were kept
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)
            // Delete the authors for books[0]. Check that one book-author links are deleted, because the filter doesn match
            assertThat(authorDao.delete(arrayOf(books[0].book.id), false)).isEqualTo(2)
            // Verify that the authors were kept
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)

            addAuthors()
            // Delete the authors for books[0]. Check that nothing is delete, because the filter doesn't match
            assertThat(authorDao.delete(arrayOf(books[0].book.id), true, filter)).isEqualTo(0)
            // Verify that the authors were kept
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)
            // Delete the authors for books[0]. Check that one book-author links are deleted, because the filter doesn match
            assertThat(authorDao.delete(arrayOf(books[1].book.id), true, filter)).isEqualTo(1)
            // Verify that the authors were deleted
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(2)
            assertThat(authorList!![0]).isEqualTo(authors[0])
            assertThat(authorList[1]).isEqualTo(authors[2])
            // Delete the authors for books[0] and check the delete count
            assertThat(authorDao.delete(arrayOf(books[0].book.id))).isEqualTo(2)
            // Verify that the authors were deleted
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(0)

            // Add the authors as a list
            authorDao.add(books[0].book.id, authors)
            // Verify that they are there
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)
            // Add the authors as a list for the other book
            authorDao.add(books[1].book.id, authors)
            // Verify that they are there
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)
            // Delete books[0] authors
            assertThat(authorDao.delete(arrayOf(books[0].book.id))).isEqualTo(3)
            // Authors are still there, because books[1] is referencing them
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)
            // Do it again and verify that nothing changed
            assertThat(authorDao.delete(arrayOf(books[0].book.id))).isEqualTo(0)
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(3)
            // Delete books[1] authors
            assertThat(authorDao.delete(arrayOf(books[1].book.id))).isEqualTo(3)
            // Verify that they are gone
            authorList = authorDao.get()
            assertThat(authorList?.size).isEqualTo(0)
        }
    }
}
