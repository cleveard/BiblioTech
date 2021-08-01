package com.github.cleveard.bibliotech.db

import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

// Isbns table name and its column names
const val ISBNS_TABLE = "isbns"             // Isbns table name
const val ISBNS_ID_COLUMN = "isbns_id"      // Incrementing id
const val ISBN_COLUMN = "isbns_isbn"        // Isbn name
const val ISBNS_FLAGS = "isbns_flags"       // Flags for isbns

// Book Isbns table name and its column names
const val BOOK_ISBNS_TABLE = "book_isbns"                       // Book Isbns table names
const val BOOK_ISBNS_ID_COLUMN = "book_isbns_id"                // Incrementing id
const val BOOK_ISBNS_BOOK_ID_COLUMN = "book_isbns_book_id"      // Book row incrementing id
const val BOOK_ISBNS_ISBN_ID_COLUMN = "book_isbns_isbn_id"      // Isbn row incrementing id

/**
 * Room entity for the isbns table
 */
@Entity(tableName = ISBNS_TABLE,
    indices = [
        Index(value = [ISBNS_ID_COLUMN],unique = true),
        Index(value = [ISBN_COLUMN])
    ])
data class IsbnEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = ISBNS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = ISBN_COLUMN,defaultValue = "",collate = ColumnInfo.NOCASE) var isbn: String,
    @ColumnInfo(name = ISBNS_FLAGS,defaultValue = "0") var flags: Int = 0
) {
    companion object {
        const val HIDDEN = 1
    }
}

/**
 * Room entity for the book isbns table
 */
@Entity(tableName = BOOK_ISBNS_TABLE,
    foreignKeys = [
        ForeignKey(entity = BookEntity::class,
            parentColumns = [BOOK_ID_COLUMN],
            childColumns = [BOOK_ISBNS_BOOK_ID_COLUMN],
            onDelete = CASCADE),
        ForeignKey(entity = IsbnEntity::class,
            parentColumns = [ISBNS_ID_COLUMN],
            childColumns = [BOOK_ISBNS_ISBN_ID_COLUMN],
            onDelete = CASCADE)
    ],
    indices = [
        Index(value = [BOOK_ISBNS_ID_COLUMN],unique = true),
        Index(value = [BOOK_ISBNS_ISBN_ID_COLUMN,BOOK_ISBNS_BOOK_ID_COLUMN],unique = true),
        Index(value = [BOOK_ISBNS_BOOK_ID_COLUMN])
    ])
data class BookAndIsbnEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOK_ISBNS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = BOOK_ISBNS_ISBN_ID_COLUMN) var isbnId: Long,
    @ColumnInfo(name = BOOK_ISBNS_BOOK_ID_COLUMN) var bookId: Long
)

/**
 * Isbn data access object
 */
@Dao
abstract class IsbnDao(private val db: BookDatabase) {
    /**
     * Get the list of isbns
     */
    @Query(value = "SELECT * FROM $ISBNS_TABLE WHERE ( ( $ISBNS_FLAGS & ${IsbnEntity.HIDDEN} ) = 0 ) ORDER BY $ISBN_COLUMN")
    abstract suspend fun get(): List<IsbnEntity>?

    /**
     * Add an isbn to the isbns table
     * @param isbns The isbn to add
     */
    @Insert
    abstract suspend fun add(isbns: IsbnEntity): Long

    /**
     * Add a book isbns link to the isbns table
     * @param bookIsbn The book isbn link to add
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun add(bookIsbn: BookAndIsbnEntity) : Long

    /**
     * Add multiple isbns for a book
     * @param bookId The id of the book
     * @param isbns The list of isbns to add
     * @param deleteIsbns True to delete unused isbns
     */
    @Transaction
    open suspend fun addWithUndo(bookId: Long, isbns: List<IsbnEntity>, deleteIsbns: Boolean = true) {
        if (deleteIsbns) {
            // Yes, get the ids of the isbns that are affects
            val oldIsbns = queryBookIds(arrayOf(bookId), null)
            // Delete the isbns
            deleteBooksWithUndo(arrayOf(bookId), null)
            // Add new isbns
            for (isbn in isbns)
                addWithUndo(bookId, isbn)
            oldIsbns?.let {
                // Delete isbns with no books
                for (isbn in it) {
                    if (isbns.indexOfFirst { a -> a.id == isbn } < 0) {
                        val list: List<BookAndIsbnEntity> = findById(isbn, 1)
                        if (list.isEmpty()) {
                            UndoRedoDao.OperationType.DELETE_ISBN.recordDelete(db.getUndoRedoDao(), isbn) {
                                deleteIsbn(isbn)
                            }
                        }
                    }
                }
            }
        } else {
            // No, just delete the links
            deleteBooksWithUndo(arrayOf(bookId), null)
            // Add new isbns
            for (isbn in isbns)
                addWithUndo(bookId, isbn)
        }
    }

