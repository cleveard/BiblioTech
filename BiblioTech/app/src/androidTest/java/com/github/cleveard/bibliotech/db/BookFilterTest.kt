package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.testutils.compareBooks
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.runner.RunWith
import java.lang.Exception
import java.lang.StringBuilder
import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random
import kotlin.reflect.KMutableProperty0

@RunWith(AndroidJUnit4::class)
class BookFilterTest {
    private lateinit var context: Context
    private lateinit var repo: BookRepository
    private lateinit var contents: List<BookAndAuthors>

    @Before
    fun startUp() {
        context = ApplicationProvider.getApplicationContext()
        //context = InstrumentationRegistry.getInstrumentation().context
        BookRepository.initialize(context)
        repo = BookRepository.repo
        contents = getContents()
        val locale = context.resources.configuration.locales[0]
        dayFormat = DateFormat.getDateInstance(DateFormat.SHORT, locale)
        monthFormat = SimpleDateFormat(context.resources.getString(R.string.month_year_pattern), locale)
        yearFormat = SimpleDateFormat("y", locale)
    }

    @After
    fun tearDown() {
        BookRepository.close()
    }

    @Test(timeout = 5000L) fun testOrderField() {
        val o1 = OrderField(Column.SOURCE, Order.Descending, true)
        assertWithMessage("Order Field").apply {
            var o2 = o1.copy()
            that(o1 == o2).isTrue()
            o2 = o1.copy(column = Column.ISBN)
            that(o1 == o2).isFalse()
            o2 = o1.copy(order = Order.Ascending)
            that(o1 == o2).isFalse()
            o2 = o1.copy(headers = false)
            that(o1 == o2).isFalse()
        }
    }

    @Test(timeout = 5000L) fun testFilterField() {
        val f1 = FilterField(Column.SOURCE, Predicate.ONE_OF, arrayOf("jjll", "kkik", "kkkok"))
        assertWithMessage("Order Field").apply {
            var o2 = FilterField(Column.SOURCE, Predicate.ONE_OF, arrayOf("jjll", "kkik", "kkkok"))
            that(f1 == o2).isTrue()
            o2 = FilterField(Column.ISBN, Predicate.ONE_OF, arrayOf("jjll", "kkik", "kkkok"))
            that(f1 == o2).isFalse()
            o2 = FilterField(Column.SOURCE, Predicate.GE, arrayOf("jjll", "kkik", "kkkok"))
            that(f1 == o2).isFalse()
            o2 = FilterField(Column.SOURCE, Predicate.ONE_OF, arrayOf("jjll", "kkikx", "kkkok"))
            that(f1 == o2).isFalse()
        }
    }

    /**
     * Change a property and check that it isn't equal
     * @param v1 Object to be changed
     * @param v2 Object that is equal to v1
     * @param p Property to change
     * @param v Value to set
     */
    private fun <T, R> StandardSubjectBuilder.changeAndCheck(v1: T, v2: T, p: KMutableProperty0<R>, v: R) {
        val save = p.get()
        p.set(v)
        that(p.get()).isEqualTo(v)
        that(v1 == v2).isFalse()
        that(v1.hashCode()).isNotEqualTo(v2.hashCode())
        p.set(save)
        that(p.get()).isEqualTo(save)
        that(v1 == v2).isTrue()
        that(v1.hashCode()).isEqualTo(v2.hashCode())
    }

