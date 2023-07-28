package com.github.cleveard.bibliotech.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

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
    /** How selection is filtered */
    enum class SelectionType {
        /** Accept both selected and unselected books */
        EITHER,
        /** Accept only selected books */
        SELECTED,
        /** Accept only unselected books */
        UNSELECTED,
        /** Accept no books */
        NONE
    }

    /** How this filter filters selected books */
    val selectionType: SelectionType
        get() {
            // Look at all SELECTED fields in the filter list
            var selected = 0
            filterList.forEach {
                if (it.column == Column.SELECTED) {
                    // Set selected based on the predicate
                    selected = selected or (if (it.predicate == Predicate.ONE_OF) 1 else 2)
                }
            }

            // Return the SelectedType
            return when (selected) {
                0 -> SelectionType.EITHER
                1 -> SelectionType.SELECTED
                2 -> SelectionType.UNSELECTED
                else -> SelectionType.NONE
            }
        }

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
    fun isSameQuery(other: BookFilter?) : Boolean {
        if (this === other) return true
        if (other == null) return false

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

    /**
     * Class representing a build filter
     * @param command The SQLiteCommand
     * @param args The arguments for the command
     */
    data class BuiltFilter(
        val command: String,
        val args: Array<Any>
    ) {
        /** @inheritDoc */
        override fun hashCode(): Int {
            return BookFilter.hashArray(args) * 31 + command.hashCode()
        }

        /** @inheritDoc */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BuiltFilter

            if (command != other.command) return false
            if (!BookFilter.equalArray(args, other.args)) return false
            return true
        }

    }

    companion object: BookFilterCompanion() {
        const val VERSION = 0
    }
}

/**
 * Build a filter that filters for selection based on selection type
 * @param type How selection is to be filter
 */
fun BookFilter?.filterForSelectionType(type: BookFilter.SelectionType): BookFilter? {
    // If the filter is already correct return it
    if (type == (this?.selectionType ?: BookFilter.SelectionType.EITHER))
        return this

    // Remove and selection fields from the filter list
    val baseList = this?.filterList?.filter { it.column != Column.SELECTED }?.toMutableList()?: mutableListOf()
    // Add the field for the requested selection type
    when (type) {
        BookFilter.SelectionType.SELECTED -> baseList.add(FilterField(Column.SELECTED, Predicate.ONE_OF, arrayOf("0")))
        BookFilter.SelectionType.UNSELECTED -> baseList.add(FilterField(Column.SELECTED, Predicate.NOT_ONE_OF, arrayOf("0")))
        else -> {}
    }
    // Return the new filter
    return BookFilter(this?.orderList?: emptyArray(), baseList.toTypedArray())
}

/**
 * Convert a Book filter to SQL and an arguments array
 * @param context The context to use for Date interpretation
 * @param select The columns to select for the filter
 * @param excludeOrder True to exclude the order terms
 */
fun BookFilter?.buildFilter(context: Context, select: Array<String>, excludeOrder: Boolean = false): BookFilter.BuiltFilter? {
    // If we aren't selecting all columns
    if ((select.isEmpty() || (select.size == 1 && select[0] == "*"))
        // and this is null or we are selecting all books with no order, then return null
        && (this == null || (this.filterList.isEmpty() && (excludeOrder || this.orderList.isEmpty())))
    ) {
        return null
    }
    // Build the  SQLite command to get the book ids for the filter
    val idFilterBuilder = BookFilter.newSQLiteQueryBuilder(context, BookDatabase.bookTable)
    if (select.isEmpty()) {
        // Add select all columns
        idFilterBuilder.addSelect("*")
    } else {
        // Only select the columns in select
        for (s in select)
            idFilterBuilder.addSelect(s)
    }

    if (this != null) {
        // Include the order terms
        if (!excludeOrder)
            idFilterBuilder.buildOrder(orderList.iterator())
        // Build the filter
        idFilterBuilder.buildFilter(filterList.iterator())
    }

    // Return the built filter
    return BookFilter.BuiltFilter(
        idFilterBuilder.createCommand(),
        idFilterBuilder.argList.toArray()
    )
}

