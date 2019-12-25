package com.example.cleve.bibliotech

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.SQLException
import android.database.sqlite.*
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.os.CancellationSignal
import java.util.*

internal class BookDatabase : DatabaseErrorHandler {
    private var mHelper: BookOpenHelper? = null
    private var mDb: SQLiteDatabase? = null
    private var mContext: Context? = null
    // Open the database if it exists, create it if it doesn't, upgrade it if
// it is an earlier version.
    fun open(context: Context?) {
        if (mHelper == null) {
            mContext = context
            mHelper = BookOpenHelper(context)
            mDb = mHelper!!.writableDatabase
        }
    }

    // Close the database
    fun close() {
        if (mHelper != null) {
            mHelper!!.close()
            mHelper = null
        }
    }

    fun isOpen() = mHelper != null

    private fun addAuthor(bookId: Long, author: String): Long {
        val last = StringBuilder()
        val remaining = StringBuilder()
        // Separate the author into last name and remaining
        separateAuthor(author, last, remaining)
        // look up the author in the authors table
        val result = mDb!!.query(
            AUTHORS_TABLE,
            arrayOf(AUTHORS_ID_COLUMN),
            "$LAST_NAME_COLUMN = '?' AND $REMAINING_COLUMN = '?'",
            arrayOf(last.toString(), remaining.toString()),
            null,
            null,
            null
        )
        val authorId: Long
        authorId = if (result.count > 0) { // Author is already there, get the id
            result.moveToFirst()
            result.getLong(result.getColumnIndex(AUTHORS_ID_COLUMN))
        } else { // Add the author to the table
            val values = ContentValues()
            values.put(LAST_NAME_COLUMN, last.toString())
            values.put(REMAINING_COLUMN, remaining.toString())
            mDb!!.insertOrThrow(AUTHORS_TABLE, null, values)
        }
        result.close()
        // Link the book and author
        val values = ContentValues()
        values.put(BOOK_ID_COLUMN, bookId)
        values.put(AUTHORS_ID_COLUMN, authorId)
        return mDb!!.insertOrThrow(BOOK_AUTHORS_TABLE, null, values)
    }

    fun addBook(book: Book): Long { // Add the book to the book table
        val values = ContentValues()
        values.put(VOLUME_ID_COLUMN, book.mVolumeID)
        values.put(ISBN_COLUMN, book.mISBN)
        values.put(TITLE_COLUMN, book.mTitle)
        values.put(SUBTITLE_COLUMN, book.mSubTitle)
        values.put(DESCRIPTION_COLUMN, book.mDescription)
        values.put(
            SMALL_THUMB_COLUMN,
            book.mThumbnails[Book.kSmallThumbnail]
        )
        values.put(LARGE_THUMB_COLUMN, book.mThumbnails[Book.kThumbnail])
        val bookId = mDb!!.insertOrThrow(BOOK_TABLE, null, values)
        // Add the authors of the book
        book.mAuthors.indices.forEach { i ->
            val author = book.mAuthors[i]
            if (author != null)
                addAuthor(bookId, author)
        }
        return bookId
    }

    fun addBookToView(viewId: Long, bookId: Long): Long { // Look to see if the book is already in the view
        val result = mDb!!.query(
            BOOK_AUTHORS_TABLE,
            arrayOf(BOOK_AUTHORS_ID_COLUMN),
            "$VIEWS_ID_COLUMN = ? AND $BOOK_ID_COLUMN = ?",
            arrayOf("$viewId", "$bookId"),
            null,
            null,
            null
        )
        if (result.count > 0) { // Book is alread in the view, return its id
            result.moveToFirst()
            val id = result.getLong(result.getColumnIndex(BOOK_AUTHORS_ID_COLUMN))
            result.close()
            return id
        }
        result.close()
        // Add the book to the view
        val values = ContentValues()
        values.put(BOOK_VIEWS_VIEW_ID_COLUMN, viewId)
        values.put(BOOK_VIEWS_BOOK_ID_COLUMN, bookId)
        values.put(SELECTED_COLUMN, false)
        values.put(OPEN_COLUMN, false)
        return mDb!!.insertOrThrow(BOOK_VIEWS_TABLE, null, values)
    }

