package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.DisableOnAndroidDebug
import com.github.cleveard.bibliotech.utils.getLive
import com.github.cleveard.bibliotech.makeBookAndAuthors
import com.github.cleveard.bibliotech.testutils.UndoTracker
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BookTagDaoTest {
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

    private suspend fun addBooks(): Array<BookAndAuthors> {
        val books = arrayOf(
            makeBookAndAuthors(1, 0),
            makeBookAndAuthors(2, BookEntity.SELECTED)
        )

        // Add the books
        db.getBookDao().addOrUpdateWithUndo(books[0])
        db.getBookDao().addOrUpdateWithUndo(books[1])
        return books
    }

    private val commonTags = listOf(
        TagEntity(id = 0L, name = "tag1", desc = "desc1", flags = TagEntity.SELECTED),
        TagEntity(id = 0L, name = "tag\\", desc = "desc\\", flags = 0),
        TagEntity(id = 0L, name = "tag_", desc = "desc_", flags = 0),
    )

    // Add tags without books
    private suspend fun addTags(callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)? = null): List<TagEntity> {
        val tags = listOf(*commonTags.map { it.copy() }.toTypedArray())
        val tagDao = db.getTagDao()
        for (t in tags) {
            assertWithMessage("Add %s", t.name).apply {
                tagDao.addWithUndo(t, callback)
                that(t.id).isNotEqualTo(0L)
                that(tagDao.get(t.id)).isEqualTo(t)
                that(tagDao.findByName(t.name)).isEqualTo(t)
            }
        }

        return tags
    }

    // Add tags for the books
    private suspend fun addTags(books: Array<BookAndAuthors>, additionalTags: Array<Any>? = emptyArray()): List<TagEntity> {
        val tags = listOf(*commonTags.map { it.copy() }.toTypedArray())
        val tagDao = db.getTagDao()
        val bookTagDao = db.getBookTagDao()

        // Add some tags and books
        var idx = 0
        for (t in tags) {
            assertWithMessage("Add %s with additionalTags: %s", t.name, additionalTags).apply {
                // Alternate tags between the two books
                val book = books[idx]
                idx = idx xor 1
                // Add an tag
                tagDao.addWithUndo(book.book.id, listOf(t), additionalTags)
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
        tagDao.addWithUndo(books[0].book.id, listOf(t), null)
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

        return tags
    }

    @Test fun testTagAddUpdateDelete() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestTagAddUpdateDelete()
        }
    }

    @Test fun testTagAddUpdateDeleteWithUndo() {
        runBlocking {
            undo.record("TestTagAddUpdateDeleteWithUndo") { doTestTagAddUpdateDelete() }
        }
    }

    private suspend fun doTestTagAddUpdateDelete() {
        val tagDao = db.getTagDao()
        val bookTagDao = db.getBookTagDao()
        val books = addBooks()
        val filter = BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, arrayOf("title2")))
        ).buildFilter(context, arrayOf(BOOK_ID_COLUMN), true)

        var tagList: List<TagEntity>?
        // Add the tags for the books
        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
        var tags = addTags(books, emptyArray())
        assertWithMessage("Delete keep tags").apply {
            // Delete the tags for books[0]. Check that two book-tag links are deleted
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[0].book.id))).isEqualTo(2)
            // Verify that the tags were kept
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
            // Do it again and verify that nothing was deleted
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[0].book.id))).isEqualTo(0)
            // Verify that the tags were kept
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
            // Delete the tags for books[0]. Check that one book-tag links are deleted
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[1].book.id))).isEqualTo(1)
            // Verify that the tags were kept
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
        }

        // Add the tags for the books, again
        tags = addTags(books, emptyArray())
        assertWithMessage("Delete delete tags").apply {
            // Delete the tags for books[0]. Check that two book-tag links are deleted
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[0].book.id), true)).isEqualTo(2)
            // Verify that the tags were deleted
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(1)
            that(tagList!![0]).isEqualTo(tags[1])
            // Do it again and verify that nothing was deleted
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[0].book.id), true)).isEqualTo(0)
            // Verify that the tags were deleted
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(1)
            that(tagList!![0]).isEqualTo(tags[1])
            // Delete the tags for books[0]. Check that one book-tag links are deleted
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[1].book.id), true)).isEqualTo(1)
            tagList = tagDao.getLive().getLive()
            // Verify that the tags were deleted
            that(tagList?.size).isEqualTo(0)
        }

        // Add the tags for the books, again
        tags = addTags(books, emptyArray())
        assertWithMessage("Delete filtered keep tags").apply {
            // Delete the tags for books[0]. Check that nothing is delete, because the filter doesn't match
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[0].book.id), false, filter)).isEqualTo(0)
            // Verify that the tags were kept
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[1].book.id), false, filter)).isEqualTo(1)
            // Verify that the tags were kept
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
            // Delete the tags for books[0]. Check that one book-tag links are deleted, because the filter doesn't match
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[0].book.id))).isEqualTo(2)
            // Verify that the tags were kept
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
        }

        tags = addTags(books, emptyArray())
        assertWithMessage("Delete filtered delete tags").apply {
            // Delete the tags for books[0]. Check that nothing is delete, because the filter doesn't match
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[0].book.id), true, filter)).isEqualTo(0)
            // Verify that the tags were kept
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
            // Delete the tags for books[0]. Check that one book-tag links are deleted, because the filter doesn't match
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[1].book.id), true, filter)).isEqualTo(1)
            // Verify that the tags were deleted
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(2)
            that(tagList!![0]).isEqualTo(tags[0])
            that(tagList!![1]).isEqualTo(tags[2])
            // Delete the tags for books[0] and check the delete count
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[0].book.id), true)).isEqualTo(2)
            // Verify that the tags were deleted
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(0)
        }

        // Add the tags as a list
        for (t in tags)
            t.id = 0L
        tagDao.addWithUndo(books[0].book.id, tags, emptyArray())
        assertWithMessage("Add tags from list").apply {
            // Verify that they are there
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
            // Add the tags as a list for the other book
            tagDao.addWithUndo(books[1].book.id, tags, null)
            // Verify that they are there
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
            // Delete books[0] tags
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[0].book.id), true)).isEqualTo(3)
            // Tags are still there, because books[1] is referencing them
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
            // Do it again and verify that nothing changed
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[0].book.id), true)).isEqualTo(0)
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(3)
            // Delete books[1] tags
            that(bookTagDao.deleteSelectedBooksWithUndo(arrayOf(books[1].book.id), true)).isEqualTo(3)
            // Verify that they are gone
            tagList = tagDao.getLive().getLive()
            that(tagList?.size).isEqualTo(0)
        }
    }

    @Test fun testLinkAddDelete() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestLinkAddDelete()
        }
    }

    @Test fun testLinkAddDeleteWithUndo() {
        runBlocking {
            undo.record("TestLinkAddDeleteWithUndo") { doTestLinkAddDelete() }
        }
    }

    private suspend fun doTestLinkAddDelete() {
        val books = addBooks()
        val tags = addTags(null)

        val bookTagDao = db.getBookTagDao()
        class TagLink(
            val tag: TagEntity,
            val bookAndTag: BookAndTagEntity
        )
        class Link(
            val book: BookAndAuthors,
            val tagLink: Array<TagLink>
        )
        val links = arrayOf(
            Link(books[0], arrayOf(
                TagLink(tags[0], BookAndTagEntity(0L, tags[0].id, books[0].book.id)),
                    TagLink(tags[2], BookAndTagEntity(0L, tags[2].id, books[0].book.id)))
            ),
            Link(books[1], arrayOf(
                TagLink(tags[1], BookAndTagEntity(0L, tags[1].id, books[1].book.id)))
            )
        )
        val filter = BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, arrayOf("title2")))
        ).buildFilter(context, arrayOf(BOOK_ID_COLUMN), true)

        suspend fun addLinks() {
            for (l in links) {
                val title = l.book.book.title
                for (t in l.tagLink) {
                    assertWithMessage("Add Link %s-%s", title, t.tag.name).apply {
                        val oldId = t.bookAndTag.id
                        t.bookAndTag.id = 0
                        t.bookAndTag.id = bookTagDao.add(t.bookAndTag)
                        if (t.bookAndTag.id < 0)
                            t.bookAndTag.id = oldId
                        that(t.bookAndTag.id).isNotEqualTo(0)
                        // Look it the book-tag link by id and check that
                        val foundBookAndTag = bookTagDao.findById(t.tag.id)
                        that(foundBookAndTag.size).isEqualTo(1)
                        that(foundBookAndTag[0]).isEqualTo(t.bookAndTag)
                    }
                }
            }

            assertWithMessage("Add Links total").that(bookTagDao.queryBookIds(Array(links.size) { links[it].book.book.id})?.size)
                .isEqualTo(links.indices.sumOf { links[it].tagLink.size })

            for (l in links) {
                assertWithMessage("Check book tag links %s", l.book.book.title).apply {
                    val list = bookTagDao.queryBookIds(arrayOf(l.book.book.id))
                    that(list?.size).isEqualTo(l.tagLink.size)
                    val ids = l.tagLink.map { it.tag.id }
                    for (id in list!!) {
                        that(id).isIn(ids)
                    }
                }
            }
        }

        addLinks()
        for (l in links) {
            assertWithMessage("DeleteById for %s", l.book.book.title).apply {
                val ids = Array<Any>(l.tagLink.size) { l.tagLink[it].bookAndTag.id }
                that(bookTagDao.deleteByIdWithUndo(ids)).isEqualTo(ids.size)
                that(bookTagDao.queryBookIds(arrayOf(l.book.book.id))?.size).isEqualTo(0)
            }
        }

        addLinks()
        assertWithMessage("DeleteTagsForBooks using multiple book ids").apply {
            val ids = Array<Any>(books.size) { books[it].book.id }
            that(bookTagDao.deleteTagsForBooksWithUndo(ids)).isEqualTo(links.indices.sumOf { links[it].tagLink.size })
            that(bookTagDao.queryBookIds(ids)?.size).isEqualTo(0)
        }

        suspend fun deleteTagsForBooks(
            deleteIt: suspend (Array<Any>, BookFilter.BuiltFilter?, Array<Any>?, Boolean) -> Int,
            filter: BookFilter.BuiltFilter?, tagIds: Array<Any>?, tagsInvert: Boolean,
            includedTags: Array<Long>, name: String
        ) {
            addLinks()
            val kept = Array(links.size) { ArrayList<Long>() }
            var expected: List<Long> = ArrayList<Long>().apply {
                for ((i, l) in links.withIndex()) {
                    for (t in l.tagLink) {
                        add(t.tag.id)
                        if (!includedTags.contains(t.tag.id))
                            kept[i].add(t.tag.id)
                    }
                }
            }
            for (i in links.size - 1 downTo 0) {
                val l = links[i]
                assertWithMessage("%s book %s", name, l.book.book.title).apply {
                    val ids = arrayOf<Any>(l.book.book.id)
                    that(deleteIt(ids, filter, tagIds, tagsInvert)).isEqualTo(l.tagLink.size - kept[i].size)
                    that(bookTagDao.queryBookIds(ids)?: emptyList<Long>()).containsExactlyElementsIn(kept[i])
                    expected = expected.dropLast(l.tagLink.size)
                    that(bookTagDao.queryBookIds(Array(i) { links[it].book.book.id })?: emptyList<Long>())
                        .containsExactlyElementsIn(expected)
                }
            }
        }

        deleteTagsForBooks(bookTagDao::deleteTagsForBooksWithUndo, null, null, true,
            arrayOf(tags[0].id, tags[1].id, tags[2].id), "DeleteTagsForBooks: no filter, all tags")
        deleteTagsForBooks(bookTagDao::deleteTagsForBooksWithUndo, null, null, false,
            emptyArray(), "DeleteTagsForBooks: no filter, no tags")
        deleteTagsForBooks(bookTagDao::deleteTagsForBooksWithUndo, null, arrayOf(tags[0].id), true,
            arrayOf(tags[1].id, tags[2].id), "DeleteTagsForBooks: no filter, not ${tags[0].name}")
        deleteTagsForBooks(bookTagDao::deleteTagsForBooksWithUndo, null, arrayOf(tags[0].id), false,
            arrayOf(tags[0].id), "DeleteTagsForBooks: no filter, only ${tags[0].name}")
        deleteTagsForBooks(bookTagDao::deleteTagsForBooksWithUndo, filter, null, true,
            arrayOf(tags[1].id), "DeleteTagsForBooks: with filter, all tags")
        deleteTagsForBooks(bookTagDao::deleteTagsForBooksWithUndo, filter, null, false,
            emptyArray(), "DeleteTagsForBooks: with filter, no tags")
        deleteTagsForBooks(bookTagDao::deleteTagsForBooksWithUndo, filter, arrayOf(tags[0].id), true,
            arrayOf(tags[1].id), "DeleteTagsForBooks: with filter, not ${tags[0].name}")
        deleteTagsForBooks(bookTagDao::deleteTagsForBooksWithUndo, filter, arrayOf(tags[0].id), false,
            emptyArray(), "DeleteTagsForBooks: with filter, only ${tags[0].name}")
        deleteTagsForBooks(bookTagDao::deleteSelectedTagsForBooksWithUndo, null, null, true,
            arrayOf(tags[1].id, tags[2].id), "deleteSelectedTagsForBooks: no filter, not selected")
        deleteTagsForBooks(bookTagDao::deleteSelectedTagsForBooksWithUndo, null, null, false,
            arrayOf(tags[0].id), "deleteSelectedTagsForBooks: no filter, selected")
        deleteTagsForBooks(bookTagDao::deleteSelectedTagsForBooksWithUndo, null, arrayOf(tags[0].id), true,
            arrayOf(tags[1].id, tags[2].id), "deleteSelectedTagsForBooks: no filter, not ${tags[0].name}")
        deleteTagsForBooks(bookTagDao::deleteSelectedTagsForBooksWithUndo, null, arrayOf(tags[0].id), false,
            arrayOf(tags[0].id), "deleteSelectedTagsForBooks: no filter, only ${tags[0].name}")
        deleteTagsForBooks(bookTagDao::deleteSelectedTagsForBooksWithUndo, filter, null, true,
            arrayOf(tags[1].id), "deleteSelectedTagsForBooks: with filter, not selected")
        deleteTagsForBooks(bookTagDao::deleteSelectedTagsForBooksWithUndo, filter, null, false,
            emptyArray(), "deleteSelectedTagsForBooks: with filter, selected")
        deleteTagsForBooks(bookTagDao::deleteSelectedTagsForBooksWithUndo, filter, arrayOf(tags[0].id), true,
            arrayOf(tags[1].id), "deleteSelectedTagsForBooks: with filter, not ${tags[0].name}")
        deleteTagsForBooks(bookTagDao::deleteSelectedTagsForBooksWithUndo, filter, arrayOf(tags[0].id), false,
            emptyArray(), "deleteSelectedTagsForBooks: with filter, only ${tags[0].name}")

        addLinks()
        assertWithMessage("DeleteTagsForTags ${tags[0].name} ${tags[1].name}").apply {
            that(bookTagDao.deleteTagsForTagsWithUndo(arrayOf(tags[0].id, tags[1].id))).isEqualTo(2)
            that(bookTagDao.queryBookIds(arrayOf(books[0].book.id))).containsExactly(tags[2].id)
            that(bookTagDao.queryBookIds(arrayOf(books[1].book.id)).isNullOrEmpty()).isTrue()
        }
        assertWithMessage("DeleteTagsForTags ${tags[2].name}").apply {
            that(bookTagDao.deleteTagsForTagsWithUndo(arrayOf(tags[2].id))).isEqualTo(1)
            that(bookTagDao.queryBookIds(arrayOf(books[0].book.id)).isNullOrEmpty()).isTrue()
            that(bookTagDao.queryBookIds(arrayOf(books[1].book.id)).isNullOrEmpty()).isTrue()
        }

        addLinks()
        assertWithMessage("DeleteTagsForTags selected").apply {
            that(bookTagDao.deleteTagsForTagsWithUndo(null)).isEqualTo(1)
            that(bookTagDao.queryBookIds(arrayOf(books[0].book.id))).containsExactly(tags[2].id)
            that(bookTagDao.queryBookIds(arrayOf(books[1].book.id))).containsExactly(tags[1].id)
        }
    }

    @Test fun testAddTagToBook() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestAddTagToBook()
        }
    }

    @Test fun testAddTagToBookWithUndo() {
        runBlocking {
            undo.record("TestAddTagToBookWithUndo") { doTestAddTagToBook() }
        }
    }

    private suspend fun doTestAddTagToBook() {
        val books = addBooks()
        val tags = addTags(null)
        val expectedTagIds = Array<ArrayList<Long>>(books.size) { ArrayList() }
        val expectedLinks = Array<ArrayList<BookAndTagEntity>>(tags.size) { ArrayList() }

        val bookTagDao = db.getBookTagDao()
        for ((i, b) in books.withIndex()) {
            for ((j, t) in tags.withIndex()) {
                val id = bookTagDao.addTagToBookWithUndo(b.book.id, t.id)
                assertWithMessage("AddTagToBook %s %s", b.book.title, t.name)
                    .that(id).isNotEqualTo(0)
                expectedTagIds[i].add(t.id)
                expectedLinks[j].add(BookAndTagEntity(id, t.id, b.book.id))
                for ((k, bb) in books.withIndex()) {
                    assertWithMessage("AddTagToBook %s %s for %s", b.book.title, t.name, bb.book.title)
                        .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                        .containsExactlyElementsIn(expectedTagIds[k])
                }
                for ((k, tt) in tags.withIndex()) {
                    assertWithMessage("AddTagToBook %s %s for %s", b.book.title, t.name, t.name)
                        .that(bookTagDao.queryTags(arrayOf(tt.id))?: emptyList<BookAndTagEntity>())
                        .containsExactlyElementsIn(expectedLinks[k])
                }
            }
        }
    }

    @Test fun testAddTagsToBook() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestAddTagsToBook()
        }
    }

    @Test fun testAddTagsToBookWithUndo() {
        runBlocking {
            undo.record("TestAddTagsToBookWithUndo") { doTestAddTagsToBook() }
        }
    }

    private suspend fun doTestAddTagsToBook() {
        val books = addBooks()
        val tags = addTags(null)
        val expected = Array<ArrayList<Long>>(books.size) { ArrayList() }
        val groups = arrayOf(listOf(tags[0].id), listOf(tags[1].id, tags[2].id))

        val bookTagDao = db.getBookTagDao()
        for ((i, b) in books.withIndex()) {
            for (g in groups) {
                bookTagDao.addTagsToBookWithUndo(b.book.id, g)
                expected[i].addAll(g)
                for ((j, bb) in books.withIndex()) {
                    assertWithMessage("AddTagsToBook %s %s for %s", b.book.title, g, bb.book.title)
                        .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                        .containsExactlyElementsIn(expected[j])
                }
            }
        }
    }

    @Test fun testAddTagsToBooks() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestAddTagsToBooks()
        }
    }

    @Test fun testAddTagsToBooksWithUndo() {
        runBlocking {
            undo.record("TestAddTagsToBooksWithUndo") { doTestAddTagsToBooks() }
        }
    }

    private suspend fun doTestAddTagsToBooks() {
        val books = addBooks()
        val tags = addTags(null)
        val expected = ArrayList<Long>()
        val groups = arrayOf(listOf(tags[0].id), listOf(tags[1].id, tags[2].id))

        val bookTagDao = db.getBookTagDao()
        for (g in groups) {
            bookTagDao.addTagsToBooksWithUndo(books.map { it.book.id }, g)
            expected.addAll(g)
            for (bb in books) {
                assertWithMessage("AddTagsToBooks %s for %s", g[0], bb.book.title)
                    .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                    .containsExactlyElementsIn(expected)
            }
        }
    }

    @Test fun testAddTagsToBooksNullSelected() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestAddTagsToBooksNullSelected()
        }
    }

    @Test fun testAddTagsToBooksNullSelectedWithUndo() {
        runBlocking {
            undo.record("TestAddTagsToBooksNullSelectedWithUndo") { doTestAddTagsToBooksNullSelected() }
        }
    }

    private suspend fun doTestAddTagsToBooksNullSelected() {
        val books = addBooks()
        val bookIds = books.map { it.book.id }.toTypedArray<Any>()
        val tags = addTags(null)
        val filter1 = BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, arrayOf("title1")))
        ).buildFilter(context, arrayOf(BOOK_ID_COLUMN), true)
        val filter2 = BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, arrayOf("title2")))
        ).buildFilter(context, arrayOf(BOOK_ID_COLUMN), true)
        val expected = Array<ArrayList<Any>>(books.size) { ArrayList() }
        val groups = arrayOf(arrayOf<Any>(tags[0].id), arrayOf<Any>(tags[1].id, tags[2].id))
        val bookTagDao = db.getBookTagDao()

        suspend fun deleteAll() {
            bookTagDao.deleteTagsForBooksWithUndo(bookIds)
            for (e in expected)
                e.clear()
            for ((i, bb) in books.withIndex()) {
                assertWithMessage("DeleteAll for %s", bb.book.title)
                    .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                    .containsExactlyElementsIn(expected[i])
            }
        }

        for (g in groups) {
            bookTagDao.addTagsToBooksWithUndo(bookIds, g, null)
            expected[0].addAll(g)
            for (bb in books) {
                assertWithMessage("AddTagsToBooks no filter %s for %s", g[0], bb.book.title)
                    .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                    .containsExactlyElementsIn(expected[0])
            }
        }
        deleteAll()

        for (g in groups) {
            bookTagDao.addTagsToBooksWithUndo(bookIds, g, filter1)
            expected[0].addAll(g)
            for ((i, bb) in books.withIndex()) {
                assertWithMessage("AddTagsToBooks with filter1 %s for %s", g[0], bb.book.title)
                    .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                    .containsExactlyElementsIn(expected[i])
            }
        }
        deleteAll()

        for (g in groups) {
            bookTagDao.addTagsToBooksWithUndo(bookIds, g, filter2)
            expected[1].addAll(g)
            for ((i, bb) in books.withIndex()) {
                assertWithMessage("AddTagsToBooks with filter2 %s for %s", g[0], bb.book.title)
                    .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                    .containsExactlyElementsIn(expected[i])
            }
        }
        deleteAll()

        for (g in groups) {
            bookTagDao.addTagsToBooksWithUndo(null, g, null)
            expected[1].addAll(g)
            for ((i, bb) in books.withIndex()) {
                assertWithMessage("AddTagsToBooks selected books no filter %s for %s", g[0], bb.book.title)
                    .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                    .containsExactlyElementsIn(expected[i])
            }
        }
        deleteAll()

        for (g in groups) {
            bookTagDao.addTagsToBooksWithUndo(null, g, filter1)
            for ((i, bb) in books.withIndex()) {
                assertWithMessage("AddTagsToBooks selected books with filter1 %s for %s", g[0], bb.book.title)
                    .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                    .containsExactlyElementsIn(expected[i])
            }
        }
        deleteAll()

        for (g in groups) {
            bookTagDao.addTagsToBooksWithUndo(null, g, filter2)
            expected[1].addAll(g)
            for ((i, bb) in books.withIndex()) {
                assertWithMessage("AddTagsToBooks selected books with filter2 %s for %s", g[0], bb.book.title)
                    .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                    .containsExactlyElementsIn(expected[i])
            }
        }
        deleteAll()

        bookTagDao.addTagsToBooksWithUndo(bookIds, null, null)
        expected[0].add(tags[0].id)
        for (bb in books) {
            assertWithMessage("AddTagsToBooks selected tags no filter for %s", bb.book.title)
                .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                .containsExactlyElementsIn(expected[0])
        }
        deleteAll()

        bookTagDao.addTagsToBooksWithUndo(bookIds, null, filter1)
        expected[0].add(tags[0].id)
        for ((i, bb) in books.withIndex()) {
            assertWithMessage("AddTagsToBooks selected tags with filter1 for %s", bb.book.title)
                .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                .containsExactlyElementsIn(expected[i])
        }
        deleteAll()

        bookTagDao.addTagsToBooksWithUndo(bookIds, null, filter2)
        expected[1].add(tags[0].id)
        for ((i, bb) in books.withIndex()) {
            assertWithMessage("AddTagsToBooks selected tags with filter2 for %s", bb.book.title)
                .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                .containsExactlyElementsIn(expected[i])
        }
        deleteAll()

        bookTagDao.addTagsToBooksWithUndo(null, null, null)
        expected[1].add(tags[0].id)
        for ((i, bb) in books.withIndex()) {
            assertWithMessage("AddTagsToBooks selected books selected tags no filter for %s", bb.book.title)
                .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                .containsExactlyElementsIn(expected[i])
        }
        deleteAll()

        bookTagDao.addTagsToBooksWithUndo(null, null, filter1)
        for ((i, bb) in books.withIndex()) {
            assertWithMessage("AddTagsToBooks selected books selected tags with filter1 for %s", bb.book.title)
                .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                .containsExactlyElementsIn(expected[i])
        }
        deleteAll()

        bookTagDao.addTagsToBooksWithUndo(null, null, filter2)
        expected[1].add(tags[0].id)
        for ((i, bb) in books.withIndex()) {
            assertWithMessage("AddTagsToBooks selected books selected tags with filter2 for %s", bb.book.title)
                .that(bookTagDao.queryBookIds(arrayOf(bb.book.id))?: emptyList<Long>())
                .containsExactlyElementsIn(expected[i])
        }
        deleteAll()
    }
}
