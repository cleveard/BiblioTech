package com.github.cleveard.bibliotech.db

import androidx.room.*
import java.lang.StringBuilder

const val BOOKSHELVES_TABLE = "bookshelves"
const val BOOKSHELVES_ID_COLUMN = "bookshelves_id"
const val BOOKSHELVES_BOOKSHELF_ID_COLUMN = "bookshelves_bookshelf_id"
const val BOOKSHELVES_TITLE_COLUMN = "bookshelves_title"
const val BOOKSHELVES_DESCRIPTION_COLUMN = "bookshelves_description"
const val BOOKSHELVES_SELF_LINK_COLUMN = "bookshelves_self_link"
const val BOOKSHELVES_MODIFIED_COLUMN = "bookshelves_modified"
const val BOOKSHELVES_BOOKS_MODIFIED_COLUMN = "bookshelves_books_modified"
const val BOOKSHELVES_TAG_ID_COLUMN = "bookshelves_tag_id"
const val BOOKSHELVES_FLAG_COLUMN = "bookshelves_flag"

@Entity(tableName = BOOKSHELVES_TABLE,
    indices = [
        Index(value = [BOOKSHELVES_ID_COLUMN], unique = true),
        Index(value = [BOOKSHELVES_BOOKSHELF_ID_COLUMN]),
        Index(value = [BOOKSHELVES_TAG_ID_COLUMN], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = TagEntity::class,
            parentColumns = [TAGS_ID_COLUMN],
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
    @ColumnInfo(name = BOOKSHELVES_TAG_ID_COLUMN, defaultValue = "NULL") var tagId: Long?,
    @ColumnInfo(name = BOOKSHELVES_FLAG_COLUMN, defaultValue = "0") var flags: Int
) {
    companion object {
        const val HIDDEN = 1
        const val PRESERVE = 0
    }
}

@Dao
abstract class BookshelvesDao(private val db: BookDatabase) {
    @Query("SELECT * FROM $BOOKSHELVES_TABLE WHERE (($BOOKSHELVES_FLAG_COLUMN & ${BookshelfEntity.HIDDEN}) = 0)")
    abstract suspend fun get(): List<BookshelfEntity>

    /**
     * Add a new bookshelf to the database
     * @param bookshelfEntity The bookshelf
     * @return The id for the added bookshelf
     */
    @Insert
    protected abstract fun add(bookshelfEntity: BookshelfEntity): Long

    @Update
    protected abstract fun update(bookshelfEntity: BookshelfEntity): Int

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
                    update(bookshelfEntity) > 0
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
}