    fun removeBook(bookId: Long) { // Save the author ids, so we can check to delete unreferenced authors later
        val selectArg = arrayOf("$bookId")
        val idColumn = arrayOf(BOOK_AUTHORS_ID_COLUMN)
        val result = mDb!!.query(
            BOOK_AUTHORS_TABLE, idColumn,
            "$BOOK_AUTHORS_BOOK_ID_COLUMN = ?", selectArg, null, null, null
        )
        val authors = LongArray(result.count)
        val index = result.getColumnIndex(BOOK_AUTHORS_AUTHOR_ID_COLUMN)
        for (i in 0 until result.count) {
            result.moveToPosition(i)
            authors[i] = result.getLong(index)
        }
        result.close()
        // Delete the book author links
        mDb!!.delete(
            BOOK_AUTHORS_TABLE,
            "$BOOK_AUTHORS_BOOK_ID_COLUMN = ?",
            selectArg
        )
        // Loop through the authors and delete the ones that aren't referenced
        for (i in authors.indices) {
            selectArg[0] = "${authors[i]}"
            val cursor = mDb!!.query(
                BOOK_AUTHORS_TABLE,
                idColumn,
                "$BOOK_AUTHORS_AUTHOR_ID_COLUMN = ?",
                selectArg,
                null,
                null,
                null
            )
            val count = cursor.count
            cursor.close()

            if (count == 0) { // Not referenced, delete it
                mDb!!.delete(
                    AUTHORS_TABLE,
                    "$AUTHORS_ID_COLUMN = ?",
                    selectArg
                )
            }
        }
        // Now delete the book from any views that it is in.
        selectArg[0] = "$bookId"
        mDb!!.delete(
            BOOK_VIEWS_TABLE,
            "$BOOK_VIEWS_BOOK_ID_COLUMN = ?",
            selectArg
        )
        // Finally delete the book
        mDb!!.delete(
            BOOK_TABLE,
            "$BOOK_ID_COLUMN = ?",
            selectArg
        )
    }

    fun removeBookFromView(viewId: Long, bookId: Long) { // Save the author ids, so we can check to delete unreferenced authors later
        val selectArg =
            arrayOf("$viewId", "$bookId")
        mDb!!.delete(
            BOOK_VIEWS_TABLE,
            "$BOOK_VIEWS_VIEW_ID_COLUMN = ? AND $BOOK_VIEWS_BOOK_ID_COLUMN = ?",
            selectArg
        )
    }

    fun isBookInAnyView(bookId: Long): Boolean { // Save the author ids, so we can check to delete unreferenced authors later
        val selectArg =
            arrayOf("$bookId")
        val idColumn =
            arrayOf(BOOK_VIEWS_ID_COLUMN)
        val result = mDb!!.query(
            BOOK_VIEWS_TABLE, idColumn,
            "$BOOK_VIEWS_BOOK_ID_COLUMN = ?", selectArg, null, null, null
        )
        val inView = result.count > 0
        result.close()
        return inView
    }

    fun getViewList(cancellationSignal: CancellationSignal?): ViewCursor {
        return ViewCursor.query(mDb, cancellationSignal)
    }

    fun getBookList(viewid: Long, sortOrder: String?, cancellationSignal: CancellationSignal?): BookCursor {
        return BookCursor.query(viewid, sortOrder, mDb, cancellationSignal)
    }

    fun getBook(bookId: Long, cancellationSignal: CancellationSignal?): BookCursor {
        return BookCursor.getBook(bookId, mDb, cancellationSignal)
    }

    override fun onCorruption(dbObj: SQLiteDatabase) { // 1. Instantiate an AlertDialog.Builder with its constructor
        val builder = AlertDialog.Builder(mContext)
        // 2. Chain together various setter methods to set the dialog characteristics
        builder.setMessage(R.string.db_error)
            .setTitle(R.string.db_error_title)
        // 3. Get the AlertDialog from create()
        val dialog = builder.create()
        dialog.show()
    }

