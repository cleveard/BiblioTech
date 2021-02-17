package com.github.cleveard.bibliotech.db


import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.testutils.BookDbTracker
import com.github.cleveard.bibliotech.testutils.compareBooks
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Truth.assertThat
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

    @Before
    fun startUp() {
        context = ApplicationProvider.getApplicationContext()
        //context = InstrumentationRegistry.getInstrumentation().context
        BookRepository.initialize(context, true)
        repo = BookRepository.repo
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
            val expected = BookDbTracker.addBooks(repo, 8832156L, "Test Empty Filters", 40)
            for (c in columnGetValue) {
                for (p in predicateValue) {
                    testFilter("Empty", expected.bookEntities.entities, Pair(c, p)) { _, _ -> emptyArray() }
                }
            }
        }
    }

    @Test(timeout = 25000L) fun testSingleFilterFieldNoOrder() {
        runBlocking {
            val expected = BookDbTracker.addBooks(repo, 8832156L, "Test Empty", 40)
            for (c in columnGetValue) {
                for (p in predicateValue) {
                    testFilter("Random", expected.bookEntities.entities, Pair(c, p)) { column, predicate ->
                        val values = ArrayList<String>()
                        repeat(expected.random.nextInt(1, 4)) {
                            var v: String?
                            do {
                                val book = expected.bookEntities[expected.random.nextInt(expected.bookEntities.size)]
                                v = predicate.modifyString.invoke(column.oneValue.invoke(book, expected.random), expected.random)
                            } while (v.isNullOrEmpty() || values.contains(v))
                            values.add(v)
                        }
                        values.toTypedArray()
                    }
                }
            }
        }
    }

    private fun StandardSubjectBuilder.compare(books: List<BookAndAuthors>, expected: List<BookAndAuthors>) {
        that(books.size).isEqualTo(expected.size)
        for ((i, b) in books.withIndex())
            compareBooks(b, expected[i])
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
        val allValues: BookAndAuthors.() -> Sequence<Any?>
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
        val test: Sequence<Any?>.(Array<String>) -> Boolean
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

    val columnGetValue: Array<ColumnValue> = arrayOf (
        // Place holder - actual entry is set below
        ColumnValue(Column.ANY, { null }) { emptySequence() },
        ColumnValue(Column.FIRST_NAME, { if (authors.isNullOrEmpty()) null else authors[it.nextInt(authors.size)].name }) {
            authors.asSequence().map { it.name }
        },
        ColumnValue(Column.LAST_NAME, { if (authors.isNullOrEmpty()) null else authors[it.nextInt(authors.size)].lastFirst() }) {
            authors.asSequence().map { it.lastFirst() }
        },
        ColumnValue(Column.CATEGORIES, { if (categories.isNullOrEmpty()) null else categories[it.nextInt(categories.size)].category }) {
            categories.asSequence().map { it.category }
        },
        ColumnValue(Column.TAGS, { if (tags.isNullOrEmpty()) null else tags[it.nextInt(tags.size)].name }) {
            tags.asSequence().map { it.name }
        },
        ColumnValue(Column.TITLE, { book.title }) {
            sequenceOf(book.title)
        },
        ColumnValue(Column.DATE_MODIFIED, { book.modified.dateValue(it) }) {
            sequenceOf(book.modified)
        },
        ColumnValue(Column.DATE_ADDED, { book.added.dateValue(it) }) {
            sequenceOf(book.added)
        },
        ColumnValue(Column.SUBTITLE, { book.subTitle }) {
            sequenceOf(book.subTitle)
        },
        ColumnValue(Column.SOURCE_ID, { book.volumeId }) {
            sequenceOf(book.volumeId)
        },
        ColumnValue(Column.SOURCE, { book.sourceId }) {
            sequenceOf(book.sourceId)
        },
        ColumnValue(Column.RATING, { book.rating.toString() }) {
            sequenceOf(book.rating)
        },
        ColumnValue(Column.PAGE_COUNT, { book.pageCount.toString() }) {
            sequenceOf(book.pageCount)
        },
        ColumnValue(Column.ISBN, { book.ISBN }) {
            sequenceOf(book.ISBN)
        },
        ColumnValue(Column.DESCRIPTION, { book.description }) {
            sequenceOf(book.description)
        },
        ColumnValue(Column.BOOK_COUNT, { book.bookCount.toString() }) {
            sequenceOf(book.bookCount)
        },
    ).also {array ->
        // Set the entry for the Column.Any column
        array[0] = ColumnValue(Column.ANY, { anyValue(it) }) {
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

    private fun Sequence<Any?>.testValues(filterValues: Array<String>, predicate: PredicateTests): Boolean {
        for (b in this) {
            val test = when (b) {
                is String -> { predicate.string.invoke(b, filterValues) }
                is Int -> { predicate.int.invoke(b, filterValues) }
                is Double -> { predicate.double.invoke(b, filterValues) }
                is Date -> { predicate.date.invoke(b, filterValues) }
                else -> { false }
            }
            if (test)
                return true
        }
        return false
    }

    private val predicateValue = arrayOf(
        PredicateValue(Predicate.ONE_OF, { this }) { testValues(it, oneOf) },
        PredicateValue(Predicate.NOT_ONE_OF, { this }) { !testValues(it, oneOf) },
        PredicateValue(Predicate.NOT_GLOB, { this?.randomSubstring(it) }) { !testValues(it, glob) },
        PredicateValue(Predicate.GLOB, { this?.randomSubstring(it) }) { testValues(it, glob) },
        PredicateValue(Predicate.LT, { this }) { testValues(it, lt) },
        PredicateValue(Predicate.GT, { this }) { testValues(it, gt) },
        PredicateValue(Predicate.LE, { this }) { testValues(it, le) },
        PredicateValue(Predicate.GE, { this }) { testValues(it, ge) },
    )

    private fun Sequence<BookAndAuthors>.filterSequence(c: ColumnValue, p: PredicateValue, values: Array<String>): Sequence<BookAndAuthors> {
        if (!c.column.desc.predicates.contains(p.predicate) || values.isEmpty())
            return this
        return filter {
            p.test.invoke(c.allValues.invoke(it), values)
        }
    }
}