    @Test(timeout = 5000L) fun testBookFilter() {
        val bf1 = BookFilter(
            arrayOf(
                OrderField(Column.ISBN, Order.Ascending, false),
                OrderField(Column.BOOK_COUNT, Order.Descending, true)
            ),
            arrayOf(
                FilterField(Column.BOOK_COUNT, Predicate.GE, arrayOf("4", "5", "6")),
                FilterField(Column.PAGE_COUNT, Predicate.LT, arrayOf("60", "40", "30"))
            )
        )
        assertWithMessage("Book Filter").apply {
            var bf2 = BookFilter(
                arrayOf(
                    OrderField(Column.ISBN, Order.Ascending, false),
                    OrderField(Column.BOOK_COUNT, Order.Descending, true)
                ),
                arrayOf(
                    FilterField(Column.BOOK_COUNT, Predicate.GE, arrayOf("4", "5", "6")),
                    FilterField(Column.PAGE_COUNT, Predicate.LT, arrayOf("60", "40", "30"))
                )
            )
            var o2 = FilterField(Column.SOURCE, Predicate.ONE_OF, arrayOf("jjll", "kkik", "kkkok"))
            that(bf1 == bf2).isTrue()
            bf2 = BookFilter(
                arrayOf(
                    OrderField(Column.TITLE, Order.Ascending, false),
                    OrderField(Column.BOOK_COUNT, Order.Descending, true)
                ),
                arrayOf(
                    FilterField(Column.BOOK_COUNT, Predicate.GE, arrayOf("4", "5", "6")),
                    FilterField(Column.PAGE_COUNT, Predicate.LT, arrayOf("60", "40", "30"))
                )
            )
            that(bf1 == bf2).isFalse()
            bf2 = BookFilter(
                arrayOf(
                    OrderField(Column.ISBN, Order.Ascending, false),
                    OrderField(Column.BOOK_COUNT, Order.Descending, true)
                ),
                arrayOf(
                    FilterField(Column.BOOK_COUNT, Predicate.GE, arrayOf("4", "5", "6")),
                    FilterField(Column.TAGS, Predicate.LT, arrayOf("60", "40", "30"))
                )
            )
            that(bf1 == bf2).isFalse()
        }
    }

    @Test(timeout = 5000L) fun testBookFilterSerialize() {
        runBlocking {
            val random = Random(419675L)
            val string = BookFilter.encodeToString(null)
            assertWithMessage("Serialize null").that(BookFilter.decodeFromString(string)).isNull()
            repeat (200) {
                val orderDesc = ArrayList<Pair<ColumnValue,Order>>()
                val filterDesc = ArrayList<Pair<ColumnValue,PredicateValue>>()
                repeat (random.nextInt(0, 7)) {
                    var p: Pair<ColumnValue,Order>
                    do {
                        p = Pair(ColumnValue.values()[random.nextInt(ColumnValue.values().size)],
                            if (random.nextBoolean()) Order.Ascending else Order.Descending)
                    } while (orderDesc.contains(p))
                    orderDesc.add(p)
                }
                repeat(random.nextInt(0, 7)) {
                    var p: Pair<ColumnValue,PredicateValue>
                    do {
                        var c: ColumnValue
                        do {
                            c = ColumnValue.values()[random.nextInt(ColumnValue.values().size)]
                        } while (c.column.desc.predicates.isEmpty())
                        val pr = c.column.desc.predicates[random.nextInt(c.column.desc.predicates.size)]
                        p = Pair(c, PredicateValue.values().first {x -> x.predicate == pr })
                    } while (filterDesc.contains(p))
                    filterDesc.add(p)
                }
                TestFilter(orderDesc.asSequence(), filterDesc.asSequence()) { column, predicate ->
                    val values = ArrayList<String>()
                    val count = random.nextInt(0, 8)
                    repeat(10) {
                        if (values.size < count) {
                            val v: String?
                            val book = contents[random.nextInt(contents.size)]
                            v = predicate.modifyString.invoke(column.oneValue.invoke(book, random), random)
                            if (!v.isNullOrEmpty() && !values.contains(v))
                                values.add(v)
                        }
                    }
                    values.toTypedArray()
                }.testSerialize(this@BookFilterTest, "Serialize $it")
            }
        }
    }

    @Test(timeout = 25000L) fun testEmptySingleFilterFieldNoOrder() {
        runBlocking {
            assertWithMessage("Test Empty").that(contents.size).isGreaterThan(39)
            //val expected = BookDbTracker.addBooks(repo, 8832156L, "Test Empty Filters", 40)
            for (c in ColumnValue.values()) {
                for (p in PredicateValue.values()) {
                    TestFilter(emptySequence(), sequenceOf(Triple(c, p, emptyArray())))
                        .test(this@BookFilterTest, "Empty", contents.asSequence())
                }
            }
        }
    }

