package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.getLive
import com.github.cleveard.bibliotech.makeBookAndAuthors
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookTagDaoTest {
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

    @Test(timeout = 1000L) fun testAddUpdateDelete() {
        runBlocking {
            val tagDao = db.getTagDao()
            val bookTagDao = db.getBookTagDao()
            val tags = listOf(
                TagEntity(id = 0L, name = "tag1", desc = "desc1", flags = TagEntity.SELECTED),
                TagEntity(id = 0L, name = "tag\\", desc = "desc\\", flags = 0),
                TagEntity(id = 0L, name = "tag_", desc = "desc_", flags = 0),
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

            // Add tags for the books
            suspend fun addTags(additionalTags: Array<Any>?) {
                // Add some tags and books
                var idx = 0
                for (t in tags) {
                    assertWithMessage("Add %s with additionalTags: %s", t.name, additionalTags).apply {
                        // Alternate tags between the two books
                        val book = books[idx]
                        idx = idx xor 1
                        // Add an tag
                        t.id = 0L
                        tagDao.add(book.book.id, listOf(t), additionalTags)
                        // Check the id
                        that(t.id).isNotEqualTo(0L)
                        // Look it up by name and check that
                        val foundTag = tagDao.findByName(t.name)
                        that(foundTag).isEqualTo(t)
                        // Look it the book-tag link by id and check that
                        val foundBookAndTag = bookTagDao.findById(t.id)
                        that(foundBookAndTag.size).isEqualTo(1)
                        that(foundBookAndTag[0].tagId).isEqualTo(t.id)
                        that(foundBookAndTag[0].bookId).isEqualTo(book.book.id)
                    }
                }

                // Make sure adding an existing tag doesn't do anything
                val t = tags[2].copy(id = 0L)
                tagDao.add(books[0].book.id, listOf(t), null)
                assertWithMessage("Update %s", tags[2].name).that(t).isEqualTo(tags[2])

                var list = bookTagDao.queryBookIds(arrayOf(books[0].book.id))
                assertWithMessage("Check book tags %s", books[0].book.id).apply {
                    that(list?.size).isEqualTo(2)
                    that(list!![0]).isAnyOf(tags[0].id, tags[2].id)
                    that(list!![1]).isAnyOf(tags[0].id, tags[2].id)
                }

                list = bookTagDao.queryBookIds(arrayOf(books[1].book.id))
                assertWithMessage("Check book tags %s", books[1].book.id).apply {
                    that(list?.size).isEqualTo(1)
                    that(list!![0]).isEqualTo(tags[1].id)
                }
            }

            var tagList: List<TagEntity>?
            // Add the tags for the books
            addTags(emptyArray())
            assertWithMessage("Delete keep tags").apply {
                // Delete the tags for books[0]. Check that two book-tag links are deleted
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[0].book.id))).isEqualTo(2)
                // Verify that the tags were kept
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
                // Do it again and verify that nothing was deleted
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[0].book.id))).isEqualTo(0)
                // Verify that the tags were kept
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
                // Delete the tags for books[0]. Check that one book-tag links are deleted
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[1].book.id))).isEqualTo(1)
                // Verify that the tags were kept
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
            }

            // Add the tags for the books, again
            addTags(emptyArray())
            assertWithMessage("Delete delete tags").apply {
                // Delete the tags for books[0]. Check that two book-tag links are deleted
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[0].book.id), true)).isEqualTo(2)
                // Verify that the tags were deleted
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(1)
                that(tagList!![0]).isEqualTo(tags[1])
                // Do it again and verify that nothing was deleted
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[0].book.id), true)).isEqualTo(0)
                // Verify that the tags were deleted
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(1)
                that(tagList!![0]).isEqualTo(tags[1])
                // Delete the tags for books[0]. Check that one book-tag links are deleted
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[1].book.id), true)).isEqualTo(1)
                tagList = getLive(tagDao.getLive())
                // Verify that the tags were deleted
                that(tagList?.size).isEqualTo(0)
            }

            // Add the tags for the books, again
            addTags(emptyArray())
            assertWithMessage("Delete filtered keep tags").apply {
                // Delete the tags for books[0]. Check that nothing is delete, because the filter doesn't match
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[0].book.id), false, filter)).isEqualTo(0)
                // Verify that the tags were kept
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[1].book.id), false, filter)).isEqualTo(1)
                // Verify that the tags were kept
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
                // Delete the tags for books[0]. Check that one book-tag links are deleted, because the filter doesn't match
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[0].book.id))).isEqualTo(2)
                // Verify that the tags were kept
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
            }

            addTags(emptyArray())
            assertWithMessage("Delete filtered delete tags").apply {
                // Delete the tags for books[0]. Check that nothing is delete, because the filter doesn't match
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[0].book.id), true, filter)).isEqualTo(0)
                // Verify that the tags were kept
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
                // Delete the tags for books[0]. Check that one book-tag links are deleted, because the filter doesn't match
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[1].book.id), true, filter)).isEqualTo(1)
                // Verify that the tags were deleted
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(2)
                that(tagList!![0]).isEqualTo(tags[0])
                that(tagList!![1]).isEqualTo(tags[2])
                // Delete the tags for books[0] and check the delete count
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[0].book.id), true)).isEqualTo(2)
                // Verify that the tags were deleted
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(0)
            }

            // Add the tags as a list
            for (t in tags)
                t.id = 0L
            tagDao.add(books[0].book.id, tags, emptyArray())
            assertWithMessage("Add tags from list").apply {
                // Verify that they are there
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
                // Add the tags as a list for the other book
                tagDao.add(books[1].book.id, tags, null)
                // Verify that they are there
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
                // Delete books[0] tags
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[0].book.id), true)).isEqualTo(3)
                // Tags are still there, because books[1] is referencing them
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
                // Do it again and verify that nothing changed
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[0].book.id), true)).isEqualTo(0)
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(3)
                // Delete books[1] tags
                that(bookTagDao.deleteSelectedBooks(arrayOf(books[1].book.id), true)).isEqualTo(3)
                // Verify that they are gone
                tagList = getLive(tagDao.getLive())
                that(tagList?.size).isEqualTo(0)
            }
        }
    }
}
