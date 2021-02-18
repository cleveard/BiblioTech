package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.testutils.compareBooks
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
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

@RunWith(AndroidJUnit4::class)
class BookFilterTest {
    private lateinit var context: Context
    private lateinit var dayFormat: DateFormat
    private lateinit var monthFormat: DateFormat
    private lateinit var yearFormat: DateFormat
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

    @Test(timeout = 25000L) fun testEmptySingleFilterFieldNoOrder() {
        runBlocking {
            assertWithMessage("Test Empty").that(contents.size).isGreaterThan(39)
            //val expected = BookDbTracker.addBooks(repo, 8832156L, "Test Empty Filters", 40)
            for (c in columnGetValue) {
                for (p in predicateValue) {
                    testFilter("Empty", contents.asSequence(), Pair(c, p)) { _, _ -> emptyArray() }
                }
            }
        }
    }

    @Test(timeout = 25000L) fun testSingleFilterFieldNoOrder() {
        runBlocking {
            assertWithMessage("Test Empty").that(contents.size).isGreaterThan(39)
            //val expected = BookDbTracker.addBooks(repo, 8832156L, "Test Empty", 40)
            val random = Random(8832156L)
            for (c in columnGetValue) {
                for (p in predicateValue) {
                    testFilter("Random", contents.asSequence(), Pair(c, p)) { column, predicate ->
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
                    }
                }
            }
        }
    }

