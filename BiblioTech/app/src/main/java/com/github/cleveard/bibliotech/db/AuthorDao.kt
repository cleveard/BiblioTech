package com.github.cleveard.bibliotech.db

import android.database.Cursor
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

// Authors table name and its column names
const val AUTHORS_TABLE = "authors"                                 // Authors table name
const val AUTHORS_ID_COLUMN = "authors_id"                          // Incrementing id
const val LAST_NAME_COLUMN = "authors_last_name"                    // Author last name
const val REMAINING_COLUMN = "authors_remaining"                    // First and middle names
const val AUTHORS_FLAGS = "authors_flags"                           // Flags for authors

// Book Authors table name and its column names
const val BOOK_AUTHORS_TABLE = "book_authors"                       // Book Authors table name
const val BOOK_AUTHORS_ID_COLUMN = "book_authors_id"                // Increment id
const val BOOK_AUTHORS_BOOK_ID_COLUMN = "book_authors_book_id"      // Book row incrementing id
const val BOOK_AUTHORS_AUTHOR_ID_COLUMN = "book_authors_author_id"  // Authors row incrementing id

/**
 * Room Entity for the Authors table
 */
@Entity(tableName = AUTHORS_TABLE,
    indices = [
        Index(value = [AUTHORS_ID_COLUMN],unique = true),
        Index(value = [LAST_NAME_COLUMN,REMAINING_COLUMN])
    ])
data class AuthorEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = AUTHORS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = LAST_NAME_COLUMN,defaultValue = "",collate = ColumnInfo.NOCASE) var lastName: String,
    @ColumnInfo(name = REMAINING_COLUMN,defaultValue = "",collate = ColumnInfo.NOCASE) var remainingName: String,
    @ColumnInfo(name = AUTHORS_FLAGS,defaultValue = "0") var flags: Int = 0
) {
    companion object {
        const val HIDDEN = 1
        const val PRESERVE = 0
    }

    /**
     * Construct entity from name
     * @param id The row id of the entity
     * @param in_name The name of the author
     */
    constructor(id: Long, in_name: String): this(id, "", "") {
        name = in_name
    }

    /**
     * The name of the author
     * Combines and separates the name into last and remaining
     */
    var name: String
        get() { return "$remainingName $lastName".trim { it <= ' ' } }
        set(in_name) {
            // Trim whitespace from start and end
            val name = in_name.trim { it <= ' ' }
            // Look for a , assume last, remaining if found
            var lastIndex = name.lastIndexOf(',')
            lastName = name
            remainingName = ""
            if (lastIndex > 0) {
                // Found a comma, last name is before the comma, first name after
                lastName = name.substring(0, lastIndex).trim { it <= ' ' }
                remainingName = name.substring(lastIndex + 1).trim { it <= ' ' }
            } else {
                // look for a space, assume remaining last if found
                lastIndex = name.lastIndexOf(' ')
                if (lastIndex > 0) {
                    lastName = name.substring(lastIndex).trim { it <= ' ' }
                    remainingName = name.substring(0, lastIndex).trim { it <= ' ' }
                }
            }
        }
}

/**
 * Room entity for the book authors table
 */
@Entity(tableName = BOOK_AUTHORS_TABLE,
    foreignKeys = [
        ForeignKey(entity = BookEntity::class,
            parentColumns = [BOOK_ID_COLUMN],
            childColumns = [BOOK_AUTHORS_BOOK_ID_COLUMN],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AuthorEntity::class,
            parentColumns = [AUTHORS_ID_COLUMN],
            childColumns = [BOOK_AUTHORS_AUTHOR_ID_COLUMN],
            onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = [BOOK_AUTHORS_ID_COLUMN],unique = true),
        Index(value = [BOOK_AUTHORS_AUTHOR_ID_COLUMN,BOOK_AUTHORS_BOOK_ID_COLUMN],unique = true),
        Index(value = [BOOK_AUTHORS_BOOK_ID_COLUMN])
    ])
data class BookAndAuthorEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOK_AUTHORS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = BOOK_AUTHORS_AUTHOR_ID_COLUMN) var authorId: Long,
    @ColumnInfo(name = BOOK_AUTHORS_BOOK_ID_COLUMN) var bookId: Long
)

/**
 * The Author data access object
 */
@Dao
abstract class AuthorDao(private val db: BookDatabase) {
    /**
     * Get the list of authors
     */
    @Query(value = "SELECT * FROM $AUTHORS_TABLE WHERE ( ( $AUTHORS_FLAGS & ${AuthorEntity.HIDDEN} ) = 0 ) ORDER BY $LAST_NAME_COLUMN, $REMAINING_COLUMN")
    abstract suspend fun get(): List<AuthorEntity>?

    /**
     * Raw Query for author cursor
     */
    @RawQuery(observedEntities = [AuthorEntity::class])
    abstract fun getCursor(query: SupportSQLiteQuery): Cursor

    /**
     * Add multiple authors for a book
     * @param bookId The id of the book
     * @param authors The list of authors
     * @param deleteAuthors True to delete unused authors
     */
    @Transaction
    open suspend fun addWithUndo(bookId: Long, authors: List<AuthorEntity>, deleteAuthors: Boolean = true) {
        if (deleteAuthors) {
            // Yes, get the ids of the authors that are affects
            val oldAuthors = queryBookIds(arrayOf(bookId), null)
            // Delete the authors
            deleteBooksWithUndo(arrayOf(bookId), null)
            // Add new authors
            for (author in authors)
                addWithUndo(bookId, author)
            oldAuthors?.let {
                // Delete authors with no books
                for (author in it) {
                    if (authors.indexOfFirst {a -> a.id == author } < 0) {
                        val list: List<BookAndAuthorEntity> = findById(author, 1)
                        if (list.isEmpty()) {
                            UndoRedoDao.OperationType.DELETE_AUTHOR.recordDelete(db.getUndoRedoDao(), author) {
                                db.setHidden(BookDatabase.authorsTable, author)
                            }
                        }
                    }
                }
            }
        } else {
            // No, just delete the links
            deleteBooksWithUndo(arrayOf(bookId), null)
            // Add new authors
            for (author in authors)
                addWithUndo(bookId, author)
        }
    }

