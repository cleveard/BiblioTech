package com.example.cleve.bibliotech.db

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.cleve.bibliotech.R
import kotlinx.serialization.Serializable
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.floor

// Temp variables used when calculating separates for dates
private val cal1: Calendar = Calendar.getInstance()
private val cal2: Calendar = Calendar.getInstance()

/**
 * The description of a filter to filter books
 * @param orderList A list of fields used to order books for the filter.
 * @param filterList A list of fields used to filter books for the filter.
 * This class is serializable and I have decided to make new classes for each
 * version. The current version will be BookFilter and older versions will have
 * a version number appended to the class name. I moved everything outside of
 * the class to make this easier.
 */
@Serializable
data class BookFilter(val orderList: Array<OrderField>, val filterList: Array<FilterField>) {
    /** @inheritDoc */
    override fun hashCode(): Int {
        return hashArray(orderList) * 31 + hashArray(filterList)
    }

    /** @inheritDoc */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookFilter

        if (!equalArray(orderList, other.orderList)) return false
        if (!equalArray(filterList, other.filterList)) return false
        return true
    }

    /** @inheritDoc */
    fun isSameQuery(other: Any?) : Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookFilter

        if (orderList.size != other.orderList.size) return false
        for (f in orderList.indices) {
            if (!orderList[f].isSameQuery(other.orderList[f])) return false
        }

        if (filterList.size != other.filterList.size) return false
        for (f in filterList.indices) {
            if (!filterList[f].isSameQuery(other.filterList[f])) return false
        }
        return true
    }

    companion object: BookFilterCompanion() {
        val VERSION = 0
    }
}

/**
 * Class with companion methods for BookFilter
 */
open class BookFilterCompanion() {
    /**
     * Build an SQLite query from a filter description
     * @param filter The filter description
     */
    fun buildFilterQuery(filter: BookFilter?): SupportSQLiteQuery {
        // The object used to build the query
        val builder = object: BuildQuery {
            // The select columns
            val selectSpec: StringBuilder = StringBuilder()
            // Joins
            val joinSpec: StringBuilder = StringBuilder()
            // Order columns
            val orderSpec: StringBuilder = StringBuilder()
            // Group by columns
            val groupSpec: StringBuilder = StringBuilder()
            // Where expression
            val filterSpec: StringBuilder = StringBuilder()
            // Track joins to prevent adding multiple times
            val joins: ArrayList<String> = ArrayList()
            // Track select columns to prevent adding multiple times
            val selects: ArrayList<String> = ArrayList()
            // Track order columns to prevent adding multiple times
            val orders: ArrayList<String> = ArrayList()

            /** @inheritDoc */
            override fun addSelect(name: String) {
                if (selects.indexOf(name) == -1) {
                    selects.add(name)
                    selectSpec.append("${if (selectSpec.isEmpty()) "" else ", "}$name")
                }
            }

            /** @inheritDoc */
            override fun addJoin(table: String, leftColumn: String, joinColumn: String) {
                if (joins.indexOf(table) == -1) {
                    joins.add(table)
                    joinSpec.append("${if (joinSpec.isEmpty()) "" else " "} LEFT JOIN $table ON $leftColumn = $joinColumn")
                }
            }

            /** @inheritDoc */
            override fun addOrderColumn(name: String, direction: String) {
                if (orders.indexOf(name) == -1) {
                    orders.add(name)
                    orderSpec.append("${if (orderSpec.isEmpty()) "" else ", "}$name $direction")
                    groupSpec.append("${if (groupSpec.isEmpty()) "" else ", "}$name")
                }
            }

            /**
             * Collect the select columns from an iterator of columns in fields
             * @param list List of columns in array of fields
             */
            fun buildSelect(list: Iterator<Column>) {
                // Add all book columns first
                for (column in ALL_BOOK_COLUMNS)
                    addSelect(column)
                // Add columns from fields
                for (field in list) {
                    field.desc.addSelection(this)
                }
            }

            /**
             * Collect joins from an iterator of columns in fields
             * @param list List of columns in array of fields
             */
            fun buildJoin(list: Iterator<Column>) {
                // Add any other fields next
                for (field in list) {
                    field.desc.addJoin(this)
                }
            }

            /**
             * Collect order by columns from iterator of columns in fields
             * @param list List of columns in array of fields
             */
            fun buildOrder(list: Iterator<OrderField>) {
                for (field in list) {
                    field.column.desc.addOrder(this, field.order)
                }
                groupSpec.append("${if (groupSpec.isEmpty()) "" else ", "}$BOOK_ID_COLUMN")
            }

            fun buildFilter(list: Iterator<FilterField>) {
            }
        }

        // If filter is null, don't collect anything
        if (filter != null) {
            // Collect select columns from orderList
            builder.buildSelect(MapIterator(filter.orderList.iterator()) { it.column })
            // Collect select columns from filterList
            builder.buildSelect(MapIterator(filter.filterList.iterator()) { it.column })
            // Collect joins from orderList
            builder.buildJoin(MapIterator(filter.orderList.iterator()) { it.column })
            // Collect joins from filterList
            builder.buildJoin(MapIterator(filter.filterList.iterator()) { it.column })
            // Collect order by columns from orderList
            builder.buildOrder(filter.orderList.iterator())
            // Collect order by columns from filterList
            builder.buildFilter(filter.filterList.iterator())
        }

        // Build the SQLite command
        val spec = StringBuilder()
        spec.append("SELECT ${builder.selectSpec} FROM $BOOK_TABLE")
        // Add joins if there are any
        if (builder.joinSpec.isNotEmpty())
            spec.append(" ${builder.joinSpec}")
        // Add where if we have a filter
        if (builder.filterSpec.isNotEmpty())
            spec.append(" WHERE ${builder.filterSpec}")
        // Add group by if we need it
        if (builder.groupSpec.isNotEmpty())
            spec.append(" GROUP BY ${builder.groupSpec}")
        // Add order by if there is one
        if (builder.orderSpec.isNotEmpty())
            spec.append(" ORDER BY ${builder.orderSpec}")

        // Return SQLite query
        return SimpleSQLiteQuery(spec.toString())
    }

