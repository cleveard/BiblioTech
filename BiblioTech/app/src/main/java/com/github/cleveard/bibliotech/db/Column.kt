package com.github.cleveard.bibliotech.db

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.github.cleveard.bibliotech.R
import java.lang.StringBuilder
import java.text.DateFormat
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
    /** ISBNs */
    ISBN(isbns),
    /** Page Count */
    PAGE_COUNT(pageCount),
    /** Number of books tracked */
    BOOK_COUNT(bookCount),
    /** Rating */
    RATING(rating),
    /** Date book was added to the database */
    DATE_ADDED(dateAdded),
    /** Date book was last modified in the database */
    DATE_MODIFIED(dateModified),
    /** The series title */
    SERIES(series);
}

/** An interface used to add components to a query */
abstract class BuildQuery(
    /** Context to use for localization */
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
     * Wrap the current filter expression
     * @param pre String to insert before the expression
     * @param post String to insert after the expression
     */
    abstract fun wrapFilterExpression(pre: String, post: String)

    /**
     * End adding filter expressions for a filter field
     */
    abstract fun endFilterField()

    /**
     * Make an SQLite builder for a sub-query
     */
    abstract fun newSQLiteBuilder(table: BookDatabase.TableDescription): SQLiteQueryBuilder
}

/** An interface used to build an SQLite command */
abstract class SQLiteQueryBuilder(context: Context): BuildQuery(context) {
    /**
     * Collect order by columns from iterator of columns in fields
     * @param list List of columns in array of fields
     */
    fun buildOrder(list: Iterator<OrderField>) {
        for (field in list) {
            field.column.desc.addOrder(this, field.order)
        }
    }

    /**
     * Collect the filter expressions from iterator of FilterFields
     * @param list The list FilterFields to process
     */
    fun buildFilter(list: Iterator<FilterField>) {
        for (f in list) {
            // Start collecting expressions
            beginFilterField()
            // Add the expression for the field
            f.column.desc.addExpression(this, f.predicate, f.values)
            // Finish up
            endFilterField()
        }
    }

    /**
     * Create the SQLiteQuery
     */
    fun createQuery(table: String = BOOK_TABLE): SupportSQLiteQuery {
        return SimpleSQLiteQuery(createCommand(table), argList.toArray())

    }

    /**
     * Create the SQLite command
     */
    abstract fun createCommand(table: String = BOOK_TABLE): String
}

