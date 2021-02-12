package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.makeBookAndAuthors
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {
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

    @Test(timeout = 1000L) fun testAddUpdateDelete()
    {
        runBlocking {
            val categoryDao = db.getCategoryDao()
            val categories = listOf(
                CategoryEntity(id = 0L, category = "cat1"),
                CategoryEntity(id = 0L, category = "cat\\"),
                CategoryEntity(id = 0L, category = "cat%"),
            )
            val books = arrayOf(
                makeBookAndAuthors(1),
                makeBookAndAuthors(2)
            )
            val filter = BookFilter(emptyArray(), arrayOf(
                FilterField(Column.TITLE, Predicate.ONE_OF, arrayOf("title2")))
            ).buildFilter(context, arrayOf(BOOK_ID_COLUMN), true)

            // Add the books
            db.getBookDao().addOrUpdate(books[0])
            db.getBookDao().addOrUpdate(books[1])

            // Add categories for the books
            suspend fun addCategories() {
                // Add some categories and books
                var idx = 0
                for (c in categories) {
                    assertWithMessage("Add %s", c.category).apply {
                        // Alternate categories between the two books
                        val book = books[idx]
                        idx = idx xor 1
                        // Add an category
                        c.id = 0L
                        categoryDao.add(book.book.id, c)
                        // Check the id
                        that(c.id).isNotEqualTo(0L)
                        // Look it up by name and check that
                        val foundCategory = categoryDao.findByName(c.category)
                        that(foundCategory.size).isEqualTo(1)
                        that(foundCategory[0]).isEqualTo(c)
                        // Look it the book-category link by id and check that
                        val foundBookAndCategory = categoryDao.findById(c.id)
                        that(foundBookAndCategory.size).isEqualTo(1)
                        that(foundBookAndCategory[0].categoryId).isEqualTo(c.id)
                        that(foundBookAndCategory[0].bookId).isEqualTo(book.book.id)
                    }
                }

                // Make sure adding an existing category doesn't do anything
                val c = categories[2].copy(id = 0L)
                categoryDao.add(books[0].book.id, c)
                assertWithMessage("Update %s", categories[2].category).that(c).isEqualTo(categories[2])
            }

            var categoryList: List<CategoryEntity>?
            // Add the categories for the books
            addCategories()
            assertWithMessage("Delete keep categories").apply {
                // Delete the categories for books[0]. Check that two book-category links are deleted
                that(categoryDao.delete(arrayOf(books[0].book.id), false)).isEqualTo(2)
                // Verify that the categories were kept
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
                // Do it again and verify that nothing was deleted
                that(categoryDao.delete(arrayOf(books[0].book.id), false)).isEqualTo(0)
                // Verify that the categories were kept
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
                // Delete the categories for books[0]. Check that one book-category links are deleted
                that(categoryDao.delete(arrayOf(books[1].book.id), false)).isEqualTo(1)
                // Verify that the categories were kept
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
            }

            // Add the categories for the books, again
            addCategories()
            assertWithMessage("Delete delete categories").apply {
                // Delete the categories for books[0]. Check that two book-category links are deleted
                that(categoryDao.delete(arrayOf(books[0].book.id))).isEqualTo(2)
                // Verify that the categories were deleted
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(1)
                that(categoryList!![0]).isEqualTo(categories[1])
                // Do it again and verify that nothing was deleted
                that(categoryDao.delete(arrayOf(books[0].book.id))).isEqualTo(0)
                // Verify that the categories were deleted
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(1)
                that(categoryList!![0]).isEqualTo(categories[1])
                // Delete the categories for books[0]. Check that one book-category links are deleted
                that(categoryDao.delete(arrayOf(books[1].book.id))).isEqualTo(1)
                categoryList = categoryDao.get()
                // Verify that the categories were deleted
                that(categoryList?.size).isEqualTo(0)
            }

            // Add the categories for the books, again
            addCategories()
            assertWithMessage("Delete filtered keep categories").apply {
                // Delete the categories for books[0]. Check that nothing is delete, because the filter doesn't match
                that(categoryDao.delete(arrayOf(books[0].book.id), false, filter))
                    .isEqualTo(0)
                // Verify that the categories were kept
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
                that(categoryDao.delete(arrayOf(books[1].book.id), false, filter))
                    .isEqualTo(1)
                // Verify that the categories were kept
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
                // Delete the categories for books[0]. Check that one book-category links are deleted, because the filter doesn't match
                that(categoryDao.delete(arrayOf(books[0].book.id), false)).isEqualTo(2)
                // Verify that the categories were kept
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
            }

            addCategories()
            assertWithMessage("Delete filtered delete categories").apply {
                // Delete the categories for books[0]. Check that nothing is delete, because the filter doesn't match
                that(categoryDao.delete(arrayOf(books[0].book.id), true, filter))
                    .isEqualTo(0)
                // Verify that the categories were kept
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
                // Delete the categories for books[0]. Check that one book-category links are deleted, because the filter doesn't match
                that(categoryDao.delete(arrayOf(books[1].book.id), true, filter))
                    .isEqualTo(1)
                // Verify that the categories were deleted
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(2)
                that(categoryList!![0]).isAnyOf(categories[0], categories[2])
                that(categoryList!![1]).isAnyOf(categories[0], categories[2])
                // Delete the categories for books[0] and check the delete count
                that(categoryDao.delete(arrayOf(books[0].book.id))).isEqualTo(2)
                // Verify that the categories were deleted
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(0)
            }

            // Add the categories as a list
            categoryDao.add(books[0].book.id, categories)
            assertWithMessage("Add categories from array").apply {
                // Verify that they are there
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
                // Add the categories as a list for the other book
                categoryDao.add(books[1].book.id, categories)
                // Verify that they are there
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
                // Delete books[0] categories
                that(categoryDao.delete(arrayOf(books[0].book.id))).isEqualTo(3)
                // Categories are still there, because books[1] is referencing them
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
                // Do it again and verify that nothing changed
                that(categoryDao.delete(arrayOf(books[0].book.id))).isEqualTo(0)
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(3)
                // Delete books[1] categories
                that(categoryDao.delete(arrayOf(books[1].book.id))).isEqualTo(3)
                // Verify that they are gone
                categoryList = categoryDao.get()
                that(categoryList?.size).isEqualTo(0)
            }
        }
    }
}