    @Test(timeout = 25000L) fun testSingleFilterFieldNoOrder() {
        runBlocking {
            assertWithMessage("Test Random").that(contents.size).isGreaterThan(39)
            //val expected = BookDbTracker.addBooks(repo, 8832156L, "Test Empty", 40)
            val random = Random(8832156L)
            for (c in ColumnValue.values()) {
                for (p in PredicateValue.values()) {
                    TestFilter(emptySequence(), sequenceOf(Pair(c, p))) { column, predicate ->
                        val values = ArrayList<String>()
                        val count = random.nextInt(1, 5)
                        repeat(10) {
                            if (values.size < count) {
                                val v: String?
                                val book = contents[random.nextInt(contents.size)]
                                v = predicate.modifyString.invoke(column.oneValue.invoke(book, random), random)
                                if (!v.isNullOrEmpty() && !values.contains(v))
                                    values.add(v)
                            }
                        }
                        values.toTypedArray()
                    }.test(this@BookFilterTest, "Single Random", contents.asSequence())
                }
            }
        }
    }

    @Test(timeout = 25000L) fun testSingleOrderFieldNoFilter() {
        runBlocking {
            assertWithMessage("Test Random").that(contents.size).isGreaterThan(39)
            for (c in ColumnValue.values()) {
                for (o in Order.values()) {
                    TestFilter(sequenceOf(Pair(c, o)), emptySequence()) {_, _ -> emptyArray() }
                        .test(this@BookFilterTest, "Single Order", contents.asSequence())
                }
            }
        }
    }

    @Test(timeout = 25000L) fun testMultipleOrderMultipleFilter() {
        runBlocking {
            assertWithMessage("Test Multiple").that(contents.size).isGreaterThan(39)
            //val expected = BookDbTracker.addBooks(repo, 8832156L, "Test Empty", 40)
            val random = Random(3352997L)
            repeat (50) {
                val orderDesc = ArrayList<Pair<ColumnValue,Order>>()
                val filterDesc = ArrayList<Pair<ColumnValue,PredicateValue>>()
                repeat (random.nextInt(1, 3)) {
                    var p: Pair<ColumnValue,Order>
                    do {
                        p = Pair(ColumnValue.values()[random.nextInt(ColumnValue.values().size)],
                            if (random.nextBoolean()) Order.Ascending else Order.Descending)
                    } while (orderDesc.contains(p))
                    orderDesc.add(p)
                }
                repeat(random.nextInt(1, 3)) {
                    var p: Pair<ColumnValue,PredicateValue>
                    do {
                        var c: ColumnValue
                        do {
                            c = ColumnValue.values()[random.nextInt(ColumnValue.values().size)]
                        } while (c.column.desc.predicates.isEmpty())
                        val pr = c.column.desc.predicates[random.nextInt(c.column.desc.predicates.size)]
                        p = Pair(c, PredicateValue.values().first {x -> x.predicate == pr })
                    } while (filterDesc.contains(p))
                    filterDesc.add(p)
                }
                TestFilter(orderDesc.asSequence(), filterDesc.asSequence()) { column, predicate ->
                    val values = ArrayList<String>()
                    val count = random.nextInt(1, 5)
                    repeat(10) {
                        if (values.size < count) {
                            val v: String?
                            val book = contents[random.nextInt(contents.size)]
                            v = predicate.modifyString.invoke(column.oneValue.invoke(book, random), random)
                            if (!v.isNullOrEmpty() && !values.contains(v))
                                values.add(v)
                        }
                    }
                    values.toTypedArray()
                }.test(this@BookFilterTest, "Multiple Random $it", contents.asSequence())
            }
        }
    }

    //@Test
    fun debugTests() {
        runBlocking {
            assertWithMessage("Debug Test").that(contents.size).isGreaterThan(39)
            val test = TestFilter.fromString(
                """
                    {
                      "order": [
                      ],
                      "filter": [
                        {
                          "first": "ANY",
                          "second": "NOT_GLOB",
                          "third": [
                            "iD"
                          ]
                        }
                      ]
                    }                    
                """
            )
            test.test(this@BookFilterTest, "Debug Test", contents.asSequence())
        }
    }