/**
 * Abstract class used to describe the data in filter columns
 * @param columnNames The database column names used by the filter columns
 * @param nameResourceId The resource id for the localized name of the filter column
 * @param predicates The predicates allowed with the column
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
        for (f in columnNames) {
            buildQuery.addOrderColumn(f, direction.dir)
        }
    }

    /**
     * Add an expression using the column
     * @param buildQuery The query we are building
     * @param predicate The predicate we want to use
     * @param values The values we want to use
     * @return True if an expression was created. False otherwise
     * Default to do nothing, so we can add filters piecemeal
     */
    fun addExpression(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ): Boolean {
        // Only process the field if the predicate is allowed
        // for the column and there are filter values
        return predicates.indexOf(predicate) >= 0 &&
            doAddExpression(buildQuery, predicate, values)
    }

    /**
     * Add an expression for a predicate with values
     * @param buildQuery The query we are building
     * @param predicate The predicate we want to use
     * @param values The values we want to use
     * @return True if an expression was created. False otherwise
     * This is called by addExpression after checking that there are values
     * and that the predicate is valid for this column
     */
    protected open fun doAddExpression(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ): Boolean {
        val hasValues = processValues(buildQuery, predicate, values)
        if (hasValues && predicate.desc.negate)
            buildQuery.wrapFilterExpression("NOT ( ", " )")
        return hasValues
    }

    /**
     * Process the values for the current expression
     * @param buildQuery The query we are building
     * @param predicate The predicate we want to use
     * @param values The values we want to use
     * @return True if any values were processed, false otherwise
     */
    protected open fun processValues(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ): Boolean {
        var hasValues = false
        // Loop through the values and add the expression for each one
        for (v in values) {
            // If we can convert the value add the expression
            if (convert(buildQuery, predicate, v)) {
                hasValues = true
                predicate.desc.buildExpression(buildQuery, columnNames)
            }
        }
        return hasValues
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
     */
    abstract fun getValue(book: BookAndAuthors): String

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
     */
    open fun getSeparatorValue(book: BookAndAuthors): String {
        return getValue(book)
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

    open fun getDisplayValue(book: BookAndAuthors): String {
        return getValue(book)
    }

    companion object {
        /** format for dates */
        private var format: DateFormat? = null
        /** locale of the date format */
        private var formatLocale: Locale? = null

        /**
         * Set the local to use for dates
         * @param locale The locale to use for the format
         */
        fun setDateLocale(locale: Locale) {
            if (format == null || locale != formatLocale) {
                format = DateFormat.getDateInstance(DateFormat.SHORT, locale)
                formatLocale = locale
            }
        }

        /**
         * Format a date
         * @param date The date to format
         * @return The formatted date
         */
        fun formatDate(date: Date): String {
            return format!!.format(date)
        }

        /**
         * Build auto complete cursor
         */
        fun buildAutoCompleteCursor(
            repo: BookRepository,
            table: BookDatabase.TableDescription,
            resultName: String,
            constraint: String?,
            group: Boolean = false
        ): Cursor {
            val hasConstraint = !constraint.isNullOrEmpty()
            val where = if (!table.flagColumn.isNullOrEmpty() || hasConstraint) " WHERE" else ""
            val flag = if (!table.flagColumn.isNullOrEmpty()) " ( ( ${table.flagColumn} ) & ${table.flagValue} ) = 0${if (hasConstraint) " AND" else ""}" else ""
            val filter = if (hasConstraint) " _result LIKE ? ESCAPE '\\'" else ""
            val groupBy = if (group) " GROUP BY _result" else ""
            val query = "SELECT ${table.idColumn} as _id, $resultName AS _result FROM ${table.name}$where$flag$filter$groupBy ORDER BY _result"
            return repo.getCursor(query,
                if (hasConstraint)
                    arrayOf("%${PredicateDataDescription.escapeLikeWildCards(constraint!!)}%")
                else
                    null
            )
        }
    }
}

/**
 * Abstract class used to describe the data in filter columns that require sub-queries
 * @param columnNames The database column names used by the filter columns
 * @param nameResourceId The resource id for the localized name of the filter column
 * @param predicates The predicates allowed with the column
 * @param selectColumn The column from the selected table used in the sub-query
 * @param queryTable The table the filter values are in
 * @param joinTable The table that connects the select table and the filter values
 */
abstract class SubQueryColumnDataDescriptor(
    columnNames: Array<String>,
    nameResourceId: Int,
   predicates: Array<Predicate>,
    private val selectColumn: String,
    private val queryTable: BookDatabase.TableDescription,
    private val joinTable: BookDatabase.TableDescription?,
) : ColumnDataDescriptor(columnNames, nameResourceId, predicates) {
    /**
     * Add columns to order list
     * @param buildQuery The query we are building
     * @param direction The direction the columns are orders
     */
    override fun addOrder(buildQuery: BuildQuery, direction: Order) {
        // Default to adding columns in order
        addJoin(buildQuery)
        for (f in columnNames) {
            buildQuery.addSelect(f)
            buildQuery.addOrderColumn(f, direction.dir)
        }
    }

    /** @inheritDoc */
    override fun doAddExpression(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ): Boolean {
        // Get the sub query builder
        val subQuery = buildQuery.newSQLiteBuilder(queryTable)
        // Select the id column
        subQuery.addSelect(queryTable.idColumn)
        // Start the expression
        subQuery.beginFilterField()
        // Process the values
        val hasExpr = processValues(subQuery, predicate, values)
        subQuery.endFilterField()

        if (hasExpr) {
            val subExpr = subQuery.createCommand(queryTable.name)
            val expr = if (joinTable != null) {
                "${if (predicate.desc.negate) "NOT " else ""}EXISTS ( SELECT NULL FROM ${joinTable.name} WHERE $selectColumn = ${joinTable.bookIdColumn} AND ${joinTable.linkIdColumn} IN ( $subExpr ) )"
            } else {
                "${if (predicate.desc.negate) "NOT " else ""}EXISTS ( SELECT NULL FROM ${queryTable.name} WHERE $selectColumn IN ( $subExpr ) )"
            }
            buildQuery.addFilterExpression(expr)
            buildQuery.argList.addAll(subQuery.argList)
        }
        return hasExpr
    }
}

/** Author - Last, First */
private val lastFirst = object: SubQueryColumnDataDescriptor(
    arrayOf(LAST_NAME_COLUMN, REMAINING_COLUMN),
    R.string.author,
    emptyArray(),        // Both author columns filter the same. Only use one
    BOOK_ID_COLUMN,
    BookDatabase.authorsTable,
    BookDatabase.bookAuthorsTable
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
    override fun getValue(book: BookAndAuthors): String {
        return "${book.sortLast?: ""}, ${book.sortFirst?: ""}"
    }

    /** @inheritDoc */
    override fun getDisplayValue(book: BookAndAuthors): String {
        return book.authors.fold(StringBuilder()) {acc, v ->
            if (acc.isNotEmpty())
                acc.append(", ")
            acc.append("${v.lastName}, ${v.remainingName}")
        }.toString()
    }
}

/** Author - First Last */
private val firstLast = object: SubQueryColumnDataDescriptor(
    arrayOf(REMAINING_COLUMN, LAST_NAME_COLUMN),
    R.string.author,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.NOT_GLOB),
    BOOK_ID_COLUMN,
    BookDatabase.authorsTable,
    BookDatabase.bookAuthorsTable
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
    override fun getValue(book: BookAndAuthors): String {
        return "${book.sortFirst?: ""} ${book.sortLast?: ""}"
    }

    /** @inheritDoc */
    override fun getDisplayValue(book: BookAndAuthors): String {
        return book.authors.fold(StringBuilder()) { acc, v ->
            if (acc.isNotEmpty())
                acc.append(", ")
            acc.append("${v.remainingName} ${v.lastName}")
        }.toString()
    }

    /** @inheritDoc */
    override fun processValues(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ): Boolean {
        var hasValues = false
        // Loop through the values and add the expression for each one
        for (v in values) {
            // If we can convert the value add the expression
            if (convert(buildQuery, predicate, v)) {
                hasValues = true
                predicate.desc.buildExpression(buildQuery, arrayOf("( $REMAINING_COLUMN || ' ' || $LAST_NAME_COLUMN )"))
            }
        }
        return hasValues
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, BookDatabase.authorsTable,
            "$REMAINING_COLUMN || ' ' || $LAST_NAME_COLUMN", constraint)
    }
}

