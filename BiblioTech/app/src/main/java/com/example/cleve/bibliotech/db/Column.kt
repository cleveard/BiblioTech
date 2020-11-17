package com.example.cleve.bibliotech.db

import android.content.Context
import android.database.Cursor
import com.example.cleve.bibliotech.R
import java.lang.UnsupportedOperationException
import java.text.DateFormat
import java.text.ParseException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.floor

// Temp variables used when calculating separates for dates
private val cal1: Calendar = Calendar.getInstance()
private val cal2: Calendar = Calendar.getInstance()

/**
 * Enum of columns
 * The enums not only identify the column, but also contain information about how to use it
 */
@Suppress("unused")
enum class Column(val desc: ColumnDataDescriptor) {
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
abstract class BuildQuery(
    /**
     * Context to use for localization
     */
    val context: Context
) {
    /**
     * Add a name to the columns selected by the query
     * @param name The name to add
     * Can be called multiple time with the same name. The name is only added once
     */
    abstract fun addSelect(name: String)

    /**
     * Add a left join to the query
     * @param table The name of the table to be joined
     * @param leftColumn The column in query used for the join
     * @param joinColumn The column in table used for the join
     * Can be call multiple times with the same table name. The join is only added once,
     * even if leftColumn and joinColumn are different. Only the first call is used.
     */
    abstract fun addJoin(table: String, leftColumn: String, joinColumn: String)

    /**
     * Add a column to order the query
     * @param name The name of the column
     * @param direction The SQL direction - ASC or DESC
     * This can be called multiple times with the same name. The order is only added once.
     * Only the first call is used.
     */
    abstract fun addOrderColumn(name: String, direction: String)

    /**
     * List where query arguments are kept
     */
    val argList: ArrayList<Any> = ArrayList()

    /**
     * Start adding filter expressions for a filter field
     */
    abstract fun beginFilterField()

    /**
     * Add a filter expression
     */
    abstract fun addFilterExpression(expression: String)

    /**
     * End adding filter expressions for a filter field
     */
    abstract fun endFilterField()
}

/**
 * Abstract class used to describe the data in filter columns
 * @param columnNames The database column names used by the filter columns
 * @param nameResourceId The resource id for the localized name of the filter column
 */
abstract class ColumnDataDescriptor(
    val columnNames: Array<String>,
    val nameResourceId: Int,
    val predicates: Array<Predicate>
) {
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
        addJoin(buildQuery)
        for (f in columnNames) {
            buildQuery.addSelect(f)
            buildQuery.addOrderColumn(f, direction.dir)
        }
    }

    /**
     * Add columns to select
     * @param buildQuery The query we are building
     */
    open fun addSelection(buildQuery: BuildQuery) {
        // Default to adding columns in order
        for (f in columnNames)
            buildQuery.addSelect(f)
    }

    /**
     * Add an expression using the column
     * Default to do nothing, so we can add filters piecemeal
     */
    fun addExpression(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ) {
        // Only process the field if the predicate is allowed
        // for the column and there are filter values
        if (predicates.indexOf(predicate) >= 0) {
            doAddExpression(buildQuery, predicate, values)
        }
    }

    /**
     * Add an expression for a predicate with values
     * @param buildQuery The query we are building
     * @param predicate The predicate we want to use
     * @param values The values we want to use
     * This is called by addExpression after checking that there are values
     * and that the predicate is valid for this column
     */
    protected open fun doAddExpression(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ) {
        // Loop through the values and add the expression for each one
        for (v in values) {
            // If we can convert the value add the expression
            if (convert(buildQuery, predicate, v)) {
                addSelection(buildQuery)
                addJoin(buildQuery)
                predicate.desc.buildExpression(buildQuery, columnNames)
            }
        }
    }

    /**
     * Convert arguments for this column for a predicate
     * @param buildQuery The query we are building
     * @param predicate The predicate we want to use
     * @param value The value we want to use
     * @return True if the value converted
     */
    protected open fun convert(
        buildQuery: BuildQuery,
        predicate: Predicate,
        value: String
    ): Boolean {
        // Default is just to escape the string wild cards
        return predicate.desc.convertString(buildQuery, value)
    }

    /**
     * Get the column value from a book
     * @param book The book holding the value
     * @param context The context to use for displaying the value
     */
    abstract fun getValue(book: BookAndAuthors, context: Context): String

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
     * @param context The context to use for displaying the value
     */
    open fun getSeparatorValue(book: BookAndAuthors, context: Context): String {
        return getValue(book, context)
    }

    /**
     * Flag to indicate a column supports auto complete
     * Override to support auto complete
     */
    open fun hasAutoComplete(): Boolean {
        return false
    }