    // View cursor supplies information about the view
    internal class ViewCursor private constructor(
        masterQuery: SQLiteCursorDriver,
        editTable: String, query: SQLiteQuery
    ) : SQLiteCursor(masterQuery, editTable, query) {
        companion object {
            private val mProjMap = HashMap<String, String>()
            private val mSelect = arrayOf(
                VIEWS_NAME_COLUMN,
                VIEWS_ID_COLUMN,
                VIEWS_ORDER_COLUMN,
                VIEWS_SORT_COLUMN
            )

            fun query(
                db: SQLiteDatabase?,
                cancellationSignal: CancellationSignal?
            ): ViewCursor {
                val query = SQLiteQueryBuilder()
                query.cursorFactory = Factory()
                query.isDistinct = false
                query.tables = VIEWS_TABLE
                query.projectionMap = mProjMap
                query.isStrict = true
                return query.query(
                    db,
                    mSelect,
                    null,
                    null,
                    null,
                    null,
                    VIEWS_ORDER_COLUMN,
                    null,
                    cancellationSignal
                ) as ViewCursor
            }

            init {
                mProjMap[VIEWS_NAME_COLUMN] = VIEWS_NAME_COLUMN
                mProjMap[VIEWS_ID_COLUMN] = VIEWS_ID_COLUMN
                mProjMap[VIEWS_ORDER_COLUMN] = VIEWS_ORDER_COLUMN
                mProjMap[VIEWS_SORT_COLUMN] = VIEWS_SORT_COLUMN
            }
        }

        private val idIndex: Int = getColumnIndex(VIEWS_ID_COLUMN)
        private val nameIndex: Int = getColumnIndex(VIEWS_NAME_COLUMN)
        private val orderIndex: Int = getColumnIndex(VIEWS_ORDER_COLUMN)
        private val sortIndex: Int = getColumnIndex(VIEWS_SORT_COLUMN)
        val id: Long
            get() = getLong(idIndex)

        val name: String
            get() = getString(nameIndex)

        val order: Int
            get() = getInt(orderIndex)

        val sort: String
            get() = getString(sortIndex)

        internal class Factory : CursorFactory {
            override fun newCursor(
                db: SQLiteDatabase, masterQuery: SQLiteCursorDriver,
                editTable: String, query: SQLiteQuery
            ): Cursor {
                return ViewCursor(masterQuery, editTable, query)
            }
        }

    }