/**
 * Class with companion methods for BookFilter
 */
open class BookFilterCompanion {
    /**
     * Build an SQLite query from a filter description
     * @param filter The filter description
     * @param context The context used to get the locale for parsing dates
     * @param table The database table the query applies to
     */
    fun buildFilterQuery(filter: BookFilter, context: Context, table: BookDatabase.TableDescription): SupportSQLiteQuery {
        // The object used to build the query
        val builder = SQLiteQueryBuilderImpl(context, table)

        // Add all book columns first
        for (column in ALL_BOOK_COLUMNS)
            builder.addSelect(column)

        // Collect order by columns from orderList
        builder.buildOrder(filter.orderList.iterator())
        // Collect filter by columns from filterList
        builder.buildFilter(filter.filterList.iterator())

        return builder.createQuery()
    }

    /**
     * Create a new SQLiteQueryBuilder
     * @param context The context used to get the locale for parsing dates
     */
    fun newSQLiteQueryBuilder(context: Context, table: BookDatabase.TableDescription): SQLiteQueryBuilder {
        return SQLiteQueryBuilderImpl(context, table)
    }

    /**
     * Convert a BookFilter to a string
     * @param filter The filter to convert
     * @return The string or null if filter is null
     */
    fun encodeToString(filter: BookFilter?): String? {
        // Encode the filter to a JsonElement
        return encodeToJson(filter)?.let {json ->
            // Then create a JsonObject with the version and encoded filter
            // and convert that to a string
            val map = HashMap<String, JsonElement>()
            map[kVersion] = JsonPrimitive(BookFilter.VERSION)
            map[kFilter] = json
            JsonObject(map).toString()
        }
    }

    /**
     * Convert a BookFilter to a JsonElement
     * @param filter The filter to convert
     * @return The JsonElement or null if filter is null
     */
    private fun encodeToJson(filter: BookFilter?): JsonElement? {
        return filter?.let {
            Json.encodeToJsonElement(BookFilter.serializer(), it)
        }
    }

    /**
     * Convert a string to a BookFilter
     * @param string The string to deserialize
     * @return The filter or null if string is null
     */
    fun decodeFromString(string: String?): BookFilter? {
        return string?.let { decodeFromJson(Json.parseToJsonElement(it)) }
    }