    /**
     * Build the auto complete adapter for the column
     * @param constraint The string to filter by
     */
    open fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        throw UnsupportedOperationException("Column does not support auto-complete")
    }

    companion object {
        /** format for dates */
        private var format: DateFormat? = null
        /** locale of the date format */
        private var formatLocale: Locale? = null

        /**
         * Format a date
         * @param date The date to format
         * @param locale The locale to use for the format
         * @return The formatted date
         */
        fun formatDate(date: Date, locale: Locale): String {
            if (format == null || locale != formatLocale) {
                format = DateFormat.getDateInstance(DateFormat.SHORT, locale)
                formatLocale = locale
            }
            return format!!.format(date)
        }

        /**
         * Build auto complete cursor
         */
        fun buildAutoCompleteCursor(
            repo: BookRepository,
            idName: String,
            resultName: String,
            tableName: String,
            constraint: String?,
            group: Boolean = false
        ): Cursor {
            val hasConstraint = constraint != null && constraint.isNotEmpty()
            val query = "SELECT $idName as _id, $resultName AS _result FROM $tableName " +
                (if (hasConstraint) "WHERE _result LIKE ? " else "") +
                (if (group) "GROUP BY _result " else "") +
                "ORDER BY _result"
            return repo.getCursor(query,
                if (hasConstraint)
                    arrayOf("%${PredicateDataDescription.escapeLikeWildCards(constraint!!)}%")
                else
                    null
            )
        }
    }
}

/** Author - Last, First */
private val lastFirst = object: ColumnDataDescriptor(
    arrayOf(LAST_NAME_COLUMN, REMAINING_COLUMN),
    R.string.author,
    emptyArray()        // Both author columns filter the same. Only use one
) {
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
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return "${book.sortLast?: ""}, ${book.sortFirst?: ""}"
    }
}

/** Author - First Last */
private val firstLast = object: ColumnDataDescriptor(
    arrayOf(REMAINING_COLUMN, LAST_NAME_COLUMN),
    R.string.author,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF)
) {
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
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return "${book.sortFirst?: ""} ${book.sortLast?: ""}"
    }

    /** @inheritDoc */
    override fun doAddExpression(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ) {
        // Loop through the values and add the expression for each one
        for (v in values) {
            // If we can convert the value add the expression
            if (convert(buildQuery, predicate, v)) {
                addJoin(buildQuery)
                buildQuery.addSelect("$REMAINING_COLUMN || ' ' || $LAST_NAME_COLUMN AS _author")
                predicate.desc.buildExpression(buildQuery, arrayOf("_author"))
            }
        }

        // We need to add a join for this filter
        addJoin(buildQuery)
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, AUTHORS_ID_COLUMN,
            "$REMAINING_COLUMN || ' ' || $LAST_NAME_COLUMN", AUTHORS_TABLE, constraint)
    }
}

/** Enum meaning filter everywhere */
private val anyColumn = object: ColumnDataDescriptor(
    arrayOf(),
    R.string.any,
    arrayOf(Predicate.GLOB)
) {
    // Set of columns to exclude from the Any column
    private val excludeFilterColumn = arrayOf<Column>()

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
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return ""
    }

    /** @inheritDoc */
    override fun doAddExpression(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ) {
        // Add an expression for every other column unless excluded
        for (c in Column.values()) {
            if (c.desc != this && excludeFilterColumn.indexOf(c) == -1)
                c.desc.addExpression(buildQuery, predicate, values)
        }
    }
}

/** Title */
private val title = object: ColumnDataDescriptor(
    arrayOf(TITLE_COLUMN),
    R.string.title,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return book.book.title
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, BOOK_ID_COLUMN,
            TITLE_COLUMN, BOOK_TABLE, constraint, true)
    }
}

/** Subtitle */
private val subtitle = object: ColumnDataDescriptor(
    arrayOf(SUBTITLE_COLUMN),
    R.string.subtitle,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return book.book.subTitle
    }
}

/**
 * Description
 * Not allowed as separator
 */
private val description = object: ColumnDataDescriptor(
    arrayOf(DESCRIPTION_COLUMN),
    R.string.description,
    arrayOf(Predicate.GLOB)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return ""
    }
}

/** Tags */
private val tags = object: ColumnDataDescriptor(
    arrayOf(TAGS_NAME_COLUMN),
    R.string.tag,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF)
) {
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
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return book.sortTag?: ""
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, TAGS_ID_COLUMN,
            TAGS_NAME_COLUMN, TAGS_TABLE, constraint)
    }
}