    @Suppress("PropertyName")
    internal class BookCursor private constructor(
        masterQuery: SQLiteCursorDriver,
        editTable: String, query: SQLiteQuery
    ) : SQLiteCursor(masterQuery, editTable, query) {
        companion object {
            private val mProjMap = HashMap<String, String>()
            private val mSelect = arrayOf(
                "$BOOK_ID_COLUMN AS _id",
                TITLE_COLUMN,
                SUBTITLE_COLUMN,
                SMALL_THUMB_COLUMN,
                LARGE_THUMB_COLUMN,
                DESCRIPTION_COLUMN,
                VOLUME_ID_COLUMN,
                ISBN_COLUMN,
                ALL_AUTHORS_COLUMN,
                BOOK_VIEWS_VIEW_ID_COLUMN
            )
            private val mSelectOpn = arrayOf(
                BOOK_ID_COLUMN,
                VOLUME_ID_COLUMN,
                SELECTED_COLUMN,
                OPEN_COLUMN
            )

            fun query(
                viewId: Long,
                sortOrder: String?,
                db: SQLiteDatabase?,
                cancellationSignal: CancellationSignal?
            ): BookCursor {
                val query = SQLiteQueryBuilder()
                query.cursorFactory = Factory()
                query.isDistinct = false
                query.tables = ("( " + BOOK_VIEWS_TABLE
                        + " LEFT JOIN " + BOOK_AUTHORS_VIEW
                        + " ON (" + BOOK_ID_COLUMN + " = " + BOOK_VIEWS_BOOK_ID_COLUMN + ") )")
                query.projectionMap = mProjMap
                query.isStrict = true
                return query.query(
                    db,
                    mSelect,
                    "$BOOK_VIEWS_VIEW_ID_COLUMN = ?",
                    arrayOf("$viewId"),
                    null,
                    null,
                    sortOrder,
                    null,
                    cancellationSignal
                ) as BookCursor
            }

            fun getBook(
                bookId: Long,
                db: SQLiteDatabase?,
                cancellationSignal: CancellationSignal?
            ): BookCursor {
                val query = SQLiteQueryBuilder()
                query.cursorFactory = Factory()
                query.isDistinct = true
                query.tables = BOOK_AUTHORS_VIEW
                query.projectionMap = mProjMap
                query.isStrict = true
                return query.query(
                    db, mSelect,
                    "_id = ?", arrayOf("$bookId"),
                    null, null, null, null, cancellationSignal
                ) as BookCursor
            }

            init {
                mProjMap["_id"] = "_id"
                mProjMap[BOOK_ID_COLUMN] = BOOK_ID_COLUMN
                mProjMap["$BOOK_ID_COLUMN AS _id"] = "$BOOK_ID_COLUMN AS _id"
                mProjMap[TITLE_COLUMN] = TITLE_COLUMN
                mProjMap[SUBTITLE_COLUMN] = SUBTITLE_COLUMN
                mProjMap[SMALL_THUMB_COLUMN] = SMALL_THUMB_COLUMN
                mProjMap[LARGE_THUMB_COLUMN] = LARGE_THUMB_COLUMN
                mProjMap[DESCRIPTION_COLUMN] = DESCRIPTION_COLUMN
                mProjMap[VOLUME_ID_COLUMN] = VOLUME_ID_COLUMN
                mProjMap[ISBN_COLUMN] = ISBN_COLUMN
                mProjMap[ALL_AUTHORS_COLUMN] = ALL_AUTHORS_COLUMN
                mProjMap[BOOK_VIEWS_VIEW_ID_COLUMN] = BOOK_VIEWS_VIEW_ID_COLUMN
            }
        }

        private val idIndex: Int = getColumnIndex("_id")
        private val titleIndex: Int = getColumnIndex(TITLE_COLUMN)
        private val subTitleIndex: Int = getColumnIndex(SUBTITLE_COLUMN)
        private val smallThumbIndex: Int = getColumnIndex(SMALL_THUMB_COLUMN)
        private val largeThumbIndex: Int = getColumnIndex(LARGE_THUMB_COLUMN)
        private val descriptionIndex: Int = getColumnIndex(DESCRIPTION_COLUMN)
        private val volumeIdIndex: Int = getColumnIndex(VOLUME_ID_COLUMN)
        private val isbnIndex: Int = getColumnIndex(ISBN_COLUMN)
        private val allAuthorsIndex: Int = getColumnIndex(ALL_AUTHORS_COLUMN)
        private val viewIdIndex: Int = getColumnIndex(BOOK_VIEWS_VIEW_ID_COLUMN)
        val id: Long
            get() = getLong(idIndex)

        val viewId: Long
            get() = getLong(viewIdIndex)

        val title: String
            get() = getString(titleIndex)

        val subTitle: String
            get() = getString(subTitleIndex)

        val smallThumb: String
            get() = getString(smallThumbIndex)

        val largeThumb: String
            get() = getString(largeThumbIndex)

        val description: String
            get() = getString(descriptionIndex)

        val volumeId: String
            get() = getString(volumeIdIndex)

        val ISBN: String
            get() = getString(isbnIndex)

        val allAuthors: String
            get() = getString(allAuthorsIndex)

        var isSelected: Boolean
            get() {
                val c = database.query(
                    true,
                    BOOK_VIEWS_TABLE,
                    mSelectOpn,
                    "$BOOK_VIEWS_BOOK_ID_COLUMN = ? AND $BOOK_VIEWS_VIEW_ID_COLUMN = ?",
                    arrayOf(
                        "$id",
                        "$viewId"
                    ),
                    null,
                    null,
                    null,
                    null
                )
                val selected =
                    c.getInt(c.getColumnIndex(SELECTED_COLUMN)) != 0
                c.close()
                return selected
            }
            set(selected) {
                val values = ContentValues()
                values.put(SELECTED_COLUMN, if (selected) 1 else 0)
                database.update(
                    BOOK_VIEWS_TABLE,
                    values,
                    "$BOOK_VIEWS_BOOK_ID_COLUMN = ? AND $BOOK_VIEWS_VIEW_ID_COLUMN = ?",
                    arrayOf(
                        "$id",
                        "$viewId"
                    )
                )
            }

        var isOpen: Boolean
            get() {
                val c = database.query(
                    true,
                    BOOK_VIEWS_TABLE,
                    mSelectOpn,
                    "$BOOK_VIEWS_BOOK_ID_COLUMN = ? AND $BOOK_VIEWS_VIEW_ID_COLUMN = ?",
                    arrayOf(
                        "$id",
                        "$viewId"
                    ),
                    null,
                    null,
                    null,
                    null
                )
                val open =
                    c.getInt(c.getColumnIndex(OPEN_COLUMN)) != 0
                c.close()
                return open
            }
            set(open) {
                val values = ContentValues()
                values.put(OPEN_COLUMN, if (open) 1 else 0)
                database.update(
                    BOOK_VIEWS_TABLE,
                    values,
                    "$BOOK_VIEWS_BOOK_ID_COLUMN = ? AND $BOOK_VIEWS_VIEW_ID_COLUMN = ?",
                    arrayOf(
                        "$id",
                        "$viewId"
                    )
                )
            }

        internal class Factory : CursorFactory {
            override fun newCursor(
                db: SQLiteDatabase, masterQuery: SQLiteCursorDriver,
                editTable: String, query: SQLiteQuery
            ): Cursor {
                return BookCursor(masterQuery, editTable, query)
            }
        }

    }