/** Enum meaning filter everywhere */
private val anyColumn = object: ColumnDataDescriptor(
    arrayOf(),
    R.string.any,
    arrayOf(Predicate.GLOB, Predicate.NOT_GLOB)
) {
    // Set of columns to exclude from the Any column
    private val excludeFilterColumn = arrayOf<String>(
        // All of the columns that ANY will add. You can uncomment
        // these if you are trying to narrow a test error
        // Make sure you uncomment the same ones in the
        // ANY ColumnValue in the BookFilter test class
        // "FIRST_NAME",
        // "TAGS",
        // "CATEGORIES",
        // "TITLE",
        // "SUBTITLE",
        // "DESCRIPTION",
        // "SOURCE",
        // "SOURCE_ID",
        // "ISBN",
        // "PAGE_COUNT",
        // "BOOK_COUNT",
    )

    /** @inheritDoc */
    override fun addJoin(buildQuery: BuildQuery) {
    }

    /** @inheritDoc */
    override fun addOrder(buildQuery: BuildQuery, direction: Order) {
    }

    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
        return ""
    }

    /** @inheritDoc */
    override fun processValues(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ): Boolean {
        var hasValues = false
        // Convert NOT_GlOB and NOT_ON_OF because final NOT is handled later
        val positivePredicate = when (predicate) {
            Predicate.NOT_GLOB -> Predicate.GLOB
            Predicate.NOT_ONE_OF -> Predicate.ONE_OF
            else -> predicate
        }
        // Add an expression for every other column unless excluded
        for (c in Column.values()) {
            if (c.desc != this && excludeFilterColumn.indexOf(c.name) == -1)
                hasValues = c.desc.addExpression(buildQuery, positivePredicate, values) || hasValues
        }
        return hasValues
    }
}

