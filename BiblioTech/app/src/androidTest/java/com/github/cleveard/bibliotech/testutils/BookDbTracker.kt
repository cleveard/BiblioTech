package com.github.cleveard.bibliotech.testutils

import com.github.cleveard.bibliotech.db.*
import com.github.cleveard.bibliotech.makeBookAndAuthors
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.random.Random

fun StandardSubjectBuilder.compareBooks(actual: BookAndAuthors?, expected: BookAndAuthors?) {
    if (actual == null || expected !is BookAndAuthors)
        that(actual).isEqualTo(expected)
    else {
        that(actual.book).isEqualTo(expected.book)
        that(actual.authors).containsExactlyElementsIn(expected.authors)
        that(actual.categories).containsExactlyElementsIn(expected.categories)
        that(actual.tags).containsExactlyElementsIn(expected.tags)
    }

}

class BookDbTracker(private val db: BookDatabase, seed: Long) {
    val random = Random(seed)
    var tagEntities: Table<TagEntity> = object: Table<TagEntity>(
        {flags, _, unique -> TagEntity(0L, "tag$unique", "desc$unique", flags) },
        {entity, prev ->  entity.id = prev?.id?: 0L },
        false
    ) {
        override fun compare(e1: TagEntity, e2: TagEntity): Int {
            return e1.name.compareTo(e2.name, true)
        }
    }
    var authorEntities: Table<AuthorEntity> = object: Table<AuthorEntity>(
        {_, _, unique -> AuthorEntity(0L, "last$unique", "first$unique") },
        {entity, prev ->  entity.id = prev?.id?: 0L }
    ) {
        override fun compare(e1: AuthorEntity, e2: AuthorEntity): Int {
            val f = e1.lastName.compareTo(e2.lastName, true)
            return if (f != 0) f else e1.remainingName.compareTo(e2.remainingName, true)
        }
    }
    var categoryEntities: Table<CategoryEntity> = object: Table<CategoryEntity>(
        {_, _, unique -> CategoryEntity(0L, "category$unique") },
        {entity, prev ->  entity.id = prev?.id?: 0L }
    ) {
        override fun compare(e1: CategoryEntity, e2: CategoryEntity): Int {
            return e1.category.compareTo(e2.category, true)
        }
    }
    var bookEntities: Table<BookAndAuthors> = object: Table<BookAndAuthors>(
        {flags, next, unique -> makeBookAndAuthors(next, flags, unique) },
        {entity, prev ->  entity.book.id = prev?.book?.id?: 0L }
    ) {
        override fun compare(e1: BookAndAuthors, e2: BookAndAuthors): Int {
            var g = e2.book.sourceId?.let { e1.book.sourceId?.compareTo(it, true)?: -1 }?: 1
            if (g == 0) {
                g = e2.book.volumeId?.let { e1.book.volumeId?.compareTo(it, true) ?: -1 } ?: 1
                if (g == 0)
                    return g
            }
            return e2.book.ISBN?.let { e1.book.ISBN?.compareTo(it, true)?: -1 }?: 1
        }
    }

    suspend fun addTag(flags: Int = 0) {
        val entity = tagEntities.new(flags)
        tagEntities.linked(entity)
        tagEntities.unlinked(entity)
        assertWithMessage("AddTag").that(db.getTagDao().add(entity)).isNotEqualTo(0L)
    }

    private fun checkConsistency(message: String) {
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
        val book = bookEntities.new(flags)
        addOneBook(message, book)
        linkBook(book)
    }

    suspend fun addOneBook(message: String, book: BookAndAuthors) {
        val bookDao = db.getBookDao()
        val tagDao = db.getTagDao()

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

        assertWithMessage("%s: Add %s", message, book.book.title).apply {
            bookDao.addOrUpdate(book, tags)
            that(book.book.id).isNotEqualTo(0L)
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

            compareBooks(bookDao.getBook(book.book.id), book)
        }
    }

    fun linkBook(book: BookAndAuthors) {
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

    fun updateBook(book: BookAndAuthors) {
        val prev = bookEntities.update(book)
        if (prev != null) {
            tagEntities.updateRelation(prev.tags, book.tags)
            categoryEntities.updateRelation(prev.categories, book.categories)
            authorEntities.updateRelation(prev.authors, book.authors)
        } else
            linkBook(book)
    }

    suspend fun addBooks(message:String, count: Int) {
        repeat (count) {
            addOneBook(message, random.nextInt(BookEntity.SELECTED or BookEntity.EXPANDED))
        }
        checkConsistency(message)
    }

    data class EntityAndCount<T>(var entity: T, var count: Int = 0)

    abstract inner class Table<T>(
        protected val make: (flags: Int, next: Int, unique: String) -> T,
        protected val setId: (entity: T, prev: T?) -> Unit,
        private val autoDelete: Boolean = true
    ): Comparator<T> {
        private val list = ArrayList<EntityAndCount<T>>()
        private val gone = ArrayList<T>()
        protected var next = 0
            private set
        fun new(flags: Int = 0): T {
            ++next
            val entity = make(flags, next, unique())
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
            val item = list.firstOrNull { it.entity == entity }?: EntityAndCount(entity).also { list.add(it) }
            ++item.count
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
        fun update(entity: T): T? {
            val item = list.firstOrNull { compare(it.entity, entity) == 0 }
            val prev = item?.entity
            setId(entity, prev)
            item?.entity = entity
            return prev
        }

        fun createRelationList(maxCount: Int) : MutableList<T> {
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

        fun linkRelation(linkList: List<T>) {
            for (e in linkList)
                linked(e)
        }

        fun unlinkRelation(linkList: List<T>) {
            for (e in linkList)
                unlinked(e)
        }

        fun updateRelation(from: List<T>, to: List<T>) {
            val entities = TreeMap<T, Int>(this)
            for (e in from)
                entities[e] = entities[e]?.dec()?: -1
            for (e in to)
                entities[e] = entities[e]?.inc()?: 1
            for (e in entities.entries) {
                when {
                    e.value > 0 -> linked(e.key)
                    e.value < 0 -> unlinked(e.key)
                    else -> update(e.key)
                }
            }
        }

        fun checkCounts(message: String, sequence: Sequence<T>, name: (T) -> String) {
            val counts = HashMap<T, Int>(size)
            for (e in sequence)
                counts[e] = (counts[e]?: 0) + 1
            for (e in list)
                assertWithMessage("%s: CheckCounts %s", message, name(e.entity)).that(counts[e.entity]).isEqualTo(e.count)
        }

        protected fun unique(): String {
            return when(next) {
                1 -> "\\"
                2 -> "_"
                3 -> "%"
                else -> next.toString()
            }
        }
    }
}