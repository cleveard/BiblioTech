package com.github.cleveard.bibliotech.db

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.*
import java.io.*
import java.lang.StringBuilder
import kotlin.collections.ArrayList

// Database column and table names.

// These are the database tables:
//      Book table - The basic data for a book
//      Authors table - The names of authors
//      Book Authors table - A link table that links Authors table rows to Book table rows
//      Categories table - Category names
//      Book Categories table - A link table that links Categories table rows to Book table rows
//      Tags table - Tag names and descriptions
//      Book Tags table - A link table that links Tags table rows to Book table rows
//      Isbns table - Isbn numbers
//      Book Isbns table - A link table that links Isbns table rows to Book table rows
//
//      I decided to make all of the column names in all of the tables unique so I don't
//      need to worry about qualifying names when building queries

// Extensions for thumbnail files in the cache.
const val kSmallThumb = ".small.png"
const val kThumb = ".png"

// SQL names for descending and ascending order
const val kDesc = "DESC"
const val kAsc = "ASC"

/**
 * The books database
 */
@Database(
    entities = [
        BookEntity::class,
        AuthorEntity::class,
        BookAndAuthorEntity::class,
        TagEntity::class,
        BookAndTagEntity::class,
        CategoryEntity::class,
        BookAndCategoryEntity::class,
        ViewEntity::class,
        UndoRedoOperationEntity::class,
        UndoTransactionEntity::class,
        IsbnEntity::class,
        BookAndIsbnEntity::class,
        SeriesEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun getBookDao(): BookDao
    abstract fun getTagDao(): TagDao
    abstract fun getBookTagDao(): BookTagDao
    abstract fun getAuthorDao(): AuthorDao
    abstract fun getCategoryDao(): CategoryDao
    abstract fun getViewDao(): ViewDao
    abstract fun getUndoRedoDao(): UndoRedoDao
    abstract fun getIsbnDao(): IsbnDao
    abstract fun getSeriesDao(): SeriesDao

    /**
     * Descriptor for tables in the book database
     * @param name The name of the table
     * @param idColumn The name of the row id column for the table
     * @param flagColumn The name of the flags column for the table
     * @param flagValue The value of the HIDDEN flag for the table
     * @param selectedValue The value of the SELECTED flag for the table
     * @param flagPreserved Flag values to preserve for the table
     * @param modTimeColumn The name of the time modified column
     * @param bookIdColumn The name of the book id column in a link table
     * @param linkIdColumn The name of the link id column in a link table
     */
    data class TableDescription(
        val name: String,
        val idColumn: String,
        val flagColumn: String?,
        val flagValue: Int,
        val selectedValue: Int,
        val flagPreserved: Int,
        val modTimeColumn: String?,
        val bookIdColumn: String?,
        val linkIdColumn: String?
    ) {
        /**
         * Return the SQLite expression that filters by the hidden flag
         * @param hidden True to filter hidden rows. False to filter visible rows
         * @return The expression as a string
         */
        fun getVisibleExpression(hidden: Boolean = false): String {
            if (flagColumn.isNullOrEmpty())
                return ""
            return "( ( $flagColumn & $flagValue ) ${if (hidden) "!=" else "="} 0 )"
        }
    }

    /** Writable database used for update and delete queries */
    private lateinit var writableDb: SupportSQLiteDatabase
    private var deleteOnCloseName: String? = null

    /** Initialize the writable database */
    private fun initWritableQueries() {
        val helper = openHelper
        writableDb = helper.writableDatabase
    }

    /**
     * Set the hidden flag for a row in a table
     */
    fun setHidden(table: TableDescription, id: Long): Int {
        return execUpdateDelete(
            SimpleSQLiteQuery(
                """UPDATE ${table.name}
                  | ${if (table.flagPreserved == 0)
                         "SET ${table.flagColumn} = ${table.flagValue}"
                     else
                         "SET ${table.flagColumn} = ( ${table.flagColumn} & ${table.flagPreserved} ) | ${table.flagValue}"
                     }
                  | WHERE ${table.idColumn} = ? AND ( ( ${table.flagColumn} & ${table.flagValue} ) = 0 )
                """.trimMargin(),
                arrayOf<Any>(id))
        )
    }

    /**
     * Set the hidden flag for a row in a table
     */
    fun setHidden(table: TableDescription, e: WhereExpression): Int {
        return execUpdateDelete(
            SimpleSQLiteQuery(
                """UPDATE ${table.name}
                  | ${if (table.flagPreserved == 0)
                        "SET ${table.flagColumn} = ${table.flagValue}"
                    else
                        "SET ${table.flagColumn} = ( ${table.flagColumn} & ${table.flagPreserved} ) | ${table.flagValue}"
                    }
                  | ${e.expression}
                """.trimMargin(),
                e.args)
        )
    }

    /**
     * Execute an UPDATE or DELETE SQL Query
     * @param query The query to run
     * Must be called within a transaction
     */
    fun execUpdateDelete(query: SupportSQLiteQuery?): Int {
        if (query == null)
            return 0
        // Compile the query and bind its arguments
        val statement = writableDb.compileStatement(query.sql)
        query.bindTo(statement)
        // Run the query
        return statement.executeUpdateDelete()
    }

    /**
     * Execute an UPDATE or DELETE SQL Query
     * @param query The query to run
     * Must be called within a transaction
     */
    fun execInsert(query: SupportSQLiteQuery?): Long {
        if (query == null)
            return 0L
        // Compile the query and bind its arguments
        val statement = writableDb.compileStatement(query.sql)
        query.bindTo(statement)
        // Run the query
        return statement.executeInsert()
    }

    /**
     *  Close the database and delete if testing
     * @param context The context for testing to use to delete the database
     */
    fun close(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        pref.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        close()
        deleteOnCloseName?.let { context.deleteDatabase(it) }
    }

    /**
     *  @inheritDoc
     *  Closes the writable database if it has been set
     */
    override fun close() {
        if (::writableDb.isInitialized)
            writableDb.close()
        super.close()
    }

    companion object {
        /** The book table descriptor */
        val bookTable = TableDescription(BOOK_TABLE, BOOK_ID_COLUMN, BOOK_FLAGS, BookEntity.HIDDEN, BookEntity.SELECTED, BookEntity.PRESERVE, DATE_MODIFIED_COLUMN, null, null)
        /** The authors table descriptor */
        val authorsTable = TableDescription(AUTHORS_TABLE, AUTHORS_ID_COLUMN, AUTHORS_FLAGS, AuthorEntity.HIDDEN, 0, AuthorEntity.PRESERVE, null, null, null)
        /** The categories table descriptor */
        val categoriesTable = TableDescription(CATEGORIES_TABLE, CATEGORIES_ID_COLUMN, CATEGORIES_FLAGS, CategoryEntity.HIDDEN, 0, CategoryEntity.PRESERVE, null, null, null)
        /** The tags table descriptor */
        val tagsTable = TableDescription(TAGS_TABLE, TAGS_ID_COLUMN, TAGS_FLAGS, TagEntity.HIDDEN, TagEntity.SELECTED, TagEntity.PRESERVE, null, null, null)
        /** The series table descriptor */
        val seriesTable = TableDescription(SERIES_TABLE, SERIES_ID_COLUMN, SERIES_FLAG_COLUMN, SeriesEntity.HIDDEN, 0, SeriesEntity.PRESERVE, null, null, null)
        /** The views table descriptor */
        val viewsTable = TableDescription(VIEWS_TABLE, VIEWS_ID_COLUMN, VIEWS_FLAGS, ViewEntity.HIDDEN, 0, ViewEntity.PRESERVE, null, null, null)
        /** The book_authors link table descriptor */
        val bookAuthorsTable = TableDescription(BOOK_AUTHORS_TABLE, BOOK_AUTHORS_ID_COLUMN, null, 0, 0, 0, null, BOOK_AUTHORS_BOOK_ID_COLUMN, BOOK_AUTHORS_AUTHOR_ID_COLUMN)
        /** The book_categories link table descriptor */
        val bookCategoriesTable = TableDescription(BOOK_CATEGORIES_TABLE, BOOK_CATEGORIES_ID_COLUMN, null, 0, 0, 0, null, BOOK_CATEGORIES_BOOK_ID_COLUMN, BOOK_CATEGORIES_CATEGORY_ID_COLUMN)
        /** The book_tags link table descriptor */
        val bookTagsTable = TableDescription(BOOK_TAGS_TABLE, BOOK_TAGS_ID_COLUMN, null, 0, 0, 0, null, BOOK_TAGS_BOOK_ID_COLUMN, BOOK_TAGS_TAG_ID_COLUMN)
        /** The isbns table descriptor */
        val isbnTable = TableDescription(ISBNS_TABLE, ISBNS_ID_COLUMN, ISBNS_FLAGS, IsbnEntity.HIDDEN, 0, IsbnEntity.PRESERVE, null, null, null)
        /** The book_isbns link table descriptor */
        val bookIsbnsTable = TableDescription(BOOK_ISBNS_TABLE, BOOK_ISBNS_ID_COLUMN, null, 0, 0, 0, null, BOOK_ISBNS_BOOK_ID_COLUMN, BOOK_ISBNS_ISBN_ID_COLUMN)

        /** Undo levels preference key */
        const val UNDO_LEVEL_KEY = "undo_levels"
        /** Undo levels preference initial value */
        const val UNDO_LEVEL_INITIAL = 20
        /** Undo levels preference minimum value */
        const val UNDO_LEVEL_MIN = 0
        /** Undo levels preference maximum value */
        const val UNDO_LEVEL_MAX = 100
        /** Clear Undo preference key */
        const val UNDO_CLEAR_KEY = "undo_clear"
        private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                UNDO_LEVEL_KEY -> MainScope().launch {
                    db.getUndoRedoDao().setMaxUndoLevels(sharedPreferences.getInt(UNDO_LEVEL_KEY, UNDO_LEVEL_INITIAL))
                }
                else -> {}
            }
        }

        /**
         * The name of the books database
         */
        private const val DATABASE_FILENAME = "books_database.db"

        /** Temp table suffix used during version 6 to 7 migration */
        const val TEMP_SUFFIX_6_7 = "_tmp_6_7"

        /**
         * The data base, once it is open
         */
        private var mDb: BookDatabase? = null
        val db: BookDatabase
            get() = mDb!!

        /**
         * Initialize the data base
         * @param context Application context
         * @param testing True to create a database for testing
         * @param name The name of the database. Use null for the default name or in-memory if testing is true
         */
        fun initialize(context: Context, testing: Boolean = false, name: String? = null) {
            if (mDb == null) {
                mDb = create(context, testing, name)
                val pref = PreferenceManager.getDefaultSharedPreferences(context)
                pref.registerOnSharedPreferenceChangeListener(preferenceListener)
                preferenceListener.onSharedPreferenceChanged(pref, UNDO_LEVEL_KEY)
            }
        }

        /**
         * Close the data base
         * @param context The context for testing to use to delete the database
         */
        fun close(context: Context) {
            mDb?.close(context)
            mDb = null
        }

        /**
         * Create the data base
         * @param context Application context
         * @param testing True to create a database for testing
         * @param name The name of the database. Use null for the default name or in-memory if testing is true
         */
        private fun create(context: Context, testing: Boolean, name: String?): BookDatabase {
            val builder: Builder<BookDatabase>
            if (testing && name == null) {
                builder = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
            } else {
                if (testing)
                    context.deleteDatabase(name)
                builder = Room.databaseBuilder(
                    context, BookDatabase::class.java, name?: DATABASE_FILENAME
                )

                // If we have a prepopulate database use it.
                // This should only be used with a fresh debug install
                val assets = context.resources.assets
                try {
                    val asset = "database/${name?: DATABASE_FILENAME}"
                    val stream = assets.open(asset)
                    stream.close()
                    builder.createFromAsset(asset)
                } catch (_: IOException) {
                    // No prepopulate asset, create empty database
                }

                builder.addMigrations(*migrations)
            }

            // Build the database
            val db = builder.build()
            db.initWritableQueries()
            if (testing)
                db.deleteOnCloseName = name

            return db
        }

        /**
         * Create an expression for a query
         * @param column The column to query
         * @param selectedExpression Lambda to get the selected items expression
         * @param ids The column values to query
         * @param invert Flag to indicate whether to query the column values in ids, or
         *               the column values not in ids
         * @param args List to add arguments for the expression
         * The expression is of the form:
         *   "( $column [ NOT ] IN ( ?, ?, ... ? ) )"
         * The question marks are replaced by arguments pushed in args
         * If we select everything then the expression is just ""
         * If we select nothing then return NULL
         */
        private fun getExpression(column: String?, selectedExpression: ((invert: Boolean) -> String)?, ids: Array<Any>?, invert: Boolean?, args: MutableList<Any>): String? {
            // Return null if we select nothing
            val emptyIds = ids?.isEmpty() ?: (selectedExpression == null)
            if (column == null || (invert != true && emptyIds))
                return null

            // Build the expression string
            val builder = StringBuilder()
            if (!emptyIds) {
                builder.append("( ")
                builder.append(column)
                if (!ids.isNullOrEmpty()) {
                    if (invert == true)
                        builder.append(" NOT")
                    builder.append(" IN ( ")
                    repeat (ids.size - 1) {
                        builder.append("?, ")
                    }
                    builder.append("? )")
                    args.addAll(ids)
                } else
                    builder.append(" IN ( ${selectedExpression!!(invert == true)} )")
                builder.append(" )")
            }
            return builder.toString()
        }

        /**
         * Build a query
         * @param command The SQLite command
         * @param flagColumn The column name for the flags. Null if hidden flag is not used.
         * @param flagValue The value of the hidden flag
         * @param condition The conditions of the query.
         * Each condition is 3 values in this order:
         *    column name
         *    selected SQLite expression lambda
         *    value array
         *    invert boolean
         * @see #getExpression(String?, String?, Array<Any>?, Boolean?, MutableList<Any>)
         * Multiple conditions are joined by AND
         */
        internal fun buildQueryForIds(command: String, flagColumn: String?, flagValue: Int, vararg condition: Any?) =
            buildQueryForIds(command, flagColumn, flagValue, null, *condition)

        /**
         * Build a query
         * @param command The SQLite command
         * @param flagColumn The column name for the flags. Null if hidden flag is not used.
         * @param flagValue The value of the hidden flag
         * @param filter A filter to restrict the book ids
         * @param condition The conditions of the query.
         * Each condition is 3 values in this order:
         *    column name
         *    selected SQLite expression lambda
         *    value array
         *    invert boolean
         * @see #getExpression(String?, String?, Array<Any>?, Boolean?, MutableList<Any>)
         * Multiple conditions are joined by AND. If a filter is used, then it is used with
         * the column name from the first condition
         */
        internal fun buildQueryForIds(command: String, flagColumn: String?, flagValue: Int, filter: BookFilter.BuiltFilter?, vararg condition: Any?): SupportSQLiteQuery? {
            return buildWhereExpressionForIds(flagColumn, flagValue, filter, *condition)?.let {
                // Return the query if there was one
                SimpleSQLiteQuery("$command${it.expression}", it.args)
            }
        }


        /**
         * Build a query
         * @param flagColumn The column name for the flags. Null if hidden flag is not used.
         * @param flagValue The value of the hidden flag
         * @param filter A filter to restrict the book ids
         * @param condition The conditions of the query.
         * Each condition is 3 values in this order:
         *    column name
         *    selected SQLite expression lambda
         *    value array
         *    invert boolean
         * @see #getExpression(String?, String?, Array<Any>?, Boolean?, MutableList<Any>)
         * Multiple conditions are joined by AND. If a filter is used, then it is used with
         * the column name from the first condition
         */
        internal fun buildWhereExpressionForIds(flagColumn: String?, flagValue: Int, filter: BookFilter.BuiltFilter?, vararg condition: Any?): WhereExpression? {
            val builder = StringBuilder()
            val args = ArrayList<Any>()

            // Create the conditions
            var bookColumn: String? = null
            // Loop through the conditions in the varargs
            for (i in 0..condition.size - 4 step 4) {
                // Get the expression and ignore conditions that select nothing
                val column = condition[i] as? String
                @Suppress("UNCHECKED_CAST")
                getExpression(
                    column,
                    condition[i + 1] as? ((Boolean) -> String),
                    condition[i + 2] as? Array<Any>,
                    condition[i + 3] as? Boolean,
                    args
                )?.let {expr ->
                    // We got an expression
                    bookColumn = bookColumn?: column
                    if (expr != "") {
                        // Start with WHERE and then use AND to separate expressions
                        builder.append("${if (builder.isEmpty()) " WHERE " else " AND "}$expr")
                    }
                }?: return null
            }

            if (filter != null && bookColumn != null) {
                builder.append("${if (builder.isEmpty()) " WHERE " else " AND "} ( $bookColumn IN ( ${filter.command} ) )")
                args.addAll(filter.args)
            }

            if (flagColumn != null)
                builder.append("${if (builder.isEmpty()) " WHERE " else " AND "} ( ( $flagColumn & $flagValue ) == 0)")

            // Return the query if there was one
            return WhereExpression(builder.toString(), args.toArray())
        }

        /**
         * Build and SQLite expression to change bits for an update statement
         * @param operation How the select bit is changed. Null mean toggle,
         *                  true mean set and false means clear
         * @param flagColumn The column to be change
         * @param bits The bits to be changed
         */
        fun changeBits(
            operation: Boolean?, flagColumn: String, bits: Int
        ): String {
            // Set the operator
            return "SET $flagColumn = ${when (operation) {
                // Set
                true -> "$flagColumn | $bits"
                // Clear
                false -> "$flagColumn & ~$bits"
                // Toggle
                else -> "~( $flagColumn & $bits ) & ( $flagColumn | $bits )"
            }}"
        }

        /**
         * Build a query to change flag bits in a flag column
         * @param id The id of the row to be changed. Null means change in all rows
         * @param filter A filter to filter the rows changed
         * @param idColumn The name of the id column in the table
         */
        fun StringBuilder.idWithFilter(
            id: Long?, filter: BookFilter.BuiltFilter?, idColumn: String
        ): StringBuilder {
            // Set the condition
            // Include id
            if (id != null)
                append(" ${if (isEmpty()) "WHERE" else "AND"} ( $idColumn == $id )" )
            // Add in filter
            if (filter != null && filter.command.isNotEmpty())
                append(" ${if (isEmpty()) "WHERE" else "AND"} ( $idColumn IN ( ${filter.command} ) )")
            return this
        }

        /**
         * Build a query to change flag bits in a flag column
         * @param flagValue The hidden flag
         * @param result True for is visible and false for is not visible
         * @param flagColumn The column to be change
         */
        fun StringBuilder.selectVisible(
            flagColumn: String, flagValue: Int, result: Boolean = true
        ): StringBuilder {
            append(" ${if (isEmpty()) "WHERE" else "AND"} ( ( $flagColumn & $flagValue ) ${if (result) "==" else "!="} 0 )")
            return this
        }

        /**
         * Build a query to change flag bits in a flag column
         * @param bits The bits to be changed
         * @param value The value to compare
         * @param result True for == and false for !=
         * @param flagColumn The column to be change
         */
        fun StringBuilder.selectByFlagBits(
            bits: Int, value: Int, result: Boolean, flagColumn: String
        ): StringBuilder {
            if (bits != 0)
                append(" ${if (isEmpty()) "WHERE" else "AND"} ( ( $flagColumn & $bits ) ${if (result) "==" else "!="} $value )")
            return this
        }

        suspend fun <T> callConflict(conflict: T, callback: (suspend CoroutineScope.(T) -> Boolean)?, nullReturn: Boolean = false): Boolean {
            return coroutineScope {
                callback?.let { it(conflict) }?: nullReturn
            }
        }

        private fun deleteUndo(database: SupportSQLiteDatabase, table: TableDescription, linkTable: TableDescription) {
            // Delete links for hidden rows in book table
            database.delete(linkTable.name, """
                        |${linkTable.bookIdColumn} IN (
                        | SELECT $BOOK_ID_COLUMN FROM $BOOK_TABLE WHERE ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) != 0 )
                        |)
                        """.trimMargin(), null)
            // Delete links for hidden rows in table
            database.delete(linkTable.name, """
                        |${linkTable.linkIdColumn} IN (
                        | SELECT ${table.idColumn} FROM ${table.name} WHERE ( ( ${table.flagColumn} & ${table.flagValue} ) != 0 )
                        |)
                        """.trimMargin(), null)
            // Delete hidden rows in table
            database.delete(table.name, "( ( ${table.flagColumn} & ${table.flagValue} ) != 0 )", null)
        }

        /**
         * Migrations for the book data base
         */
        private val migrations = arrayOf(
            object: Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // This SQL is take from the BookDatabase_Impl file
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$VIEWS_TABLE` (`$VIEWS_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$VIEWS_NAME_COLUMN` TEXT NOT NULL COLLATE NOCASE, `$VIEWS_DESC_COLUMN` TEXT NOT NULL DEFAULT '', `$VIEWS_FILTER_COLUMN` TEXT)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${VIEWS_TABLE}_$VIEWS_ID_COLUMN` ON `$VIEWS_TABLE` (`$VIEWS_ID_COLUMN`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${VIEWS_TABLE}_$VIEWS_NAME_COLUMN` ON `$VIEWS_TABLE` (`$VIEWS_NAME_COLUMN`)")
                }
            },
            object: Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // This SQL is take from the BookDatabase_Impl file
                    db.execSQL("ALTER TABLE $BOOK_TABLE ADD COLUMN `$BOOK_FLAGS` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $TAGS_TABLE ADD COLUMN `$TAGS_FLAGS` INTEGER NOT NULL DEFAULT 0")
                }
            },
            object: Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                }
            },
            object: Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Add tables for the undo and redo
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$OPERATION_TABLE` (`$OPERATION_UNDO_ID_COLUMN` INTEGER NOT NULL, `$OPERATION_OPERATION_ID_COLUMN` INTEGER NOT NULL, `$OPERATION_TYPE_COLUMN` INTEGER NOT NULL, `$OPERATION_CUR_ID_COLUMN` INTEGER NOT NULL, `$OPERATION_OLD_ID_COLUMN` INTEGER NOT NULL DEFAULT 0, `$OPERATION_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_operation_table_operation_id` ON `$OPERATION_TABLE` (`$OPERATION_ID_COLUMN`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_operation_table_operation_undo_id_operation_operation_id` ON `$OPERATION_TABLE` (`$OPERATION_UNDO_ID_COLUMN`, `$OPERATION_OPERATION_ID_COLUMN`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$UNDO_TABLE` (`$TRANSACTION_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$TRANSACTION_UNDO_ID_COLUMN` INTEGER NOT NULL, `$TRANSACTION_DESC_COLUMN` TEXT NOT NULL, `$TRANSACTION_FLAGS_COLUMN` INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_undo_table_transaction_id` ON `$UNDO_TABLE` (`$TRANSACTION_ID_COLUMN`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_undo_table_transaction_undo_id` ON `$UNDO_TABLE` (`$TRANSACTION_UNDO_ID_COLUMN`)")
                    // Add flags to authors, categories and views tables
                    db.execSQL("ALTER TABLE $AUTHORS_TABLE ADD COLUMN `$AUTHORS_FLAGS` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $CATEGORIES_TABLE ADD COLUMN `$CATEGORIES_FLAGS` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $VIEWS_TABLE ADD COLUMN `$VIEWS_FLAGS` INTEGER NOT NULL DEFAULT 0")
                    // Change UNIQUE indexes to be non-unique
                    db.execSQL("DROP INDEX IF EXISTS `index_books_books_volume_id_books_source_id`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_books_volume_id_books_source_id` ON `$BOOK_TABLE` (`$VOLUME_ID_COLUMN`, `$SOURCE_ID_COLUMN`)")
                    db.execSQL("DROP INDEX IF EXISTS `index_books_books_isbn`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_books_isbn` ON `$BOOK_TABLE` (`books_isbn`)")
                    db.execSQL("DROP INDEX IF EXISTS `index_authors_authors_last_name_authors_remaining`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_authors_authors_last_name_authors_remaining` ON `$AUTHORS_TABLE` (`$LAST_NAME_COLUMN`, `$REMAINING_COLUMN`)")
                    db.execSQL("DROP INDEX IF EXISTS `index_tags_tags_name`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_tags_name` ON `$TAGS_TABLE` (`$TAGS_NAME_COLUMN`)")
                    db.execSQL("DROP INDEX IF EXISTS `index_categories_categories_category`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_categories_category` ON `$CATEGORIES_TABLE` (`$CATEGORY_COLUMN`)")
                    db.execSQL("DROP INDEX IF EXISTS `index_views_views_name`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_views_views_name` ON `$VIEWS_TABLE` (`$VIEWS_NAME_COLUMN`)")
                }
            },
            object: Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Create new tables and indices for isbns
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$ISBNS_TABLE` (`$ISBNS_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$ISBN_COLUMN` TEXT NOT NULL COLLATE NOCASE DEFAULT '', `$ISBNS_FLAGS` INTEGER NOT NULL DEFAULT 0)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_isbns_isbns_id` ON `$ISBNS_TABLE` (`$ISBNS_ID_COLUMN`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_isbns_isbns_isbn` ON `$ISBNS_TABLE` (`$ISBN_COLUMN`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$BOOK_ISBNS_TABLE` (`$BOOK_ISBNS_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$BOOK_ISBNS_ISBN_ID_COLUMN` INTEGER NOT NULL, `$BOOK_ISBNS_BOOK_ID_COLUMN` INTEGER NOT NULL, FOREIGN KEY(`$BOOK_ISBNS_BOOK_ID_COLUMN`) REFERENCES `$BOOK_TABLE`(`$BOOK_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`$BOOK_ISBNS_ISBN_ID_COLUMN`) REFERENCES `$ISBNS_TABLE`(`$ISBNS_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_isbns_book_isbns_id` ON `$BOOK_ISBNS_TABLE` (`$BOOK_ISBNS_ID_COLUMN`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_isbns_book_isbns_isbn_id_book_isbns_book_id` ON `$BOOK_ISBNS_TABLE` (`$BOOK_ISBNS_ISBN_ID_COLUMN`, `$BOOK_ISBNS_BOOK_ID_COLUMN`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_isbns_book_isbns_book_id` ON `$BOOK_ISBNS_TABLE` (`$BOOK_ISBNS_BOOK_ID_COLUMN`)")

                    // Clear all undo from the database
                    db.delete(OPERATION_TABLE, null, null)
                    db.delete(UNDO_TABLE, null, null)
                    deleteUndo(db, authorsTable, bookAuthorsTable)
                    deleteUndo(db, tagsTable, bookTagsTable)
                    deleteUndo(db, categoriesTable, bookCategoriesTable)
                    db.delete(BOOK_TABLE, "( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) != 0 )", null)

                    // Insert all of the isbns in to the isbn table
                    db.execSQL("INSERT OR ABORT INTO $ISBNS_TABLE ( $ISBNS_ID_COLUMN, $ISBN_COLUMN ) SELECT NULL, `books_isbn` FROM `$BOOK_TABLE` WHERE `books_isbn` NOTNULL")

                    // Insert all of the isbns in to the isbn table
                    db.execSQL("INSERT OR ABORT INTO $BOOK_ISBNS_TABLE ( $BOOK_ISBNS_ID_COLUMN, $BOOK_ISBNS_BOOK_ID_COLUMN, $BOOK_ISBNS_ISBN_ID_COLUMN ) SELECT NULL, $BOOK_ID_COLUMN, $ISBNS_ID_COLUMN FROM `$BOOK_TABLE` LEFT JOIN $ISBNS_TABLE ON $ISBN_COLUMN = `books_isbn` WHERE `books_isbn` NOTNULL")

                    // Delete the isbn index and set all values to NULL
                    db.execSQL("DROP INDEX IF EXISTS `index_books_books_isbn`")
                    // Set the original books_isbn column to null
                    db.execSQL("UPDATE OR ABORT $BOOK_TABLE SET `books_isbn` = NULL")
                }
            },
            object: Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Drop unused isbn column from book table
                    // Dropping a column is a pain, because a new books table must be created
                    // without the column. This means that all of the tables with foreign keys,
                    // also need to be recreated and all of the indexes for the new tables need
                    // to be recreated. The tables that need to be recreated are:
                    // books, book_authors, book_tags, book_categories and book_isbns
                    // These are the steps:
                    // 1) Drop all of the temp tables, just to make sure they aren't there
                    // 2) Drop all of the indices for the original tables
                    // 3) Rename all of the original tables to new names.
                    // 4) Recreate the original tables
                    // 5) Insert the rows from the temp tables into the original tables
                    // 6) Drop the temp tables
                    // 7) Recreate the indexes for the original tables
                    
                    // 1) Drop the temp tables
                    db.execSQL("DROP TABLE IF EXISTS `$BOOK_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("DROP TABLE IF EXISTS `$BOOK_AUTHORS_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("DROP TABLE IF EXISTS `$BOOK_TAGS_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("DROP TABLE IF EXISTS `$BOOK_CATEGORIES_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("DROP TABLE IF EXISTS `$BOOK_ISBNS_TABLE$TEMP_SUFFIX_6_7`")

                    // 2) Drop the indexes for the all of the tables
                    db.execSQL("DROP INDEX IF EXISTS `index_books_books_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_books_books_volume_id_books_source_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_books_books_series_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_authors_book_authors_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_authors_book_authors_author_id_book_authors_book_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_authors_book_authors_book_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_tags_book_tags_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_tags_book_tags_book_id_book_tags_tag_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_tags_book_tags_tag_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_categories_book_categories_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_categories_book_categories_category_id_book_categories_book_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_categories_book_categories_book_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_isbns_book_isbns_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_isbns_book_isbns_isbn_id_book_isbns_book_id`")
                    db.execSQL("DROP INDEX IF EXISTS `index_book_isbns_book_isbns_book_id`")

                    // 3) Rename the original tables to the temp tables
                    db.execSQL("ALTER TABLE `$BOOK_TABLE` RENAME TO `$BOOK_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("ALTER TABLE `$BOOK_AUTHORS_TABLE` RENAME TO `$BOOK_AUTHORS_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("ALTER TABLE `$BOOK_TAGS_TABLE` RENAME TO `$BOOK_TAGS_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("ALTER TABLE `$BOOK_CATEGORIES_TABLE` RENAME TO `$BOOK_CATEGORIES_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("ALTER TABLE `$BOOK_ISBNS_TABLE` RENAME TO `$BOOK_ISBNS_TABLE$TEMP_SUFFIX_6_7`")

                    // 4) Create the original tables
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$BOOK_TABLE` (`$BOOK_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$VOLUME_ID_COLUMN` TEXT, `$SOURCE_ID_COLUMN` TEXT, `$TITLE_COLUMN` TEXT NOT NULL DEFAULT '', `$SUBTITLE_COLUMN` TEXT NOT NULL DEFAULT '', `$DESCRIPTION_COLUMN` TEXT NOT NULL DEFAULT '', `$PAGE_COUNT_COLUMN` INTEGER NOT NULL DEFAULT 0, `$BOOK_COUNT_COLUMN` INTEGER NOT NULL DEFAULT 1, `$VOLUME_LINK` TEXT NOT NULL DEFAULT '', `$RATING_COLUMN` REAL NOT NULL DEFAULT -1.0, `$DATE_ADDED_COLUMN` INTEGER NOT NULL DEFAULT 0, `$DATE_MODIFIED_COLUMN` INTEGER NOT NULL DEFAULT 0, `$SMALL_THUMB_COLUMN` TEXT, `$LARGE_THUMB_COLUMN` TEXT, `$BOOK_FLAGS` INTEGER NOT NULL DEFAULT 0 )")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$BOOK_AUTHORS_TABLE` (`$BOOK_AUTHORS_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$BOOK_AUTHORS_AUTHOR_ID_COLUMN` INTEGER NOT NULL, `$BOOK_AUTHORS_BOOK_ID_COLUMN` INTEGER NOT NULL, FOREIGN KEY(`$BOOK_AUTHORS_BOOK_ID_COLUMN`) REFERENCES `$BOOK_TABLE`(`$BOOK_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`$BOOK_AUTHORS_AUTHOR_ID_COLUMN`) REFERENCES `$AUTHORS_TABLE`(`$AUTHORS_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$BOOK_TAGS_TABLE` (`$BOOK_TAGS_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$BOOK_TAGS_TAG_ID_COLUMN` INTEGER NOT NULL, `$BOOK_TAGS_BOOK_ID_COLUMN` INTEGER NOT NULL, FOREIGN KEY(`$BOOK_TAGS_BOOK_ID_COLUMN`) REFERENCES `$BOOK_TABLE`(`$BOOK_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`$BOOK_TAGS_TAG_ID_COLUMN`) REFERENCES `$TAGS_TABLE`(`$TAGS_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$BOOK_CATEGORIES_TABLE` (`$BOOK_CATEGORIES_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$BOOK_CATEGORIES_CATEGORY_ID_COLUMN` INTEGER NOT NULL, `$BOOK_CATEGORIES_BOOK_ID_COLUMN` INTEGER NOT NULL, FOREIGN KEY(`$BOOK_CATEGORIES_BOOK_ID_COLUMN`) REFERENCES `$BOOK_TABLE`(`$BOOK_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`$BOOK_CATEGORIES_CATEGORY_ID_COLUMN`) REFERENCES `$CATEGORIES_TABLE`(`$CATEGORIES_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$BOOK_ISBNS_TABLE` (`$BOOK_ISBNS_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$BOOK_ISBNS_ISBN_ID_COLUMN` INTEGER NOT NULL, `$BOOK_ISBNS_BOOK_ID_COLUMN` INTEGER NOT NULL, FOREIGN KEY(`$BOOK_ISBNS_BOOK_ID_COLUMN`) REFERENCES `$BOOK_TABLE`(`$BOOK_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`$BOOK_ISBNS_ISBN_ID_COLUMN`) REFERENCES `$ISBNS_TABLE`(`$ISBNS_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                    // 5) Insert the rows from the temp tables back into the original one
                    db.execSQL("INSERT INTO `$BOOK_TABLE` (`$BOOK_ID_COLUMN`, `$VOLUME_ID_COLUMN`, `$SOURCE_ID_COLUMN`, `$TITLE_COLUMN`, `$SUBTITLE_COLUMN`, `$DESCRIPTION_COLUMN`, `$PAGE_COUNT_COLUMN`, `$BOOK_COUNT_COLUMN`, `$VOLUME_LINK`, `$RATING_COLUMN`, `$DATE_ADDED_COLUMN`, `$DATE_MODIFIED_COLUMN`, `$SMALL_THUMB_COLUMN`, `$LARGE_THUMB_COLUMN`, `$BOOK_FLAGS`) SELECT `$BOOK_ID_COLUMN`, `$VOLUME_ID_COLUMN`, `$SOURCE_ID_COLUMN`, `$TITLE_COLUMN`, `$SUBTITLE_COLUMN`, `$DESCRIPTION_COLUMN`, `$PAGE_COUNT_COLUMN`, `$BOOK_COUNT_COLUMN`, `$VOLUME_LINK`, `$RATING_COLUMN`, `$DATE_ADDED_COLUMN`, `$DATE_MODIFIED_COLUMN`, `$SMALL_THUMB_COLUMN`, `$LARGE_THUMB_COLUMN`, `$BOOK_FLAGS` FROM `$BOOK_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("INSERT INTO `$BOOK_AUTHORS_TABLE` (`$BOOK_AUTHORS_ID_COLUMN`, `$BOOK_AUTHORS_AUTHOR_ID_COLUMN`, `$BOOK_AUTHORS_BOOK_ID_COLUMN`) SELECT `$BOOK_AUTHORS_ID_COLUMN`, `$BOOK_AUTHORS_AUTHOR_ID_COLUMN`, `$BOOK_AUTHORS_BOOK_ID_COLUMN` FROM `$BOOK_AUTHORS_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("INSERT INTO `$BOOK_TAGS_TABLE` (`$BOOK_TAGS_ID_COLUMN`, `$BOOK_TAGS_TAG_ID_COLUMN`, `$BOOK_TAGS_BOOK_ID_COLUMN`) SELECT `$BOOK_TAGS_ID_COLUMN`, `$BOOK_TAGS_TAG_ID_COLUMN`, `$BOOK_TAGS_BOOK_ID_COLUMN` FROM `$BOOK_TAGS_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("INSERT INTO `$BOOK_CATEGORIES_TABLE` (`$BOOK_CATEGORIES_ID_COLUMN`, `$BOOK_CATEGORIES_CATEGORY_ID_COLUMN`, `$BOOK_CATEGORIES_BOOK_ID_COLUMN`) SELECT `$BOOK_CATEGORIES_ID_COLUMN`, `$BOOK_CATEGORIES_CATEGORY_ID_COLUMN`, `$BOOK_CATEGORIES_BOOK_ID_COLUMN` FROM `$BOOK_CATEGORIES_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("INSERT INTO `$BOOK_ISBNS_TABLE` (`$BOOK_ISBNS_ID_COLUMN`, `$BOOK_ISBNS_ISBN_ID_COLUMN`, `$BOOK_ISBNS_BOOK_ID_COLUMN`) SELECT `$BOOK_ISBNS_ID_COLUMN`, `$BOOK_ISBNS_ISBN_ID_COLUMN`, `$BOOK_ISBNS_BOOK_ID_COLUMN` FROM `$BOOK_ISBNS_TABLE$TEMP_SUFFIX_6_7`")

                    // 6) Drop the temp tables
                    db.execSQL("DROP TABLE `$BOOK_AUTHORS_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("DROP TABLE `$BOOK_TAGS_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("DROP TABLE `$BOOK_CATEGORIES_TABLE$TEMP_SUFFIX_6_7`")
                    db.execSQL("DROP TABLE `$BOOK_ISBNS_TABLE$TEMP_SUFFIX_6_7`")
                    // Drop last after foreign keys are dropped
                    db.execSQL("DROP TABLE `$BOOK_TABLE$TEMP_SUFFIX_6_7`")

                    // 7) Recreate the original indexes
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_books_id` ON `$BOOK_TABLE` (`$BOOK_ID_COLUMN`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_books_volume_id_books_source_id` ON `$BOOK_TABLE` (`$VOLUME_ID_COLUMN`, `$SOURCE_ID_COLUMN`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_authors_book_authors_id` ON `$BOOK_AUTHORS_TABLE` (`$BOOK_AUTHORS_ID_COLUMN`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_authors_book_authors_author_id_book_authors_book_id` ON `$BOOK_AUTHORS_TABLE` (`$BOOK_AUTHORS_AUTHOR_ID_COLUMN`, `$BOOK_AUTHORS_BOOK_ID_COLUMN`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_authors_book_authors_book_id` ON `$BOOK_AUTHORS_TABLE` (`$BOOK_AUTHORS_BOOK_ID_COLUMN`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_tags_book_tags_id` ON `$BOOK_TAGS_TABLE` (`$BOOK_TAGS_ID_COLUMN`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_tags_book_tags_book_id_book_tags_tag_id` ON `$BOOK_TAGS_TABLE` (`$BOOK_TAGS_BOOK_ID_COLUMN`, `$BOOK_TAGS_TAG_ID_COLUMN`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_tags_book_tags_tag_id` ON `$BOOK_TAGS_TABLE` (`$BOOK_TAGS_TAG_ID_COLUMN`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_categories_book_categories_id` ON `$BOOK_CATEGORIES_TABLE` (`$BOOK_CATEGORIES_ID_COLUMN`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_categories_book_categories_category_id_book_categories_book_id` ON `$BOOK_CATEGORIES_TABLE` (`$BOOK_CATEGORIES_CATEGORY_ID_COLUMN`, `$BOOK_CATEGORIES_BOOK_ID_COLUMN`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_categories_book_categories_book_id` ON `$BOOK_CATEGORIES_TABLE` (`$BOOK_CATEGORIES_BOOK_ID_COLUMN`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_isbns_book_isbns_id` ON `$BOOK_ISBNS_TABLE` (`$BOOK_ISBNS_ID_COLUMN`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_isbns_book_isbns_isbn_id_book_isbns_book_id` ON `$BOOK_ISBNS_TABLE` (`$BOOK_ISBNS_ISBN_ID_COLUMN`, `$BOOK_ISBNS_BOOK_ID_COLUMN`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_isbns_book_isbns_book_id` ON `$BOOK_ISBNS_TABLE` (`$BOOK_ISBNS_BOOK_ID_COLUMN`)")

                    // Add series columns to book table
                    db.execSQL("ALTER TABLE $BOOK_TABLE ADD COLUMN `$BOOK_SERIES_COLUMN` INTEGER DEFAULT NULL REFERENCES `$SERIES_TABLE`(`$SERIES_ID_COLUMN`) ON UPDATE NO ACTION ON DELETE CASCADE")
                    db.execSQL("ALTER TABLE $BOOK_TABLE ADD COLUMN `$SERIES_ORDER_COLUMN` INTEGER DEFAULT NULL")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_books_series_id` ON `$BOOK_TABLE` (`$BOOK_SERIES_COLUMN`)")

                    // Add series table and indices
                    db.execSQL("CREATE TABLE IF NOT EXISTS `$SERIES_TABLE` (`$SERIES_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$SERIES_SERIES_ID_COLUMN` TEXT NOT NULL, `$SERIES_TITLE_COLUMN` TEXT NOT NULL, `$SERIES_FLAG_COLUMN` INTEGER NOT NULL DEFAULT 0)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_series_id` ON `$SERIES_TABLE` (`$SERIES_ID_COLUMN`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_series_series_id` ON `$SERIES_TABLE` (`$SERIES_SERIES_ID_COLUMN`)")
                }
            },
            object: Migration(7,8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE $OPERATION_TABLE ADD COLUMN `$OPERATION_MOD_TIME_COLUMN` INTEGER DEFAULT NULL")

                }
            }
        )
    }
}
