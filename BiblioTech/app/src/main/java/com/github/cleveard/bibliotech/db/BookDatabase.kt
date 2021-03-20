package com.github.cleveard.bibliotech.db

import android.content.Context
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
        UndoTransactionEntity::class,
        RedoTransactionEntity::class
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

    /** Writable database used for update and delete queries */
    private lateinit var writableDb: SupportSQLiteDatabase

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
     *  @inheritDoc
     *  Closes the writable database if it has been set
     */
    override fun close() {
        if (::writableDb.isInitialized)
            writableDb.close()
        super.close()
    }

    companion object {
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
         * @param inMemory True to create an in-memory database
         */
        fun initialize(context: Context, inMemory: Boolean = false) {
            if (mDb == null) {
                mDb = create(context, inMemory)
            }
        }

        /**
         * Close the data base
         */
        fun close() {
            mDb?.close()
            mDb = null
        }

        /**
         * Create the data base
         * @param context Application context
         * @param inMemory True to create an in-memory database
         */
        private fun create(context: Context, inMemory: Boolean): BookDatabase {
            val builder: Builder<BookDatabase>
            if (inMemory) {
                builder = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
            } else {
                builder = Room.databaseBuilder(
                    context, BookDatabase::class.java, DATABASE_FILENAME
                )

                // If we have a prepopulate database use it.
                // This should only be used with a fresh debug install
                val assets = context.resources.assets
                try {
                    val asset = "database/$DATABASE_FILENAME"
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
         * @param condition The conditions of the query.
         * Each condition is 3 values in this order:
         *    column name
         *    selected SQLite expression lambda
         *    value array
         *    invert boolean
         * @see #getExpression(String?, String?, Array<Any>?, Boolean?, MutableList<Any>)
         * Multiple conditions are joined by AND
         */
        internal fun buildQueryForIds(command: String, vararg condition: Any?) =
            buildQueryForIds(command, null, *condition)

        /**
         * Build a query
         * @param command The SQLite command
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
        internal fun buildQueryForIds(command: String, filter: BookFilter.BuiltFilter?, vararg condition: Any?): SupportSQLiteQuery? {
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
                        builder.append("${if (builder.isEmpty()) "WHERE " else " AND "}$expr")
                    }
                }?: return null
            }

            if (filter != null && bookColumn != null) {
                builder.append("${if (builder.isEmpty()) "WHERE " else " AND "} ( $bookColumn IN ( ${filter.command} ) )")
                args.addAll(filter.args)
            }

            // Return the query if there was one
            return SimpleSQLiteQuery("$command $builder", args.toArray())
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
                    database.execSQL("CREATE TABLE IF NOT EXISTS `$OPERATION_TABLE` (`$OPERATION_UNDO_ID_COLUMN` INTEGER NOT NULL, `$OPERATION_OPERATION_ID_COLUMN` INTEGER NOT NULL, `$OPERATION_TYPE_COLUMN` INTEGER NOT NULL, `$OPERATION_CUR_ID_COLUMN` INTEGER NOT NULL, `$OPERATION_OLD_ID_COLUMN` INTEGER NOT NULL, `$OPERATION_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_operation_table_operation_id` ON `$OPERATION_TABLE` (`$OPERATION_ID_COLUMN`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_operation_table_operation_undo_id` ON `$OPERATION_TABLE` (`$OPERATION_UNDO_ID_COLUMN`)")
                    database.execSQL("CREATE TABLE IF NOT EXISTS `$UNDO_TABLE` (`$TRANSACTION_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$TRANSACTION_UNDO_ID_COLUMN` INTEGER NOT NULL, `$TRANSACTION_DESC_COLUMN` TEXT NOT NULL)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_undo_table_transaction_id` ON `$UNDO_TABLE` (`$TRANSACTION_ID_COLUMN`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_undo_table_transaction_undo_id` ON `$UNDO_TABLE` (`$TRANSACTION_UNDO_ID_COLUMN`)")
                    database.execSQL("CREATE TABLE IF NOT EXISTS `$REDO_TABLE` (`$TRANSACTION_ID_COLUMN` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `$TRANSACTION_UNDO_ID_COLUMN` INTEGER NOT NULL, `$TRANSACTION_DESC_COLUMN` TEXT NOT NULL)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_redo_table_transaction_id` ON `$REDO_TABLE` (`$TRANSACTION_ID_COLUMN`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_redo_table_transaction_undo_id` ON `$REDO_TABLE` (`$TRANSACTION_UNDO_ID_COLUMN`)")
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