    @Test(timeout = 25000L) fun testSingleOrderFieldNoFilter() {
        runBlocking {
            for (c in columnGetValue) {
                for (o in Order.values()) {
                    testOrder("Single", contents.asSequence(), Pair(c, o))
                }
            }
        }
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

    private fun testFilter(label: String, seq: Sequence<BookAndAuthors>, vararg filter: Pair<ColumnValue, PredicateValue>, getValues: (ColumnValue, PredicateValue) -> Array<String>) {
        val message = StringBuilder("$label Filter ")
        val list = ArrayList<FilterField>()
        var sequence = seq
        for (f in filter) {
            message.append("${f.first.column}, ${f.second.predicate}, ")
            val vArray = getValues(f.first, f.second)
            list.add(FilterField(f.first.column, f.second.predicate, vArray))
            sequence = sequence.filterSequence(f.first, f.second, vArray)
        }

        val bookFilter = BookFilter(emptyArray(), list.toTypedArray())
        val books = getContents(bookFilter).apply { sortBy { it.book.id } }
        val expected = ArrayList<BookAndAuthors>().apply { addAll(sequence) ; sortBy { it.book.id } }
        assertWithMessage(message.toString()).compare(books, expected)
    }

    private fun testOrder(label: String, seq: Sequence<BookAndAuthors>, vararg order: Pair<ColumnValue, Order>) {
        val message = StringBuilder("$label Order ")
        val list = ArrayList<OrderField>()
        val sortOrder = ArrayList<BookAndAuthors.(BookAndAuthors) -> Int>()
        var sequence = seq
        for (o in order) {
            message.append("${o.first.column}, ${o.second} ")
            list.add(OrderField(o.first.column, o.second, false))
            sortOrder.add(if (o.second == Order.Ascending)
                o.first.orderCompare
            else {
                { -o.first.orderCompare.invoke(this, it) }
            })
            sequence = o.first.orderSequence.invoke(sequence)
        }

        assertWithMessage(message.toString()).apply {
            val bookFilter = BookFilter(list.toTypedArray(), emptyArray())
            val books = getContents(bookFilter)
            val sortSequence = sortOrder.asSequence()
            checkOrder(books, sortSequence)
            val withId = sortSequence + sequenceOf({ this.book.id.compareTo(it.book.id) })
            books.sortWith {b1, b2 -> orderBooks(b1, b2, withId) }
            val expected = ArrayList<BookAndAuthors>().apply { addAll(sequence) ; sortWith {b1, b2 -> orderBooks(b1, b2, withId) } }
            compare(books, expected)
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

    data class ColumnValue(
        val column: Column,
        val oneValue: BookAndAuthors.(Random) -> String?,
        val orderCompare: BookAndAuthors.(BookAndAuthors) -> Int,
        val orderSequence: Sequence<BookAndAuthors>.() -> Sequence<BookAndAuthors> = { this },
        val allValues: BookAndAuthors.() -> Sequence<Sequence<Any?>>,
    )

    data class PredicateTests(
        val string: String.(Array<String>) -> Boolean,
        val int: Int.(Array<String>) -> Boolean,
        val double: Double.(Array<String>) -> Boolean,
        val date: Date.(Array<String>) -> Boolean
    )

    data class PredicateValue(
        val predicate: Predicate,
        val modifyString: String?.(Random) -> String?,
        val test: Sequence<Sequence<Any?>>.(Array<String>) -> Boolean
    )

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
            val c = columnGetValue[r.nextInt(1, columnGetValue.size)]
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

    private val columnGetValue: Array<ColumnValue> = arrayOf (
        // Place holder - actual entry is set below
        ColumnValue(Column.ANY, { null }, { 0 }) { emptySequence() },
        ColumnValue(Column.FIRST_NAME, { if (authors.isNullOrEmpty()) null else authors[it.nextInt(authors.size)].name },
            {
                var o = this.sortFirst.compareWith(it.sortFirst, true)
                if (o == 0)
                    o = this.sortLast.compareWith(it.sortLast, true)
                o
            },
            {
                map {book->
                    if (book.authors.isEmpty())
                        sequenceOf(book.copy(sortFirst = null, sortLast = null))
                    else
                        book.authors.asSequence().map { book.copy(sortFirst = it.remainingName, sortLast = it.lastName) }
                }.flatten()
            }) {
            sequenceOf(authors.asSequence().map { it.name })
        },
        ColumnValue(Column.LAST_NAME, { if (authors.isNullOrEmpty()) null else authors[it.nextInt(authors.size)].lastFirst() },
            {
                var o = this.sortLast.compareWith(it.sortLast, true)
                if (o == 0)
                    o = this.sortFirst.compareWith(it.sortFirst, true)
                o
            },
            {
                map {book->
                    if (book.authors.isEmpty())
                        sequenceOf(book.copy(sortFirst = null, sortLast = null))
                    else
                        book.authors.asSequence().map { book.copy(sortFirst = it.remainingName, sortLast = it.lastName) }
                }.flatten()
            }) {
            sequenceOf(authors.asSequence().map { it.lastFirst() })
        },
        ColumnValue(Column.CATEGORIES, { if (categories.isNullOrEmpty()) null else categories[it.nextInt(categories.size)].category },
            { this.sortCategory.compareWith(it.sortCategory, true) },
            {
                map {book->
                    if (book.categories.isEmpty())
                        sequenceOf(book.copy(sortCategory = null))
                    else
                        book.categories.asSequence().map { book.copy(sortCategory = it.category) }
                }.flatten()
            }) {
            sequenceOf(categories.asSequence().map { it.category })
        },
        ColumnValue(Column.TAGS, { if (tags.isNullOrEmpty()) null else tags[it.nextInt(tags.size)].name },
            { (this.sortTag).compareWith(it.sortTag, true) },
            {
                map {book->
                    if (book.tags.isEmpty())
                        sequenceOf(book.copy(sortTag = null))
                    else
                        book.tags.asSequence().map { book.copy(sortTag = it.name) }
                }.flatten()
            }) {
            sequenceOf(tags.asSequence().map { it.name })
        },
        ColumnValue(Column.TITLE, { book.title }, { book.title.compareWith(it.book.title) }) {
            sequenceOf(sequenceOf(book.title))
        },
        ColumnValue(Column.DATE_MODIFIED, { book.modified.dateValue(it) }, { book.modified.compareTo(it.book.modified) }) {
            sequenceOf(sequenceOf(book.modified))
        },
        ColumnValue(Column.DATE_ADDED, { book.added.dateValue(it) }, { book.added.compareTo(it.book.added) }) {
            sequenceOf(sequenceOf(book.added))
        },
        ColumnValue(Column.SUBTITLE, { book.subTitle }, { book.subTitle.compareWith(it.book.subTitle) }) {
            sequenceOf(sequenceOf(book.subTitle))
        },
        ColumnValue(Column.SOURCE_ID, { book.volumeId }, { book.volumeId.compareWith(it.book.volumeId) }) {
            sequenceOf(sequenceOf(book.volumeId))
        },
        ColumnValue(Column.SOURCE, { book.sourceId }, { book.sourceId.compareWith(it.book.sourceId) }) {
            sequenceOf(sequenceOf(book.sourceId))
        },
        ColumnValue(Column.RATING, { book.rating.toString() }, { book.rating.compareTo(it.book.rating) }) {
            sequenceOf(sequenceOf(book.rating))
        },
        ColumnValue(Column.PAGE_COUNT, { book.pageCount.toString() }, { book.pageCount.compareTo(it.book.pageCount) }) {
            sequenceOf(sequenceOf(book.pageCount))
        },
        ColumnValue(Column.ISBN, { book.ISBN }, { book.ISBN.compareWith(it.book.ISBN) }) {
            sequenceOf(sequenceOf(book.ISBN))
        },
        ColumnValue(Column.DESCRIPTION, { book.description }, { book.description.compareWith(it.book.description) }) {
            sequenceOf(sequenceOf(book.description))
        },
        ColumnValue(Column.BOOK_COUNT, { book.bookCount.toString() }, { book.bookCount.compareTo(it.book.bookCount) }) {
            sequenceOf(sequenceOf(book.bookCount))
        },
    ).also {array ->
        // Set the entry for the Column.Any column
        array[0] = ColumnValue(Column.ANY, { anyValue(it) }, { 0 }) {
            array.asSequence()
                .filter { it.column != Column.ANY }  // Add more filters as needed
                .map { it.allValues(this) }         // Convert array entries to value sequences
                .flatten()                          // Flatten the sequence
        }
    }

    private fun String.randomSubstring(r: Random): String {
        if (this.isEmpty())
            return this
        val start = r.nextInt((length - 5).coerceAtLeast(1))
        val len = r.nextInt(1, 10.coerceAtMost(length - start + 1))
        return substring(start, start + len)
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

    private fun <T, S> T.convertAndTest(filterValues: Array<String>, cvt: String.() -> S, test: T.(S) -> Boolean): Boolean {
        for (v in filterValues) {
            try {
                if (this.test(v.cvt()))
                    return true
            } catch (e: Exception) {}
        }
        return false
    }

    private val oneOf = PredicateTests(
        {array -> this.convertAndTest(array, { this }, { compareTo(it, true) == 0 }) },
        {array -> this.convertAndTest(array, { toInt() }, { this == it }) },
        {array -> this.convertAndTest(array, { toDouble() }, { this == it }) },
        {array -> this.convertAndTest(array, { cvtDate() }, {range -> range?.let { time >= it.first && time <= it.second }?: false }) },
    )

    private val glob = PredicateTests(
        {array -> this.convertAndTest(array, { this }, { this.contains(it, true) }) },
        {array -> this.convertAndTest(array, { this }, { toString().contains(it, true) }) },
        {array -> this.convertAndTest(array, { this }, { toString().contains(it, true) }) },
        {array -> this.convertAndTest(array, { this }, { dayFormat.format(this).contains(it, true) }) },
    )

    private val lt = PredicateTests(
        {array -> this.convertAndTest(array, { this }, { compareTo(it, true) < 0 }) },
        {array -> this.convertAndTest(array, { toInt() }, { this < it }) },
        {array -> this.convertAndTest(array, { toDouble() }, { this < it }) },
        {array -> this.convertAndTest(array, { cvtDate() }, { range -> range?.let { time < it.first }?: false }) },
    )

    private val gt = PredicateTests(
        {array -> this.convertAndTest(array, { this }, { compareTo(it, true) > 0 }) },
        {array -> this.convertAndTest(array, { toInt() }, { this > it }) },
        {array -> this.convertAndTest(array, { toDouble() }, { this > it }) },
        {array -> this.convertAndTest(array, { cvtDate() }, { range -> range?.let { time > it.second }?: false }) },
    )

    private val le = PredicateTests(
        {array -> this.convertAndTest(array, { this }, { compareTo(it, true) <= 0 }) },
        {array -> this.convertAndTest(array, { toInt() }, { this <= it }) },
        {array -> this.convertAndTest(array, { toDouble() }, { this <= it }) },
        {array -> this.convertAndTest(array, { cvtDate() }, { range -> range?.let { time <= it.second }?: false }) },
    )

    private val ge = PredicateTests(
        {array -> this.convertAndTest(array, { this }, { compareTo(it, true) >= 0 }) },
        {array -> this.convertAndTest(array, { toInt() }, { this >= it }) },
        {array -> this.convertAndTest(array, { toDouble() }, { this >= it }) },
        {array -> this.convertAndTest(array, { cvtDate() }, { range -> range?.let { time >= it.first }?: false }) },
    )

    private fun Sequence<Sequence<Any?>>.testValues(filterValues: Array<String>, predicate: PredicateTests): Boolean? {
        var result: Boolean? = null
        forEach {values ->
            var count = 0
            values.forEach {
                ++count
                if (it != null) {
                    val test = when (it) {
                        is String -> predicate.string.invoke(it, filterValues)
                        is Int -> predicate.int.invoke(it, filterValues)
                        is Double -> predicate.double.invoke(it, filterValues)
                        is Date -> predicate.date.invoke(it, filterValues)
                        else -> false
                    }
                    if (test)
                        return true
                    result = test
                }
            }
            if (count == 0)
                result = false
        }
        return result
    }

    private val predicateValue = arrayOf(
        PredicateValue(Predicate.ONE_OF, { this }) { testValues(it, oneOf) == true },
        PredicateValue(Predicate.NOT_ONE_OF, { this }) { testValues(it, oneOf) == false },
        PredicateValue(Predicate.NOT_GLOB, { this?.randomSubstring(it) }) { testValues(it, glob) == false },
        PredicateValue(Predicate.GLOB, { this?.randomSubstring(it) }) { testValues(it, glob) == true },
        PredicateValue(Predicate.LT, { this }) { testValues(it, lt) == true },
        PredicateValue(Predicate.GT, { this }) { testValues(it, gt) == true },
        PredicateValue(Predicate.LE, { this }) { testValues(it, le) == true },
        PredicateValue(Predicate.GE, { this }) { testValues(it, ge) == true },
    )

    private fun Sequence<BookAndAuthors>.filterSequence(c: ColumnValue, p: PredicateValue, values: Array<String>): Sequence<BookAndAuthors> {
        if (!c.column.desc.predicates.contains(p.predicate) || values.isEmpty())
            return this
        return filter {
            p.test.invoke(c.allValues.invoke(it), values)
        }
    }
}
