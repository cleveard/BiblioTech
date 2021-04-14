package com.github.cleveard.bibliotech.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.github.cleveard.bibliotech.db.BookDatabase.Companion.idWithFilter
import com.github.cleveard.bibliotech.db.BookDatabase.Companion.selectByFlagBits
import com.github.cleveard.bibliotech.db.BookDatabase.Companion.selectVisible
import com.github.cleveard.bibliotech.utils.Thumbnails
import kotlinx.coroutines.*
import java.util.*
import java.lang.StringBuilder
import kotlin.collections.ArrayList

// Book table name and its column names
const val BOOK_TABLE = "books"                          // Book table name
const val BOOK_ID_COLUMN = "books_id"                   // Incrementing id
const val VOLUME_ID_COLUMN = "books_volume_id"          // Volume id from book source site
const val SOURCE_ID_COLUMN = "books_source_id"          // Id of the site where the book data came from
const val ISBN_COLUMN = "books_isbn"                    // ISBN
const val TITLE_COLUMN = "books_title"                  // Title
const val SUBTITLE_COLUMN = "books_subtitle"            // Subtitle
const val DESCRIPTION_COLUMN = "books_description"      // Description
const val PAGE_COUNT_COLUMN = "books_page_count"        // Page count
const val BOOK_COUNT_COLUMN = "books_count"             // Number of books being tracked
const val VOLUME_LINK = "books_volume_link"             // Link to book on the web
const val RATING_COLUMN = "books_rating"                // Rating of the book
const val DATE_ADDED_COLUMN = "books_date_added"        // Date the book was added
const val DATE_MODIFIED_COLUMN = "books_date_modified"  // Date the book was last modified
const val SMALL_THUMB_COLUMN = "books_small_thumb"      // Link to small thumbnail of book cover
const val LARGE_THUMB_COLUMN = "books_large_thumb"      // Link to large thumbnail of book cover
const val BOOK_FLAGS = "books_flags"                    // Flags for the book
val ALL_BOOK_COLUMNS = arrayOf(                         // Array of all book columns, used building queries
    BOOK_ID_COLUMN, VOLUME_ID_COLUMN, SOURCE_ID_COLUMN,
    ISBN_COLUMN, TITLE_COLUMN, SUBTITLE_COLUMN,
    DESCRIPTION_COLUMN, PAGE_COUNT_COLUMN, BOOK_COUNT_COLUMN,
    VOLUME_LINK, RATING_COLUMN, DATE_ADDED_COLUMN,
    DATE_MODIFIED_COLUMN, SMALL_THUMB_COLUMN, LARGE_THUMB_COLUMN, BOOK_FLAGS)

// Type convert to convert between dates in the data base, which are longs, and Date objects
class DateConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

/**
 * Room Entity for the Books table
 */