    /** Calculate hash from array contents */
    fun <T> hashArray(array: Array<T>): Int {
        var result = 0
        for (v in array)
            result = result * 31 + v.hashCode()
        return result
    }

    /** Compare array contents */
    fun <T, R> equalArray(a1: Array<T>, a2: Array<R>): Boolean {
        if (a1 === a2) return true
        if (a1.javaClass != a2.javaClass) return false
        if (a1.size != a2.size) return false

        for (i in a1.indices) {
            if (a1[i] != a2[i])
                return false
        }
        return true
    }
}

/**
 * A field to order books
 * @param column The column used for ordering
 * @param order The order applied to the column
 * @param headers True if this field should be included in separators
 */
@Serializable
data class OrderField(
    val column: Column,
    val order: Order,
    val headers: Boolean
) {
    /**
     * Determine whether this field is the same SQLite query as another one
     * @param other The other field
     */
    fun isSameQuery(other: Any?) : Boolean {
        // Make sure other is an OrderField
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OrderField

        // The query is the same if the column and order are the same.
        // Separators don't matter
        if (column != other.column) return false
        if (order != other.order) return false
        return true
    }
}

/**
 * A field used to filter books
 * To make this serializable, the FilterField class is abstract and
 * a generic Value subclass is used to type the values for the filter
 */
@Serializable
sealed class FilterField {
    /** The column being filters */
    abstract val column: Column

    /** The predicate for the filter */
    abstract val predicate: Predicate

    /** Will this field generate the same SQL query as another */
    abstract fun isSameQuery(other: Any?) : Boolean

    /**
     * Get the values for the filter field, or null
     * @param className The class name of the value type.
     * @return The value array, if the classname argument matches the field className, or null
     */
    abstract fun <R> getValuesForType(className: String?): Array<R>?

    /**
     * Inline function to supply class name for _getValuesForType
     * @return The value array, if the class types match
     */
    inline fun <reified T> getValuesForType(): Array<T>? {
        return getValuesForType(T::class.qualifiedName)
    }

    /**
     * Inline function to create a generic Value field and supply the className
     * @param column The column for the field
     * @param predicate The predicate for the field
     * @param values The values for the field
     * @return The created FilterField
     */
    inline fun <reified T> createField(column: Column, predicate: Predicate, values: Array<T>): FilterField {
        return Value(column, predicate, values, T::class.qualifiedName)
    }