    @Serializable
    private data class TestFilter(
        val order: List<Pair<ColumnValue, Order>>,
        val filter: List<Triple<ColumnValue, PredicateValue, Array<String>>>
    ) {
        constructor(
            order: Sequence<Pair<ColumnValue, Order>>,
            filter: Sequence<Pair<ColumnValue, PredicateValue>>,
            getValues: (ColumnValue, PredicateValue) -> Array<String>
        ): this(order, filter.map { Triple(it.first, it.second, getValues(it.first, it.second)) })

        constructor(
            order: Sequence<Pair<ColumnValue, Order>>,
            filter: Sequence<Triple<ColumnValue, PredicateValue, Array<String>>>
        ): this(order.toList(), filter.toList())

        override fun toString(): String {
            return Json.encodeToString(serializer(), this)
        }

        fun test(test: BookFilterTest, label: String, seq: Sequence<BookAndAuthors>) {
            val message = StringBuilder("$label Filter ")
            val filterList = ArrayList<FilterField>()
            val orderList = ArrayList<OrderField>()
            val sortOrder = ArrayList<BookAndAuthors.(BookAndAuthors) -> Int>()
            var sequence = seq
            for (o in order) {
                message.append("${o.first.column}, ${o.second}, ")
                orderList.add(OrderField(o.first.column, o.second, false))
                sortOrder.add(if (o.second == Order.Ascending)
                    o.first.orderCompare
                else {
                    { -o.first.orderCompare.invoke(this, it) }
                })
                sequence = o.first.orderSequence.invoke(sequence)
            }

            for (f in filter) {
                message.append("${f.first.column}, ${f.second.predicate}, ")
                filterList.add(FilterField(f.first.column, f.second.predicate, f.third))
                sequence = sequence.filterSequence(f.first, f.second, f.third)
            }


            assertWithMessage("%s: Filter: %s", message.toString(), this).apply {
                val bookFilter = BookFilter(orderList.toTypedArray(), filterList.toTypedArray())
                val books = test.getContents(bookFilter)
                val sortSequence = sortOrder.asSequence()
                if (sortOrder.isNotEmpty())
                    checkOrder(books, sortSequence)
                val withId = sortSequence + sequenceOf({ this.book.id.compareTo(it.book.id) })
                books.sortWith { b1, b2 -> orderBooks(b1, b2, withId) }
                val expected = ArrayList<BookAndAuthors>().apply { addAll(sequence); sortWith { b1, b2 -> orderBooks(b1, b2, withId) } }
                compare(books, expected)
            }
        }

        fun testSerialize(test: BookFilterTest, label: String) {
            val message = StringBuilder("$label Filter ")
            val filterList = ArrayList<FilterField>()
            val orderList = ArrayList<OrderField>()
            for (o in order) {
                message.append("${o.first.column}, ${o.second}, ")
                orderList.add(OrderField(o.first.column, o.second, false))
            }

            for (f in filter) {
                message.append("${f.first.column}, ${f.second.predicate}, ")
                filterList.add(FilterField(f.first.column, f.second.predicate, f.third))
            }

            assertWithMessage("%s: Filter: %s", message.toString(), this).apply {
                val bookFilter = BookFilter(orderList.toTypedArray(), filterList.toTypedArray())
                val string = BookFilter.encodeToString(bookFilter)
                val newFilter = BookFilter.decodeFromString(string)
                that(newFilter).isEqualTo(bookFilter)
            }
        }

        companion object {
            fun fromString(serialized: String): TestFilter {
                return Json.decodeFromString(serializer(), serialized)
            }

            private fun StandardSubjectBuilder.compare(books: List<BookAndAuthors>, expected: List<BookAndAuthors>) {
                withMessage("Compare Books %s, %s", books.size, expected.size).apply {
                    var i = 0
                    var j = 0
                    repeat(books.size.coerceAtMost(expected.size)) {
                        compareBooks(books[i++], expected[j++])
                    }
                    if (i < books.size)
                        compareBooks(books[i], null)
                    else if (j < expected.size)
                        compareBooks(null, expected[j])
                }
            }

            private fun orderBooks(b1: BookAndAuthors, b2: BookAndAuthors, order: Sequence<(BookAndAuthors, BookAndAuthors) -> Int>): Int {
                order.forEach {
                    val o = it(b1, b2)
                    if (o != 0)
                        return o
                }
                return 0
            }

            private fun StandardSubjectBuilder.checkOrder(books: List<BookAndAuthors>, order: Sequence<(BookAndAuthors, BookAndAuthors) -> Int>) {
                if (books.isNotEmpty()) {
                    for (i in 1 until books.size) {
                        withMessage("Check Order %s", i).apply {
                            that(orderBooks(books[i - 1], books[i], order)).isAtMost(0)
                        }
                    }
                }
            }

            private fun Sequence<BookAndAuthors>.filterSequence(c: ColumnValue, p: PredicateValue, values: Array<String>): Sequence<BookAndAuthors> {
                if (!c.column.desc.predicates.contains(p.predicate) || values.isEmpty())
                    return this
                return filter {
                    p.test.invoke(c.allValues.invoke(it, p.predicate), values)
                }
            }
        }
    }