    /**
     * Add a single isbns for a book
     * @param bookId The id o the book
     * @param isbn The isbn to add
     */
    @Transaction
    open suspend fun addWithUndo(bookId: Long, isbn: IsbnEntity) {
        // Find the isbn
        isbn.isbn = isbn.isbn.trim { it <= ' ' }
        val list: List<IsbnEntity> = findByName(isbn.isbn)
        // Use existing id, or add the isbn
        isbn.id = if (list.isNotEmpty()) {
            list[0].id
        } else {
            isbn.id = 0L
            add(isbn).also {
                if (it != 0L)
                    UndoRedoDao.OperationType.ADD_ISBN.recordAdd(db.getUndoRedoDao(), it)
            }
        }
        // Add the link
        add(BookAndIsbnEntity(
            id = 0,
            isbnId = isbn.id,
            bookId = bookId
        ))
        UndoRedoDao.OperationType.ADD_BOOK_ISBN_LINK.recordLink(db.getUndoRedoDao(), bookId, isbn.id)
    }

    /**
     * Delete all isbns from books
     * @param bookIds A list of book ids. Null means use selected books.
     * @param filter A filter to restrict the book ids
     */
    @Transaction
    protected open suspend fun deleteBooksWithUndo(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): Int {
        return BookDatabase.buildWhereExpressionForIds(
            null, 0, filter,
            BOOK_ISBNS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false
        )?. let {e ->
            UndoRedoDao.OperationType.DELETE_BOOK_ISBN_LINK.recordDelete(db.getUndoRedoDao(), e) {
                db.execUpdateDelete(
                    SimpleSQLiteQuery("DELETE FROM $BOOK_ISBNS_TABLE${it.expression}",
                        it.args)
                )
            }
        }?: 0
    }

    /**
     * Query isbns for a book
     * @param query The SQLite query to get the ids
     */
    @RawQuery(observedEntities = [BookAndIsbnEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<Long>?

    /**
     * Query author ids for a set of books
     * @param bookIds A list of book ids. Null means use the selected books.
     * @param filter A filter to restrict the book ids
     */
    private suspend fun queryBookIds(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_ISBNS_ISBN_ID_COLUMN FROM $BOOK_ISBNS_TABLE",
            null, 0, filter,
            BOOK_ISBNS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false)?.let { queryBookIds(it) }
    }

    /**
     * Delete all books for an isbn
     * @param isbnsId The id of the isbn
     */
    @Query("UPDATE $ISBNS_TABLE SET $ISBNS_FLAGS = ${IsbnEntity.HIDDEN} WHERE $ISBNS_ID_COLUMN = :isbnsId AND ( ( $ISBNS_FLAGS & ${IsbnEntity.HIDDEN} ) = 0 )")
    protected abstract suspend fun deleteIsbn(isbnsId: Long): Int

    /**
     * Find an isbn by name
     * @param isbns The name of the isbn
     */
    @Query(value = "SELECT * FROM $ISBNS_TABLE"
        + " WHERE $ISBN_COLUMN LIKE :isbns ESCAPE '\\' AND ( ( $ISBNS_FLAGS & ${IsbnEntity.HIDDEN} ) = 0 )")
    abstract suspend fun doFindByName(isbns: String): List<IsbnEntity>

    /**
     * Find an isbn by name
     * @param isbns The name of the isbn
     */
    suspend fun findByName(isbns: String): List<IsbnEntity> {
        return doFindByName(PredicateDataDescription.escapeLikeWildCards(isbns))
    }

    /**
     * Find books by isbns
     * @param isbnsId The id of the isbn
     * @param limit A limit on the number of book isbn links to return
     */
    @Query("SELECT * FROM $BOOK_ISBNS_TABLE WHERE $BOOK_ISBNS_ISBN_ID_COLUMN = :isbnsId LIMIT :limit")
    abstract suspend fun findById(isbnsId: Long, limit: Int = -1): List<BookAndIsbnEntity>

    /**
     * Delete all isbns from books
     * @param bookIds A list of book ids. Null means use the selected books.
     * @param deleteIsbns A flag to indicate whether the isbn in the Isbns table should be deleted
     *                      if all of its books have been deleted
     * @param filter A filter to restrict the book ids
     */
    @Transaction
    open suspend fun deleteWithUndo(bookIds: Array<Any>?, deleteIsbns: Boolean = true, filter: BookFilter.BuiltFilter? = null): Int {
        val count: Int
        // Should we delete isbns
        if (deleteIsbns) {
            // Yes get the id of the isbns affected
            val isbns = queryBookIds(bookIds, filter)
            // Delete the book isbn links
            count = deleteBooksWithUndo(bookIds, filter)
            isbns?.let {
                // Delete any empty isbns
                for (isbn in it) {
                    val list: List<BookAndIsbnEntity> = findById(isbn, 1)
                    if (list.isEmpty()) {
                        UndoRedoDao.OperationType.DELETE_ISBN.recordDelete(db.getUndoRedoDao(), isbn) {
                            deleteIsbn(isbn)
                        }
                    }
                }
            }
        } else {
            // No, just delete the book isbn links
            count = deleteBooksWithUndo(bookIds, filter)
        }

        return count
    }
}