@TypeConverters(DateConverters::class)
@Entity(tableName = BOOK_TABLE,
    indices = [
        Index(value = [BOOK_ID_COLUMN],unique = true),
        Index(value = [VOLUME_ID_COLUMN, SOURCE_ID_COLUMN]),
        Index(value = [ISBN_COLUMN])
    ])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOK_ID_COLUMN) var id: Long,
    @ColumnInfo(name = VOLUME_ID_COLUMN) var volumeId: String?,
    @ColumnInfo(name = SOURCE_ID_COLUMN) var sourceId: String?,
    @ColumnInfo(name = ISBN_COLUMN) var ISBN: String?,
    @ColumnInfo(name = TITLE_COLUMN,defaultValue = "") var title: String,
    @ColumnInfo(name = SUBTITLE_COLUMN,defaultValue = "") var subTitle: String,
    @ColumnInfo(name = DESCRIPTION_COLUMN,defaultValue = "") var description: String,
    @ColumnInfo(name = PAGE_COUNT_COLUMN,defaultValue = "0") var pageCount: Int,
    @ColumnInfo(name = BOOK_COUNT_COLUMN,defaultValue = "1") var bookCount: Int,
    @ColumnInfo(name = VOLUME_LINK,defaultValue = "") var linkUrl: String,
    @ColumnInfo(name = RATING_COLUMN,defaultValue = "-1.0") var rating: Double,
    @ColumnInfo(name = DATE_ADDED_COLUMN,defaultValue = "0") var added: Date,
    @ColumnInfo(name = DATE_MODIFIED_COLUMN,defaultValue = "0") var modified: Date,
    @ColumnInfo(name = SMALL_THUMB_COLUMN) var smallThumb: String?,
    @ColumnInfo(name = LARGE_THUMB_COLUMN) var largeThumb: String?,
    @ColumnInfo(name = BOOK_FLAGS,defaultValue = "0") var flags: Int
) {
    var isSelected: Boolean
        get() = ((flags and SELECTED) != 0)
        set(v) {
            flags = if (v)
                flags or SELECTED
            else
                flags and SELECTED.inv()
        }

    var isExpanded: Boolean
        get() = ((flags and EXPANDED) != 0)
        set(v) {
            flags = if (v)
                flags or EXPANDED
            else
                flags and EXPANDED.inv()
        }

    companion object {
        const val SELECTED = 1
        const val EXPANDED = 2
        const val HIDDEN = 4
    }
}

/**
 * Selectable Book object with authors, tags and categories
 */