    /**
     * A field used to filter books
     * @param column The column for filtering
     * @param predicate The predicate for filtering
     * @param values The filter values
     * @param className The name of the value type.
     */
    @Serializable
    class Value<T>(
        override val column: Column,
        override val predicate: Predicate,
        private val values: Array<T>,
        private val className: String?
    ): FilterField() {
        /**
         * Indicate whether two fields are the same query
         * @param other The other field
         */
        override fun isSameQuery(other: Any?) : Boolean {
            return this == other
        }

        /** @inheritDoc */
        @Suppress("UNCHECKED_CAST")
        override fun <R> getValuesForType(className: String?): Array<R>? {
            // Check the class name and return the value or null
            // The cast is unsafe, but hopefully verifying the class name will make it OK
            return if (className == this.className)
                values as? Array<R>
            else
                null
        }

        /** @inheritDoc */
        override fun equals(other: Any?): Boolean {
            // If references are equal, so are the object
            if (this === other) return true
            // If the classes are different, objects are different
            if (this.javaClass != other?.javaClass) return false

            other as Value<*>

            // Make sure contents are equal
            if (column != other.column) return false
            if (predicate != other.predicate) return false
            return BookFilter.equalArray(values, other.values)
        }

        /** @inheritDoc */
        override fun hashCode(): Int {
            var hash = column.hashCode()
            hash = hash * 31 + predicate.hashCode()
            hash = hash * 31 + BookFilter.hashArray(values)
            return hash
        }
    }
}

@Suppress("unused")
enum class Predicate {
    ONE_OF,
    ALL_OF,
    GLOB,
    GT,
    GE,
    LT,
    LE
}

/** Order direction enum */
enum class Order(val dir: String) {
    /** Ascending direction */
    Ascending(kAsc),
    /** Descending direction */
    Descending(kDesc);
}

/**
 * Enum of columns
 * The enums not only identify the column, but also contain information about how to use it
 */
@Suppress("unused")
enum class Column(val desc: DataDescriptor) {
    /** Author - Last, First */
    LAST_NAME(lastFirst),
    /** Author - First Last */
    FIRST_NAME(firstLast),
    /** Enum meaning filter everywhere */
    ANY(anyColumn),
    /** Title */
    TITLE(title),
    /** Subtitle */
    SUBTITLE(subtitle),
    /** Description */
    DESCRIPTION(description),
    /** Tags */
    TAGS(tags),
    /** Categories */
    CATEGORIES(categories),
    /** Source of book details */
    SOURCE(source),
    /** Id of book details in the source */
    SOURCE_ID(sourceId),
    /** ISBN */
    ISBN(isbn),
    /** Page Count */
    PAGE_COUNT(pageCount),
    /** Number of books tracked */
    BOOK_COUNT(bookCount),
    /** Rating */
    RATING(rating),
    /** Date book was added to the database */
    DATE_ADDED(dateAdded),
    /** Date book was last modified in the database */
    DATE_MODIFIED(dateModified);
}

/** An interface used to add components to a query */
interface BuildQuery {
    /**
     * Add a name to the columns selected by the query
     * @param name The name to add
     * Can be called multiple time with the same name. The name is only added once
     */
    fun addSelect(name: String)

    /**
     * Add a left join to the query
     * @param table The name of the table to be joined
     * @param leftColumn The column in query used for the join
     * @param joinColumn The column in table used for the join
     * Can be call multiple times with the same table name. The join is only added once,
     * even if leftColumn and joinColumn are different. Only the first call is used.
     */
    fun addJoin(table: String, leftColumn: String, joinColumn: String)

    /**
     * Add a column to order the query
     * @param name The name of the column
     * @param direction The SQL direction - ASC or DESC
     * This can be called multiple times with the same name. The order is only added once.
     * Only the first call is used.
     */
    fun addOrderColumn(name: String, direction: String)
}

/**
 * Utility class to map the contents of an iterator
 * @param <T> The type of the source iterator
 * @param <R> The type of the mapped iterator
 * @param src The source iterator
 * @param map A lambda to map objects in the source iterator to the destination iterator.
 */
