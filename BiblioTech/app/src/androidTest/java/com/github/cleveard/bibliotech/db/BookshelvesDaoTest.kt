package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.DisableOnAndroidDebug
import com.github.cleveard.bibliotech.testutils.UndoTracker
import com.github.cleveard.bibliotech.utils.getLive
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.*
import org.junit.*
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BookshelvesDaoTest {
    private lateinit var db: BookDatabase
    private lateinit var undo: UndoTracker
    private lateinit var context: Context

    @Before fun startUp() {
        context = ApplicationProvider.getApplicationContext()
        BookDatabase.initialize(context, true)
        db = BookDatabase.db
        undo = UndoTracker(db.getUndoRedoDao())
    }

    @After fun tearDown() {
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
            undo.record("TestAddUpdateDeleteWithUndo", false) { doTestAddUpdateDelete() }
        }
    }

    private suspend fun doTestAddUpdateDelete()
    {
        // Add a couple of shelves
        val shelves = listOf(
            Bookshelf(0L, 1000, "bookshelf1", "desc1", "", 0L, 0L, 0L),
            Bookshelf(0L, 1001, "bookshelf\\", "desc\\", "", 0L, 0L, 0L),
            Bookshelf(0L, 1002, "bookshelf_", "desc_", "", 0L, 0L, 0L),
        )

        // Check Add
        val bookshelvesDao = db.getBookshelvesDao()

        for (s in shelves) {
            assertWithMessage("Add %s", s.title).apply {
                bookshelvesDao.add(s)
                that(s.id).isNotEqualTo(0L)
                that(bookshelvesDao.getShelfAndTag(s.id)).isEqualTo(BookshelfAndTag(s, null))
            }
        }

        // Check that we can update last sync volumes
        val volumes = setOf("volume1, volume2, volume3")
        assertWithMessage("update last sync volumes").apply {
            that(bookshelvesDao.updateLastShelfVolumes(shelves[0].id, 4L, volumes)).isEqualTo(1)
            that(bookshelvesDao.getLastShelfVolumes(shelves[0].id)).isEqualTo(volumes)
            that(bookshelvesDao.getShelfAndTag(shelves[0].id)?.bookshelf?.booksLastUpdate).isEqualTo(4L)
        }

        // Verify that we can update
        val update = shelves[2].copy()
        // Change the title
        update.title = "bookshelf%"
        assertWithMessage("Name Change %s", update.title).apply {
            bookshelvesDao.updateBookshelf(update)   // Don't expect a conflict
            that(update.id).isEqualTo(shelves[2].id)
            that(bookshelvesDao.getShelfAndTag(update.id)).isEqualTo(BookshelfAndTag(update, null))
        }

        // Make sure we can delete using id
        bookshelvesDao.delete(BookshelfAndTag(update, null))
        assertWithMessage("Delete %s", update.title).that(bookshelvesDao.getShelfAndTag(update.id)).isNull()
    }

    @Test fun testQueries() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestQueries()
        }
    }

    @Test fun testQueriesWithUndo() {
        runBlocking {
            undo.record("TestQueriesWithUndo", false) { doTestQueries() }
        }
    }

    private suspend fun doTestQueries() {
        // Add a couple of shelves
        val shelves = listOf(
            Bookshelf(0L, 1000, "bookshelf1", "desc1", "", 0L, 0L, 0L),
            Bookshelf(0L, 1001, "bookshelf\\", "desc\\", "", 0L, 0L, 0L),
            Bookshelf(0L, 1002, "bookshelf_", "desc_", "", 0L, 0L, 0L),
        )

        // Check Add
        val bookshelvesDao = db.getBookshelvesDao()
        for (s in shelves) {
            assertWithMessage("Add %s", s.title).apply {
                bookshelvesDao.add(s)
                that(s.id).isNotEqualTo(0L)
                that(bookshelvesDao.getShelfAndTag(s.id)).isEqualTo(BookshelfAndTag(s, null))
            }
        }

        // Check the paging source get
        assertWithMessage("PagingSource").apply {
            val page = bookshelvesDao.getShelvesAndTags()
            val result = page.load(
                PagingSource.LoadParams.Refresh(
                    key = 0,
                    loadSize = shelves.size,
                    placeholdersEnabled = false
                )
            )
            that(result is PagingSource.LoadResult.Page).isTrue()
            result as PagingSource.LoadResult.Page<Int, BookshelfAndTag>
            that(result.data.size).isEqualTo(shelves.size)
            for (i in shelves.indices)
                that(result.data[i]).isEqualTo(BookshelfAndTag(shelves[i], null))
        }

        var bookshelvesList: List<BookshelfAndTag>?
        // Check the live list
        assertWithMessage("getLive").apply {
            bookshelvesList = bookshelvesDao.getShelvesAndTagsLive().getLive()
            that(bookshelvesList?.size).isEqualTo(shelves.size)
            for (i in shelves.indices)
                that(bookshelvesList?.get(i)).isEqualTo(BookshelfAndTag(shelves[i], null))
        }

        // Check the normal list
        assertWithMessage("getLive").apply {
            bookshelvesList = bookshelvesDao.getShelves()
            that(bookshelvesList.size).isEqualTo(shelves.size)
            for (i in shelves.indices)
                that(bookshelvesList.get(i)).isEqualTo(BookshelfAndTag(shelves[i], null))
        }

        // Find by shelf id
        for (s in shelves) {
            assertWithMessage("find by shelf id %s", s.bookshelfId).apply {
                that(bookshelvesDao.findBookshelfByBookshelfId(s.bookshelfId)).isEqualTo(s)
            }
        }
    }

    @Test fun testToggleLink() {
        runBlocking {
            db.getUndoRedoDao().setMaxUndoLevels(0)
            doTestToggleLink()
        }
    }

    @Test fun testToggleLinkWithUndo() {
        runBlocking {
            undo.record("TestBitChanges") { doTestToggleLink() }
        }
    }

    private suspend fun doTestToggleLink() {
        // Add a couple of shelves
        val shelves = listOf(
            BookshelfAndTag(Bookshelf(0L, 1000, "bookshelf1", "desc1", "", 0L, 0L, 0L), null),
            BookshelfAndTag(Bookshelf(0L, 1001, "bookshelf\\", "desc\\", "", 0L, 0L, 0L), null),
            BookshelfAndTag(Bookshelf(0L, 1002, "bookshelf_", "desc_", "", 0L, 0L, 0L), null)
        )
        val tag1 = TagEntity(0L, "bookshelf\\", "desc\\", 0, null)
        val tag2 = TagEntity(0L, "bookshelf_", "desc_", 0, null)

        // Check Add
        val bookshelvesDao = db.getBookshelvesDao()
        val tagDao = db.getTagDao()
        for (s in shelves) {
            assertWithMessage("Add %s", s.bookshelf.title).apply {
                bookshelvesDao.add(s.bookshelf)
                that(s.bookshelf.id).isNotEqualTo(0L)
                that(bookshelvesDao.getShelfAndTag(s.bookshelf.id)).isEqualTo(s)
            }
        }
        assertWithMessage("Add tag %s", tag1.name).apply {
            tagDao.addWithUndo(tag1)
            that(tag1.id).isNotEqualTo(0L)
            that(tagDao.get(tag1.id)).isEqualTo(tag1)
            that(tagDao.findByName(tag1.name)).isEqualTo(tag1)
        }

        // Check toggle link
        assertWithMessage("toggle %s", shelves[1].bookshelf.title).apply {
            bookshelvesDao.toggleTagAndBookshelfLink(shelves[1])
            tag1.id = shelves[1].tag!!.id
            tag1.bookshelf = shelves[1].bookshelf.id
            that(shelves[1]).isEqualTo(BookshelfAndTag(shelves[1].bookshelf, tag1))
            that(bookshelvesDao.getShelfAndTag(shelves[1].bookshelf.id)).isEqualTo(shelves[1])
            bookshelvesDao.toggleTagAndBookshelfLink(shelves[1])
            that(shelves[1].tag).isNull()
        }

        assertWithMessage("toggle %s", shelves[2].bookshelf.title).apply {
            bookshelvesDao.toggleTagAndBookshelfLink(shelves[2])
            tag2.id = shelves[2].tag!!.id
            tag2.bookshelf = shelves[2].bookshelf.id
            that(shelves[2]).isEqualTo(BookshelfAndTag(shelves[2].bookshelf, tag2))
            that(bookshelvesDao.getShelfAndTag(shelves[2].bookshelf.id)).isEqualTo(shelves[2])
            bookshelvesDao.toggleTagAndBookshelfLink(shelves[2])
            that(shelves[2].tag).isNull()
        }
    }
}
