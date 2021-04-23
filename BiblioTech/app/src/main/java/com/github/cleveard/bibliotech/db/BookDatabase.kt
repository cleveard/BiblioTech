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
        UndoTransactionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun getBookDao(): BookDao
    abstract fun getTagDao(): TagDao
    abstract fun getBookTagDao(): BookTagDao
    abstract fun getAuthorDao(): AuthorDao
    abstract fun getCategoryDao(): CategoryDao
    abstract fun getViewDao(): ViewDao
    abstract fun getUndoRedoDao(): UndoRedoDao

    /**
     * Descriptor for tables in the book database
     * @param name The name of the table
     * @param idColumn The name of the row id column for the table
     * @param flagColumn The name of the flags column for the table
     * @param flagValue The value of the HIDDEN flag for the table
     * @param bookIdColumn The name of the book id column in a link table
     * @param linkIdColumn The name of the link id column in a link table
     */
    data class TableDescription(
        val name: String,
        val idColumn: String,
        val flagColumn: String?,
        val flagValue: Int,
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
        val bookTable = TableDescription(BOOK_TABLE, BOOK_ID_COLUMN, BOOK_FLAGS, BookEntity.HIDDEN, null, null)
        /** The authors table descriptor */
        val authorsTable = TableDescription(AUTHORS_TABLE, AUTHORS_ID_COLUMN, AUTHORS_FLAGS, AuthorEntity.HIDDEN, null, null)
        /** The categories table descriptor */
        val categoriesTable = TableDescription(CATEGORIES_TABLE, CATEGORIES_ID_COLUMN, CATEGORIES_FLAGS, CategoryEntity.HIDDEN, null, null)
        /** The tags table descriptor */
        val tagsTable = TableDescription(TAGS_TABLE, TAGS_ID_COLUMN, TAGS_FLAGS, TagEntity.HIDDEN, null, null)
        /** The views table descriptor */
        val viewsTable = TableDescription(VIEWS_TABLE, VIEWS_ID_COLUMN, VIEWS_FLAGS, ViewEntity.HIDDEN, null, null)
        /** The book_authors link table descriptor */
        val bookAuthorsTable = TableDescription(BOOK_AUTHORS_TABLE, BOOK_AUTHORS_ID_COLUMN, null, 0, BOOK_AUTHORS_BOOK_ID_COLUMN, BOOK_AUTHORS_AUTHOR_ID_COLUMN)
        /** The book_categories link table descriptor */
        val bookCategoriesTable = TableDescription(BOOK_CATEGORIES_TABLE, BOOK_CATEGORIES_ID_COLUMN, null, 0, BOOK_CATEGORIES_BOOK_ID_COLUMN, BOOK_CATEGORIES_CATEGORY_ID_COLUMN)
        /** The book_tags link table descriptor */
        val bookTagsTable = TableDescription(BOOK_TAGS_TABLE, BOOK_TAGS_ID_COLUMN, null, 0, BOOK_TAGS_BOOK_ID_COLUMN, BOOK_TAGS_TAG_ID_COLUMN)

        /** Undo levels preference key */
        const val UNDO_LEVEL_KEY = "undo_levels"
        /** Undo levels preference initial value */
        const val UNDO_LEVEL_INITIAL = 20
        /** Undo levels preference minimum value */
        const val UNDO_LEVEL_MIN = 0
        /** Undo levels preference maximum value */
        const val UNDO_LEVEL_MAX = 100
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
                } catch (ex: IOException) {
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
                if (ids != null) {
                    if (invert == true)
                        builder.append(" NOT")
                    builder.append(" IN ( ")
                    for (i in 1 until ids.size)
                        builder.append("?, ")
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

        /**
         * Migrations for the book data base
         */
        private val migrations = arrayOf(
            object: Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // This SQL is take from the BookDatabase_Impl file
                    database.execSQL("CREATE TABLE IF NOT EXISTS `$VIEWS_TABLE` (`$VIEWS_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$VIEWS_NAME_COLUMN` TEXT NOT NULL COLLATE NOCASE, `$VIEWS_DESC_COLUMN` TEXT NOT NULL DEFAULT '', `$VIEWS_FILTER_COLUMN` TEXT)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${VIEWS_TABLE}_$VIEWS_ID_COLUMN` ON `$VIEWS_TABLE` (`$VIEWS_ID_COLUMN`)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${VIEWS_TABLE}_$VIEWS_NAME_COLUMN` ON `$VIEWS_TABLE` (`$VIEWS_NAME_COLUMN`)")
                }
            },
            object: Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // This SQL is take from the BookDatabase_Impl file
                    database.execSQL("ALTER TABLE $BOOK_TABLE ADD COLUMN `$BOOK_FLAGS` INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE $TAGS_TABLE ADD COLUMN `$TAGS_FLAGS` INTEGER NOT NULL DEFAULT 0")
                }
            },
            object: Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                }
            },
            object: Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Add tables for the undo and redo
                    database.execSQL("CREATE TABLE IF NOT EXISTS `$OPERATION_TABLE` (`$OPERATION_UNDO_ID_COLUMN` INTEGER NOT NULL, `$OPERATION_OPERATION_ID_COLUMN` INTEGER NOT NULL, `$OPERATION_TYPE_COLUMN` INTEGER NOT NULL, `$OPERATION_CUR_ID_COLUMN` INTEGER NOT NULL, `$OPERATION_OLD_ID_COLUMN` INTEGER NOT NULL DEFAULT 0, `$OPERATION_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_operation_table_operation_id` ON `$OPERATION_TABLE` (`$OPERATION_ID_COLUMN`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_operation_table_operation_undo_id_operation_operation_id` ON `$OPERATION_TABLE` (`$OPERATION_UNDO_ID_COLUMN`, `$OPERATION_OPERATION_ID_COLUMN`)")
                    database.execSQL("CREATE TABLE IF NOT EXISTS `$UNDO_TABLE` (`$TRANSACTION_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$TRANSACTION_UNDO_ID_COLUMN` INTEGER NOT NULL, `$TRANSACTION_DESC_COLUMN` TEXT NOT NULL, `$TRANSACTION_FLAGS_COLUMN` INTEGER NOT NULL)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_undo_table_transaction_id` ON `$UNDO_TABLE` (`$TRANSACTION_ID_COLUMN`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_undo_table_transaction_undo_id` ON `$UNDO_TABLE` (`$TRANSACTION_UNDO_ID_COLUMN`)")
                    // Add flags to authors, categories and views tables
                    database.execSQL("ALTER TABLE $AUTHORS_TABLE ADD COLUMN `$AUTHORS_FLAGS` INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE $CATEGORIES_TABLE ADD COLUMN `$CATEGORIES_FLAGS` INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE $VIEWS_TABLE ADD COLUMN `$VIEWS_FLAGS` INTEGER NOT NULL DEFAULT 0")
                    // Change UNIQUE indexes to be non-unique
                    database.execSQL("DROP INDEX IF EXISTS `index_books_books_volume_id_books_source_id`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_books_volume_id_books_source_id` ON `$BOOK_TABLE` (`$VOLUME_ID_COLUMN`, `$SOURCE_ID_COLUMN`)")
                    database.execSQL("DROP INDEX IF EXISTS `index_books_books_isbn`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_books_isbn` ON `$BOOK_TABLE` (`$ISBN_COLUMN`)")
                    database.execSQL("DROP INDEX IF EXISTS `index_authors_authors_last_name_authors_remaining`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_authors_authors_last_name_authors_remaining` ON `$AUTHORS_TABLE` (`$LAST_NAME_COLUMN`, `$REMAINING_COLUMN`)")
                    database.execSQL("DROP INDEX IF EXISTS `index_tags_tags_name`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_tags_name` ON `$TAGS_TABLE` (`$TAGS_NAME_COLUMN`)")
                    database.execSQL("DROP INDEX IF EXISTS `index_categories_categories_category`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_categories_category` ON `$CATEGORIES_TABLE` (`$CATEGORY_COLUMN`)")
                    database.execSQL("DROP INDEX IF EXISTS `index_views_views_name`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_views_views_name` ON `$VIEWS_TABLE` (`$VIEWS_NAME_COLUMN`)")
                }
            }
        )
    }
}