/** Categories */
private val categories = object: ColumnDataDescriptor(
    arrayOf(CATEGORY_COLUMN),
    R.string.category,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF)
) {
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
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return book.sortCategory?: ""
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, CATEGORIES_ID_COLUMN,
            CATEGORY_COLUMN, CATEGORIES_TABLE, constraint)
    }
}

/** Source of book details */
private val source = object: ColumnDataDescriptor(
    arrayOf(SOURCE_ID_COLUMN),
    R.string.source,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.sourceId != other.book.sourceId
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return book.book.sourceId?: ""
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, BOOK_ID_COLUMN,
            SOURCE_ID_COLUMN, BOOK_TABLE, constraint, true)
    }
}

/** Id of book details in the source */
private val sourceId = object: ColumnDataDescriptor(
    arrayOf(VOLUME_ID_COLUMN),
    R.string.volume,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.volumeId != other.book.volumeId
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return book.book.volumeId?: ""
    }
}

/** ISBN */
private val isbn = object: ColumnDataDescriptor(
    arrayOf(ISBN_COLUMN),
    R.string.isbn,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.ISBN != other.book.ISBN
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return book.book.ISBN?: ""
    }
}

/** Page Count */
private val pageCount = object: ColumnDataDescriptor(
    arrayOf(PAGE_COUNT_COLUMN),
    R.string.pages,
    arrayOf(Predicate.ONE_OF, Predicate.GT, Predicate.GE, Predicate.LT, Predicate.LE, Predicate.GLOB)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return book.book.pageCount.toString()
    }

    /** @inheritDoc */
    override fun convert(
        buildQuery: BuildQuery,
        predicate: Predicate,
        value: String
    ): Boolean {
        // Convert to ints for
        return predicate.desc.convertInt(buildQuery, value)
    }
}

/** Number of books tracked */
private val bookCount = object: ColumnDataDescriptor(
    arrayOf(BOOK_COUNT_COLUMN),
    R.string.books,
    arrayOf(Predicate.ONE_OF, Predicate.GT, Predicate.GE, Predicate.LT, Predicate.LE, Predicate.GLOB)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.bookCount != other.book.bookCount
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return book.book.bookCount.toString()
    }

    /** @inheritDoc */
    override fun convert(
        buildQuery: BuildQuery,
        predicate: Predicate,
        value: String
    ): Boolean {
        // Convert to ints for
        return predicate.desc.convertInt(buildQuery, value)
    }
}

/** Rating */
private val rating = object: ColumnDataDescriptor(
    arrayOf(RATING_COLUMN),
    R.string.rating,
    arrayOf(Predicate.ONE_OF, Predicate.GT, Predicate.GE, Predicate.LT, Predicate.LE)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return floor(book.book.rating) != floor(other.book.rating)
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return floor(book.book.rating).toString()
    }

    /** @inheritDoc */
    override fun convert(
        buildQuery: BuildQuery,
        predicate: Predicate,
        value: String
    ): Boolean {
        // Convert to doubles for
        return predicate.desc.convertDouble(buildQuery, value)
    }
}

/** Date book was added to the database */
private val dateAdded = object: ColumnDataDescriptor(
    arrayOf(DATE_ADDED_COLUMN),
    R.string.date_added,
    arrayOf(Predicate.ONE_OF, Predicate.GT, Predicate.GE, Predicate.LT, Predicate.LE)
) {
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
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return formatDate(book.book.added, context.resources.configuration.locales[0])
    }

    /**
     * @inheritDoc
     */
    override fun doAddExpression(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ) {
        // Loop through the values and add the expression for each one
        for (v in values) {
            // If we can convert the value add the expression
            if (predicate.desc.convertDate(buildQuery, v)) {
                addSelection(buildQuery)
                addJoin(buildQuery)
                predicate.desc.buildExpressionDate(buildQuery, columnNames)
            }
        }
    }
}

/** Date book was last modified in the database */
private val dateModified = object: ColumnDataDescriptor(
    arrayOf(DATE_MODIFIED_COLUMN),
    R.string.date_changed,
    arrayOf(Predicate.ONE_OF, Predicate.GT, Predicate.GE, Predicate.LT, Predicate.LE)
) {
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
    override fun getValue(book: BookAndAuthors, context: Context): String {
        return formatDate(book.book.modified, context.resources.configuration.locales[0])
    }

    /**
     * @inheritDoc
     */
    override fun doAddExpression(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ) {
        // Loop through the values and add the expression for each one
        for (v in values) {
            // If we can convert the value add the expression
            if (predicate.desc.convertDate(buildQuery, v)) {
                addSelection(buildQuery)
                addJoin(buildQuery)
                predicate.desc.buildExpressionDate(buildQuery, columnNames)
            }
        }
    }
}
