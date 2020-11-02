package com.example.cleve.bibliotech.db

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.cleve.bibliotech.R
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

interface BuildQuery {
    fun addSelect(name: String)
    fun addJoin(table: String, leftColumn: String, joinColumn: String)
    fun addOrderColumn(name: String, direction: String)
}

class MapIterator<T, R>(private val src: Iterator<T>, private val map: (T) -> R) : Iterator<R> {
    override fun hasNext(): Boolean = src.hasNext()
    override fun next(): R = map(src.next())
}

class BookFilter(val orderList: Array<OrderField>, val filterList: Array<FilterField>) {

    @Suppress("unused")
    enum class Column {
        LAST_NAME {
            override val joinTable: String? = AUTHORS_TABLE
            override fun addJoin(buildQuery: BuildQuery) {
                buildQuery.addJoin(BOOK_AUTHORS_TABLE, BOOK_ID_COLUMN, BOOK_AUTHORS_BOOK_ID_COLUMN)
                buildQuery.addJoin(AUTHORS_TABLE, BOOK_AUTHORS_AUTHOR_ID_COLUMN, AUTHORS_ID_COLUMN)
            }

            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(LAST_NAME_COLUMN, direction.dir)
                buildQuery.addOrderColumn(REMAINING_COLUMN, direction.dir)
            }