    private enum class ColumnValue(
        val column: Column,
        val oneValue: BookAndAuthors.(Random) -> String?,
        val orderCompare: BookAndAuthors.(BookAndAuthors) -> Int,
        val orderSequence: Sequence<BookAndAuthors>.() -> Sequence<BookAndAuthors>,
        val allValues: BookAndAuthors.(p: Predicate) -> Sequence<Sequence<Any>>,
    ) {
        // Place holder - actual entry is set below
        // Set the entry for the Column.Any column
        ANY(Column.ANY, { anyValue(it) }, { 0 }, { this },
            {predicate ->
                values().asSequence()
                .filter { it != ANY &&
                    // All of the columns that ANY will add. You can uncomment
                    // these if you are trying to narrow a test error
                    // Make sure you uncomment the same ones in the
                    // anyColumn object in the Column.kt
                    // it != FIRST_NAME &&
                    // it != TAGS &&
                    // it != CATEGORIES &&
                    // it != TITLE &&
                    // it != SUBTITLE &&
                    // it != DESCRIPTION &&
                    // it != SOURCE &&
                    // it != SOURCE_ID &&
                    // it != ISBN &&
                    // it != PAGE_COUNT &&
                    // it != BOOK_COUNT &&
                    it.column.desc.predicates.contains(predicate) }  // Add more filters as needed
                .map { it.allValues.invoke(this, predicate) }         // Convert array entries to value sequences
                .flatten()                          // Flatten the sequence
            }
        ),
        FIRST_NAME(Column.FIRST_NAME, { if (authors.isNullOrEmpty()) null else authors[it.nextInt(authors.size)].name },
            {
                var o = this.sortFirst.compareWith(it.sortFirst, true)
                if (o == 0)
                    o = this.sortLast.compareWith(it.sortLast, true)
                o
            },
            {
                map { book ->
                    if (book.authors.isEmpty())
                        sequenceOf(book.copy(sortFirst = null, sortLast = null))
                    else
                        book.authors.asSequence().map { book.copy(sortFirst = it.remainingName, sortLast = it.lastName) }
                }.flatten()
            },
            { sequenceOf(authors.asSequence().map { it.name }) }
        ),
        LAST_NAME(Column.LAST_NAME, { if (authors.isNullOrEmpty()) null else authors[it.nextInt(authors.size)].lastFirst() },
            {
                var o = this.sortLast.compareWith(it.sortLast, true)
                if (o == 0)
                    o = this.sortFirst.compareWith(it.sortFirst, true)
                o
            },
            {
                map { book ->
                    if (book.authors.isEmpty())
                        sequenceOf(book.copy(sortFirst = null, sortLast = null))
                    else
                        book.authors.asSequence().map { book.copy(sortFirst = it.remainingName, sortLast = it.lastName) }
                }.flatten()
            },
            { sequenceOf(authors.asSequence().map { it.lastFirst() }) }
        ),
        CATEGORIES(Column.CATEGORIES, { if (categories.isNullOrEmpty()) null else categories[it.nextInt(categories.size)].category },
            { this.sortCategory.compareWith(it.sortCategory, true) },
            {
                map { book ->
                    if (book.categories.isEmpty())
                        sequenceOf(book.copy(sortCategory = null))
                    else
                        book.categories.asSequence().map { book.copy(sortCategory = it.category) }
                }.flatten()
            }, { sequenceOf(categories.asSequence().map { it.category }) }
        ),
        TAGS(Column.TAGS, { if (tags.isNullOrEmpty()) null else tags[it.nextInt(tags.size)].name },
            { (this.sortTag).compareWith(it.sortTag, true) },
            {
                map { book ->
                    if (book.tags.isEmpty())
                        sequenceOf(book.copy(sortTag = null))
                    else
                        book.tags.asSequence().map { book.copy(sortTag = it.name) }
                }.flatten()
            }, { sequenceOf(tags.asSequence().map { it.name }) }
        ),
        TITLE(Column.TITLE, { book.title }, { book.title.compareWith(it.book.title) }, { this }, {
            sequenceOf(sequenceOf(book.title))
        }),
        DATE_MODIFIED(Column.DATE_MODIFIED, { book.modified.dateValue(it) }, { book.modified.compareTo(it.book.modified) }, { this }, {
            sequenceOf(sequenceOf(book.modified))
        }),
        DATE_ADDED(Column.DATE_ADDED, { book.added.dateValue(it) }, { book.added.compareTo(it.book.added) }, { this }, {
            sequenceOf(sequenceOf(book.added))
        }),
        SUBTITLE(Column.SUBTITLE, { book.subTitle }, { book.subTitle.compareWith(it.book.subTitle) }, { this }, {
            sequenceOf(sequenceOf(book.subTitle))
        }),
        SOURCE_ID(Column.SOURCE_ID, { book.volumeId }, { (book.volumeId?: "").compareWith(it.book.volumeId?: "") }, { this }, {
            sequenceOf(sequenceOf(book.volumeId?: ""))
        }),
        SOURCE(Column.SOURCE, { book.sourceId }, { (book.sourceId?: "").compareWith(it.book.sourceId?: "") }, { this }, {
            sequenceOf(sequenceOf(book.sourceId?: ""))
        }),
        RATING(Column.RATING, { book.rating.toString() }, { book.rating.compareTo(it.book.rating) }, { this }, {
            sequenceOf(sequenceOf(book.rating))
        }),
        PAGE_COUNT(Column.PAGE_COUNT, { book.pageCount.toString() }, { book.pageCount.compareTo(it.book.pageCount) }, { this }, {
            sequenceOf(sequenceOf(book.pageCount))
        }),
        ISBN(Column.ISBN, { book.ISBN }, { (book.ISBN?: "").compareWith(it.book.ISBN?: "") }, { this }, {
            sequenceOf(sequenceOf(book.ISBN?: ""))
        }),
        DESCRIPTION(Column.DESCRIPTION, { book.description }, { book.description.compareWith(it.book.description) }, { this }, {
            sequenceOf(sequenceOf(book.description))
        }),
        BOOK_COUNT(Column.BOOK_COUNT, { book.bookCount.toString() }, { book.bookCount.compareTo(it.book.bookCount) }, { this }, {
            sequenceOf(sequenceOf(book.bookCount))
        });

        companion object {
            private fun AuthorEntity.lastFirst(): String {
                return "$lastName, $remainingName"
            }

            private fun Date.dateValue(r: Random): String {
                val format = when (r.nextInt(3)) {
                    0 -> { dayFormat }
                    1 -> { monthFormat }
                    else -> { yearFormat }
                }
                // Parse the date. If the parse fails try year month
                return format.format(this)
            }

            private fun BookAndAuthors.anyValue(r: Random): String {
                var v: String?
                do {
                    val c = values()[r.nextInt(1, values().size)]
                    v = c.oneValue.invoke(this, r)
                } while (v == null || !c.column.desc.predicates.contains(Predicate.GLOB))
                return v
            }

            private fun String?.compareWith(other: String?, ignoreCase: Boolean = false): Int {
                return when {
                    this === other -> 0
                    this == null -> -1
                    other == null -> 1
                    else -> this.compareTo(other, ignoreCase)
                }
            }
        }
    }

