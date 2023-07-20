package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.DisableOnAndroidDebug
import com.github.cleveard.bibliotech.utils.getLive
import com.github.cleveard.bibliotech.testutils.BookDbTracker
import com.github.cleveard.bibliotech.testutils.BookDbTracker.Companion.nextFlags
import com.github.cleveard.bibliotech.testutils.compareBooks
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class UndoRedoDaoTest {
    private lateinit var repo: BookRepository
    private lateinit var context: Context

    @Before
    fun startUp() {
        context = ApplicationProvider.getApplicationContext()
        BookRepository.initialize(context, true)
        repo = BookRepository.repo
    }

    @After
    fun tearDown() {
        BookRepository.close(context)
    }

    @get:Rule
    val timeout = DisableOnAndroidDebug(Timeout(50L, TimeUnit.SECONDS))

    private suspend fun BookDbTracker.testBookFlags(message: String, bookFilter: BookFilter?) {
        undoTracker.syncUndo(message)
        val bookFlags = repo.bookFlags
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
            that(bookFlags.countBits(bits, value, include, id, filter)).isEqualTo(s.count())
        }

        suspend fun checkCount(bits: Int, value: Int, id: Long?) {
            assertWithMessage("checkCount$message value: value id: id").apply {
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
                val count = bookFlags.changeBits(operation, mask, book?.book?.id, filter)
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

        undoTracker.checkUndo(message)
    }

    @Test fun testBookFlags() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestBookFlags()
        }
    }

    @Test fun testBookFlagsWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestBookFlags()
        }
    }

    private suspend fun doTestBookFlags() {
        val expected = BookDbTracker.addBooks(repo, 552841129L, "Test Book Flags", 20)
        expected.undoTracker.clearUndo("Book Flags")
        expected.testBookFlags("", null)
        assertThat(repo.canUndo()).isFalse()
        assertThat(repo.canRedo()).isFalse()
    }

    @Test fun testBookFlagsFiltered() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestBookFlagsFiltered()
        }
    }

    @Test fun testBookFlagsFilteredWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestBookFlagsFiltered()
        }
    }

    private suspend fun doTestBookFlagsFiltered() {
        val expected = BookDbTracker.addBooks(repo, 1165478L, "Test Book Flags filtered", 20)
        expected.undoTracker.clearUndo("Book Flags Filtered")
        expected.testBookFlags(" filtered", expected.makeFilter())
        assertThat(repo.canUndo()).isFalse()
        assertThat(repo.canRedo()).isFalse()
    }

    private suspend fun BookDbTracker.changeTagBits(message: String, operation: Boolean?, mask: Int, index: Int?, count: Int, filter: BookFilter.BuiltFilter?, vararg values: Int) {
        val tag = index?.let { tables.tagEntities[index] }
        val changed = repo.tagFlags.changeBits(operation, mask, tag?.id, filter)
        assertWithMessage(message).that(changed).isEqualTo(count)
        if (changed > 0) {
            if (tag != null) {
                tag.flags = values[0]
            } else {
                var i = 0
                for (t in tables.tagEntities.entities)
                    t.flags = values[i++]
            }
        }
        checkDatabase(message)
    }

    @Test fun testTagFlags() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestTagFlags()
        }
    }

    @Test fun testTagFlagsWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestTagFlags()
        }
    }

    private suspend fun doTestTagFlags() {
        val expected = BookDbTracker.addBooks(repo, 4455688L, "Test Tag Flags", 0)
        assertThat(repo.canUndo()).isFalse()
        assertThat(repo.canRedo()).isFalse()

        apply {
            // Add a couple of tags
            val tags = listOf(
                TagEntity(0L, "tag1", "desc1", 0b01101),
                TagEntity(0L, "tag2", "desc2", 0b10001),
                TagEntity(0L, "tag3", "desc3", 0b11100),
            )

            // Check Add
            for (t in tags) {
                assertWithMessage("Add %s", t.name).apply {
                    expected.addTag(t)
                    that(t.id).isNotEqualTo(0L)
                    that(repo.getTag(t.id)).isEqualTo(t)
                    expected.undoAndCheck("Test Tag Flags Add ${t.name}")
                    expected.redoAndCheck("Test Tag Flags Add ${t.name}")
                }
            }
        }

        suspend fun checkCount(bits: Int, value: Int, id: Long?, expTrue: Int, expFalse: Int) {
            assertWithMessage("checkCount value: value id: id").apply {
                that(repo.tagFlags.countBits(bits, value, true, id, null)).isEqualTo(expTrue)
                that(repo.tagFlags.countBits(bits, value, false, id, null)).isEqualTo(expFalse)
                that(repo.tagFlags.countBitsLive(bits, value, true, id, null).getLive()).isEqualTo(expTrue)
                that(repo.tagFlags.countBitsLive(bits, value, false, id, null).getLive()).isEqualTo(expFalse)
            }
        }
        checkCount(0b101, 0b001, null, 1, 2)
        checkCount(0b101, 0b101, null, 1, 2)
        checkCount(0b101, 0b101, expected.tables.tagEntities[0].id, 1, 0)
        checkCount(0b101, 0b001, expected.tables.tagEntities[0].id, 0, 1)

        expected.changeTagBits("changeBits single op: false, mask: 0b101", false, 0b101, 0, 1, null, 0b01000)
        expected.changeTagBits("changeBits single op: true, mask: 0b10001", true, 0b10001, 0, 1, null, 0b11001)
        expected.changeTagBits("changeBits single op: null, mask: 0b10100", null, 0b10100, 0, 1, null, 0b01101)
        expected.changeTagBits("changeBits single op: true, mask: 0b01101", true, 0b01101, 0, 0, null, 0b01101)
        expected.changeTagBits("changeBits single op: false, mask: 0b10000", false, 0b10000, 0, 0, null, 0b01101)
        expected.changeTagBits("changeBits all op: false, mask: 0b00100", false, 0b00100, null, 2, null, 0b01001, 0b10001, 0b11000)
        expected.changeTagBits("changeBits all op: true, mask: 0b10001", true, 0b10001, null, 2, null, 0b11001, 0b10001, 0b11001)
        expected.changeTagBits("changeBits all op: null, mask: 0b10100", null, 0b10100, null, expected.tables.tagEntities.size, null, 0b01101, 0b00101, 0b01101)

        expected.undoTracker.checkUndo("Test Tag Flags")
        expected.testRandomUndo("Test Tag Flags Undo", 6)
    }

    @Test fun testGetBooks() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestGetBooks()
        }
    }

    @Test fun testGetBooksWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestGetBooks()
        }
    }

    private suspend fun doTestGetBooks() {
        val expected = BookDbTracker.addBooks(repo,56542358L, "GetBooks", 20)
        assertWithMessage("GetBooks").apply {
            val source = repo.getBooks()
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
            expected.undoTracker.clearUndo("GetBooks")
        }
    }

    @Test fun testGetBooksFiltered() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestGetBooksFiltered()
        }
    }

    @Test fun testGetBooksFilteredWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestGetBooksFiltered()
        }
    }

    private suspend fun doTestGetBooksFiltered() {
        val expected = BookDbTracker.addBooks(repo,565199823L, "GetBooks Filtered", 20)
        assertWithMessage("GetBooks").apply {
            val filter = expected.makeFilter()
            val source = repo.getBooks(filter, context)
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
            expected.undoTracker.clearUndo("GetBooks")
        }
    }

    @Test fun testGetBookList() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestGetBookList()
        }
    }

    @Test fun testGetBookListWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestGetBookList()
        }
    }

    private suspend fun doTestGetBookList() {
        val expected = BookDbTracker.addBooks(repo,56542358L, "GetBookList", 20)
        assertWithMessage("GetBookList").apply {
            val source = repo.getBookList().getLive()
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
            repo.setMaxUndoLevels(0)
            doTestGetBookListFiltered()
        }
    }

    @Test fun testGetBookListFilteredWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestGetBookListFiltered()
        }
    }

    private suspend fun doTestGetBookListFiltered() {
        val expected = BookDbTracker.addBooks(repo,565199823L, "GetBookList Filtered", 20)
        assertWithMessage("GetBookList").apply {
            val filter = expected.makeFilter()
            val source = repo.getBookList(filter, context).getLive()
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

    @Test fun testAddDeleteBookEntity() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestAddDeleteBookEntity(BookDbTracker.addBooks(repo,2564621L, "AddBooks Delete", 20))
        }
    }

    @Test fun testAddDeleteBookEntityWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestAddDeleteBookEntity(BookDbTracker.addBooks(repo,2564621L, "AddBooks Delete", 20))
        }
    }

    private suspend fun doTestAddDeleteBookEntity(expected: BookDbTracker) {
        expected.undoTracker.syncUndo("Delete Books")
        expected.undoStarted()
        val deleted = ArrayList<BookAndAuthors>()
        for (b in ArrayList<BookAndAuthors>().apply {
            addAll(expected.tables.bookEntities.entities)
        }) {
            if (b.book.isSelected) {
                expected.unlinkBook(b)
                deleted.add(b)
            }
        }
        assertWithMessage("Delete Selected").apply {
            that(repo.deleteSelectedBooks(null, null).also {
                expected.undoEnded("Delete Selected", it > 0)
            }).isEqualTo(deleted.size)
            expected.checkDatabase("Delete Selected")
            expected.undoAndCheck("Delete Selected")
            expected.redoAndCheck("Delete Selected")
        }

        var count: Int
        var occurrence = 0
        while (expected.tables.bookEntities.size > 0) {
            ++occurrence
            count = expected.random.nextInt(4).coerceAtMost(expected.tables.bookEntities.size)
            val bookIds = Array<Any>(count) { 0L }
            expected.undoStarted()
            repeat (count) {
                val i = expected.random.nextInt(expected.tables.bookEntities.size)
                val book = expected.tables.bookEntities[i]
                bookIds[it] = book.book.id
                expected.unlinkBook(book)
            }
            assertWithMessage("Deleted $occurrence").apply {
                that(repo.deleteSelectedBooks(null, bookIds).also {
                    expected.undoEnded("Delete $occurrence", it > 0)
                }).isEqualTo(bookIds.size)
                expected.checkDatabase("Delete $occurrence")
                expected.undoAndCheck("Delete $occurrence")
                expected.redoAndCheck("Delete $occurrence")
            }
        }
        expected.testRandomUndo("Delete", 10)
    }

    @Test fun testAddDeleteBookEntityEmptyFilter() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestAddDeleteBookEntityEmptyFilter()
        }
    }

    @Test fun testAddDeleteBookEntityEmptyFilterWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestAddDeleteBookEntityEmptyFilter()
        }
    }

    private suspend fun doTestAddDeleteBookEntityEmptyFilter() {
        val expected = BookDbTracker.addBooks(repo,9922621L, "AddBooks Empty Filter", 20)
        val filter = BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, emptyArray())
        )).buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)

        var count = 0
        expected.undoStarted()
        for (b in ArrayList<BookAndAuthors>().apply {
            addAll(expected.tables.bookEntities.entities)
        }) {
            if (b.book.isSelected) {
                expected.unlinkBook(b)
                ++count
            }
        }
        assertWithMessage("Delete Selected Empty Filter").that(repo.deleteSelectedBooks(filter,null).also {
            expected.undoEnded("Delete Selected Empty Filter", it > 0)
        }).isEqualTo(count)
        expected.checkDatabase("Delete Selected Empty Filter")
        expected.undoAndCheck("Delete Selected Empty Filter")
        expected.redoAndCheck("Delete Selected Empty Filter")

        var occurrence = 0
        while (expected.tables.bookEntities.size > 0) {
            ++occurrence
            count = expected.random.nextInt(4).coerceAtMost(expected.tables.bookEntities.size)
            val bookIds = Array<Any>(count) { 0L }
            expected.undoStarted()
            repeat (count) {
                val i = expected.random.nextInt(expected.tables.bookEntities.size)
                val book = expected.tables.bookEntities[i]
                bookIds[it] = book.book.id
                expected.unlinkBook(book)
            }
            assertWithMessage("Deleted $occurrence Empty Filter").that(repo.deleteSelectedBooks(filter, bookIds).also {
                expected.undoEnded("Delete Selected Empty Filter", it > 0)
            }).isEqualTo(bookIds.size)
            expected.checkDatabase("Delete $occurrence Empty Filter")
            expected.undoAndCheck("Delete $occurrence Empty Filter")
            expected.redoAndCheck("Delete $occurrence Empty Filter")
        }
        expected.testRandomUndo("Delete Empty Filter", 10)
    }

    @Test fun testAddDeleteBookEntityWithFilter() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestAddDeleteBookEntityWithFilter()
        }
    }

    @Test fun testAddDeleteBookEntityWithFilterWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestAddDeleteBookEntityWithFilter()
        }
    }

    private suspend fun doTestAddDeleteBookEntityWithFilter() {
        val expected = BookDbTracker.addBooks(repo,8964521L, "AddBooks Filter", 20)
        val bookFilter = expected.makeFilter()
        val filter = bookFilter.buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
        val keptCount = expected.tables.bookEntities.size - bookFilter.filterList[0].values.size

        var count = 0
        expected.undoStarted()
        for (b in ArrayList<BookAndAuthors>().apply {
            addAll(expected.tables.bookEntities.entities)
        }) {
            if (b.book.isSelected && bookFilter.filterList[0].values.contains(b.book.title)) {
                expected.unlinkBook(b)
                ++count
            }
        }
        assertWithMessage("Delete Selected Filtered").that(repo.deleteSelectedBooks(filter,null).also {
            expected.undoEnded("Delete Selected Filtered", it > 0)
        }).isEqualTo(count)
        expected.checkDatabase("Delete Selected Filtered")
        expected.undoAndCheck("Delete Selected Filtered")
        expected.redoAndCheck("Delete Selected Filtered")

        var occurrence = 0
        while (expected.tables.bookEntities.size > keptCount) {
            ++occurrence
            count = expected.random.nextInt(4).coerceAtMost(expected.tables.bookEntities.size)
            val bookIds = Array<Any>(count) { 0L }
            var size = 0
            expected.undoStarted()
            repeat (count) {
                val i = expected.random.nextInt(expected.tables.bookEntities.size)
                val book = expected.tables.bookEntities[i]
                bookIds[it] = book.book.id
                if (bookFilter.filterList[0].values.contains(book.book.title)) {
                    expected.unlinkBook(book)
                    ++size
                }
            }
            assertWithMessage("Deleted $occurrence Filtered").that(repo.deleteSelectedBooks(filter, bookIds).also {
                expected.undoEnded("Delete Selected Filtered", it > 0)
            }).isEqualTo(size)
            expected.checkDatabase("Delete $occurrence Filtered")
            expected.undoAndCheck("Delete $occurrence Filtered")
            expected.redoAndCheck("Delete $occurrence Filtered")
        }
        expected.testRandomUndo("Delete Filtered", 10)
    }

    @Test fun testUpdateBookEntity() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestUpdateBookEntity()
        }
    }

    @Test fun testUpdateBookEntityWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestUpdateBookEntity()
        }
    }

    private suspend fun doTestUpdateBookEntity() {
        val expected = BookDbTracker.addBooks(repo,554321L, "AddBooks Update", 20)

        repeat (15) {
            val i = expected.random.nextInt(expected.tables.bookEntities.size)
            val old = expected.tables.bookEntities[i]
            val new = expected.tables.bookEntities.new()
            val x = expected.random.nextInt(3)
            if ((x and 1) == 0)
                new.isbns = old.isbns.map { it.copy() }
            if (x > 0) {
                new.book.sourceId = old.book.sourceId
                new.book.volumeId = old.book.volumeId
            }
            new.book.flags = new.book.flags xor expected.random.nextFlags(BookEntity.SERIES or BookEntity.SELECTED or BookEntity.EXPANDED)
            expected.addOneBook("Update ${new.book.title}", new, true)
        }
        expected.testRandomUndo("AddBooks Update", 10)
    }

    @Test fun testUpdatePasses() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestUpdatePasses()
        }
    }

    @Test fun testUpdatePassesWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestUpdatePasses()
        }
    }

    private suspend fun doTestUpdatePasses() {
        // Updating a book that conflicts with two other books will fail
        val expected = BookDbTracker.addBooks(repo, 5668721L, "AddBooks Update", 20)
        expected.undoTracker.syncUndo("Update Fails")
        try {
            val book = expected.tables.bookEntities.new()
            book.isbns = expected.tables.bookEntities[3].isbns.map { it.copy() }
            book.book.sourceId = expected.tables.bookEntities[11].book.sourceId
            book.book.volumeId = expected.tables.bookEntities[11].book.volumeId
            expected.addOneBook("Update Fail", book, true)
        } finally {
            expected.undoTracker.checkUndo("Update Fails")
        }
    }

    @Test fun testGetTags() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestGetTags()
        }
    }

    @Test fun testGetTagsWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestGetTags()
        }
    }

    private suspend fun doTestGetTags() {
        val expected = BookDbTracker.addBooks(repo, 332974L, "Test Get Tags", 0)
        assertThat(repo.canUndo()).isFalse()
        assertThat(repo.canRedo()).isFalse()

        // Add a couple of tags
        val tags = listOf(
            TagEntity(0L, "tag1", "desc1", 0),
            TagEntity(0L, "tag\\", "desc\\", TagEntity.SELECTED),
            TagEntity(0L, "tag_", "desc_", 0),
        )

        // Check Add
        for (t in tags) {
            assertWithMessage("Add %s", t.name).apply {
                expected.addTag(t)
                that(t.id).isNotEqualTo(0L)
                that(repo.getTag(t.id)).isEqualTo(t)
                expected.undoAndCheck("Test Get Tags")
                expected.redoAndCheck("Test Get Tags")
            }
        }

        // Check the paging source get
        assertWithMessage("PagingSource").apply {
            val page = repo.getTags()
            val result = page.load(
                PagingSource.LoadParams.Refresh(
                    key = 0,
                    loadSize = tags.size,
                    placeholdersEnabled = false
                )
            )
            that(result is PagingSource.LoadResult.Page).isTrue()
            result as PagingSource.LoadResult.Page<Int, TagEntity>
            that(result.data.size).isEqualTo(tags.size)
            for (i in tags.indices)
                that(result.data[i]).isEqualTo(tags[i])
        }

        var tagList: List<TagEntity>?
        // Check the live list
        assertWithMessage("getLive").apply {
            tagList = repo.getTagsLive().getLive()
            that(tagList?.size).isEqualTo(tags.size)
            for (i in tags.indices)
                that(tagList?.get(i)).isEqualTo(tags[i])
        }

        // Check the live list, selected
        assertWithMessage("getLive selected").apply {
            tagList = repo.getTagsLive(true).getLive()
            that(tagList?.size).isEqualTo(1)
            that(tagList?.get(0)).isEqualTo(tags[1])
        }
        expected.undoTracker.checkUndo("Test Get Tags")
        expected.testRandomUndo("Test Get Tags", 6)
    }

    @Test fun testAddUpdateDelete() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestAddUpdateDelete()
        }
    }

    @Test fun testAddUpdateDeleteWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestAddUpdateDelete()
        }
    }

    private suspend fun doTestAddUpdateDelete()
    {
        val expected = BookDbTracker.addBooks(repo, 4455688L, "Test Tag Update Delete", 0)
        assertThat(repo.canUndo()).isFalse()
        assertThat(repo.canRedo()).isFalse()

        // Add a couple of tags
        val tags = listOf(
            TagEntity(0L, "tag1", "desc1", 0),
            TagEntity(0L, "tag\\", "desc\\", 0),
            TagEntity(0L, "tag_", "desc_", 0),
        )

        // Check Add
        for (t in tags) {
            assertWithMessage("Add %s", t.name).apply {
                expected.addTag(t)
                that(t.id).isNotEqualTo(0L)
                that(repo.getTag(t.id)).isEqualTo(t)
                expected.undoAndCheck("Test Tag Update Delete")
                expected.redoAndCheck("Test Tag Update Delete")
            }
        }

        // Verify that we can update
        var update = tags[2].copy(id = 0L)
        update.isSelected = true
        assertWithMessage("Update Succeeded %s", update.name).apply {
            expected.addTag(update) { true }
            that(update.id).isEqualTo(tags[2].id)
            that(repo.getTag(update.id)).isEqualTo(update)
            that(repo.findTagByName(update.name)).isEqualTo(update)
            expected.undoAndCheck("Test Tag Update Succeeded")
            expected.redoAndCheck("Test Tag Update Succeeded")
        }

        // Change the name
        update = update.copy(name = "tag%")
        assertWithMessage("Name Change %s", update.name).apply {
            expected.addTag(update)
            that(update.id).isEqualTo(tags[2].id)
            that(repo.getTag(update.id)).isEqualTo(update)
            that(repo.findTagByName(update.name)).isEqualTo(update)
            that(repo.findTagByName(tags[2].name)).isNull()
            expected.undoTracker.checkUndo("Test Tag Update Fail")
            expected.checkDatabase("Test Tag Update Fail")
        }

        // Merge two tags
        update = update.copy(name = tags[1].name)
        assertWithMessage("Merge Tags %s", update.name).apply {
            expected.addTag(update) { true }
            that(update.id).isAnyOf(tags[2].id, tags[1].id)
            that(repo.getTag(update.id)).isEqualTo(update)
            that(repo.findTagByName(update.name)).isEqualTo(update)
            that(repo.findTagByName("tag%")).isNull()
            if (update.id == tags[2].id) {
                expected.tables.tagEntities.merge(update, tags[1])
                that(repo.getTag(tags[1].id)).isNull()
            } else {
                expected.tables.tagEntities.merge(update, tags[2])
                that(repo.getTag(tags[2].id)).isNull()
            }
            expected.undoAndCheck("Test Tag Update Merge")
            expected.redoAndCheck("Test Tag Update Merge")
        }

        // Delete selected
        assertWithMessage("Delete selected").apply {
            tags[2].isSelected = true
            tags[2].id = 0L
            tags[1].isSelected = true
            tags[1].id = 0L
            expected.addTag(tags[2]) { true }
            that(repo.getTag(tags[2].id)).isEqualTo(tags[2])
            expected.undoAndCheck("Test Tag Delete Selected Add 2")
            expected.redoAndCheck("Test Tag Delete Selected Add 2")
            expected.addTag(tags[1]) { true }
            that(repo.getTag(tags[1].id)).isEqualTo(tags[1])
            expected.undoAndCheck("Test Tag Delete Selected Add 1")
            expected.redoAndCheck("Test Tag Delete Selected Add 1")
            expected.undoStarted()
            repo.deleteSelectedTags().also {
                expected.tables.tagEntities.unlinked(tags[2], true)
                expected.tables.tagEntities.unlinked(tags[1], true)
                expected.undoEnded("Test Tag Delete Selected", it > 0)
            }
            that(repo.getTag(tags[2].id)).isNull()
            that(repo.getTag(tags[1].id)).isNull()
            expected.undoAndCheck("Test Tag Delete Selected")
            expected.redoAndCheck("Test Tag Delete Selected")
        }
        expected.testRandomUndo("Random Undo Add Delete Update Tags", 12)
    }

    private suspend fun BookDbTracker.testAddRemoveTagsFromBooks(message: String, bookFilter: BookFilter?) {
        val filter = bookFilter?.buildFilter(context, arrayOf(BOOK_ID_COLUMN), true)
        fun randomTags(): List<TagEntity> {
            return ArrayList<TagEntity>().apply {
                repeat (random.nextInt(tables.tagEntities.size / 4)) {
                    add(tables.tagEntities[random.nextInt(tables.tagEntities.size)])
                }
            }
        }
        fun selectedTags(): List<TagEntity> {
            return ArrayList<TagEntity>().apply {
                for (t in tables.tagEntities.entities) {
                    if (t.isSelected)
                        add(t)
                }
            }
        }
        fun randomBooks(): List<BookAndAuthors> {
            return ArrayList<BookAndAuthors>().apply {
                repeat (random.nextInt(tables.bookEntities.size / 4)) {
                    add(tables.bookEntities[random.nextInt(tables.bookEntities.size)])
                }
            }
        }
        fun selectedBooks(): List<BookAndAuthors> {
            return ArrayList<BookAndAuthors>().apply {
                for (b in tables.bookEntities.entities) {
                    if (b.book.isSelected)
                        add(b)
                }
            }
        }
        fun updateAddBooks(books: List<BookAndAuthors>, tags: List<TagEntity>) {
            val mod = Calendar.getInstance().time
            for (b in books) {
                if (bookFilter?.filterList?.get(0)?.values?.contains(b.book.title) != false) {
                    val newTags = ArrayList<TagEntity>(b.tags)
                    var changed = false
                    for (t in tags) {
                        if (!newTags.contains(t)) {
                            changed = true
                            newTags.add(t)
                            tables.tagEntities.linked(t)
                        }
                    }
                    if (changed)
                        b.book.modified = mod
                    b.tags = newTags
                }
            }
        }
        fun updateRemoveBooks(books: List<BookAndAuthors>, tags: List<TagEntity>, invert: Boolean) {
            val mod = Calendar.getInstance().time
            for (b in books) {
                if (bookFilter?.filterList?.get(0)?.values?.contains(b.book.title) != false) {
                    val newTags = ArrayList<TagEntity>(b.tags)
                    var changed = false
                    for (t in tables.tagEntities.entities) {
                        if (tags.contains(t) != invert && newTags.contains(t)) {
                            changed = true
                            newTags.remove(t)
                            tables.tagEntities.unlinked(t)
                        }
                    }
                    if (changed)
                        b.book.modified = mod
                    b.tags = newTags
                }
            }
        }

        var tags: List<TagEntity>
        var books: List<BookAndAuthors>

        undoStarted()
        updateAddBooks(selectedBooks(), selectedTags())
        repo.addTagsToBooks(null, null, filter).also {
            undoEnded("Add Selected Tags To Selected Books$message", it > 0)
        }
        checkDatabase("Add Selected Tags To Selected Books$message")
        undoAndCheck("Add Selected Tags To Selected Books$message")
        redoAndCheck("Add Selected Tags To Selected Books$message")

        tags = randomTags()
        undoStarted()
        updateAddBooks(selectedBooks(), tags)
        repo.addTagsToBooks(null, Array(tags.size) { tags[it].id }, filter).also {
            undoEnded("Add Tags To Selected Books$message", it > 0)
        }
        checkDatabase("Add Tags To Selected Books$message")
        undoAndCheck("Add Tags To Selected Books$message")
        redoAndCheck("Add Tags To Selected Books$message")

        undoStarted()
        updateRemoveBooks(selectedBooks(), selectedTags(), false)
        repo.removeTagsFromBooks(null, null, filter, false).also {
            undoEnded("Remove Selected Tags From Selected Books$message", it > 0)
        }
        checkDatabase("Remove Selected Tags From Selected Books$message")
        undoAndCheck("Remove Selected Tags From Selected Books$message")
        redoAndCheck("Remove Selected Tags From Selected Books$message")

        books = randomBooks()
        undoStarted()
        updateAddBooks(books, selectedTags())
        repo.addTagsToBooks(Array(books.size) { books[it].book.id }, null, filter). also {
            undoEnded("Add Selected Tags To Books$message", it > 0)
        }
        checkDatabase("Add Selected Tags To Books$message")
        undoAndCheck("Add Selected Tags To Books$message")
        redoAndCheck("Add Selected Tags To Books$message")

        var invert = true
        repeat (5) {
            tags = randomTags()
            books = randomBooks()
            undoStarted()
            updateAddBooks(books, tags)
            repo.addTagsToBooks(Array(books.size) { books[it].book.id }, Array(tags.size) { tags[it].id }, filter).also {
                undoEnded("Add Tags To Books$message", it > 0)
            }
            checkDatabase("Add Tags To Books$message")
            undoAndCheck("Add Tags To Books$message")
            redoAndCheck("Add Tags To Books$message")

            invert = !invert
            undoStarted()
            updateRemoveBooks(books, tags, invert)
            repo.removeTagsFromBooks(Array(books.size) { books[it].book.id }, Array(tags.size) { tags[it].id }, filter, invert).also {
                undoEnded("Remove $invert Tags From Books$message", it > 0)
            }
            checkDatabase("Remove $invert Tags From Books$message")
            undoAndCheck("Remove $invert Tags From Books$message")
            redoAndCheck("Remove $invert Tags From Books$message")
        }
        testRandomUndo("Add/Remove Tags From Books", 10)
    }

    @Test fun testAddRemoveTagsFromBooks() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestAddRemoveTagsFromBooks()
        }
    }

    @Test fun testAddRemoveTagsFromBooksWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestAddRemoveTagsFromBooks()
        }
    }

    private suspend fun doTestAddRemoveTagsFromBooks() {
        val expected = BookDbTracker.addBooks(repo, 115288533L, "Add Remove Tags", 20)
        expected.testAddRemoveTagsFromBooks("", null)
    }

    @Test fun testAddRemoveTagsFromBooksFiltered() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestAddRemoveTagsFromBooksFiltered()
        }
    }

    @Test fun testAddRemoveTagsFromBooksFilteredWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestAddRemoveTagsFromBooksFiltered()
        }
    }

    private suspend fun doTestAddRemoveTagsFromBooksFiltered() {
        val expected = BookDbTracker.addBooks(repo, 115288533L, "Add Remove Tags", 20)
        expected.testAddRemoveTagsFromBooks(" Filtered", expected.makeFilter())
    }

    @Test fun testView() {
        runBlocking {
            repo.setMaxUndoLevels(0)
            doTestView()
        }
    }

    @Test fun testViewWithUndo() {
        runBlocking {
            repo.setMaxUndoLevels(20, 25)
            doTestView()
        }
    }

    private suspend fun doTestView() {
        val expected = BookDbTracker.addBooks(repo, 45666298L, "Test View", 0)
        // Make some views
        val views = arrayOf(
            ViewEntity(id = 0L, name = "view1", desc = "desc1"),
            ViewEntity(
                id = 0L, name = "view\\", desc = "desc\\", filter = BookFilter(
                arrayOf(OrderField(Column.LAST_NAME, Order.Ascending, false)),
                emptyArray()
            )
            ),
            ViewEntity(
                id = 0L, name = "view_", desc = "desc_", filter = BookFilter(
                arrayOf(OrderField(Column.DATE_MODIFIED, Order.Descending, false)),
                arrayOf(FilterField(Column.TAGS, Predicate.NOT_ONE_OF, arrayOf("SciFi")))
            )
            ),
        )

        // Keep the names in a set, because the query order may not
        // match the order of the views array
        val names = HashMap<String, ViewEntity>()
        // Add the views
        for (v in views) {
            // Save the name
            names[v.name] = v
            // Add the view
            val id = expected.addView(v) { false }
            // Check id and findByName
            assertWithMessage("Add View %s", v.name).apply {
                that(id).isNotEqualTo(0L)
                that(id).isEqualTo(v.id)
                that(repo.findViewByName(v.name)).isEqualTo(v)
            }
            expected.undoAndCheck("AddView %s", v.name)
            expected.redoAndCheck("AddView %s", v.name)
        }

        suspend fun StandardSubjectBuilder.checkNames() {
            val nameList = repo.getViewNames().getLive()
            that(nameList?.size).isEqualTo(names.size)
            val inList = HashSet<String>()
            for (n in nameList!!) {
                that(names.contains(n)).isTrue()
                that(inList.contains(n)).isFalse()
                that(repo.findViewByName(n)).isEqualTo(names[n])
                inList.add(n)
            }
        }

        // Get the names an makes sure they are all there
        assertWithMessage("All Views").checkNames()

        val newView = views[1].copy(id = 0L, desc = "descNew", filter = BookFilter(emptyArray(), emptyArray()))
        // Fail to add a conflicting view
        assertWithMessage("Conflict Fail").apply {
            that(expected.addView(newView) { false }).isEqualTo(0L)
            checkNames()
            expected.undoAndCheck("View Conflict Fail")
            expected.redoAndCheck("View Conflict Fail")
        }

        // Add a conflicting view
        assertWithMessage("Conflict Succeed").apply {
            val id = expected.addView(newView) { true }
            that(id).isEqualTo(views[1].id)
            that(id).isEqualTo(newView.id)
            that(repo.findViewByName(newView.name)).isEqualTo(newView)
            names[newView.name] = newView
            checkNames()
            expected.undoAndCheck("View Conflict Succeed")
            expected.redoAndCheck("View Conflict Succeed")
        }

        // Delete the views one at a time and check the list
        for (i in views.size - 1 downTo 0) {
            assertWithMessage("Delete View %s", views[i].name).apply {
                expected.undoStarted()
                that(repo.removeView(views[i].name)).isEqualTo(1)
                expected.tables.viewEntities.unlinked(views[i], true)
                expected.undoEnded("View Delete ${views[i].name}", true)
                that(repo.findViewByName(views[i].name)).isEqualTo(null)
                names.remove(views[i].name)
                checkNames()
                expected.undoAndCheck("View Delete %s", views[i].name)
                expected.redoAndCheck("View Delete %s", views[i].name)
            }
        }
        expected.testRandomUndo("Test Views", 6)
    }

    @Test fun testMiscUndoStuff() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo,4522998L, "Misc Undo Test", 20)
            val dao = expected.db.getUndoRedoDao()

            // First do some things to get a good undo set
            doTestAddDeleteBookEntity(expected)

            // Make sure we can undo everything
            var count = 0
            while (repo.canUndo()) {
                expected.undoAndCheck("Check All Undoes %s", count++)
            }
            assertThat(count).isEqualTo(repo.maxUndoLevels)
            assertThat(dao.maxUndoId - dao.minUndoId + 1).isEqualTo(repo.maxUndoLevels)
            assertThat(dao.undoId).isIn((dao.minUndoId - 1..dao.maxUndoId))

            // Make sure we can redo everything
            while (count-- > 6) {
                assertWithMessage("Can Redo %s", count).that(repo.canRedo()).isTrue()
                expected.redoAndCheck("Check All Redoes %s", count)
            }
            assertThat(dao.maxUndoId - dao.minUndoId + 1).isEqualTo(repo.maxUndoLevels)
            assertThat(dao.undoId).isIn((dao.minUndoId - 1..dao.maxUndoId))

            repo.setMaxUndoLevels(15)
            assertThat(repo.maxUndoLevels).isEqualTo(15)
            assertThat(dao.maxUndoId - dao.minUndoId + 1).isEqualTo(15)
            assertThat(dao.undoId).isIn((dao.minUndoId - 1..dao.maxUndoId))
            expected.undoLevelsChanged()
            expected.undoTracker.checkUndo("Undo Levels Changed 15")
            repo.setMaxUndoLevels(30)
            assertThat(repo.maxUndoLevels).isEqualTo(30)
            assertThat(dao.maxUndoId - dao.minUndoId + 1).isEqualTo(15)
            assertThat(dao.undoId).isIn((dao.minUndoId - 1..dao.maxUndoId))
            expected.undoLevelsChanged()
            expected.undoTracker.checkUndo("Undo Levels Changed 30")
            repo.setMaxUndoLevels(7)
            assertThat(repo.maxUndoLevels).isEqualTo(7)
            assertThat(dao.maxUndoId - dao.minUndoId + 1).isEqualTo(7)
            assertThat(dao.undoId).isIn((dao.minUndoId - 1..dao.maxUndoId))
            expected.undoLevelsChanged()
            expected.undoTracker.checkUndo("Undo Levels Changed 7")
            repo.setMaxUndoLevels(6)
            assertThat(repo.maxUndoLevels).isEqualTo(6)
            assertThat(dao.maxUndoId - dao.minUndoId + 1).isEqualTo(6)
            assertThat(dao.undoId).isIn((dao.minUndoId - 1..dao.maxUndoId))
            expected.undoLevelsChanged()
            expected.undoTracker.checkUndo("Undo Levels Changed 6")
            repo.setMaxUndoLevels(5)
            assertThat(repo.maxUndoLevels).isEqualTo(5)
            assertThat(dao.maxUndoId - dao.minUndoId + 1).isEqualTo(5)
            assertThat(dao.undoId).isIn((dao.minUndoId - 1..dao.maxUndoId))
            expected.undoLevelsChanged()
            expected.undoTracker.checkUndo("Undo Levels Changed 5")
            repo.setMaxUndoLevels(0)
            assertThat(repo.maxUndoLevels).isEqualTo(0)
            assertThat(dao.maxUndoId - dao.minUndoId + 1).isEqualTo(0)
            assertThat(dao.undoId).isIn((dao.minUndoId - 1..dao.maxUndoId))
            expected.undoLevelsChanged()
            expected.undoTracker.checkUndo("Undo Levels Changed 0")
        }
    }
}