            override fun addSelection(buildQuery: BuildQuery) {
            }
            override val nameResourceId: Int
                get() = R.string.author
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return "${book.sortLast?: ""}, ${book.sortFirst?: ""}"
            }
        },
        FIRST_NAME {
            override val joinTable: String? = AUTHORS_TABLE
            override fun addJoin(buildQuery: BuildQuery) {
                buildQuery.addJoin(BOOK_AUTHORS_TABLE, BOOK_ID_COLUMN, BOOK_AUTHORS_BOOK_ID_COLUMN)
                buildQuery.addJoin(AUTHORS_TABLE, BOOK_AUTHORS_AUTHOR_ID_COLUMN, AUTHORS_ID_COLUMN)
            }

            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(REMAINING_COLUMN, direction.dir)
                buildQuery.addOrderColumn(LAST_NAME_COLUMN, direction.dir)
            }

            override fun addSelection(buildQuery: BuildQuery) {
            }
            override val nameResourceId: Int
                get() = R.string.author
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return "${book.sortFirst?: ""}, ${book.sortLast?: ""}"
            }
        },
        ANY {
            override fun addJoin(buildQuery: BuildQuery) {
            }

            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
            }

            override fun addSelection(buildQuery: BuildQuery) {
            }
            override val nameResourceId: Int
                get() = 0
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return ""
            }
        },
        TITLE {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(TITLE_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = R.string.title
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return book.book.title
            }
        },
        SUBTITLE {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(SUBTITLE_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = R.string.subtitle
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return book.book.subTitle
            }
        },
        DESCRIPTION {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(DESCRIPTION_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = 0
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return ""
            }
        },
        TAGS {
            override val joinTable: String? = TAGS_TABLE
            override fun addJoin(buildQuery: BuildQuery) {
                buildQuery.addJoin(BOOK_TAGS_TABLE, BOOK_ID_COLUMN, BOOK_TAGS_BOOK_ID_COLUMN)
                buildQuery.addJoin(TAGS_TABLE, BOOK_TAGS_TAG_ID_COLUMN, TAGS_ID_COLUMN)
            }

            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(TAGS_NAME_COLUMN, direction.dir)
            }

            override fun addSelection(buildQuery: BuildQuery) {
            }
            override val nameResourceId: Int
                get() = R.string.tag
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return book.sortTag?: ""
            }
        },
        CATEGORIES {
            override val joinTable: String? = CATEGORIES_TABLE
            override fun addJoin(buildQuery: BuildQuery) {
                buildQuery.addJoin(BOOK_CATEGORIES_TABLE, BOOK_ID_COLUMN, BOOK_CATEGORIES_BOOK_ID_COLUMN)
                buildQuery.addJoin(CATEGORIES_TABLE, BOOK_CATEGORIES_CATEGORY_ID_COLUMN, CATEGORIES_ID_COLUMN)
            }

            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(CATEGORY_COLUMN, direction.dir)
            }

            override fun addSelection(buildQuery: BuildQuery) {
            }
            override val nameResourceId: Int
                get() = R.string.category
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return book.sortCategory?: ""
            }
        },
        SOURCE {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(SOURCE_ID_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = R.string.source
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return book.book.sourceId?: ""
            }
        },
        SOURCE_ID {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(VOLUME_ID_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = R.string.volume
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return book.book.volumeId?: ""
            }
        },
        ISBN {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(ISBN_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = R.string.isbn
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return book.book.ISBN?: ""
            }
        },
        PAGE_COUNT {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(PAGE_COUNT_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = R.string.pages
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return book.book.pageCount.toString()
            }
        },
        BOOK_COUNT {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(BOOK_COUNT_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = R.string.books
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return book.book.bookCount.toString()
            }
        },
        RATING {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(RATING_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = R.string.rating
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return book.book.rating.toString()
            }
        },
        DATE_ADDED {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(DATE_ADDED_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = R.string.date_added
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return SimpleDateFormat("MM/dd/yy", locale).format(book.book.added)
            }
        },
        DATE_MODIFIED {
            override fun addOrder(buildQuery: BuildQuery, direction: Order) {
                buildQuery.addOrderColumn(DATE_MODIFIED_COLUMN, direction.dir)
            }
            override val nameResourceId: Int
                get() = R.string.date_changed
            override fun getValue(book: BookAndAuthors, locale: Locale): String {
                return SimpleDateFormat("MM/dd/yy", locale).format(book.book.modified)
            }
        };

        open val joinTable: String? = null
        open fun addJoin(buildQuery: BuildQuery) {
        }
        abstract fun addOrder(buildQuery: BuildQuery, direction: Order)
        open fun addSelection(buildQuery: BuildQuery) {
        }
        abstract val nameResourceId: Int
        abstract fun getValue(book: BookAndAuthors, locale: Locale): String
    }

    @Suppress("unused")
    enum class Predicate {
        ONE_OF {

        },
        ALL_OF {

        },
        GLOB {

        },
        GT {

        },
        GE {

        },
        LT {

        },
        LE {

        }
    }

    enum class Order {
        Ascending {
            override val dir: String
                get() = kAsc
        },
        Descending {
            override val dir: String
                get() = kDesc
        };

        abstract val dir: String
    }

    data class OrderField(
        val column: Column,
        val order: Order,
        val headers: Boolean
    ) {
        override fun hashCode(): Int {
            var result = column.hashCode()
            result = 31 * result + order.hashCode()
            result = 31 * result + headers.hashCode()
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as OrderField

            if (column != other.column) return false
            if (order != other.order) return false
            if (headers != other.headers) return false
            return true
        }

        fun isSameQuery(other: Any?) : Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as OrderField

            if (column != other.column) return false
            if (order != other.order) return false
            return true
        }
    }

    data class FilterField(
        val column: Column,
        val predicate: Predicate,
        val value: Any
    ) {
        override fun hashCode(): Int {
            var result = column.hashCode()
            result = 31 * result + predicate.hashCode()
            result = 31 * result + value.hashCode()
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FilterField

            if (column != other.column) return false
            if (predicate != other.predicate) return false
            if (value != other.value) return false
            return true
        }

        fun isSameQuery(other: Any?) : Boolean {
            return this == other
        }
    }

    override fun hashCode(): Int {
        return hashArray(orderList)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookFilter

        if (!equalArray(orderList, other.orderList)) return false
        return true
    }

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

    companion object {
        fun buildFilterQuery(filter: BookFilter?): SupportSQLiteQuery {
            val builder = object: BuildQuery {
                val selectSpec: StringBuilder = StringBuilder()
                val joinSpec: StringBuilder = StringBuilder()
                val orderSpec: StringBuilder = StringBuilder()
                val groupSpec: StringBuilder = StringBuilder()
                val filterSpec: StringBuilder = StringBuilder()
                val joins: ArrayList<String> = ArrayList()
                val selects: ArrayList<String> = ArrayList()
                val orders: ArrayList<String> = ArrayList()

                override fun addSelect(name: String) {
                    if (selects.indexOf(name) == -1) {
                        selects.add(name)
                        selectSpec.append("${if (selectSpec.isEmpty()) "" else ", "}$name")
                    }
                }

                override fun addJoin(table: String, leftColumn: String, joinColumn: String) {
                    if (joins.indexOf(table) == -1) {
                        joins.add(table)
                        joinSpec.append("${if (joinSpec.isEmpty()) "" else " "} LEFT JOIN $table ON $leftColumn = $joinColumn")
                    }
                }

                override fun addOrderColumn(name: String, direction: String) {
                    if (orders.indexOf(name) == -1) {
                        orders.add(name)
                        orderSpec.append("${if (orderSpec.isEmpty()) "" else ", "}$name $direction")
                        groupSpec.append("${if (groupSpec.isEmpty()) "" else ", "}$name")
                    }
                }

                fun buildSelect(list: Iterator<Column>) {
                    for (field in list) {
                        field.addSelection(this)
                    }
                }

                fun buildJoin(list: Iterator<Column>) {
                    for (column in ALL_BOOK_COLUMNS)
                        addSelect(column)
                    for (field in list) {
                        field.addJoin(this)
                    }
                }

                fun buildOrder(list: Iterator<OrderField>) {
                    for (field in list) {
                        field.column.addOrder(this, field.order)
                    }
                    groupSpec.append("${if (groupSpec.isEmpty()) "" else ", "}$BOOK_ID_COLUMN")
                }

                fun buildFilter(list: Iterator<FilterField>) {
                }
            }

            if (filter != null) {
                builder.buildSelect(MapIterator(filter.orderList.iterator()) { it.column })
                builder.buildSelect(MapIterator(filter.filterList.iterator()) { it.column })
                builder.buildJoin(MapIterator(filter.orderList.iterator()) { it.column })
                builder.buildJoin(MapIterator(filter.filterList.iterator()) { it.column })
                builder.buildOrder(filter.orderList.iterator())
                builder.buildFilter(filter.filterList.iterator())
            }

            val spec = StringBuilder()
            spec.append("SELECT * FROM $BOOK_TABLE")
            if (builder.joinSpec.isNotEmpty())
                spec.append(" ${builder.joinSpec}")
            if (builder.filterSpec.isNotEmpty())
                spec.append(" WHERE ${builder.filterSpec}")
            if (builder.groupSpec.isNotEmpty())
                spec.append(" GROUP BY ${builder.groupSpec}")
            if (builder.orderSpec.isNotEmpty())
                spec.append(" ORDER BY ${builder.orderSpec}")

            return SimpleSQLiteQuery(spec.toString())
        }

        fun <T> hashArray(array: Array<T>): Int {
            var result = 0
            for (v in array)
                result = result * 31 + v.hashCode()
            return result
        }

        fun <T> equalArray(a1: Array<T>, a2: Array<T>): Boolean {
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
}