    private enum class PredicateTests(
        val string: String.(Array<String>) -> Boolean,
        val int: Int.(Array<String>) -> Boolean,
        val double: Double.(Array<String>) -> Boolean,
        val date: Date.(Array<String>) -> Boolean
    ) {
        ONE_OF(
            { array -> this.convertAndTest(array, { this }, { compareTo(it, true) == 0 }) },
            { array -> this.convertAndTest(array, { toInt() }, { this == it }) },
            { array -> this.convertAndTest(array, { toDouble() }, { this == it }) },
            { array -> this.convertAndTest(array, { cvtDate() }, { range -> range?.let { time >= it.first && time <= it.second } ?: false }) },
        ),
        GLOB(
            { array -> this.convertAndTest(array, { this }, { this.contains(it, true) }) },
            { array -> this.convertAndTest(array, { this }, { toString().contains(it, true) }) },
            { array -> this.convertAndTest(array, { this }, { toString().contains(it, true) }) },
            { array -> this.convertAndTest(array, { this }, { dayFormat.format(this).contains(it, true) }) },
        ),
        LT(
            { array -> this.convertAndTest(array, { this }, { compareTo(it, true) < 0 }) },
            { array -> this.convertAndTest(array, { toInt() }, { this < it }) },
            { array -> this.convertAndTest(array, { toDouble() }, { this < it }) },
            { array -> this.convertAndTest(array, { cvtDate() }, { range -> range?.let { time < it.first } ?: false }) },
        ),
        GT(
            { array -> this.convertAndTest(array, { this }, { compareTo(it, true) > 0 }) },
            { array -> this.convertAndTest(array, { toInt() }, { this > it }) },
            { array -> this.convertAndTest(array, { toDouble() }, { this > it }) },
            { array -> this.convertAndTest(array, { cvtDate() }, { range -> range?.let { time > it.second } ?: false }) },
        ),
        LE(
            { array -> this.convertAndTest(array, { this }, { compareTo(it, true) <= 0 }) },
            { array -> this.convertAndTest(array, { toInt() }, { this <= it }) },
            { array -> this.convertAndTest(array, { toDouble() }, { this <= it }) },
            { array -> this.convertAndTest(array, { cvtDate() }, { range -> range?.let { time <= it.second } ?: false }) },
        ),
        GE(
            { array -> this.convertAndTest(array, { this }, { compareTo(it, true) >= 0 }) },
            { array -> this.convertAndTest(array, { toInt() }, { this >= it }) },
            { array -> this.convertAndTest(array, { toDouble() }, { this >= it }) },
            { array -> this.convertAndTest(array, { cvtDate() }, { range -> range?.let { time >= it.first } ?: false }) },
        );

        companion object {
            private fun <T, S> T.convertAndTest(filterValues: Array<String>, cvt: String.() -> S, test: T.(S) -> Boolean): Boolean {
                for (v in filterValues) {
                    try {
                        if (this.test(v.cvt()))
                            return true
                    } catch (e: Exception) {}
                }
                return false
            }

            private fun String.cvtDate(): Pair<Long, Long>? {
                // Start parsing at the start of the value string
                val pos = ParsePosition(0)
                val millisPerDay = 60L * 60L * 1000L * 24L

                val time: Long
                val limit: Long
                // Parse the date. If the parse fails try year month
                if (dayFormat.parse(this, pos) != null && pos.index >= length) {
                    // Got the date, set the limit to one day
                    time = dayFormat.calendar.timeInMillis
                    limit = millisPerDay
                } else {

                    // Try month and year. Get the date formatter and try to parse the string
                    pos.index = 0
                    if (monthFormat.parse(this, pos) != null && pos.index >= length) {
                        // Got the date, set the limit to one month
                        val calendar = monthFormat.calendar
                        time = calendar.timeInMillis
                        limit = millisPerDay * (calendar.getActualMaximum(Calendar.DAY_OF_MONTH) + 1 -
                            calendar.getActualMinimum(Calendar.DAY_OF_MONTH))
                    } else {

                        pos.index = 0
                        if (yearFormat.parse(this, pos) != null && pos.index >= length) {
                            // Got the date, set the limit to one month
                            val calendar = yearFormat.calendar
                            time = calendar.timeInMillis
                            limit = millisPerDay * (calendar.getActualMaximum(Calendar.DAY_OF_YEAR) + 1 -
                                calendar.getActualMinimum(Calendar.DAY_OF_YEAR))
                        } else
                            return null
                    }
                }

                return Pair(time, time + limit - 1)
            }
        }
    }

