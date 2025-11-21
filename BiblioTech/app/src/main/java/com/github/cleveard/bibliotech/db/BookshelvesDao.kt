package com.github.cleveard.bibliotech.db

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.github.cleveard.bibliotech.db.BookDatabase.Companion.selectByFlagBits
import com.github.cleveard.bibliotech.db.BookDatabase.Companion.selectVisible
import com.github.cleveard.bibliotech.db.TagDao.Companion.selectedIdSubQuery
import java.lang.StringBuilder

const val BOOKSHELVES_TABLE = "bookshelves"
const val BOOKSHELVES_ID_COLUMN = "bookshelves_id"
const val BOOKSHELVES_BOOKSHELF_ID_COLUMN = "bookshelves_bookshelf_id"
const val BOOKSHELVES_TITLE_COLUMN = "bookshelves_title"
const val BOOKSHELVES_DESCRIPTION_COLUMN = "bookshelves_description"
const val BOOKSHELVES_SELF_LINK_COLUMN = "bookshelves_self_link"
const val BOOKSHELVES_MODIFIED_COLUMN = "bookshelves_modified"
const val BOOKSHELVES_BOOKS_MODIFIED_COLUMN = "bookshelves_books_modified"
const val BOOKSHELVES_BOOKS_LAST_UPDATE_COLUMN = "bookshelves_books_last_update"
const val BOOKSHELVES_TAG_ID_COLUMN = "bookshelves_tag_id"
const val BOOKSHELVES_FLAG_COLUMN = "bookshelves_flag"

/**
 * Entity for a bookshelf
 */