    /**
     * Convert a JsonElement to a BookFilter
     * @param inJson The JsonElement to deserialize
     * @return The filter or null if string is null
     */
    private fun decodeFromJson(inJson: JsonElement?): BookFilter? {
        return inJson?.let {json ->
            // Extract the version, return null if it is missing
            val obj = json.jsonObject
            val version = obj[kVersion]?.jsonPrimitive?.int?: return@let null
            // If the version is valid, then decode the filter for that version
            // Otherwise return null
            if (version in deserializers.indices)
                obj[kFilter]?.let { deserializers[version](it) }
            else
                null
        }
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

    companion object {
        private const val kVersion = "VERSION"
        private const val kFilter = "FILTER"

        /**
         * Deserializer versions
         */
        private val deserializers: Array<(json: JsonElement) -> BookFilter> = arrayOf(
            {json: JsonElement ->
                Json.decodeFromJsonElement(BookFilter.serializer(), json)
            }
        )
    }
}

/**
 * Implementation of class to build SQLite queries
 * @param context The context for the locale. Used for interpreting dates
 */
private class SQLiteQueryBuilderImpl(context: Context, table: BookDatabase.TableDescription): SQLiteQueryBuilder(context) {
    // The select columns
    private val selectSpec: StringBuilder = StringBuilder()
    // Joins
    private val joinSpec: StringBuilder = StringBuilder()
    // Order columns
    private val orderSpec: StringBuilder = StringBuilder()
    // Where expression
    private val filterSpec: StringBuilder = StringBuilder(table.getVisibleExpression())
    // Holds expressions for a single field
    private val filterFieldExpression: StringBuilder = StringBuilder()
    // Holds rollback of argList if filterFieldExpression is abandoned
    private var argRollback: Int = 0
    // Track joins to prevent adding multiple times
    private val joins: ArrayList<String> = ArrayList()
    // Track select columns to prevent adding multiple times
    private val selects: ArrayList<String> = ArrayList()
    // Track order columns to prevent adding multiple times
    private val orders: ArrayList<String> = ArrayList()

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
        }
    }

    /** @inheritDoc */
    override fun beginFilterField() {
        // If we call begin without calling end
        // it will clear any expressions for the field
        while (argList.size > argRollback)
            argList.removeLast()
        filterFieldExpression.clear()
    }

    /** @inheritDoc */
    override fun addFilterExpression(expression: String) {
        filterFieldExpression.append(
            "${if (filterFieldExpression.isEmpty()) "" else " OR "} ( $expression )")
    }

    /** @inheritDoc */
    override fun wrapFilterExpression(pre: String, post: String) {
        if (filterFieldExpression.isNotEmpty()) {
            filterFieldExpression.insert(0, pre)
            filterFieldExpression.append(post)
        }
    }

    /** @inheritDoc */
    override fun endFilterField() {
        // Only append expressions if there are any
        if (filterFieldExpression.isNotEmpty()) {
            filterSpec.append(
                "${if (filterSpec.isEmpty()) "" else " AND "} ( $filterFieldExpression )")
            filterFieldExpression.clear()
            argRollback = argList.size
        }
    }

    /** @inheritDoc */
    override fun createCommand(table: String): String {
        // Build the SQLite command
        val spec = StringBuilder()
        spec.append("SELECT $selectSpec FROM $table")
        // Add joins if there are any
        if (joinSpec.isNotEmpty())
            spec.append(" $joinSpec")
        // Add where if we have a filter
        if (filterSpec.isNotEmpty())
            spec.append(" WHERE $filterSpec")
        // Add order by if there is one
        if (orderSpec.isNotEmpty())
            spec.append(" ORDER BY $orderSpec")

        return spec.toString()
    }

    /** @inheritDoc */
    override fun newSQLiteBuilder(table: BookDatabase.TableDescription): SQLiteQueryBuilder {
        return SQLiteQueryBuilderImpl(context, table)
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
    fun isSameQuery(other: OrderField) : Boolean {
        return this == other
    }
}

/**
 * A field used to filter books
 * To make this serializable, the FilterField class is abstract and
 * a generic Value subclass is used to type the values for the filter
 */
@Serializable
class FilterField(
    /** The column being filters */
    val column: Column,
    /** The predicate for the filter */
    val predicate: Predicate,
    /** The values for the filter */
    val values: Array<String>
) {
    /**
     * Indicate whether two fields are the same query
     * @param other The other field
     */
    fun isSameQuery(other: FilterField) : Boolean {
        // Make sure contents are equal
        if (column != other.column) return false
        if (predicate != other.predicate) return false
        return BookFilter.equalArray(values, other.values)
    }

    /** @inheritDoc */
    override fun equals(other: Any?): Boolean {
        // If references are equal, so are the object
        if (this === other) return true
        // If the classes are different, objects are different
        if (this.javaClass != other?.javaClass) return false

        return isSameQuery(other as FilterField)
    }

    /** @inheritDoc */
    override fun hashCode(): Int {
        var hash = column.hashCode()
        hash = hash * 31 + predicate.hashCode()
        hash = hash * 31 + BookFilter.hashArray(values)
        return hash
    }
}

/** Order direction enum */
enum class Order(val dir: String) {
    /** Ascending direction */
    Ascending(kAsc),
    /** Descending direction */
    Descending(kDesc);
}