    private enum class PredicateValue(
        val predicate: Predicate,
        val modifyString: String?.(Random) -> String?,
        val test: Sequence<Sequence<Any>>.(Array<String>) -> Boolean
    ) {
        ONE_OF(Predicate.ONE_OF, { this }, { testValues(it, PredicateTests.ONE_OF) }),
        NOT_ONE_OF(Predicate.NOT_ONE_OF, { this }, { !testValues(it, PredicateTests.ONE_OF) }),
        NOT_GLOB(Predicate.NOT_GLOB, { this?.randomSubstring(it) }, { !testValues(it, PredicateTests.GLOB) }),
        GLOB(Predicate.GLOB, { this?.randomSubstring(it) }, { testValues(it, PredicateTests.GLOB) }),
        LT(Predicate.LT, { this }, { testValues(it, PredicateTests.LT) }),
        GT(Predicate.GT, { this }, { testValues(it, PredicateTests.GT) }),
        LE(Predicate.LE, { this }, { testValues(it, PredicateTests.LE) }),
        GE(Predicate.GE, { this }, { testValues(it, PredicateTests.GE) });

        companion object {

            private fun Sequence<Sequence<Any>>.testValues(filterValues: Array<String>, predicate: PredicateTests): Boolean {
                forEach {values ->
                    values.forEach {
                        val test = when (it) {
                            is String -> predicate.string.invoke(it, filterValues)
                            is Int -> predicate.int.invoke(it, filterValues)
                            is Double -> predicate.double.invoke(it, filterValues)
                            is Date -> predicate.date.invoke(it, filterValues)
                            else -> false
                        }
                        if (test)
                            return true
                    }
                }
                return false
            }

            private fun String.randomSubstring(r: Random): String {
                if (this.isEmpty())
                    return this
                val start = r.nextInt((length - 5).coerceAtLeast(1))
                val len = r.nextInt(1, 10.coerceAtMost(length - start + 1))
                return substring(start, start + len)
            }
        }
    }

    private fun getContents(): ArrayList<BookAndAuthors> {
        return getContents(repo.getBooks())
    }

    private fun getContents(filter: BookFilter): ArrayList<BookAndAuthors> {
        return getContents(repo.getBooks(filter, context))
    }

    private fun getContents(page: PagingSource<Int, BookAndAuthors>): ArrayList<BookAndAuthors> {
        return runBlocking {
            val list = ArrayList<BookAndAuthors>()
            suspend fun nextPage(params: PagingSource.LoadParams<Int>): Int? {
                val result = page.load(params) as PagingSource.LoadResult.Page
                list.addAll(result.data)
                return result.nextKey
            }
            var key = nextPage(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = 20,
                    placeholdersEnabled = false
                )
            )
            while(key != null) {
                key = nextPage(
                    PagingSource.LoadParams.Append(
                        key = key,
                        loadSize = 20,
                        placeholdersEnabled = false
                    )
                )
            }
            list
        }
    }

    companion object {
        private lateinit var dayFormat: DateFormat
        private lateinit var monthFormat: DateFormat
        private lateinit var yearFormat: DateFormat
    }
}