data class BookAndAuthors(
    @Embedded var book: BookEntity,
    @Relation(
        entity = AuthorEntity::class,
        parentColumn = BOOK_ID_COLUMN,
        entityColumn = AUTHORS_ID_COLUMN,
        associateBy = Junction(
            BookAndAuthorEntity::class,
            parentColumn = BOOK_AUTHORS_BOOK_ID_COLUMN,
            entityColumn = BOOK_AUTHORS_AUTHOR_ID_COLUMN
        )
    )
    var authors: List<AuthorEntity>,
    @Relation(
        entity = CategoryEntity::class,
        parentColumn = BOOK_ID_COLUMN,
        entityColumn = CATEGORIES_ID_COLUMN,
        associateBy = Junction(
            BookAndCategoryEntity::class,
            parentColumn = BOOK_CATEGORIES_BOOK_ID_COLUMN,
            entityColumn = BOOK_CATEGORIES_CATEGORY_ID_COLUMN
        )
    )
    var categories: List<CategoryEntity>,
    @Relation(
        entity = TagEntity::class,
        parentColumn = BOOK_ID_COLUMN,
        entityColumn = TAGS_ID_COLUMN,
        associateBy = Junction(
            BookAndTagEntity::class,
            parentColumn = BOOK_TAGS_BOOK_ID_COLUMN,
            entityColumn = BOOK_TAGS_TAG_ID_COLUMN
        )
    )
    var tags: List<TagEntity>,
    // Columns used when sorting by related field. These are used to
    // Construct the header for the list.
    @ColumnInfo(name= REMAINING_COLUMN) var sortFirst: String? = null,
    @ColumnInfo(name= LAST_NAME_COLUMN) var sortLast: String? = null,
    @ColumnInfo(name= TAGS_NAME_COLUMN) var sortTag: String? = null,
    @ColumnInfo(name= CATEGORY_COLUMN) var sortCategory: String? = null
) : Parcelable {
    /**
     * @inheritDoc
     *
     * Override to exclude sortFirst, sortLast, sortTag and sortCategory from equals
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookAndAuthors

        if (book != other.book) return false
        if (authors != other.authors) return false
        if (categories != other.categories) return false
        if (tags != other.tags) return false

        return true
    }

    /**
     * @inheritDoc
     *
     * Override to exclude sortFirst, sortLast, sortTag and sortCategory from hash
     */
    override fun hashCode(): Int {
        var result = book.hashCode()
        result = 31 * result + authors.hashCode()
        result = 31 * result + categories.hashCode()
        result = 31 * result + tags.hashCode()
        return result
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        writeBook(dest, book)
        dest.writeInt(authors.size)
        for (i in authors) {
            writeAuthor(dest, i)
        }
        dest.writeInt(categories.size)
        for (i in categories) {
            writeCategory(dest, i)
        }
        dest.writeInt(tags.size)
        for (i in tags) {
            writeTag(dest, i)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    private fun writeBook(dest: Parcel, book: BookEntity) {
        dest.writeLong(book.id)
        dest.writeString(book.volumeId)
        dest.writeString(book.sourceId)
        dest.writeString(book.ISBN)
        dest.writeString(book.title)
        dest.writeString(book.subTitle)
        dest.writeString(book.description)
        dest.writeInt(book.pageCount)
        dest.writeInt(book.bookCount)
        dest.writeString(book.linkUrl)
        dest.writeDouble(book.rating)
        dest.writeLong(book.added.time)
        dest.writeLong(book.modified.time)
        dest.writeString(book.smallThumb)
        dest.writeString(book.largeThumb)
        dest.writeInt(book.flags)
    }

    private fun writeAuthor(dest: Parcel, author: AuthorEntity) {
        dest.writeLong(author.id)
        dest.writeString(author.lastName)
        dest.writeString(author.remainingName)
    }

    private fun writeCategory(dest: Parcel, category: CategoryEntity) {
        dest.writeLong(category.id)
        dest.writeString(category.category)
    }

    private fun writeTag(dest: Parcel, tag: TagEntity) {
        dest.writeLong(tag.id)
        dest.writeString(tag.name)
        dest.writeString(tag.desc)
        dest.writeInt(tag.flags)
    }

    companion object {
        @Suppress("unused")
        @JvmField
        val CREATOR = object : Parcelable.Creator<BookAndAuthors> {
            override fun createFromParcel(src: Parcel): BookAndAuthors {
                val book = readBook(src)
                var count = src.readInt()
                val authors = ArrayList<AuthorEntity>(count)
                for (i in 0 until count) {
                    authors.add(readAuthor(src))
                }
                count = src.readInt()
                val categories = ArrayList<CategoryEntity>(count)
                for (i in 0 until count) {
                    categories.add(readCategory(src))
                }
                count = src.readInt()
                val tags = ArrayList<TagEntity>(count)
                for (i in 0 until count) {
                    tags.add(readTag(src))
                }

                return BookAndAuthors(book, authors, categories, tags)
            }

            override fun newArray(size: Int): Array<BookAndAuthors?> {
                return arrayOfNulls(size)
            }
        }

        private fun readBook(src: Parcel): BookEntity {
            val id = src.readLong()
            val volumeId = src.readString()
            val sourceId = src.readString()
            val iSBN = src.readString()
            val title = src.readString()
            val subTitle = src.readString()
            val description = src.readString()
            val pageCount = src.readInt()
            val bookCount = src.readInt()
            val linkUrl = src.readString()
            val rating = src.readDouble()
            val added = Date(src.readLong())
            val modified = Date(src.readLong())
            val smallThumb = src.readString()
            val largeThumb = src.readString()
            val flags = src.readInt()
            return BookEntity(
                id = id,
                volumeId = volumeId,
                sourceId = sourceId,
                ISBN = iSBN,
                title = title?:"",
                subTitle = subTitle?:"",
                description = description?:"",
                pageCount = pageCount,
                bookCount = bookCount,
                linkUrl = linkUrl?:"",
                rating = rating,
                added = added,
                modified = modified,
                smallThumb = smallThumb,
                largeThumb = largeThumb,
                flags = flags
            )
        }

        private fun readAuthor(src: Parcel): AuthorEntity {
            val id = src.readLong()
            val lastName = src.readString()
            val remainingName = src.readString()
            return AuthorEntity(
                id = id,
                lastName = lastName?:"",
                remainingName = remainingName?:""
            )
        }

        private fun readCategory(src: Parcel): CategoryEntity {
            val id = src.readLong()
            val category = src.readString()
            return CategoryEntity(
                id = id,
                category = category?:""
            )
        }

        private fun readTag(src: Parcel): TagEntity {
            val id = src.readLong()
            val name = src.readString()
            val desc = src.readString()
            val flags = src.readInt()
            return TagEntity(
                id = id,
                name = name?:"",
                desc = desc?:"",
                flags = flags
            )
        }
    }
}

@Dao
abstract class BookDao(private val db: BookDatabase) {
    private val thumbnails = Thumbnails()

    /**
     * Add a book to the data base
     * @param book The book to add
     */
    @Insert
    protected abstract suspend fun add(book: BookEntity): Long

    /**
     * Delete books
     * @param bookIds A list of book ids. Null means delete the selected books.
     * @param filter A filter to restrict the book ids
     */
    @Transaction
    protected open suspend fun deleteBooksWithUndo(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): Int {
        return BookDatabase.buildWhereExpressionForIds(
            BOOK_FLAGS, BookEntity.HIDDEN, filter,
            BOOK_ID_COLUMN,
            selectedIdSubQuery,
            bookIds,
            false
        )?.let {e ->
            UndoRedoDao.OperationType.DELETE_BOOK.recordDelete(db.getUndoRedoDao(), e) {
                db.execUpdateDelete(
                    SimpleSQLiteQuery("UPDATE $BOOK_TABLE SET $BOOK_FLAGS = ${BookEntity.HIDDEN}${it.expression}",
                        it.args)
                )
            }
        }?: 0
    }

    /**
     * Class to get the book ids
     */
    protected data class BookId(
        @ColumnInfo(name = BOOK_ID_COLUMN) val id: Long
    )

    /**
     * Query book ids
     * @param query The SQLite query to get the book ids
     */
    @RawQuery(observedEntities = [BookEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<BookId>?

    /**
     * Query book ids
     * @param query The SQLite query to get the book ids
     */
    @RawQuery(observedEntities = [BookEntity::class])
    protected abstract suspend fun queryBookIdCount(query: SupportSQLiteQuery): Int

    /**
     * Query book ids
     * @param bookIds A list of book ids. Null means use selected.
     * @param filter A filter to restrict the book ids
     */
    suspend fun queryBookIds(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_ID_COLUMN FROM $BOOK_TABLE",
            BOOK_FLAGS, BookEntity.HIDDEN, filter,
            BOOK_ID_COLUMN,
            selectedIdSubQuery,
            bookIds,
            false)?.let {query -> queryBookIds(query)?.map { it.id } }
    }

    /**
     * Update a book in the book table
     * @param book The book to update
     */
    @Update
    protected abstract suspend fun update(book: BookEntity): Int

    /**
     * Look for existing book
     * @param query The SQLite query to find the existing book
     */
    @RawQuery
    protected abstract suspend fun findConflict(query: SupportSQLiteQuery): List<BookEntity>?

    /**
     * Find a conflicting book
     * @param volumeId The conflicting volume id
     * @param sourceId The conflicting source id
     * @param ISBN The conflicting ISBN
     */
    private suspend fun findConflict(volumeId: String?, sourceId: String?, ISBN: String?): List<BookEntity>? {
        // If this book doesn't have a volume id or ISBN, Then we can't conflict
        if (volumeId == null && ISBN == null)
            return null
        val args = ArrayList<String?>(3)

        // Build query to look for conflict
        val query = StringBuilder("SELECT * FROM $BOOK_TABLE WHERE (")

        // If there is a volume id, then look for it
        if (volumeId != null) {
            query.append(" ( $VOLUME_ID_COLUMN = ? AND $SOURCE_ID_COLUMN = ? )")
            args.add(volumeId)
            args.add(sourceId)
        }

        // If there is an ISBN look for it
        if (ISBN != null) {
            if (volumeId != null)
                query.append(" OR")
            query.append(" $ISBN_COLUMN = ?")
            args.add(ISBN)
        }

        // Only need one row to conflict
        query.append(" ) AND ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) = 0 )")

        return findConflict(SimpleSQLiteQuery(query.toString(), args.toArray()))
    }

    /**
     * Add or update a book in the data base
     */
    @Transaction
    protected open suspend fun addOrUpdateWithUndo(book: BookEntity, callback: (suspend CoroutineScope.(conflict: List<BookEntity>) -> Boolean)? = null): Long {
        // Assume adding, set the added and modified time to now
        val time = Calendar.getInstance().time

        // If either the volume or source ids are not null
        // make sure both aren't null
        if (book.volumeId != null || book.sourceId != null) {
            book.volumeId = book.volumeId?: ""
            book.sourceId = book.sourceId?: ""
        }

        // Look for a conflict
        val ids = findConflict(book.volumeId, book.sourceId, book.ISBN)
        if (ids.isNullOrEmpty()) {
            // Didn't find a conflict add the book
            book.id = 0
            book.added = time
            book.modified = time
            book.id = add(book)
            if (book.id != 0L)
                UndoRedoDao.OperationType.ADD_BOOK.recordAdd(db.getUndoRedoDao(), book.id)
        } else if (BookDatabase.callConflict(ids, callback, true)) {
            if (ids.size != 1)
                throw SQLiteConstraintException("Multiple conflicting books")
            // Had a conflict, get the id and time added and update the book
            book.id = ids[0].id
            book.added = ids[0].added
            book.modified = time
            if (!UndoRedoDao.OperationType.CHANGE_BOOK.recordUpdate(db.getUndoRedoDao(), book.id) {
                update(book) > 0
            }) {
                book.id = 0L
            }
        } else
            book.id = 0L

        return book.id
    }

    /**
     * Add book with additional tags
     * @param book The book to add
     * @param tagIds The list of tag ids. Null means to use the selected tags.
     * @param callback Callback used to resolve conflicts
     */
    @Transaction
    open suspend fun addOrUpdateWithUndo(book: BookAndAuthors, tagIds: Array<Any>? = null, callback: (suspend CoroutineScope.(conflict: List<BookEntity>) -> Boolean)? = null): Long {
        // Add or update the book
        val id = addOrUpdateWithUndo(book.book, callback)

        if (id != 0L) {
            // Delete existing thumbnails, if any
            invalidateThumbnails(book.book.id)

            // Add categories
            db.getCategoryDao().addWithUndo(book.book.id, book.categories)
            // Add Authors
            db.getAuthorDao().addWithUndo(book.book.id, book.authors)
            // Add Tags from book along with additional tags
            db.getTagDao().addWithUndo(book.book.id, book.tags, tagIds)
        }

        return id
    }

    /**
     * Delete books
     * @param filter A filter to restrict the book ids
     * @param bookIds Optional bookIds to delete. Null means delete selected books
     */
    @Transaction
    open suspend fun deleteSelectedWithUndo(filter: BookFilter.BuiltFilter?, bookIds: Array<Any>?): Int {
        // Delete all tags for the books - keep tags with no books
        db.getBookTagDao().deleteSelectedBooksWithUndo(bookIds, false, filter)
        // Delete all authors for the books - delete authors with no books
        db.getAuthorDao().deleteWithUndo(bookIds, true, filter)
        // Delete all categories for the book - delete categories with no books
        db.getCategoryDao().deleteWithUndo(bookIds, true, filter)

        // Delete all thumbnails
        queryBookIds(bookIds, filter)?.let {
            for (book in it) {
                invalidateThumbnails(book)
            }
        }

        // Finally delete the books
        return deleteBooksWithUndo(bookIds, filter)

    }

    /**
     * Get a book by id
     * @param bookId The id of the book
     * BookAndAuthors contains optional fields that are not in all queries,
     * so ignore CURSOR_MISMATCH warnings
     */
    @Transaction
    @Query(value = "SELECT * FROM $BOOK_TABLE"
            + " WHERE $BOOK_ID_COLUMN = :bookId AND ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) = 0 ) LIMIT 1")
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    abstract suspend fun getBook(bookId: Long): BookAndAuthors?

    /**
     * Get books
     * @param query The SQLite query to get the books
     */
    @RawQuery(observedEntities = [BookAndAuthors::class])
    protected abstract fun getBooks(query: SupportSQLiteQuery): PagingSource<Int, BookAndAuthors>

    @RawQuery(observedEntities = [BookAndAuthors::class])
    protected abstract fun getBookList(query: SupportSQLiteQuery): LiveData<List<BookAndAuthors>>

    /**
     * Get books
     */
    fun getBooks(): PagingSource<Int, BookAndAuthors> {
        return getBooks(SimpleSQLiteQuery("SELECT * FROM $BOOK_TABLE WHERE ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) = 0 )"))
    }

    /**
     * Get books
     * @param filter The filter description used to filter and order the books
     */
    fun getBooks(filter: BookFilter, context: Context): PagingSource<Int, BookAndAuthors> {
        if (filter.orderList.isEmpty() && filter.filterList.isEmpty())
            return getBooks()
        return getBooks(BookFilter.buildFilterQuery(filter, context, BookDatabase.bookTable))
    }

    /**
     * Get books
     */
    suspend fun getBookList(): LiveData<List<BookAndAuthors>> {
        return withContext(db.queryExecutor.asCoroutineDispatcher()) {
            getBookList(SimpleSQLiteQuery("SELECT * FROM $BOOK_TABLE WHERE ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) = 0 ) ORDER BY $BOOK_ID_COLUMN"))
        }
    }

    /**
     * Get books
     * @param filter The filter description used to filter and order the books
     */
    suspend fun getBookList(filter: BookFilter, context: Context): LiveData<List<BookAndAuthors>> {
        if (filter.orderList.isEmpty() && filter.filterList.isEmpty())
            return getBookList()
        return withContext(db.queryExecutor.asCoroutineDispatcher()) {
            getBookList(BookFilter.buildFilterQuery(filter, context, BookDatabase.bookTable))
        }
    }

    /**
     * Get the small thumbnail url for a book
     * @param bookId The book id
     */
    @Query("SELECT $SMALL_THUMB_COLUMN FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = :bookId AND ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) = 0 ) LIMIT 1")
    protected abstract suspend fun getSmallThumbnailUrl(bookId: Long): String?

    /**
     * Get the large thumbnail url for a book
     * @param bookId The book id
     */
    @Query("SELECT $LARGE_THUMB_COLUMN FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = :bookId AND ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) = 0 ) LIMIT 1")
    protected abstract suspend fun getLargeThumbnailUrl(bookId: Long): String?

    /**
     * Get a thumbnail for a book
     * @param bookId The book id
     * @param large True to get the large thumbnail file. False for the small thumbnail file.
     */
    @Transaction
    open suspend fun getThumbnail(bookId: Long, large: Boolean): Bitmap? {
        return thumbnails.getThumbnail(bookId, large) {b, l ->
            if (l) getLargeThumbnailUrl(b) else getSmallThumbnailUrl(b)
        }
    }

    /**
     * Query to count bits in the flags column
     */
    @RawQuery(observedEntities = [BookEntity::class])
    protected abstract suspend fun countBits(query: SupportSQLiteQuery): Int?

    /**
     * Query to count bits in the flags column
     */
    @RawQuery(observedEntities = [BookEntity::class])
    protected abstract fun countBitsLive(query: SupportSQLiteQuery): LiveData<Int?>

    /**
     * Query to count bits in the flags column
     * @param bits The bits we are checking
     * @param value The value we are checking
     * @param include True to include values that match. False to exclude values that match.
     * @param id A book id whose bits are counted
     * @param filter A filter to restrict the rows
     * @return The count
     */
    open suspend fun countBits(bits: Int, value: Int, include: Boolean, id: Long?, filter: BookFilter.BuiltFilter?): Int? {
        val condition = StringBuilder().idWithFilter(id, filter, BOOK_ID_COLUMN)
            .selectVisible(BOOK_FLAGS, BookEntity.HIDDEN)
            .selectByFlagBits(bits, value, include, BOOK_FLAGS)
            .toString()
        return countBits(SimpleSQLiteQuery(
            "SELECT COUNT($BOOK_ID_COLUMN) FROM $BOOK_TABLE$condition", filter?.args
        ))
    }

    /**
     * Query to count bits in the flags column
     * @param bits The bits we are checking
     * @param value The value we are checking
     * @param include True to include values that match. False to exclude values that match.
     * @param id A book id whose bits are counted
     * @param filter A filter to restrict the rows
     * @return The count in a LiveData
     */
    open suspend fun countBitsLive(bits: Int, value: Int, include: Boolean, id: Long?, filter: BookFilter.BuiltFilter?): LiveData<Int?> {
        return withContext(db.queryExecutor.asCoroutineDispatcher()) {
            val condition = StringBuilder().idWithFilter(id, filter, BOOK_ID_COLUMN)
                .selectVisible(BOOK_FLAGS, BookEntity.HIDDEN)
                .selectByFlagBits(bits, value, include, BOOK_FLAGS)
                .toString()
            countBitsLive(SimpleSQLiteQuery(
                "SELECT COUNT($BOOK_ID_COLUMN) FROM $BOOK_TABLE$condition", filter?.args
            ))
        }
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
        val condition = StringBuilder().idWithFilter(id, null, BOOK_ID_COLUMN)
            .selectVisible(BOOK_FLAGS, BookEntity.HIDDEN)
        // Only select the rows where the change will make a difference
        condition.selectByFlagBits(mask, value, false, BOOK_FLAGS)
        return db.execUpdateDelete(SimpleSQLiteQuery("UPDATE $BOOK_TABLE SET $BOOK_FLAGS = ( $BOOK_FLAGS & ${mask.inv()} ) | $value$condition"))
    }

    /**
     * Change bits in the flags column
     * @param operation The operation to perform. True to set, false to clear, null to toggle
     * @param mask The bits to change
     * @param id The id of the tag to change. Null to change all
     * @param filter A filter to restrict the rows
     * @return The number of rows changed
     */
    @Transaction
    open suspend fun changeBits(operation: Boolean?, mask: Int, id: Long?, filter: BookFilter.BuiltFilter?): Int {
        val condition = StringBuilder().idWithFilter(id, filter, BOOK_ID_COLUMN)
            .selectVisible(BOOK_FLAGS, BookEntity.HIDDEN)
        // Only select the rows where the change will make a difference
        if (operation == true)
            condition.selectByFlagBits(mask, mask, false, BOOK_FLAGS)
        else if (operation == false)
            condition.selectByFlagBits(mask, 0, false, BOOK_FLAGS)
        val bits = BookDatabase.changeBits(operation, BOOK_FLAGS, mask)
        return db.execUpdateDelete(SimpleSQLiteQuery("UPDATE $BOOK_TABLE $bits$condition", filter?.args))
    }

    /**
     * Invalidate the thumbnails for a book
     * @param bookId The id of the book
     */
    suspend fun invalidateThumbnails(bookId: Long) {
        thumbnails.deleteThumbFile(bookId, true)
        thumbnails.deleteThumbFile(bookId, false)
    }

    companion object {
        /** Sub-query that returns the selected book ids **/
        val selectedIdSubQuery: (invert: Boolean) -> String = {
            "SELECT $BOOK_ID_COLUMN FROM $BOOK_TABLE WHERE ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) = 0 " +
            "AND ( $BOOK_FLAGS & ${BookEntity.SELECTED} ) ${if (it) "==" else "!="} 0 )"
        }
    }
}