class MapIterator<T, R>(private val src: Iterator<T>, private val map: (T) -> R) : Iterator<R> {
    /**
     * @inheritDoc
     * The MapIterator has more objects only when the src does
     */
    override fun hasNext(): Boolean = src.hasNext()

    /**
     * @inheritDoc
     * Map the next source object to the destination
     */
    override fun next(): R = map(src.next())
}

/**
 * Abstract class used to describe the data in filter columns
 * @param columns The database column names used by the filter columns
 * @param nameResourceId The resource id for the localized name of the filter column
 */
abstract class DataDescriptor(private val columns: Array<String>, val nameResourceId: Int) {
    /** Add a join if needed to extract the column */
    open fun addJoin(buildQuery: BuildQuery) {
        // Default is that no join is required
    }

    /**
     * Add columns to order list
     * @param buildQuery The query we are building
     * @param direction The direction the columns are orders
     */
    open fun addOrder(buildQuery: BuildQuery, direction: Order) {
        // Default to adding columns in order
        for (f in columns)
            buildQuery.addOrderColumn(f, direction.dir)
    }

    /**
     * Add columns to select
     * @param buildQuery The query we are building
     */
    open fun addSelection(buildQuery: BuildQuery) {
        // Default to adding columns in order
        for (f in columns)
            buildQuery.addSelect(f)
    }

    /**
     * Get the column value from a book
     * @param book The book holding the value
     * @param locale The locale to use for displaying the value
     */
    abstract fun getValue(book: BookAndAuthors, locale: Locale): String

    /**
     * Return whether a separator should be between two books
     * @param book The first book
     * @param other The other book
     * @return True if a separator is needed
     */
    abstract fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean

    /**
     * Get the value string to include in the separator
     * @param book The book after the separator
     * @param locale The locale to use for the separator
     */
    open fun getSeparatorValue(book: BookAndAuthors, locale: Locale): String {
        return getValue(book, locale)
    }
}

/** Author - Last, First */
private val lastFirst = object: DataDescriptor(arrayOf(LAST_NAME_COLUMN, REMAINING_COLUMN), R.string.author) {
    /** @inheritDoc */
    override fun addJoin(buildQuery: BuildQuery) {
        buildQuery.addJoin(BOOK_AUTHORS_TABLE, BOOK_ID_COLUMN, BOOK_AUTHORS_BOOK_ID_COLUMN)
        buildQuery.addJoin(AUTHORS_TABLE, BOOK_AUTHORS_AUTHOR_ID_COLUMN, AUTHORS_ID_COLUMN)
    }

    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.sortLast != other.sortLast || book.sortFirst != other.sortFirst
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return "${book.sortLast?: ""}, ${book.sortFirst?: ""}"
    }
}

/** Author - First Last */
private val firstLast = object: DataDescriptor(arrayOf(REMAINING_COLUMN, LAST_NAME_COLUMN), R.string.author) {
    /** @inheritDoc */
    override fun addJoin(buildQuery: BuildQuery) {
        buildQuery.addJoin(BOOK_AUTHORS_TABLE, BOOK_ID_COLUMN, BOOK_AUTHORS_BOOK_ID_COLUMN)
        buildQuery.addJoin(AUTHORS_TABLE, BOOK_AUTHORS_AUTHOR_ID_COLUMN, AUTHORS_ID_COLUMN)
    }

    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.sortLast != other.sortLast || book.sortFirst != other.sortFirst
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return "${book.sortFirst?: ""} ${book.sortLast?: ""}"
    }
}

/** Enum meaning filter everywhere */
private val anyColumn = object: DataDescriptor(arrayOf(), 0) {
    /** @inheritDoc */
    override fun addJoin(buildQuery: BuildQuery) {
    }

    /** @inheritDoc */
    override fun addOrder(buildQuery: BuildQuery, direction: Order) {
    }

    /** @inheritDoc */
    override fun addSelection(buildQuery: BuildQuery) {
    }

    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return ""
    }
}

/** Title */
private val title = object: DataDescriptor(arrayOf(TITLE_COLUMN), R.string.title) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return book.book.title
    }
}

/** Subtitle */
private val subtitle = object: DataDescriptor(arrayOf(SUBTITLE_COLUMN), R.string.subtitle) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return book.book.subTitle
    }
}

/**
 * Description
 * Not allowed as separator
 */