    private inner class BookOpenHelper internal constructor(context: Context?) :
        SQLiteOpenHelper(
            context,
            DATABASE_FILENAME,
            null,
            VERSION,
            this@BookDatabase
        ) {
        override fun onCreate(db: SQLiteDatabase) {
            for (t in mPersistantTables) {
                t.onCreate(db, false)
            }
            val bookConcatView =
                ("CREATE VIEW " + BOOK_AUTHORS_VIEW + " AS"
                        + " SELECT *, GROUP_CONCAT((" + LAST_NAME_COLUMN + " || ', ' || " + REMAINING_COLUMN + "), ',\n') AS " + ALL_AUTHORS_COLUMN
                        + " FROM ( SELECT * FROM " + BOOK_TABLE
                        + " LEFT JOIN " + BOOK_AUTHORS_TABLE
                        + " ON (" + BOOK_AUTHORS_BOOK_ID_COLUMN + " = " + BOOK_ID_COLUMN + ")"
                        + " LEFT JOIN " + AUTHORS_TABLE
                        + " ON (" + AUTHORS_ID_COLUMN + " = " + BOOK_AUTHORS_AUTHOR_ID_COLUMN + ")"
                        + ") GROUP BY " + BOOK_ID_COLUMN + ";")
            db.execSQL(bookConcatView)
            // Add the default book lists
            val values = ContentValues()
            values.put(
                VIEWS_NAME_COLUMN,
                mContext!!.getString(R.string.title_section1)
            )
            values.put(VIEWS_ORDER_COLUMN, 0)
            values.put(
                VIEWS_SORT_COLUMN,
                BOOK_VIEWS_ID_COLUMN
            )
            db.insert(VIEWS_TABLE, null, values)
            values.clear()
            values.put(
                VIEWS_NAME_COLUMN,
                mContext!!.getString(R.string.title_section2)
            )
            values.put(VIEWS_ORDER_COLUMN, 1)
            values.put(
                VIEWS_SORT_COLUMN,
                BOOK_VIEWS_ID_COLUMN
            )
            db.insert(VIEWS_TABLE, null, values)
        }

        override fun onUpgrade(
            db: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) {
            for (t in mPersistantTables) {
                t.onUpgrade(db, oldVersion, newVersion)
            }
        }
    }

    private class Column internal constructor(
        var mName: String,
        var mDefinition: String,
        var mVersion: Int
    )