/** Title */
private val title = object: ColumnDataDescriptor(
    arrayOf(TITLE_COLUMN),
    R.string.title,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.NOT_GLOB)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
        return book.book.title
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, BookDatabase.bookTable,
            TITLE_COLUMN, constraint, true)
    }
}

/** Subtitle */
private val subtitle = object: ColumnDataDescriptor(
    arrayOf(SUBTITLE_COLUMN),
    R.string.subtitle,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.NOT_GLOB)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
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
    arrayOf(Predicate.GLOB, Predicate.NOT_GLOB)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
        return ""
    }

    /** @inheritDoc */
    override fun getDisplayValue(book: BookAndAuthors): String {
        return book.book.description
    }
}

/** Tags */
private val tags = object: SubQueryColumnDataDescriptor(
    arrayOf(TAGS_NAME_COLUMN),
    R.string.tag,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.NOT_GLOB),
    BOOK_ID_COLUMN,
    BookDatabase.tagsTable,
    BookDatabase.bookTagsTable
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
    override fun getValue(book: BookAndAuthors): String {
        return book.sortTag?: ""
    }

    /** @inheritDoc */
    override fun getDisplayValue(book: BookAndAuthors): String {
        return book.tags.fold(StringBuilder()) { acc, v ->
            if (acc.isNotEmpty())
                acc.append(", ")
            acc.append(v.name)
        }.toString()
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, BookDatabase.tagsTable,
            TAGS_NAME_COLUMN, constraint)
    }
}

/** Categories */
private val categories = object: SubQueryColumnDataDescriptor(
    arrayOf(CATEGORY_COLUMN),
    R.string.category,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.NOT_GLOB),
    BOOK_ID_COLUMN,
    BookDatabase.categoriesTable,
    BookDatabase.bookCategoriesTable
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
    override fun getValue(book: BookAndAuthors): String {
        return book.sortCategory?: ""
    }

    /** @inheritDoc */
    override fun getDisplayValue(book: BookAndAuthors): String {
        return book.categories.fold(StringBuilder()) { acc, v ->
            if (acc.isNotEmpty())
                acc.append(", ")
            acc.append(v.category)
        }.toString()
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, BookDatabase.categoriesTable,
            CATEGORY_COLUMN, constraint)
    }
}

/** Source of book details */
private val source = object: ColumnDataDescriptor(
    arrayOf("ifnull($SOURCE_ID_COLUMN, '')"),
    R.string.source,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.NOT_GLOB)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.sourceId != other.book.sourceId
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
        return book.book.sourceId?: ""
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, BookDatabase.bookTable,
            SOURCE_ID_COLUMN, constraint, true)
    }
}

/** Id of book details in the source */
private val sourceId = object: ColumnDataDescriptor(
    arrayOf("ifnull($VOLUME_ID_COLUMN, '')"),
    R.string.volume,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.NOT_GLOB)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.volumeId != other.book.volumeId
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
        return book.book.volumeId?: ""
    }
}

/** ISBN */
private val isbns = object: SubQueryColumnDataDescriptor(
    arrayOf(ISBN_COLUMN),
    R.string.isbn,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.NOT_GLOB),
    BOOK_ID_COLUMN,
    BookDatabase.isbnTable,
    BookDatabase.bookIsbnsTable
) {
    /** @inheritDoc */
    override fun addJoin(buildQuery: BuildQuery) {
        buildQuery.addJoin(BOOK_ISBNS_TABLE, BOOK_ID_COLUMN, BOOK_ISBNS_BOOK_ID_COLUMN)
        buildQuery.addJoin(ISBNS_TABLE, BOOK_ISBNS_ISBN_ID_COLUMN, ISBNS_ID_COLUMN)
    }

    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.sortIsbn != other.sortIsbn
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
        return book.sortIsbn?: ""
    }

    /** @inheritDoc */
    override fun getDisplayValue(book: BookAndAuthors): String {
        return book.isbns.fold(StringBuilder()) { acc, v ->
            if (acc.isNotEmpty())
                acc.append(", ")
            acc.append(v.isbn)
        }.toString()
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, BookDatabase.isbnTable,
            ISBN_COLUMN, constraint)
    }
}

