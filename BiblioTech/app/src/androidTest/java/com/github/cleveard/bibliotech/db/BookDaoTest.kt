package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.makeBookAndAuthors
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
    private val random = Random(2564621L)
    private var tagEntities: Table<TagEntity> = object: Table<TagEntity>(false) {
        override fun make(flags: Int, unique: String): TagEntity {
            return TagEntity(0L, "tag$unique", "desc$unique", flags)
        }
    }
    private var authorEntities: Table<AuthorEntity> = object: Table<AuthorEntity>() {
        override fun make(flags: Int, unique: String): AuthorEntity {
            return AuthorEntity(0L, "last$unique", "first$unique")
        }
    }
    private var categoryEntities: Table<CategoryEntity> = object: Table<CategoryEntity>() {
        override fun make(flags: Int, unique: String): CategoryEntity {
            return CategoryEntity(0L, "category$unique")
        }
    }
    private var bookEntities: Table<BookAndAuthors> = object: Table<BookAndAuthors>() {
        override fun make(flags: Int, unique: String): BookAndAuthors {
            return makeBookAndAuthors(next, flags, unique)
        }
    }

    private suspend fun addTag(flags: Int = 0) {
        val entity = tagEntities.new(flags)
        assertWithMessage("AddTag").that(db.getTagDao().add(entity)).isNotEqualTo(0L)
    }

    private fun <T> Table<T>.createRelationList(maxCount: Int) : MutableList<T> {
        val returnList = ArrayList<T>()
        val count = random.nextInt(maxCount + 1)
        var existing = random.nextInt(count + 1).coerceAtMost(size)
        repeat (count) {
            if (existing > 0 && random.nextBoolean()) {
                --existing
                var entity: T
                do {
                    entity = this[random.nextInt(size)]
                } while (returnList.contains(entity))
                returnList.add(entity)
            } else {
                val flags = if (random.nextInt(5) > 3)
                    TagEntity.SELECTED
                else
                    0
                returnList.add(new(flags))
            }
        }
        return returnList
    }

    private fun <T> Table<T>.linkRelation(linkList: List<T>) {
        for (e in linkList)
            linked(e)
    }

    private fun <T> Table<T>.unlinkRelation(linkList: List<T>) {
        for (e in linkList)
            unlinked(e)
    }

    private fun StandardSubjectBuilder.compareBooks(actual: BookAndAuthors?, expected: BookAndAuthors?) {
        if (actual == null || expected !is BookAndAuthors)
            that(actual).isEqualTo(expected)
        else {
            that(actual.book).isEqualTo(expected.book)
            that(actual.authors).containsExactlyElementsIn(expected.authors)
            that(actual.categories).containsExactlyElementsIn(expected.categories)
            that(actual.tags).containsExactlyElementsIn(expected.tags)
        }

    }

    private fun <T> Table<T>.checkCounts(message: String, sequence: Sequence<T>, name: (T) -> String) {
        val counts = HashMap<T, Int>(size)
        for (e in sequence)
            counts[e] = (counts[e]?: 0) + 1
        for (e in entitiesAndCounts)
            assertWithMessage("%s: CheckCounts %s", message, name(e.entity)).that(counts[e.entity]).isEqualTo(e.count)
    }

    fun checkConsistency(message: String) {
        val bookSequence = bookEntities.entities
        tagEntities.checkCounts("$message: Tags", bookSequence.map { it.tags.asSequence() }.flatten()) { it.name }
        authorEntities.checkCounts("$message: Authors", bookSequence.map { it.authors.asSequence() }.flatten()) { it.name }
        categoryEntities.checkCounts("$message: Categories", bookSequence.map { it.categories.asSequence() }.flatten()) { it.category }
    }

    suspend fun checkDatabase(message: String) {
        val bookDao = db.getBookDao()
        val tagDao = db.getTagDao()
        val authorDao = db.getAuthorDao()
        val categoryDao = db.getCategoryDao()
        for (book in bookEntities.deleted)
            assertWithMessage("%s: Deleted %s", message, book.book.title).that(bookDao.getBook(book.book.id)).isNull()
        for (book in bookEntities.entities)
            assertWithMessage("%s: Deleted %s", message, book.book.title).compareBooks(bookDao.getBook(book.book.id), book)
        for (tag in tagEntities.deleted)
            assertWithMessage("%s: Deleted %s", message, tag.name).that(tagDao.findByName(tag.name)).isNull()
        for (tag in tagEntities.entities)
            assertWithMessage("%s: Deleted %s", message, tag.name).that(tagDao.findByName(tag.name)).isEqualTo(tag)
        for (category in categoryEntities.deleted)
            assertWithMessage("%s: Deleted %s", message, category.category).that(categoryDao.findByName(category.category)).isEmpty()
        for (category in categoryEntities.entities)
            assertWithMessage("%s: Deleted %s", message, category.category).that(categoryDao.findByName(category.category)).containsExactlyElementsIn(listOf(category))
        for (author in authorEntities.deleted)
            assertWithMessage("%s: Deleted %s", message, author.lastName).that(authorDao.findByName(author.lastName, author.remainingName)).isEmpty()
        for (author in authorEntities.entities)
            assertWithMessage("%s: Deleted %s", message, author.lastName).that(authorDao.findByName(author.lastName, author.remainingName)).containsExactlyElementsIn(listOf(author))
    }

    private suspend fun addOneBook(message: String, flags: Int = 0) {
        val bookDao = db.getBookDao()
        val tagDao = db.getTagDao()

        val book = bookEntities.new(flags)
        // Add 0 to 3 authors
        book.authors = authorEntities.createRelationList(3)
        // Add 0 to 3 categories
        book.categories = categoryEntities.createRelationList(3)
        // Add 0 to 4 additional tags
        val tagCount = random.nextInt(5).coerceAtMost(tagEntities.size)
        val tags: Array<Any>?
        if (tagCount != 0 || random.nextBoolean()) {
            tags = Array(random.nextInt(5)) { 0L }
            repeat (tags.size) {i ->
                var id: Long
                do {
                    id = tagEntities[random.nextInt(tagEntities.size)].id
                } while (id == 0L || tags.contains(id))
                tags[i] = id
            }
        } else
            tags = null
        // Add 0 to 2 tags
        book.tags = tagEntities.createRelationList(2)

        bookDao.addOrUpdate(book, tags)
        book.tags = (tags?: ArrayList<Any>().apply {
            addAll(tagEntities.entities.filter { it.isSelected }.map { it.id })
        }.toArray()).let {
            val newTags = ArrayList<TagEntity>(book.tags)
            for (id in it) {
                val tag = tagDao.get(id as Long)
                if (tag != null && !book.tags.contains(tag))
                    newTags.add(tag)
            }
            newTags
        }

        assertWithMessage("%s: Add %s", message, book.book.title).apply {
            compareBooks(bookDao.getBook(book.book.id), book)
        }
        tagEntities.linkRelation(book.tags)
        categoryEntities.linkRelation(book.categories)
        authorEntities.linkRelation(book.authors)
        bookEntities.linked(book)
    }

    fun unlinkBook(book: BookAndAuthors) {
        tagEntities.unlinkRelation(book.tags)
        categoryEntities.unlinkRelation(book.categories)
        authorEntities.unlinkRelation(book.authors)
        bookEntities.unlinked(book)
    }

    private suspend fun addBooks(message:String, count: Int) {
        repeat (count) {
            addOneBook(message, random.nextInt(BookEntity.SELECTED or BookEntity.EXPANDED))
        }
        checkConsistency(message)
    }

    @Before
    fun startUp() {
        context = ApplicationProvider.getApplicationContext()
        BookDatabase.initialize(context, true)
        db = BookDatabase.db

        tagEntities = object: Table<TagEntity>(false) {
            override fun make(flags: Int, unique: String): TagEntity {
                return TagEntity(0L, "tag$unique", "desc$unique", flags)
            }
        }
        authorEntities = object: Table<AuthorEntity>() {
            override fun make(flags: Int, unique: String): AuthorEntity {
                return AuthorEntity(0L, "last$unique", "first$unique")
            }
        }
        categoryEntities = object: Table<CategoryEntity>() {
            override fun make(flags: Int, unique: String): CategoryEntity {
                return CategoryEntity(0L, "category$unique")
            }
        }
        bookEntities = object: Table<BookAndAuthors>() {
            override fun make(flags: Int, unique: String): BookAndAuthors {
                return makeBookAndAuthors(next, flags, unique)
            }
        }

        runBlocking {
            addTag()
            addTag(TagEntity.SELECTED)
            addTag(TagEntity.SELECTED)
            addTag(0)
        }
    }

    @After
    fun tearDown() {
        BookDatabase.close()
    }

    @Test/*(timeout = 5000L)*/ fun testAddDeleteBookEntity() {
        runBlocking {
            addBooks("AddBooks", 20)
            val bookDao = db.getBookDao()

            for (b in ArrayList<BookAndAuthors>().apply {
                addAll(bookEntities.entities)
            }) {
                if (b.book.isSelected)
                    unlinkBook(b)
            }
            bookDao.deleteSelected(null,null)
            checkDatabase("Delete Selected")

            var occurrance = 0
            while (bookEntities.size > 0) {
                ++occurrance
                val count = random.nextInt(4).coerceAtMost(bookEntities.size)
                val bookIds = Array<Any>(count) { 0L }
                repeat (count) {
                    val i = random.nextInt(bookEntities.size)
                    val book = bookEntities[i]
                    bookIds[it] = book.book.id
                    unlinkBook(book)
                }
                bookDao.deleteSelected(null, bookIds)
                checkDatabase("Delete $occurrance")
            }
        }
    }
}