private val description = object: DataDescriptor(arrayOf(DESCRIPTION_COLUMN), 0) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return ""
    }
}

/** Tags */
private val tags = object: DataDescriptor(arrayOf(TAGS_NAME_COLUMN), R.string.tag) {
    /** @inheritDoc */
    override fun addJoin(buildQuery: BuildQuery) {
        buildQuery.addJoin(BOOK_TAGS_TABLE, BOOK_ID_COLUMN, BOOK_TAGS_BOOK_ID_COLUMN)
        buildQuery.addJoin(TAGS_TABLE, BOOK_TAGS_TAG_ID_COLUMN, TAGS_ID_COLUMN)
    }

    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.sortTag != other.sortTag
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return book.sortTag?: ""
    }
}

/** Categories */
private val categories = object: DataDescriptor(arrayOf(CATEGORY_COLUMN), R.string.category) {
    /** @inheritDoc */
    override fun addJoin(buildQuery: BuildQuery) {
        buildQuery.addJoin(BOOK_CATEGORIES_TABLE, BOOK_ID_COLUMN, BOOK_CATEGORIES_BOOK_ID_COLUMN)
        buildQuery.addJoin(CATEGORIES_TABLE, BOOK_CATEGORIES_CATEGORY_ID_COLUMN, CATEGORIES_ID_COLUMN)
    }

    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.sortCategory != other.sortCategory
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return book.sortCategory?: ""
    }
}

/** Source of book details */
private val source = object: DataDescriptor(arrayOf(SOURCE_ID_COLUMN), R.string.source) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.sourceId != other.book.sourceId
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return book.book.sourceId?: ""
    }
}

/** Id of book details in the source */
private val sourceId = object: DataDescriptor(arrayOf(VOLUME_ID_COLUMN), R.string.volume) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.volumeId != other.book.volumeId
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return book.book.volumeId?: ""
    }
}

/** ISBN */
private val isbn = object: DataDescriptor(arrayOf(ISBN_COLUMN), R.string.isbn) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.ISBN != other.book.ISBN
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return book.book.ISBN?: ""
    }
}

/** Page Count */
private val pageCount = object: DataDescriptor(arrayOf(PAGE_COUNT_COLUMN), R.string.pages) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return book.book.pageCount.toString()
    }
}

/** Number of books tracked */
private val bookCount = object: DataDescriptor(arrayOf(BOOK_COUNT_COLUMN), R.string.books) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.bookCount != other.book.bookCount
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return book.book.bookCount.toString()
    }
}
/** Rating */
private val rating = object: DataDescriptor(arrayOf(RATING_COLUMN), R.string.rating) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return floor(book.book.rating) != floor(other.book.rating)
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return floor(book.book.rating).toString()
    }
}
/** Date book was added to the database */
private val dateAdded = object: DataDescriptor(arrayOf(DATE_ADDED_COLUMN), R.string.date_added) {
    /**
     * @inheritDoc
     * Add separators at day boundaries
     */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        cal1.time = book.book.added
        cal2.time = other.book.added
        return cal1.get(Calendar.DAY_OF_MONTH) != cal2.get(Calendar.DAY_OF_MONTH) ||
                cal1.get(Calendar.MONTH) != cal2.get(Calendar.MONTH) ||
                cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR)
    }

    /**
     * @inheritDoc
     * Separator value is the day
     */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return SimpleDateFormat("MM/dd/yy", locale).format(book.book.added)
    }
}
/** Date book was last modified in the database */
private val dateModified = object: DataDescriptor(arrayOf(DATE_MODIFIED_COLUMN), R.string.date_changed) {
    /**
     * @inheritDoc
     * Add separators at day boundaries
     */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        cal1.time = book.book.modified
        cal2.time = other.book.modified
        return cal1.get(Calendar.DAY_OF_MONTH) != cal2.get(Calendar.DAY_OF_MONTH) ||
                cal1.get(Calendar.MONTH) != cal2.get(Calendar.MONTH) ||
                cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR)
    }

    /**
     * @inheritDoc
     * Separator value is the day
     */
    override fun getValue(book: BookAndAuthors, locale: Locale): String {
        return SimpleDateFormat("MM/dd/yy", locale).format(book.book.modified)
    }
}