    private class Table @JvmOverloads internal constructor(
        var mName: String,
        var mColumns: Array<Column>,
        var mConstraints: String,
        var mVersion: Int,
        var mNoRowId: Boolean = false
    ) {
        fun buildCreateString(temp: Boolean): String {
            val createString = StringBuilder(1024)
            createString.append("CREATE ")
            if (temp) createString.append("TEMP ")
            createString.append("TABLE ")
            createString.append(mName)
            createString.append(" ( ")
            for (c in mColumns) {
                createString.append(c.mName)
                createString.append(" ")
                createString.append(c.mDefinition)
                createString.append(", ")
            }
            if (mConstraints.isEmpty()) createString.delete(
                createString.length - 2,
                createString.length
            ) else createString.append(mConstraints)
            createString.append(" )")
            if (mNoRowId) {
                createString.append(" WITHOUT ROWID")
            }
            createString.append(";")
            return createString.toString()
        }

        fun buildAlterString(c: Column): String {
            return "ALTER TABLE " + mName + " ADD COLUMN " + c.mName + " " + c.mDefinition + ";"
        }

        // Create the table
        fun onCreate(db: SQLiteDatabase, temp: Boolean) {
            val create = buildCreateString(temp)
            db.execSQL(create)
        }

        // Create the table
        fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (mVersion <= oldVersion) {
                try {
                    for (c in mColumns) {
                        if (c.mVersion in (oldVersion + 1)..newVersion) {
                            val alter = buildAlterString(c)
                            db.execSQL(alter)
                        }
                    }
                    return
                } catch (e: SQLException) { // If we can't alter the table, then delete it and create it
                }
                db.execSQL("DROP TABLE IF EXISTS $mName")
            }
            onCreate(db, false)
        }

    }

    companion object {
        private const val DATABASE_FILENAME = "books_database"
        private const val BOOK_TABLE = "books"
        private const val BOOK_ID_COLUMN = "books_id"
        private const val VOLUME_ID_COLUMN = "books_volume_id"
        private const val ISBN_COLUMN = "books_isbn"
        private const val TITLE_COLUMN = "books_title"
        private const val SUBTITLE_COLUMN = "books_subtitle"
        private const val DESCRIPTION_COLUMN = "books_description"
        private const val SMALL_THUMB_COLUMN = "books_small_thumb"
        private const val LARGE_THUMB_COLUMN = "books_large_thumb"
        private const val AUTHORS_TABLE = "authors"
        private const val AUTHORS_ID_COLUMN = "authors_id"
        private const val LAST_NAME_COLUMN = "authors_last_name"
        private const val REMAINING_COLUMN = "authors_remaining"
        private const val BOOK_AUTHORS_TABLE = "book_authors"
        private const val BOOK_AUTHORS_ID_COLUMN = "book_authods_id"
        private const val BOOK_AUTHORS_BOOK_ID_COLUMN = "book_authors_book_id"
        private const val BOOK_AUTHORS_AUTHOR_ID_COLUMN = "book_authors_author_id"
        private const val VIEWS_TABLE = "views"
        private const val VIEWS_ID_COLUMN = "views_id"
        private const val VIEWS_NAME_COLUMN = "views_name"
        private const val VIEWS_ORDER_COLUMN = "views_order"
        private const val VIEWS_SORT_COLUMN = "views_sort"
        private const val BOOK_VIEWS_TABLE = "book_views"
        private const val BOOK_VIEWS_ID_COLUMN = "book_views_id"
        private const val BOOK_VIEWS_VIEW_ID_COLUMN = "book_views_view_id"
        private const val BOOK_VIEWS_BOOK_ID_COLUMN = "book_views_book_id"
        private const val SELECTED_COLUMN = "book_views_selected"
        private const val OPEN_COLUMN = "book_views_open"
        private const val BOOK_AUTHORS_VIEW = "book_authors_view"
        private const val ALL_AUTHORS_COLUMN = "book_authors_view_all_authors"
        // If you change the databse definition, make sure you change
// the version
        private const val VERSION = 1
        // Define the databse tables.
        private val mPersistantTables =
            arrayOf( // The main book table.
                Table(
                    BOOK_TABLE, arrayOf(
                        Column(
                            BOOK_ID_COLUMN,
                            "INTEGER PRIMARY KEY AUTOINCREMENT",
                            1
                        ),
                        Column(
                            VOLUME_ID_COLUMN,
                            "TEXT DEFAULT NULL UNIQUE",
                            1
                        ),
                        Column(ISBN_COLUMN, "TEXT DEFAULT NULL UNIQUE", 1),
                        Column(TITLE_COLUMN, "TEXT NOT NULL DEFAULT ''", 1),
                        Column(
                            SUBTITLE_COLUMN,
                            "TEXT NOT NULL DEFAULT ''",
                            1
                        ),
                        Column(
                            DESCRIPTION_COLUMN,
                            "TEXT NOT NULL DEFAULT ''",
                            1
                        ),
                        Column(
                            SMALL_THUMB_COLUMN,
                            "TEXT NOT NULL DEFAULT ''",
                            1
                        ),
                        Column(
                            LARGE_THUMB_COLUMN,
                            "TEXT NOT NULL DEFAULT ''",
                            1
                        )
                    ),
                    "", 1
                ),  // The author's table. We only keep author's names. Lastname and the remaining
// We force the author names to be unique. In our case two different authots with the
// same name isn't important, since all we track is the name.
                Table(
                    AUTHORS_TABLE,
                    arrayOf(
                        Column(
                            AUTHORS_ID_COLUMN,
                            "INTEGER PRIMARY KEY AUTOINCREMENT",
                            1
                        ),
                        Column(LAST_NAME_COLUMN, "TEXT NOT NULL", 1),
                        Column(
                            REMAINING_COLUMN,
                            "TEXT NOT NULL DEFAULT ''",
                            1
                        )
                    ),
                    "UNIQUE ($LAST_NAME_COLUMN, $REMAINING_COLUMN)",
                    1
                ),  // This table contains an entry for each author for each book. This is how multiple
// authors are handled. To find all the authors of a book, look in this table for
// all entries with the book id. The Author id's identify the authors
                Table(
                    BOOK_AUTHORS_TABLE,
                    arrayOf(
                        Column(
                            BOOK_AUTHORS_ID_COLUMN,
                            "INTEGER PRIMARY KEY AUTOINCREMENT",
                            1
                        ),
                        Column(
                            BOOK_AUTHORS_BOOK_ID_COLUMN,
                            "INTEGER NOT NULL REFERENCES $BOOK_TABLE($BOOK_ID_COLUMN)",
                            1
                        ),
                        Column(
                            BOOK_AUTHORS_AUTHOR_ID_COLUMN,
                            "INTEGER NOT NULL REFERENCES $AUTHORS_TABLE($AUTHORS_ID_COLUMN)",
                            1
                        )
                    ),
                    "UNIQUE ($BOOK_AUTHORS_BOOK_ID_COLUMN, $BOOK_AUTHORS_AUTHOR_ID_COLUMN)",
                    1
                ),  // The table of all of the views in the database. A view is a list of books. A book may
// appear in multiple views. The order column is the order the views should appear.
                Table(
                    VIEWS_TABLE, arrayOf(
                        Column(
                            VIEWS_ID_COLUMN,
                            "INTEGER PRIMARY KEY AUTOINCREMENT",
                            1
                        ),
                        Column(VIEWS_NAME_COLUMN, "TEXT NOT NULL", 1),
                        Column(VIEWS_ORDER_COLUMN, "INTEGER NOT NULL", 1),
                        Column(VIEWS_SORT_COLUMN, "STRING NOT NULL", 1)
                    ),
                    "", 1
                ),  // The table that identifies which books are in which views.
                Table(
                    BOOK_VIEWS_TABLE,
                    arrayOf(
                        Column(
                            BOOK_VIEWS_ID_COLUMN,
                            "INTEGER PRIMARY KEY AUTOINCREMENT",
                            1
                        ),
                        Column(
                            BOOK_VIEWS_BOOK_ID_COLUMN,
                            "INTEGER NOT NULL REFERENCES $BOOK_TABLE($BOOK_ID_COLUMN)",
                            1
                        ),
                        Column(
                            BOOK_VIEWS_VIEW_ID_COLUMN,
                            "INTEGER NOT NULL REFERENCES $VIEWS_TABLE($VIEWS_ID_COLUMN)",
                            1
                        ),
                        Column(SELECTED_COLUMN, "INTEGER NOT NULL", 1),
                        Column(OPEN_COLUMN, "INTEGER NOT NULL", 1)
                    ),
                    "UNIQUE ($BOOK_VIEWS_BOOK_ID_COLUMN, $BOOK_VIEWS_VIEW_ID_COLUMN)",
                    1
                )
            )

        private fun separateAuthor(
            in_name: String,
            last: StringBuilder,
            remaining: StringBuilder
        ) {
            var name = in_name
            name = name.trim { it <= ' ' }
            // Look for a , assume last, remaining if found
            var lastIndex = name.lastIndexOf(',')
            if (lastIndex > 0) {
                last.append(name.substring(0, lastIndex).trim { it <= ' ' })
                remaining.append(name.substring(lastIndex + 1).trim { it <= ' ' })
            } else { // look for a space, assume remaining last if foud
                lastIndex = name.lastIndexOf(' ')
                if (lastIndex > 0) {
                    last.append(name.substring(lastIndex))
                    remaining.append(name.substring(0, lastIndex).trim { it <= ' ' })
                } else { // No space or commas, only last name
                    last.append(name)
                }
            }
        }
    }
}