/** Page Count */
private val pageCount = object: ColumnDataDescriptor(
    arrayOf(PAGE_COUNT_COLUMN),
    R.string.pages,
    arrayOf(Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.GT, Predicate.GE, Predicate.LT, Predicate.LE, Predicate.GLOB, Predicate.NOT_GLOB)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return false
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
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
    arrayOf(Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.GT, Predicate.GE, Predicate.LT, Predicate.LE, Predicate.GLOB, Predicate.NOT_GLOB)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.book.bookCount != other.book.bookCount
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
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
    arrayOf(Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.GT, Predicate.GE, Predicate.LT, Predicate.LE)
) {
    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return floor(book.book.rating) != floor(other.book.rating)
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
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
    arrayOf(Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.GT, Predicate.GE, Predicate.LT, Predicate.LE)
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
    override fun getValue(book: BookAndAuthors): String {
        return formatDate(book.book.added)
    }

    /**
     * @inheritDoc
     */
    override fun processValues(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ): Boolean {
        var hasValues = false
        // Loop through the values and add the expression for each one
        for (v in values) {
            // If we can convert the value add the expression
            if (predicate.desc.convertDate(buildQuery, v)) {
                hasValues = true
                predicate.desc.buildExpressionDate(buildQuery, columnNames)
            }
        }
        return hasValues
    }
}

/** Date book was last modified in the database */
private val dateModified = object: ColumnDataDescriptor(
    arrayOf(DATE_MODIFIED_COLUMN),
    R.string.date_changed,
    arrayOf(Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.GT, Predicate.GE, Predicate.LT, Predicate.LE)
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
    override fun getValue(book: BookAndAuthors): String {
        return formatDate(book.book.modified)
    }

    /**
     * @inheritDoc
     */
    override fun processValues(
        buildQuery: BuildQuery,
        predicate: Predicate,
        values: Array<String>
    ): Boolean {
        var hasValues = false
        // Loop through the values and add the expression for each one
        for (v in values) {
            // If we can convert the value add the expression
            if (predicate.desc.convertDate(buildQuery, v)) {
                hasValues = true
                predicate.desc.buildExpressionDate(buildQuery, columnNames)
            }
        }
        return hasValues
    }
}

/** Series */
private val series = object: SubQueryColumnDataDescriptor(
    arrayOf(SERIES_TITLE_COLUMN),
    R.string.series,
    arrayOf(Predicate.GLOB, Predicate.ONE_OF, Predicate.NOT_ONE_OF, Predicate.NOT_GLOB),
    BOOK_SERIES_COLUMN,
    BookDatabase.seriesTable,
    null
) {
    /** @inheritDoc */
    override fun addJoin(buildQuery: BuildQuery) {
        buildQuery.addJoin(SERIES_TABLE, BOOK_SERIES_COLUMN, SERIES_ID_COLUMN)
    }

    /** @inheritDoc */
    override fun shouldAddSeparator(book: BookAndAuthors, other: BookAndAuthors): Boolean {
        return book.sortSeries != other.sortSeries
    }

    /** @inheritDoc */
    override fun getValue(book: BookAndAuthors): String {
        return book.sortSeries?: ""
    }

    /** @inheritDoc */
    override fun getDisplayValue(book: BookAndAuthors): String {
        return book.series?.title?: ""
    }

    /** @inheritDoc */
    override fun hasAutoComplete(): Boolean {
        return true
    }

    /** @inheritDoc */
    override fun getAutoCompleteCursor(repo: BookRepository, constraint: String?): Cursor {
        return buildAutoCompleteCursor(repo, BookDatabase.seriesTable,
            SERIES_TITLE_COLUMN, constraint)
    }
}