private abstract class Table<T>(val autoDelete: Boolean = true) {
    class EntityAndCount<T>(
        val entity: T
    ) {
        var count: Int = 0
    }
    private val list = ArrayList<EntityAndCount<T>>()
    private val gone = ArrayList<T>()
    protected var next = 0
        private set
    fun new(flags: Int = 0): T {
        ++next
        val entity = make(flags, unique())
        list.add(EntityAndCount(entity))
        return entity
    }

    operator fun get(i: Int): T {
        return list[i].entity
    }
    val size: Int
        get() = list.size
    val entitiesAndCounts: Sequence<EntityAndCount<T>>
        get() = list.asSequence()
    val entities: Sequence<T>
        get() = list.asSequence().map { it.entity }
    val deleted: Sequence<T>
        get() = gone.asSequence()

    fun linked(entity: T) {
        ++list.first { it.entity == entity }.count
    }
    fun unlinked(entity: T) {
        val i = list.indexOfFirst { it.entity == entity }
        val item = list[i]
        item.count = (item.count - 1).coerceAtLeast(0)
        if (autoDelete && item.count == 0) {
            gone.add(item.entity)
            list.removeAt(i)
        }
    }


    protected fun unique(): String {
        return when(next) {
            1 -> "\\"
            2 -> "_"
            3 -> "%"
            else -> next.toString()
        }
    }

    protected abstract fun make(flags: Int, unique: String): T
}
