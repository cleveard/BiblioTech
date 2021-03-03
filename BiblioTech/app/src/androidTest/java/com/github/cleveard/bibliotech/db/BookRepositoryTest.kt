package com.github.cleveard.bibliotech.db


import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.utils.getLive
import com.github.cleveard.bibliotech.testutils.BookDbTracker
import com.github.cleveard.bibliotech.testutils.compareBooks
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookRepositoryTest {
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
        BookRepository.close()
    }

    private suspend fun BookDbTracker.testBookFlags(message: String, bookFilter: BookFilter?) {
        val bookFlags = repo.bookFlags
        val filter = bookFilter?.let { it.buildFilter(context, arrayOf(BOOK_ID_COLUMN), true) }

        suspend fun StandardSubjectBuilder.checkCountBits(bits: Int, value: Int, include: Boolean, id: Long?) {
            var s = bookEntities.entities
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
            assertWithMessage("checkCount$message value: %s id: %s", value, id).apply {
                checkCountBits(bits, value, true, null)
                checkCountBits(bits, value, false, null)
                checkCountBits(bits, value, true, id)
                checkCountBits(bits, value, false, id)
            }
        }

        checkCount(0b11, 0b01, null)
        checkCount(0b11, 0b11, null)

        fun nextBook(): BookAndAuthors {
            return bookEntities[random.nextInt(bookEntities.size)]
        }
        repeat(10) {
            checkCount(0b11, 0b11, nextBook().book.id)
            checkCount(0b11, 0b01, nextBook().book.id)
        }

        suspend fun checkChange(all: String, operation: Boolean?, mask: Int, book: BookAndAuthors?) {
            assertWithMessage(
                "changeBits$message %s %s op: %s, mask: %s", book?.book?.flags, all, operation, mask
            ).apply {
                var s = bookEntities.entities
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
    }

    @Test(timeout = 50000L) fun testBookFlags() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo, 552841129L, "Test Book Flags", 20)
            expected.testBookFlags("", null)
        }
    }

    @Test(timeout = 50000L) fun testBookFlagsFiltered() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo, 1165478L, "Test Book Flags filtered", 20)
            expected.testBookFlags(" filtered", expected.makeFilter())
        }
    }

    @Test(timeout = 5000L) fun testTagFlags() {
        runBlocking {
            // Add a couple of tags
            val tags = listOf(
                TagEntity(0L, "tag1", "desc1", 0b0111),
                TagEntity(0L, "tag2", "desc2", 0b1001),
                TagEntity(0L, "tag3", "desc3", 0b1110),
            )

            // Check Add
            val tagFlags = repo.tagFlags
            for (t in tags) {
                assertWithMessage("Add %s", t.name).apply {
                    repo.addOrUpdateTag(t)
                    that(t.id).isNotEqualTo(0L)
                    that(repo.getTag(t.id)).isEqualTo(t)
                }
            }

            suspend fun checkCount(bits: Int, value: Int, id: Long?, expTrue: Int, expFalse: Int) {
                assertWithMessage("checkCount value: %s id: %s", value, id).apply {
                    that(tagFlags.countBits(bits, value, true, id, null)).isEqualTo(expTrue)
                    that(tagFlags.countBits(bits, value, false, id, null)).isEqualTo(expFalse)
                    that(tagFlags.countBitsLive(bits, value, true, id, null).getLive()).isEqualTo(expTrue)
                    that(tagFlags.countBitsLive(bits, value, false, id, null).getLive()).isEqualTo(expFalse)
                }
            }
            checkCount(0b11, 0b01, null, 1, 2)
            checkCount(0b11, 0b11, null, 1, 2)
            checkCount(0b11, 0b11, tags[0].id, 1, 0)
            checkCount(0b11, 0b01, tags[0].id, 0, 1)

            var count: Int?
            assertWithMessage(
                "changeBits single op: %s, mask: %s", false, 0b11
            ).apply {
                count = tagFlags.changeBits(false, 0b11, tags[0].id)
                that(count).isEqualTo(1)
                that(repo.getTag(tags[0].id)?.flags).isEqualTo(0b0100)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", true, 0b1001
            ).apply {
                count = tagFlags.changeBits(true, 0b1001, tags[0].id)
                that(count).isEqualTo(1)
                that(repo.getTag(tags[0].id)?.flags).isEqualTo(0b1101)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", null, 0b1010
            ).apply {
                count = tagFlags.changeBits(null, 0b1010, tags[0].id)
                that(count).isEqualTo(1)
                that(repo.getTag(tags[0].id)?.flags).isEqualTo(0b0111)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", true, 0b0111
            ).apply {
                count = tagFlags.changeBits(true, 0b0111, tags[0].id)
                that(count).isEqualTo(0)
                that(repo.getTag(tags[0].id)?.flags).isEqualTo(0b0111)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", false, 0b1000
            ).apply {
                count = tagFlags.changeBits(false, 0b1000, tags[0].id)
                that(count).isEqualTo(0)
                that(repo.getTag(tags[0].id)?.flags).isEqualTo(0b0111)
            }

            suspend fun StandardSubjectBuilder.checkChange(vararg values: Int) {
                for (i in tags.indices) {
                    that(repo.getTag(tags[i].id)?.flags).isEqualTo(values[i])
                }
            }
            assertWithMessage(
                "changeBits all op: %s, mask: %s", false, 0b0010
            ).apply {
                count = tagFlags.changeBits(false, 0b0010, null)
                that(count).isEqualTo(2)
                checkChange(0b0101, 0b1001, 0b1100)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", true, 0b1001
            ).apply {
                count = tagFlags.changeBits(true, 0b1001, null)
                that(count).isEqualTo(2)
                checkChange(0b1101, 0b1001, 0b1101)
            }
            assertWithMessage(
                "changeBits single op: %s, mask: %s", null, 0b1010
            ).apply {
                count = tagFlags.changeBits(null, 0b1010, null)
                that(count).isEqualTo(tags.size)
                checkChange(0b0111, 0b0011, 0b0111)
            }
        }
    }

    @Test(timeout = 5000L) fun testGetBooks() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo,56542358L, "GetBooks", 20)
            assertWithMessage("GetBooks").apply {
                val source = repo.getBooks()
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

    @Test(timeout = 5000L) fun testGetBooksFiltered() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo,565199823L, "GetBooks Filtered", 20)
            assertWithMessage("GetBooks").apply {
                val filter = expected.makeFilter()
                val source = repo.getBooks(filter, context)
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

    @Test(timeout = 25000L) fun testAddDeleteBookEntity() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo,2564621L, "AddBooks Delete", 20)

            var count = 0
            for (b in ArrayList<BookAndAuthors>().apply {
                addAll(expected.bookEntities.entities)
            }) {
                if (b.book.isSelected) {
                    expected.unlinkBook(b)
                    ++count
                }
            }
            assertWithMessage("Delete Selected").that(repo.deleteSelectedBooks(null,null)).isEqualTo(count)
            expected.checkDatabase("Delete Selected")

            var occurrence = 0
            while (expected.bookEntities.size > 0) {
                ++occurrence
                count = expected.random.nextInt(4).coerceAtMost(expected.bookEntities.size)
                val bookIds = Array<Any>(count) { 0L }
                repeat (count) {
                    val i = expected.random.nextInt(expected.bookEntities.size)
                    val book = expected.bookEntities[i]
                    bookIds[it] = book.book.id
                    expected.unlinkBook(book)
                }
                assertWithMessage("Deleted $occurrence").that(repo.deleteSelectedBooks(null, bookIds)).isEqualTo(bookIds.size)
                expected.checkDatabase("Delete $occurrence")
            }
        }
    }

    @Test(timeout = 25000L) fun testAddDeleteBookEntityEmptyFilter() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo,9922621L, "AddBooks Empty Filter", 20)
            val filter = BookFilter(emptyArray(), arrayOf(
                FilterField(Column.TITLE, Predicate.ONE_OF, emptyArray())
            )).buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)

            var count = 0
            for (b in ArrayList<BookAndAuthors>().apply {
                addAll(expected.bookEntities.entities)
            }) {
                if (b.book.isSelected) {
                    expected.unlinkBook(b)
                    ++count
                }
            }
            assertWithMessage("Delete Selected Empty Filter").that(repo.deleteSelectedBooks(filter,null)).isEqualTo(count)
            expected.checkDatabase("Delete Selected Empty Filter")

            var occurrence = 0
            while (expected.bookEntities.size > 0) {
                ++occurrence
                count = expected.random.nextInt(4).coerceAtMost(expected.bookEntities.size)
                val bookIds = Array<Any>(count) { 0L }
                repeat (count) {
                    val i = expected.random.nextInt(expected.bookEntities.size)
                    val book = expected.bookEntities[i]
                    bookIds[it] = book.book.id
                    expected.unlinkBook(book)
                }
                assertWithMessage("Deleted $occurrence Empty Filter").that(repo.deleteSelectedBooks(filter, bookIds)).isEqualTo(bookIds.size)
                expected.checkDatabase("Delete $occurrence Empty Filter")
            }
        }
    }

    @Test(timeout = 25000L) fun testAddDeleteBookEntityWithFilter() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo,8964521L, "AddBooks Filter", 20)
            val bookFilter = expected.makeFilter()
            val filter = bookFilter.buildFilter(context, arrayOf(BOOK_ID_COLUMN),true)
            val keptCount = expected.bookEntities.size - bookFilter.filterList[0].values.size

            var count = 0
            for (b in ArrayList<BookAndAuthors>().apply {
                addAll(expected.bookEntities.entities)
            }) {
                if (b.book.isSelected && bookFilter.filterList[0].values.contains(b.book.title)) {
                    expected.unlinkBook(b)
                    ++count
                }
            }
            assertWithMessage("Delete Selected Filtered").that(repo.deleteSelectedBooks(filter,null)).isEqualTo(count)
            expected.checkDatabase("Delete Selected Filtered")

            var occurrence = 0
            while (expected.bookEntities.size > keptCount) {
                ++occurrence
                count = expected.random.nextInt(4).coerceAtMost(expected.bookEntities.size)
                val bookIds = Array<Any>(count) { 0L }
                var size = 0
                repeat (count) {
                    val i = expected.random.nextInt(expected.bookEntities.size)
                    val book = expected.bookEntities[i]
                    bookIds[it] = book.book.id
                    if (bookFilter.filterList[0].values.contains(book.book.title)) {
                        expected.unlinkBook(book)
                        ++size
                    }
                }
                assertWithMessage("Deleted $occurrence Filtered").that(repo.deleteSelectedBooks(filter, bookIds)).isEqualTo(size)
                expected.checkDatabase("Delete $occurrence Filtered")
            }
        }
    }

    @Test(timeout = 25000L) fun testUpdateBookEntity() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo,554321L, "AddBooks Update", 20)

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

    @Test(timeout = 5000L, expected = SQLiteConstraintException::class) fun testUpdateFails() {
        runBlocking {
            // Updating a book that conflicts with two other books will fail
            val expected = BookDbTracker.addBooks(repo,5668721L, "AddBooks Update", 20)
            val book = expected.bookEntities.new()
            book.book.ISBN = expected.bookEntities[3].book.ISBN
            book.book.sourceId = expected.bookEntities[11].book.sourceId
            book.book.volumeId = expected.bookEntities[11].book.volumeId
            expected.addOneBook("Update Fail", book)
        }
    }

    @Test(timeout = 5000L) fun testgetTags() {
        runBlocking {
            // Add a couple of tags
            val tags = listOf(
                TagEntity(0L, "tag1", "desc1", 0),
                TagEntity(0L, "tag\\", "desc\\", TagEntity.SELECTED),
                TagEntity(0L, "tag_", "desc_", 0),
            )

            // Check Add
            for (t in tags) {
                assertWithMessage("Add %s", t.name).apply {
                    repo.addOrUpdateTag(t)
                    that(t.id).isNotEqualTo(0L)
                    that(repo.getTag(t.id)).isEqualTo(t)
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
        }
    }

    @Test(timeout = 5000L) fun testAddUpdateDelete()
    {
        runBlocking {
            // Add a couple of tags
            val tags = listOf(
                TagEntity(0L, "tag1", "desc1", 0),
                TagEntity(0L, "tag\\", "desc\\", 0),
                TagEntity(0L, "tag_", "desc_", 0),
            )

            // Check Add
            for (t in tags) {
                assertWithMessage("Add %s", t.name).apply {
                    repo.addOrUpdateTag(t)
                    that(t.id).isNotEqualTo(0L)
                    that(repo.getTag(t.id)).isEqualTo(t)
                }
            }

            // Verify that we can update
            val update = tags[2].copy(id = 0L)
            update.isSelected = true
            assertWithMessage("Update Succeeded %s", update.name).apply {
                repo.addOrUpdateTag(update) { true }
                that(update.id).isEqualTo(tags[2].id)
                that(repo.getTag(update.id)).isEqualTo(update)
                that(repo.findTagByName(update.name)).isEqualTo(update)
            }

            // Change the name
            update.name = "tag%"
            assertWithMessage("Name Change %s", update.name).apply {
                repo.addOrUpdateTag(update)   // Don't expect a conflict
                that(update.id).isEqualTo(tags[2].id)
                that(repo.getTag(update.id)).isEqualTo(update)
                that(repo.findTagByName(update.name)).isEqualTo(update)
                that(repo.findTagByName(tags[2].name)).isNull()
            }

            // Merge two tags
            update.name = tags[1].name
            assertWithMessage("Merge Tags %s", update.name).apply {
                repo.addOrUpdateTag(update) { true }    // Don't expect a conflict
                that(update.id).isAnyOf(tags[2].id, tags[1].id)
                that(repo.getTag(update.id)).isEqualTo(update)
                that(repo.findTagByName(update.name)).isEqualTo(update)
                that(repo.findTagByName("tag%")).isNull()
                if (update.id == tags[2].id)
                    that(repo.getTag(tags[1].id)).isNull()
                else
                    that(repo.getTag(tags[2].id)).isNull()
            }

            // Delete selected
            assertWithMessage("Delete selected").apply {
                tags[2].isSelected = true
                tags[2].id = 0L
                tags[1].isSelected = true
                tags[1].id = 0L
                repo.addOrUpdateTag(tags[2]) { true }
                that(repo.getTag(tags[2].id)).isEqualTo(tags[2])
                repo.addOrUpdateTag(tags[1]) { true }
                that(repo.getTag(tags[1].id)).isEqualTo(tags[1])
                repo.deleteSelectedTags()
                that(repo.getTag(tags[2].id)).isNull()
                that(repo.getTag(tags[1].id)).isNull()
            }
        }
    }

    suspend fun BookDbTracker.testAddRemoveTagsFromBooks(message: String, bookFilter: BookFilter?) {
        val filter = bookFilter?.buildFilter(context, arrayOf(BOOK_ID_COLUMN), true)
        fun randomTags(): List<TagEntity> {
            return ArrayList<TagEntity>().apply {
                repeat (random.nextInt(tagEntities.size / 4)) {
                    add(tagEntities[random.nextInt(tagEntities.size)])
                }
            }
        }
        fun selectedTags(): List<TagEntity> {
            return ArrayList<TagEntity>().apply {
                for (t in tagEntities.entities) {
                    if (t.isSelected)
                        add(t)
                }
            }
        }
        fun randomBooks(): List<BookAndAuthors> {
            return ArrayList<BookAndAuthors>().apply {
                repeat (random.nextInt(bookEntities.size / 4)) {
                    add(bookEntities[random.nextInt(bookEntities.size)])
                }
            }
        }
        fun selectedBooks(): List<BookAndAuthors> {
            return ArrayList<BookAndAuthors>().apply {
                for (b in bookEntities.entities) {
                    if (b.book.isSelected)
                        add(b)
                }
            }
        }
        fun updateAddBooks(books: List<BookAndAuthors>, tags: List<TagEntity>) {
            for (b in books) {
                if (bookFilter?.filterList?.get(0)?.values?.contains(b.book.title) != false) {
                    val newTags = ArrayList<TagEntity>(b.tags)
                    for (t in tags) {
                        if (!newTags.contains(t)) {
                            newTags.add(t)
                            tagEntities.linked(t)
                        }
                    }
                    b.tags = newTags
                }
            }
        }
        fun updateRemoveBooks(books: List<BookAndAuthors>, tags: List<TagEntity>, invert: Boolean) {
            for (b in books) {
                if (bookFilter?.filterList?.get(0)?.values?.contains(b.book.title) != false) {
                    val newTags = ArrayList<TagEntity>(b.tags)
                    for (t in tagEntities.entities) {
                        if (tags.contains(t) != invert && newTags.contains(t)) {
                            newTags.remove(t)
                            tagEntities.unlinked(t)
                        }
                    }
                    b.tags = newTags
                }
            }
        }

        var tags: List<TagEntity>
        var books: List<BookAndAuthors>

        updateAddBooks(selectedBooks(), selectedTags())
        repo.addTagsToBooks(null, null, filter)
        checkDatabase("Add Selected Tags To Selected Books$message")

        tags = randomTags()
        updateAddBooks(selectedBooks(), tags)
        repo.addTagsToBooks(null, Array(tags.size) { tags[it].id }, filter)
        checkDatabase("Add Tags To Selected Books$message")

        updateRemoveBooks(selectedBooks(), selectedTags(), false)
        repo.removeTagsFromBooks(null, null, filter, false)
        checkDatabase("Remove Selected Tags From Selected Books$message")

        books = randomBooks()
        updateAddBooks(books, selectedTags())
        repo.addTagsToBooks(Array(books.size) { books[it].book.id }, null, filter)
        checkDatabase("Add Selected Tags To Books$message")

        var invert = true
        repeat (5) {
            tags = randomTags()
            books = randomBooks()
            updateAddBooks(books, tags)
            repo.addTagsToBooks(Array(books.size) { books[it].book.id }, Array(tags.size) { tags[it].id }, filter)
            checkDatabase("Add Tags To Books$message")

            invert = !invert
            updateRemoveBooks(books, tags, invert)
            repo.removeTagsFromBooks(Array(books.size) { books[it].book.id }, Array(tags.size) { tags[it].id }, filter, invert)
            checkDatabase("Remove $invert Tags From Books$message")
        }
    }

    @Test(timeout = 25000L) fun testAddRemoveTagsFromBooks() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo, 115288533L, "Add Remove Tags", 20)
            expected.testAddRemoveTagsFromBooks("", null)
        }
    }

    @Test(timeout = 25000L) fun testAddRemoveTagsFromBooksFiltered() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo, 115288533L, "Add Remove Tags", 20)
            expected.testAddRemoveTagsFromBooks(" Filtered", expected.makeFilter())
        }
    }

    @Test(timeout = 5000L) fun testView() {
        runBlocking {
            //val viewDao = db.getViewDao()
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
                val id = repo.addOrUpdateView(v) { false }
                // Check id and findByName
                assertWithMessage("Add View %s", v.name).apply {
                    that(id).isNotEqualTo(0L)
                    that(id).isEqualTo(v.id)
                    that(repo.findViewByName(v.name)).isEqualTo(v)
                }
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

            val newView = views[0].copy(id = 0L, desc = "descNew", filter = BookFilter(emptyArray(), emptyArray()))
            // Fail to add a conflicting view
            assertWithMessage("Conflict Fail").apply {
                that(repo.addOrUpdateView(newView) { false }).isEqualTo(0L)
                checkNames()
            }

            // Add a conflicting view
            assertWithMessage("Conflict Succeed").apply {
                val id = repo.addOrUpdateView(newView) { true }
                that(id).isEqualTo(views[0].id)
                that(id).isEqualTo(newView.id)
                that(repo.findViewByName(newView.name)).isEqualTo(newView)
                names[newView.name] = newView
                checkNames()
            }

            // Delete the views one at a time and check the list
            for (i in views.size - 1 downTo 0) {
                assertWithMessage("Delete View %s", views[i].name).apply {
                    that(repo.removeView(views[i].name)).isEqualTo(1)
                    that(repo.findViewByName(views[i].name)).isEqualTo(null)
                    names.remove(views[i].name)
                    checkNames()
                }
            }
        }
    }
}