@Entity(tableName = BOOKSHELVES_TABLE,
    indices = [
        Index(value = [BOOKSHELVES_ID_COLUMN], unique = true),
        Index(value = [BOOKSHELVES_BOOKSHELF_ID_COLUMN]),
        Index(value = [BOOKSHELVES_TAG_ID_COLUMN])
    ],
    foreignKeys = [
        ForeignKey(entity = BookshelfEntity::class,
            parentColumns = [BOOKSHELVES_ID_COLUMN],
            childColumns = [BOOKSHELVES_TAG_ID_COLUMN],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class BookshelfEntity (
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOKSHELVES_ID_COLUMN) var id: Long,
    @ColumnInfo(name = BOOKSHELVES_BOOKSHELF_ID_COLUMN, defaultValue = "0") var bookshelfId: Int,
    @ColumnInfo(name = BOOKSHELVES_TITLE_COLUMN, defaultValue = "") var title: String,
    @ColumnInfo(name = BOOKSHELVES_DESCRIPTION_COLUMN, defaultValue = "") var description: String,
    @ColumnInfo(name = BOOKSHELVES_SELF_LINK_COLUMN, defaultValue = "") var selfLink: String,
    @ColumnInfo(name = BOOKSHELVES_MODIFIED_COLUMN, defaultValue = "0") var modified: Long,
    @ColumnInfo(name = BOOKSHELVES_BOOKS_MODIFIED_COLUMN, defaultValue = "0") var booksModified: Long,
    @ColumnInfo(name = BOOKSHELVES_BOOKS_LAST_UPDATE_COLUMN, defaultValue = "0") var booksLastUpdate: Long,
    @ColumnInfo(name = BOOKSHELVES_TAG_ID_COLUMN, defaultValue = "NULL") var tagId: Long?,
    @ColumnInfo(name = BOOKSHELVES_FLAG_COLUMN, defaultValue = "0") var flags: Int
) {
    companion object {
        const val HIDDEN = 1
        const val PRESERVE = 0
    }
}

/**
 * Bookshelf entity with associated tag entity
 */
data class BookshelfAndTag(
    @Embedded val bookshelf: BookshelfEntity,
    @Relation(
        entity = TagEntity::class,
        parentColumn = BOOKSHELVES_TAG_ID_COLUMN,
        entityColumn = TAGS_ID_COLUMN
    )
    var tag: TagEntity?
)

@Dao
abstract class BookshelvesDao(private val db: BookDatabase) {
    /**
     * Get the paging source for all bookshelves
     */
    @Transaction
    @Query(value = "SELECT * FROM $BOOKSHELVES_TABLE WHERE ( ( $BOOKSHELVES_FLAG_COLUMN & ${BookshelfEntity.HIDDEN} ) = 0 ) ORDER BY $BOOKSHELVES_TITLE_COLUMN")
    abstract fun getShelvesAndTags(): PagingSource<Int, BookshelfAndTag>

    /**
     * Get the paging source for all bookshelves
     */
    @Transaction
    @Query(value = "SELECT * FROM $BOOKSHELVES_TABLE WHERE ( ( $BOOKSHELVES_FLAG_COLUMN & ${BookshelfEntity.HIDDEN} ) = 0 AND $BOOKSHELVES_ID_COLUMN == :bookshelfId ) LIMIT 1")
    abstract suspend fun getShelfAndTag(bookshelfId: Long): BookshelfAndTag?

    /**
     * Get a LiveData of the list of bookshelves ordered by name
     */
    @Transaction
    @Query(value = "SELECT * FROM $BOOKSHELVES_TABLE WHERE ( ( $BOOKSHELVES_FLAG_COLUMN & ${BookshelfEntity.HIDDEN} ) = 0 ) ORDER BY $BOOKSHELVES_TITLE_COLUMN")
    protected abstract fun getShelvesAndTagsLive(): LiveData<List<BookshelfAndTag>>

    @Transaction
    @Query("SELECT * FROM $BOOKSHELVES_TABLE WHERE ( ( $BOOKSHELVES_FLAG_COLUMN & ${BookshelfEntity.HIDDEN} ) = 0 )")
    abstract suspend fun getShelves(): List<BookshelfAndTag>

    /**
     * Add a new bookshelf to the database
     * @param bookshelfEntity The bookshelf
     * @return The id for the added bookshelf
     */
    @Insert
    protected abstract suspend fun add(bookshelfEntity: BookshelfEntity): Long

    @Update
    protected abstract suspend fun internalUpdate(bookshelfEntity: BookshelfEntity): Int

    suspend fun updateBookshelf(bookshelf: BookshelfEntity): Boolean {
        return UndoRedoDao.OperationType.CHANGE_TAG.recordUpdate(db.getUndoRedoDao(), bookshelf.id) {
            internalUpdate(bookshelf) > 0
        }
    }

    @RawQuery(observedEntities = [ BookshelfEntity::class ])
    protected abstract suspend fun getShelves(query: SupportSQLiteQuery): List<BookshelfEntity>

    suspend fun clearSelectedTagIds() {
        BookDatabase.buildWhereExpressionForIds(
            BOOKSHELVES_FLAG_COLUMN, BookshelfEntity.HIDDEN, null, // Select visible tags
            BOOKSHELVES_TAG_ID_COLUMN,                           // Column to query
            selectedIdSubQuery,                       // Selected tag ids sub-query
            null,                                     // Ids to delete
            false                                     // Delete ids or not ids
        )?.let {e ->
            val query = SimpleSQLiteQuery("SELECT * FROM $BOOKSHELVES_TABLE ${e.expression}", e.args)
            getShelves(query).forEach { shelf ->
                shelf.tagId = null
                updateBookshelf(shelf)
            }
        }
    }

    /**
     * Find a bookshelf entity using the bookshelf id
     * @param bookshelfId The bookshelf id
     * @return The BookshelfEntity or null if not found
     */
    @Query("SELECT * FROM $BOOKSHELVES_TABLE WHERE ( $BOOKSHELVES_BOOKSHELF_ID_COLUMN = :bookshelfId ) AND ( ( $BOOKSHELVES_FLAG_COLUMN & ${BookshelfEntity.HIDDEN} ) = 0 ) LIMIT 1")
    abstract suspend fun findBookshelfByBookshelfId(bookshelfId: Int): BookshelfEntity?

    @Transaction
    open suspend fun addWithUndo(bookshelfEntity: BookshelfEntity): Long {
        // If the bookshelf is already in the database, then use it
        (findBookshelfByBookshelfId(bookshelfEntity.bookshelfId)?.also {
            bookshelfEntity.id = it.id
            if (it.title != bookshelfEntity.title
                && !UndoRedoDao.OperationType.CHANGE_BOOKSHELF.recordUpdate(db.getUndoRedoDao(), it.id) {
                    internalUpdate(bookshelfEntity) > 0
                }
            ) {
                bookshelfEntity.id = 0L
            }
        })?: run {
            // Add the new bookshelf entity
            bookshelfEntity.id = 0L
            bookshelfEntity.id = add(bookshelfEntity).also {
                if (it != 0L)
                    UndoRedoDao.OperationType.ADD_BOOKSHELF.recordAdd(db.getUndoRedoDao(), it)
            }
        }
        return bookshelfEntity.id
    }

    /**
     * Delete multiple tags
     */
    @Transaction
    open suspend fun deleteWithUndo(bookshelf: BookshelfAndTag): Int {
        if (bookshelf.bookshelf.id == 0L)
            return 0
        return BookDatabase.buildWhereExpressionForIds(
            BOOKSHELVES_FLAG_COLUMN, BookshelfEntity.HIDDEN, null, // Select visible tags
            BOOKSHELVES_ID_COLUMN,                           // Column to query
            null,                                     // Selected tag ids sub-query
            arrayOf<Any>(bookshelf.bookshelf.id),                // Ids to delete
            false                                     // Delete ids or not ids
        )?.let {e ->
            bookshelf.tag?.let {
                it.hasBookshelf = false
                db.getTagDao().addWithUndo(it) { false }
            }

            UndoRedoDao.OperationType.DELETE_BOOKSHELF.recordDelete(db.getUndoRedoDao(), e) {
                db.setHidden(BookDatabase.bookshelvesTable, e)
            }
        }?: 0
    }

    /**
     * Query to count bits in the flags column
     */
    @RawQuery(observedEntities = [BookshelfEntity::class])
    protected abstract suspend fun countBits(query: SupportSQLiteQuery): Int

    /**
     * Query to count bits in the flags column
     */
    @RawQuery(observedEntities = [BookshelfEntity::class])
    protected abstract fun countBitsLive(query: SupportSQLiteQuery): LiveData<Int>

    /**
     * Query to count bits in the flags column
     * @param bits The bits we are checking
     * @param value The value we are checking
     * @param include True to include values that match. False to exclude values that match.
     * @param id A tag id to count
     * @return The count in a LiveData
     */
    open suspend fun countBits(bits: Int, value: Int, include: Boolean, id: Long?): Int {
        // Build the selection from the bits
        val condition = StringBuilder().selectVisible(BOOKSHELVES_FLAG_COLUMN, BookshelfEntity.HIDDEN)
            .selectByFlagBits(bits, value, include, BOOKSHELVES_FLAG_COLUMN)
            .toString()
        // Return the query
        return countBits(SimpleSQLiteQuery(
            "SELECT COUNT($BOOKSHELVES_ID_COLUMN) FROM $BOOKSHELVES_TABLE$condition"
        ))
    }

    /**
     * Query to count bits in the flags column
     * @param bits The bits we are checking
     * @param value The value we are checking
     * @param include True to include values that match. False to exclude values that match.
     * @param id A tag id to count
     * @return The count in a LiveData
     */
    open fun countBitsLive(bits: Int, value: Int, include: Boolean, id: Long?): LiveData<Int> {
        // Build the selection from the bits
        val condition = StringBuilder().selectVisible(BOOKSHELVES_FLAG_COLUMN, BookshelfEntity.HIDDEN)
            .selectByFlagBits(bits, value, include, BOOKSHELVES_FLAG_COLUMN)
            .toString()
        // Return the query
        return countBitsLive(
            SimpleSQLiteQuery(
                "SELECT COUNT($BOOKSHELVES_ID_COLUMN) FROM $BOOKSHELVES_TABLE$condition"
            )
        )
    }

    /**
     * Change bits in the flags column
     * @param mask The bits to change
     * @param value The value to set
     * @param id The id of the tag to change. Null to change all
     * @return The number of rows changed
     */
    @Transaction
    open suspend fun changeBits(mask: Int, value: Int, id: Long?): Int? {
        val condition = StringBuilder().selectVisible(BOOKSHELVES_FLAG_COLUMN, BookshelfEntity.HIDDEN)
        // Only select the rows where the change will make a difference
        condition.selectByFlagBits(mask, value, false, BOOKSHELVES_FLAG_COLUMN)
        return db.execUpdateDelete(SimpleSQLiteQuery("UPDATE $BOOKSHELVES_TABLE SET $BOOKSHELVES_FLAG_COLUMN = ( $BOOKSHELVES_FLAG_COLUMN & ${mask.inv()} ) | $value$condition"))
    }

    /**
     * Change bits in the flags column
     * @param operation The operation to perform. True to set, false to clear, null to toggle
     * @param mask The bits to change
     * @param id The id of the tag to change. Null to change all
     * @return The number of rows changed
     */
    @Transaction
    open suspend fun changeBits(operation: Boolean?, mask: Int, id: Long?): Int {
        val condition = StringBuilder().selectVisible(BOOKSHELVES_FLAG_COLUMN, BookshelfEntity.HIDDEN)
        // Only select the rows where the change will make a difference
        if (operation == true)
            condition.selectByFlagBits(mask, mask, false, BOOKSHELVES_FLAG_COLUMN)
        else if (operation == false)
            condition.selectByFlagBits(mask, 0, false, BOOKSHELVES_FLAG_COLUMN)
        val bits = BookDatabase.changeBits(operation, BOOKSHELVES_FLAG_COLUMN, mask)
        return db.execUpdateDelete(SimpleSQLiteQuery("UPDATE $BOOKSHELVES_TABLE $bits$condition"))
    }
}
