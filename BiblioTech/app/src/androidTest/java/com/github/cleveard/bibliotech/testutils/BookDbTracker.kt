package com.github.cleveard.bibliotech.testutils

import com.github.cleveard.bibliotech.db.*
import com.github.cleveard.bibliotech.makeBookAndAuthors
import com.github.cleveard.bibliotech.utils.getLive
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CoroutineScope
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

abstract class BookDbTracker(val db: BookDatabase, seed: Long) {
    protected abstract suspend fun getTag(tagId: Long): TagEntity?
    protected abstract suspend fun getBooks(): List<BookAndAuthors>
    protected abstract suspend fun getTags(): List<TagEntity>
    protected abstract suspend fun addOrUpdate(book: BookAndAuthors, tags: Array<Any>?): Array<Any>?
    protected abstract suspend fun addOrUpdateTag(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)? = null): Long
    protected abstract suspend fun addOrUpdateView(view: ViewEntity, onConflict: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)?): Long
    protected abstract suspend fun findTagByName(name: String): TagEntity?
    protected abstract suspend fun findViewByName(name: String): ViewEntity?
    protected abstract suspend fun undo(): Boolean
    protected abstract suspend fun redo(): Boolean
    protected abstract val undoLevels: Int

    val undoTracker: UndoTracker = UndoTracker(db.getUndoRedoDao())
    val random = Random(seed)

    var tables = Tables()
    private val undoneState = UndoTracker.ArrayListRemoveRange<Tables>()
    private val redoneState = UndoTracker.ArrayListRemoveRange<Tables>()

    private suspend fun getBook(bookId: Long): BookAndAuthors? {
        return db.getBookDao().getBook(bookId)
    }

    private suspend fun getAuthors(): List<AuthorEntity> {
        return db.getAuthorDao().get()?: emptyList()
    }

    private suspend fun getCategories(): List<CategoryEntity> {
        return db.getCategoryDao().get()?: emptyList()
    }

    private suspend fun getViews(): List<ViewEntity> {
        return db.getViewDao().getViews()
    }

    suspend fun addTag(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)? = null): Long {
        return addOrUpdateTag(tag, callback).also {
            if (it != 0L) {
                tables.tagEntities.linked(tag)
                tables.tagEntities.unlinked(tag)
            }
        }
    }

    private suspend fun addTag(flags: Int = 0): Long {
        return addTag(tables.tagEntities.new(flags)).also {
            assertWithMessage("AddTag").that(it).isNotEqualTo(0L)
        }
    }

    suspend fun addView(view: ViewEntity, callback: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)? = null): Long {
        return addOrUpdateView(view, callback).also {
            if (it != 0L) {
                tables.viewEntities.linked(view)
            }
        }
    }

    private fun checkConsistency(message: String) {
        val bookSequence = tables.bookEntities.entities
        tables.tagEntities.checkCounts("$message: Tags", bookSequence.map { it.tags.asSequence() }.flatten()) { it.name }
        tables.authorEntities.checkCounts("$message: Authors", bookSequence.map { it.authors.asSequence() }.flatten()) { it.name }
        tables.categoryEntities.checkCounts("$message: Categories", bookSequence.map { it.categories.asSequence() }.flatten()) { it.category }
    }

    suspend fun checkDatabase(message: String, vararg args: Any) {
        assertWithMessage("Check Database: $message", *args).apply {
            val actual = getBooks().sortedBy { it.book.id }
            val expected = tables.bookEntities.entities.toList().sortedBy { it.book.id }
            for (i in 0 until expected.size.coerceAtLeast(actual.size)) {
                compareBooks(
                    if (i >= actual.size) null else actual[i],
                    if (i >= expected.size) null else expected[i]
                )
            }
            that(getTags()).containsExactlyElementsIn(tables.tagEntities.entities.toList())
            that(getAuthors()).containsExactlyElementsIn(tables.authorEntities.entities.toList())
            that(getCategories()).containsExactlyElementsIn(tables.categoryEntities.entities.toList())
            that(getViews()).containsExactlyElementsIn(tables.viewEntities.entities.toList())
        }
    }

    private suspend fun addOneBook(message: String, flags: Int = 0, isUpdate: Boolean = false) {
        val book = tables.bookEntities.new(flags)
        addOneBook(message, book, isUpdate)
    }

    suspend fun addOneBook(message: String, book: BookAndAuthors, isUpdate: Boolean = false) {
        // Add 0 to 3 authors
        book.authors = tables.authorEntities.createRelationList(3, random)
        // Add 0 to 3 categories
        book.categories = tables.categoryEntities.createRelationList(3, random)
        // Add 0 to 4 additional tags
        val tagCount = random.nextInt(5).coerceAtMost(tables.tagEntities.size)
        var tags: Array<Any>?
        if (tagCount != 0 || random.nextBoolean()) {
            tags = Array<Any>(random.nextInt(5)) { 0L }.also {
                repeat(it.size) { i ->
                    var id: Long
                    do {
                        id = tables.tagEntities[random.nextInt(tables.tagEntities.size)].id
                    } while (id == 0L || it.contains(id))
                    it[i] = id
                }
            }
        } else
            tags = null
        // Add 0 to 2 tags
        book.tags = tables.tagEntities.createRelationList(2, random)

        assertWithMessage("%s: Add %s", message, book.book.title).apply {
            tags = addOrUpdate(book, tags)
            that(book.book.id).isNotEqualTo(0L)
            book.tags = (tags?: ArrayList<Any>().apply {
                addAll(tables.tagEntities.entities.filter { it.isSelected }.map { it.id })
            }.toArray()).let {
                val newTags = ArrayList<TagEntity>(book.tags)
                for (id in it) {
                    val tag = getTag(id as Long)
                    if (tag != null && !book.tags.contains(tag))
                        newTags.add(tag)
                }
                newTags
            }

            if (isUpdate)
                updateBook(book)
            else
                linkBook(book)
            val label = "$message: Add ${book.book.title}"
            undoAndCheck(label)
            redoAndCheck(label)
            compareBooks(getBook(book.book.id), book)
        }
    }

    private fun linkBook(book: BookAndAuthors) {
        tables.tagEntities.linkRelation(book.tags)
        tables.categoryEntities.linkRelation(book.categories)
        tables.authorEntities.linkRelation(book.authors)
        tables.bookEntities.linked(book)
    }

    fun unlinkBook(book: BookAndAuthors) {
        tables.tagEntities.unlinkRelation(book.tags)
        tables.categoryEntities.unlinkRelation(book.categories)
        tables.authorEntities.unlinkRelation(book.authors)
        tables.bookEntities.unlinked(book)
    }

    private fun updateBook(book: BookAndAuthors) {
        val prev = tables.bookEntities.linked(book)
        if (prev != null) {
            tables.tagEntities.updateRelation(prev.tags, book.tags)
            tables.categoryEntities.updateRelation(prev.categories, book.categories)
            tables.authorEntities.updateRelation(prev.authors, book.authors)
        } else
            linkBook(book)
    }

    suspend fun testRandomUndo(message: String, count: Int) {
        val dao = db.getUndoRedoDao()
        repeat(count) {
            if (random.nextBoolean()) {
                undoAndCheck("%s: min: %s, undo: %s, max: %s", message, dao.minUndoId, dao.undoId, dao.maxUndoId)
            } else {
                redoAndCheck("%s: min: %s, undo: %s, max: %s", message, dao.minUndoId, dao.undoId, dao.maxUndoId)
            }
        }
        @Suppress("ControlFlowWithEmptyBody")
        while (redoAndCheck("%s: min: %s, undo: %s, max: %s", message, dao.minUndoId, dao.undoId, dao.maxUndoId)) {
        }
    }

    suspend fun addBooks(message:String, count: Int): BookDbTracker {
        repeat (count) {
            addOneBook(message, random.nextInt(BookEntity.SELECTED or BookEntity.EXPANDED), false)
        }
        checkConsistency(message)
        testRandomUndo(message, 10)
        return this
    }

    suspend fun addTags(message: String): BookDbTracker {
        addTag().also {
            undoAndCheck(message)
            redoAndCheck(message)
        }
        addTag(TagEntity.SELECTED).also {
            undoAndCheck(message)
            redoAndCheck(message)
        }
        addTag(TagEntity.SELECTED).also {
            undoAndCheck(message)
            redoAndCheck(message)
        }
        addTag(0).also {
            undoAndCheck(message)
            redoAndCheck(message)
        }
        return this
    }

    fun makeFilter(): BookFilter {
        val values = ArrayList<String>()
        fun getValues(count: Int, selected: Boolean) {
            repeat(count) {
                var book: BookAndAuthors
                do {
                    book = tables.bookEntities[random.nextInt(tables.bookEntities.size)]
                } while (book.book.isSelected != selected && values.contains(book.book.title))
                values.add(book.book.title)
            }
        }
        val selected = tables.bookEntities.entities.filter { it.book.isSelected }.count().coerceAtMost(3)
        getValues(selected, true)
        getValues(5 - selected, false)
        return BookFilter(emptyArray(), arrayOf(
            FilterField(Column.TITLE, Predicate.ONE_OF, values.toTypedArray())
        ))
    }

    fun undoStarted() {
        if (undoLevels > 0 && !db.getUndoRedoDao().isRecording) {
            undoneState.add(tables.copy())
            redoneState.clear()
        }
    }
    suspend fun undoEnded(message: String, succeeded: Boolean) {
        if (undoLevels > 0 && !db.getUndoRedoDao().isRecording) {
            if (!succeeded) {
                undoneState.removeLast()
            } else {
                val state = undoneState.last()
                state.clearFlags(tables)
                undoTracker.record(message)
                if (undoneState.size > undoLevels)
                    undoneState.removeRange(0, undoneState.size - undoLevels)
            }
        }
    }

    fun undoLevelsChanged() {
        undoTracker.undoLevelsChanged(undoLevels)
        if (undoneState.size + redoneState.size > undoLevels) {
            undoneState.removeRange(0, (undoneState.size + redoneState.size - undoLevels).coerceAtMost(undoneState.size))
            if (redoneState.size > undoLevels) {
                redoneState.removeRange(undoLevels, redoneState.size)
            }
        }
    }

    suspend fun undoAndCheck(message: String, vararg args: Any): Boolean {
        if (db.getUndoRedoDao().isRecording)
            return false
        val label = "Undo and check $message"
        val canUndo = undoneState.isNotEmpty()
        assertWithMessage(label, *args).apply {
            that(undo()).isEqualTo(canUndo)
            that(undoTracker.undo()).isEqualTo(canUndo)
            if (undoneState.isNotEmpty()) {
                val state = undoneState.removeLast()
                state.swapTables(tables)
                redoneState.add(tables)
                tables = state
            }
            checkDatabase(label, *args)
            undoTracker.checkUndo(label, *args)
        }
        return canUndo
    }

    suspend fun redoAndCheck(message: String, vararg args: Any): Boolean {
        if (db.getUndoRedoDao().isRecording)
            return false
        val label = "Redo and check $message"
        val canRedo = redoneState.isNotEmpty()
        assertWithMessage(label, *args).apply {
            that(redo()).isEqualTo(canRedo)
            that(undoTracker.redo()).isEqualTo(canRedo)
            if (redoneState.isNotEmpty()) {
                val state = redoneState.removeLast()
                state.swapTables(tables)
                undoneState.add(tables)
                tables = state
            }
            checkDatabase(label, *args)
            undoTracker.checkUndo(label, *args)
        }
        return canRedo
    }

    companion object {
        suspend fun addBooks(_db: BookDatabase, seed: Long, message: String, count: Int): BookDbTracker {
            // Updating a book that conflicts with two other books will fail
            return object: BookDbTracker(_db, seed) {
                override suspend fun getTag(tagId: Long): TagEntity? {
                    return db.getTagDao().get(tagId)
                }

                override suspend fun getBooks(): List<BookAndAuthors> {
                    return db.getBookDao().getBookList().getLive()?: emptyList()
                }

                override suspend fun getTags(): List<TagEntity> {
                    return db.getTagDao().getLive(false).getLive()?: emptyList()                }

                override suspend fun addOrUpdate(book: BookAndAuthors, tags: Array<Any>?): Array<Any>? {
                    undoStarted()
                    db.getUndoRedoDao().withUndo("Add or Update Book") {
                        db.getBookDao().addOrUpdateWithUndo(book, tags)
                    }.also {
                        undoEnded("Add or Update Book", it != 0L)
                    }
                    return tags
                }

                override suspend fun addOrUpdateTag(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)?): Long {
                    undoStarted()
                    return db.getUndoRedoDao().withUndo("Add Tag") {
                        db.getTagDao().addWithUndo(tag, callback)
                    }.also {
                        undoEnded("Add Tag", it != 0L)
                    }
                }

                override suspend fun addOrUpdateView(view: ViewEntity, onConflict: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)?): Long {
                    undoStarted()
                    return db.getUndoRedoDao().withUndo("Add View") {
                        db.getViewDao().addOrUpdateWithUndo(view, onConflict)
                    }.also {
                        undoEnded("Add View", it != 0L)
                    }
                }

                override suspend fun findTagByName(name: String): TagEntity? {
                    return db.getTagDao().findByName(name)
                }

                override suspend fun findViewByName(name: String): ViewEntity? {
                    return db.getViewDao().findByName(name)
                }

                override suspend fun undo(): Boolean {
                    return db.getUndoRedoDao().undo()
                }

                override suspend fun redo(): Boolean {
                    return db.getUndoRedoDao().redo()
                }

                override val undoLevels: Int
                    get() { return db.getUndoRedoDao().maxUndoLevels }
            }.apply {
                if (!db.getUndoRedoDao().isRecording)
                    undoTracker.clearUndo(message)
                if (count > 0)
                    addTags(message).addBooks(message, count)
            }
        }

        suspend fun addBooks(repo: BookRepository, seed: Long, message: String, count: Int): BookDbTracker {
            // Updating a book that conflicts with two other books will fail
            return object: BookDbTracker(BookDatabase.db, seed) {
                override suspend fun getTag(tagId: Long): TagEntity? {
                    return repo.getTag(tagId)
                }

                override suspend fun getBooks(): List<BookAndAuthors> {
                    return repo.getBookList().getLive()?: emptyList()
                }

                override suspend fun getTags(): List<TagEntity> {
                    return repo.getTagsLive(false).getLive()?: emptyList()
                }

                override suspend fun addOrUpdate(book: BookAndAuthors, tags: Array<Any>?): Array<Any>? {
                    undoStarted()
                    repo.addOrUpdateBook(book).also {
                        undoEnded("Add or Update Book", it != 0L)
                    }
                    return null
                }

                override suspend fun addOrUpdateTag(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)?): Long {
                    undoStarted()
                    return repo.addOrUpdateTag(tag, callback).also {
                        undoEnded("Add Tag", it != 0L)
                    }
                }

                override suspend fun addOrUpdateView(view: ViewEntity, onConflict: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)?): Long {
                    undoStarted()
                    return repo.addOrUpdateView(view, onConflict).also {
                        undoEnded("Add View", it != 0L)
                    }
                }

                override suspend fun findTagByName(name: String): TagEntity? {
                    return repo.findTagByName(name)
                }

                override suspend fun findViewByName(name: String): ViewEntity? {
                    return repo.findViewByName(name)
                }

                override suspend fun undo(): Boolean {
                    return repo.undo()
                }

                override suspend fun redo(): Boolean {
                    return repo.redo()
                }

                override val undoLevels: Int
                    get() = repo.maxUndoLevels
            }.apply {
                if (!db.getUndoRedoDao().isRecording)
                    undoTracker.clearUndo(message)
                if (count > 0)
                    addTags(message).addBooks(message, count)
            }
        }
    }

    abstract class Table<T>(
        protected val make: (flags: Int, next: Int, unique: String) -> T,
        protected val setId: (entity: T, prev: T?) -> Unit,
        protected val sameId: (e1: T, e2: T) -> Boolean,
        private val autoDelete: Boolean = true
    ): Comparator<T> {
        data class EntityAndCount<T>(var entity: T, var count: Int = 0)

        private val list = ArrayList<EntityAndCount<T>>()
        protected var next = 0
            private set
        fun new(flags: Int = 0): T {
            ++next
            return this.make(flags, next, unique())
        }

        fun copy(from: Table<T>, copyMap: (entity: T) -> T) {
            list.clear()
            list.addAll(from.list.map { EntityAndCount(copyMap(it.entity), it.count) })
            next = from.next
        }

        operator fun get(i: Int): T {
            return list[i].entity
        }
        val size: Int
            get() = list.size
        val entities: Sequence<T>
            get() = list.asSequence().map { it.entity }

        fun linked(entity: T): T? {
            var item = list.firstOrNull { sameId(it.entity, entity) }
            val prev = item?.entity
            setId(entity, prev)
            if (item == null)
                item = EntityAndCount(entity).also { list.add(it) }
            else
                item.entity = entity
            ++item.count
            return prev
        }
        fun unlinked(entity: T, delete: Boolean = false) {
            val i = list.indexOfFirst { sameId(it.entity, entity) }
            val item = list[i]
            item.count = (item.count - 1).coerceAtLeast(0)
            if (delete || (autoDelete && item.count == 0)) {
                list.removeAt(i)
            }
        }
        fun merge(entity: T, delete: T) {
            val e = list.first { sameId(it.entity, entity) }
            val d = list.indexOfFirst { sameId(it.entity, delete) }
            if (d >= 0) {
                e.count += list[d].count
                list.removeAt(d)
            }
        }

        fun createRelationList(maxCount: Int, random: Random) : MutableList<T> {
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
                    e.value >= 0 -> linked(e.key)
                    e.value < 0 -> unlinked(e.key)
                }
            }
        }

        fun checkCounts(message: String, sequence: Sequence<T>, name: (T) -> String) {
            val counts = HashMap<T, Int>(size)
            for (e in sequence)
                counts[e] = (counts[e]?: 0) + 1
            for (e in list)
                assertWithMessage("%s: CheckCounts %s", message, name(e.entity)).that(counts[e.entity]?: 0).isEqualTo(e.count)
        }

        private fun unique(): String {
            return when(next) {
                1 -> "\\"
                2 -> "_"
                3 -> "%"
                else -> next.toString()
            }
        }
    }

    inner class Tables(
        val tagEntities: Table<TagEntity>,
        val authorEntities: Table<AuthorEntity>,
        val categoryEntities: Table<CategoryEntity>,
        val bookEntities: Table<BookAndAuthors>,
        val viewEntities: Table<ViewEntity>
    ) {
        constructor(): this(
            object: Table<TagEntity>(
                {flags, _, unique -> TagEntity(0L, "tag$unique", "desc$unique", flags) },
                {entity, prev ->  prev?.let { entity.id = it.id } },
                {e1, e2 -> e1.id == e2.id },
                false
            ) {
                override fun compare(e1: TagEntity, e2: TagEntity): Int {
                    return e1.name.compareTo(e2.name, true)
                }
            },
            object: Table<AuthorEntity>(
                {_, _, unique -> AuthorEntity(0L, "last$unique", "first$unique") },
                {entity, prev ->  prev?.let { entity.id = it.id } },
                {e1, e2 -> e1.id == e2.id }
            ) {
                override fun compare(e1: AuthorEntity, e2: AuthorEntity): Int {
                    val f = e1.lastName.compareTo(e2.lastName, true)
                    return if (f != 0) f else e1.remainingName.compareTo(e2.remainingName, true)
                }
            },
            object: Table<CategoryEntity>(
                {_, _, unique -> CategoryEntity(0L, "category$unique") },
                {entity, prev ->  prev?.let { entity.id = it.id } },
                {e1, e2 -> e1.id == e2.id }
            ) {
                override fun compare(e1: CategoryEntity, e2: CategoryEntity): Int {
                    return e1.category.compareTo(e2.category, true)
                }
            },
            object: Table<BookAndAuthors>(
                {flags, next, unique -> makeBookAndAuthors(next, flags, unique) },
                {entity, prev ->  prev?.let { entity.book.id = it.book.id } },
                {e1, e2 -> e1.book.id == e2.book.id }
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
            },
            object: Table<ViewEntity>(
                {_, _, unique -> ViewEntity(0L, "view$unique", "desc$unique", makeFilter(), 0) },
                {entity, prev ->  prev?.let { entity.id = it.id } },
                {e1, e2 -> e1.id == e2.id }
            ) {
                override fun compare(e1: ViewEntity, e2: ViewEntity): Int {
                    return e1.name.compareTo(e2.name, true)
                }
            }
        )

        fun clearFlags(new: Tables) {
            val tags = new.tagEntities.entities.map { it.id }.toSet()
            for (t in tagEntities.entities) {
                if (!tags.contains(t.id))
                    t.flags = 0
            }
            val books = new.bookEntities.entities.map { it.book.id }.toSet()
            for (b in bookEntities.entities) {
                if (!books.contains(b.book.id))
                    b.book.flags = 0
            }
        }

        fun swapTables(old: Tables) {
            val tags = mapOf(*old.tagEntities.entities.map { Pair(it.id, it) }.toList().toTypedArray())
            for (t in tagEntities.entities)
                tags[t.id]?.let { t.flags = it.flags }
            val books = mapOf(*old.bookEntities.entities.map { Pair(it.book.id, it) }.toList().toTypedArray())
            for (b in bookEntities.entities)
                books[b.book.id]?.let { b.book.flags = it.book.flags }
        }

        fun copy(): Tables {
            return Tables().also {t ->
                t.tagEntities.copy(tagEntities) { it.copy() }
                val tags = mapOf(*t.tagEntities.entities.map { Pair(it.id, it) }.toList().toTypedArray())
                t.authorEntities.copy(authorEntities) { it }
                t.categoryEntities.copy(categoryEntities) { it }
                t.bookEntities.copy(bookEntities) {book ->
                    book.copy(
                        book = book.book.copy(),
                        tags = book.tags.map { tags[it.id]!! }
                    )
                }
                t.viewEntities.copy(viewEntities) { it.copy() }
            }
        }
    }}