    /**
     * Add a single author for a book
     * @param bookId The id of the book
     * @param author The author entity
     */
    @Transaction
    open suspend fun addWithUndo(bookId: Long, author: AuthorEntity) {
        // Find the author
        author.lastName = author.lastName.trim { it <= ' ' }
        author.remainingName = author.remainingName.trim { it <= ' ' }
        val list: List<AuthorEntity> = findByName(author.lastName, author.remainingName)
        // Get the author id if it isn't empty, otherwise add the author
        author.id = if (list.isNotEmpty()) {
            list[0].id
        } else {
            author.id = 0
            add(author).also {
                if (it != 0L)
                    UndoRedoDao.OperationType.ADD_AUTHOR.recordAdd(db.getUndoRedoDao(), it)
            }
        }
        // Add the link
        add(BookAndAuthorEntity(0, author.id, bookId))
        UndoRedoDao.OperationType.ADD_BOOK_AUTHOR_LINK.recordLink(db.getUndoRedoDao(), bookId, author.id)
    }

    /**
     * Delete all authors from books
     * @param bookIds A list of book ids. Null means use the selected books
     * @param filter A filter to restrict the book ids
    */
    @Transaction
    protected open suspend fun deleteBooksWithUndo(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): Int {
        return BookDatabase.buildWhereExpressionForIds(
            null, 0, filter,
            BOOK_AUTHORS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false
        )?.let {e ->
            UndoRedoDao.OperationType.DELETE_BOOK_AUTHOR_LINK.recordDelete(db.getUndoRedoDao(), e) {
                db.execUpdateDelete(
                    SimpleSQLiteQuery("DELETE FROM $BOOK_AUTHORS_TABLE${it.expression}",
                        it.args)
                )
            }
        }?: 0
    }

    /**
     * Query authors for books
     * @param query SQLite query to query the authors
     */
    @RawQuery(observedEntities = [BookAndAuthorEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<Long>?

    /**
     * Query author ids for a set of books
     * @param bookIds A list of book ids. Null means use the selected books.
     * @param filter A filter to restrict the book ids
    */
    private suspend fun queryBookIds(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_AUTHORS_AUTHOR_ID_COLUMN FROM $BOOK_AUTHORS_TABLE",
            null, 0, filter,
            BOOK_AUTHORS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false)?.let { queryBookIds(it) }
    }

    /**
     * Find an author by name
     * @param last The author last name
     * @param remaining The rest of the author name
     */
    @Query(value = "SELECT * FROM $AUTHORS_TABLE"
        + " WHERE $LAST_NAME_COLUMN LIKE :last ESCAPE '\\' AND $REMAINING_COLUMN LIKE :remaining ESCAPE '\\' AND ( ( $AUTHORS_FLAGS & ${AuthorEntity.HIDDEN} ) = 0 )")
    abstract suspend fun doFindByName(last: String, remaining: String): List<AuthorEntity>

    /**
     * Find an author by name
     * @param last The author last name
     * @param remaining The rest of the author name
     */
    suspend fun findByName(last: String, remaining: String): List<AuthorEntity> {
        return doFindByName(
            PredicateDataDescription.escapeLikeWildCards(last),
            PredicateDataDescription.escapeLikeWildCards(remaining)
        )
    }

    /**
     * Get the book author links for an author id
     * @param authorId The author id
     * @param limit A flag to limit the number of author links returned
     */
    @Query("SELECT * FROM $BOOK_AUTHORS_TABLE WHERE $BOOK_AUTHORS_AUTHOR_ID_COLUMN = :authorId LIMIT :limit")
    abstract suspend fun findById(authorId: Long, limit: Int = -1): List<BookAndAuthorEntity>

    /**
     * Add an author
     * @param author The author to be added
     */
    @Insert
    protected abstract suspend fun add(author: AuthorEntity) : Long

    /**
     * Add a book and author relationship
     * @param bookAndAuthor The link to be added
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun add(bookAndAuthor: BookAndAuthorEntity): Long

    /**
     * Delete all authors from books
     * @param bookIds A list of book ids. Null means use the selected books.
     * @param deleteAuthors A flag to indicate whether the author in the Authors table should be deleted
     *                      if all of its books have been deleted
     * @param filter A filter to restrict the book ids
     * @return The number of book-author links deleted
     */
    @Transaction
    open suspend fun deleteWithUndo(bookIds: Array<Any>?, deleteAuthors: Boolean = true, filter: BookFilter.BuiltFilter? = null): Int {
        // Do we want to delete authors with no books?
        val count: Int
        if (deleteAuthors) {
            // Yes, get the ids of the authors that are affects
            val authors = queryBookIds(bookIds, filter)
            // Delete the authors
            count = deleteBooksWithUndo(bookIds, filter)
            authors?.let {
                // Delete authors with no books
                for (author in it) {
                    val list: List<BookAndAuthorEntity> = findById(author, 1)
                    if (list.isEmpty()) {
                        UndoRedoDao.OperationType.DELETE_AUTHOR.recordDelete(db.getUndoRedoDao(), author) {
                            db.setHidden(BookDatabase.authorsTable, author)
                        }
                    }
                }
            }
        } else {
            // No, just delete the links
            count = deleteBooksWithUndo(bookIds, filter)
        }

        return count
    }
}
