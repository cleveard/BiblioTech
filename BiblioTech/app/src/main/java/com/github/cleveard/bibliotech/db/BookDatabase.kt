package com.github.cleveard.bibliotech.db

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.github.cleveard.bibliotech.db.BookDatabase.Companion.idWithFilter
import com.github.cleveard.bibliotech.db.BookDatabase.Companion.selectByFlagBits
import com.github.cleveard.bibliotech.utils.Thumbnails
import kotlinx.coroutines.*
import java.io.*
import java.lang.Exception
import java.util.*
import java.lang.StringBuilder
import kotlin.collections.ArrayList

// Database column and table names. These are public to allow the
// BookFilter class to access them.

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

// Categories table name and its column names
const val CATEGORIES_TABLE = "categories"               // Categories table name
const val CATEGORIES_ID_COLUMN = "categories_id"        // Incrementing id
const val CATEGORY_COLUMN = "categories_category"       // Category name
const val CATEGORIES_FLAGS = "categories_flags"         // Flags for categories

// Book Categories table name and its column names
const val BOOK_CATEGORIES_TABLE = "book_categories"                         // Book Categories table names
const val BOOK_CATEGORIES_ID_COLUMN = "book_categories_id"                  // Incrementing id
const val BOOK_CATEGORIES_BOOK_ID_COLUMN = "book_categories_book_id"        // Book row incrementing id
const val BOOK_CATEGORIES_CATEGORY_ID_COLUMN = "book_categories_category_id"// Category row incrementing id

// Tags table and its column names
const val TAGS_TABLE = "tags"                               // Tags table name
const val TAGS_ID_COLUMN = "tags_id"                        // Incrementing id
const val TAGS_NAME_COLUMN = "tags_name"                    // Tag name
const val TAGS_DESC_COLUMN = "tags_desc"                    // Tag description
const val TAGS_FLAGS = "tags_flags"                         // Flags for a tag

// Book Tags table name and column names
const val BOOK_TAGS_TABLE = "book_tags"                     // Book tags table name
const val BOOK_TAGS_ID_COLUMN = "book_tags_id"              // Incrementing id
const val BOOK_TAGS_BOOK_ID_COLUMN = "book_tags_book_id"    // Book row incrementing id
const val BOOK_TAGS_TAG_ID_COLUMN = "book_tags_tag_id"      // Tag row incrementing id

// Views Table name and column names
const val VIEWS_TABLE = "views"                         // Views table name
const val VIEWS_ID_COLUMN = "views_id"                  // Incrementing id
const val VIEWS_NAME_COLUMN = "views_name"              // View name
const val VIEWS_DESC_COLUMN = "views_desc"              // View description
const val VIEWS_FILTER_COLUMN = "views_filter"          // View filter
const val VIEWS_FLAGS = "views_flags"                   // Flags for Views

// Extensions for thumbnail files in the cache.
const val kSmallThumb = ".small.png"
const val kThumb = ".png"

// SQL names for descending and ascending order
const val kDesc = "DESC"
const val kAsc = "ASC"

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

// Type convert to convert between filters and strings
class FilterConverters {
    @TypeConverter
    fun filterFromString(value: String?): BookFilter? {
        return try {
            BookFilter.decodeFromString(value)
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun filterToString(value: BookFilter?): String? {
        return try {
            BookFilter.encodeToString(value)
        } catch (e: Exception) {
            null
        }
    }
}

private suspend fun <T> callConflict(conflict: T, callback: (suspend CoroutineScope.(T) -> Boolean)?, nullReturn: Boolean = false): Boolean {
    return coroutineScope {
        callback?.let { it(conflict) }?: nullReturn
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
    }

    constructor(id: Long, in_name: String): this(id, "", "") {
        name = in_name
    }

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
            onDelete = CASCADE),
        ForeignKey(entity = AuthorEntity::class,
            parentColumns = [AUTHORS_ID_COLUMN],
            childColumns = [BOOK_AUTHORS_AUTHOR_ID_COLUMN],
            onDelete = CASCADE)
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
 * Room entity for the categories table
 */
@Entity(tableName = CATEGORIES_TABLE,
    indices = [
        Index(value = [CATEGORIES_ID_COLUMN],unique = true),
        Index(value = [CATEGORY_COLUMN])
    ])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = CATEGORIES_ID_COLUMN) var id: Long,
    @ColumnInfo(name = CATEGORY_COLUMN,defaultValue = "",collate = ColumnInfo.NOCASE) var category: String,
    @ColumnInfo(name = CATEGORIES_FLAGS,defaultValue = "0") var flags: Int = 0
) {
    companion object {
        const val HIDDEN = 1
    }
}

/**
 * Room entity for the book categories table
 */
@Entity(tableName = BOOK_CATEGORIES_TABLE,
    foreignKeys = [
        ForeignKey(entity = BookEntity::class,
            parentColumns = [BOOK_ID_COLUMN],
            childColumns = [BOOK_CATEGORIES_BOOK_ID_COLUMN],
            onDelete = CASCADE),
        ForeignKey(entity = CategoryEntity::class,
            parentColumns = [CATEGORIES_ID_COLUMN],
            childColumns = [BOOK_CATEGORIES_CATEGORY_ID_COLUMN],
            onDelete = CASCADE)
    ],
    indices = [
        Index(value = [BOOK_CATEGORIES_ID_COLUMN],unique = true),
        Index(value = [BOOK_CATEGORIES_CATEGORY_ID_COLUMN,BOOK_CATEGORIES_BOOK_ID_COLUMN],unique = true),
        Index(value = [BOOK_CATEGORIES_BOOK_ID_COLUMN])
    ])
data class BookAndCategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOK_CATEGORIES_ID_COLUMN) var id: Long,
    @ColumnInfo(name = BOOK_CATEGORIES_CATEGORY_ID_COLUMN) var categoryId: Long,
    @ColumnInfo(name = BOOK_CATEGORIES_BOOK_ID_COLUMN) var bookId: Long
)

/**
 * Room entity for the tags table
 */
@Entity(tableName = TAGS_TABLE,
    indices = [
        Index(value = [TAGS_ID_COLUMN],unique = true),
        Index(value = [TAGS_NAME_COLUMN])
    ])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TAGS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = TAGS_NAME_COLUMN,collate = ColumnInfo.NOCASE) var name: String,
    @ColumnInfo(name = TAGS_DESC_COLUMN,defaultValue = "") var desc: String,
    @ColumnInfo(name = TAGS_FLAGS,defaultValue = "0") var flags: Int
) {
    var isSelected: Boolean
        get() = ((flags and SELECTED) != 0)
        set(v) {
            flags = if (v)
                flags or SELECTED
            else
                flags and SELECTED.inv()
        }

    companion object {
        const val SELECTED = 1
        const val HIDDEN = 2
    }
}

/**
 * Entity for the Book Tags table
 */
@Entity(tableName = BOOK_TAGS_TABLE,
    foreignKeys = [
        ForeignKey(entity = BookEntity::class,
            parentColumns = [BOOK_ID_COLUMN],
            childColumns = [BOOK_TAGS_BOOK_ID_COLUMN],
            onDelete = CASCADE),
        ForeignKey(entity = TagEntity::class,
            parentColumns = [TAGS_ID_COLUMN],
            childColumns = [BOOK_TAGS_TAG_ID_COLUMN],
            onDelete = CASCADE)
    ],
    indices = [
        Index(value = [BOOK_TAGS_ID_COLUMN],unique = true),
        Index(value = [BOOK_TAGS_BOOK_ID_COLUMN,BOOK_TAGS_TAG_ID_COLUMN],unique = true),
        Index(value = [BOOK_TAGS_TAG_ID_COLUMN])
    ])
data class BookAndTagEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOK_TAGS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = BOOK_TAGS_TAG_ID_COLUMN) var tagId: Long,
    @ColumnInfo(name = BOOK_TAGS_BOOK_ID_COLUMN) var bookId: Long
)

/**
 * Room entity for the views table
 */
@TypeConverters(FilterConverters::class)
@Entity(tableName = VIEWS_TABLE,
    indices = [
        Index(value = [VIEWS_ID_COLUMN],unique = true),
        Index(value = [VIEWS_NAME_COLUMN])
    ])
data class ViewEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = VIEWS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = VIEWS_NAME_COLUMN,collate = ColumnInfo.NOCASE) var name: String,
    @ColumnInfo(name = VIEWS_DESC_COLUMN,defaultValue = "") var desc: String,
    @ColumnInfo(name = VIEWS_FILTER_COLUMN) var filter: BookFilter? = null,
    @ColumnInfo(name = VIEWS_FLAGS) var flags: Int = 0
) {
    companion object {
        const val HIDDEN = 1
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

/**
 * Room data access object for the tags table
 */
@Dao
abstract class TagDao(private val db: BookDatabase) {
    /**
     * Get a tag using its incrementing id
     * @param tagId The id of the tag to retrieve
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN = :tagId LIMIT 1")
    abstract suspend fun get(tagId: Long): TagEntity?

    /**
     * Get the list of all tags
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE ORDER BY $TAGS_NAME_COLUMN")
    abstract fun get(): PagingSource<Int, TagEntity>

    /**
     * Get a LiveData of the list of tags ordered by name
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE ORDER BY $TAGS_NAME_COLUMN")
    protected abstract fun doGetLive(): LiveData<List<TagEntity>>

    /**
     * Get a LiveData of the list of tags ordered by name
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE WHERE ( $TAGS_FLAGS & ${TagEntity.SELECTED} ) != 0 ORDER BY $TAGS_NAME_COLUMN")
    protected abstract fun doGetSelectedLive(): LiveData<List<TagEntity>>

    /**
     * Get a LiveData of the list of tags ordered by name
     * @param selected True to get the selected tags. False to get all tags
     */
    suspend fun getLive(selected: Boolean = false): LiveData<List<TagEntity>> {
        return withContext(db.queryExecutor.asCoroutineDispatcher()) {
            if (selected)
                doGetSelectedLive()
            else
                doGetLive()
        }
    }

    /**
     * Add a tag
     * @param tag The tag to be added
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun internalAdd(tag: TagEntity): Long

    /**
     * Update a tag
     * @param tag The tag to be updated
     */
    @Update(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun internalUpdate(tag: TagEntity): Int

    /**
     * Add multiple tags for a book
     * @param bookId The id of the book
     * @param tags A list of possibly new tags for the book. Null means include selected tags
     * @param tagIds An optional list of tag ids. Null means to use the selected tags
     */
    @Transaction
    open suspend fun add(bookId: Long, tags: List<TagEntity>, tagIds: Array<Any>?) {
        // Add or find all of the tags
        for (tag in tags) {
            tag.id = findByName(tag.name)?.id ?: add(tag)
        }

        // Build a set of all tag ids for the book
        val set = HashSet<Any>()
        // Add ids from tagIds
        tagIds?.toCollection(set)?: db.getTagDao().queryTagIds(null)?.toCollection(set)
        // Add ids from tags
        tags.mapTo(set) { it.id }
        val list = set.toArray()

        // Delete the tags that we don't want to keep
        db.getBookTagDao().deleteTagsForBooks(arrayOf(bookId), null, list, true)

        // Add the tags that we want
        val tagList = db.getTagDao().queryTagIds(list)
        db.getBookTagDao().addTagsToBook(bookId, tagList)
    }

    @Transaction
    /**
     * Add/update a tag and resolve conflict
     * @param tag The tag to be added
     * @param callback A callback used to ask what to do if the tag already exists
     * This is also be used to rename existing tags. If the renamed tag conflicts with an
     * existing tag, then we will merge the books for the two tags into a single tag
     */
    open suspend fun add(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)? = null): Long {
        // Empty tag is not accepted
        tag.name = tag.name.trim { it <= ' ' }
        if (tag.name.isEmpty())
            return 0
        // See if there is a conflicting tag with the same name.
        val conflict = findByName(tag.name)
        if (conflict != null && conflict.id != tag.id) {
            // Yep, ask caller what to do, null or false return means do nothing
            if (!callConflict(conflict, callback))
                return 0L

            // If the tag.id is 0, then this is a new tag. Replace the existing tag with the new one
            if (tag.id == 0L) {
                tag.id = conflict.id
            } else {
                // We will update the tag with the fewest books
                // Get the list of book-tag links for tag and conflict
                val tagBooks = db.getBookTagDao().queryTags(arrayOf(tag.id))
                val tagCount = tagBooks?.size?: 0
                val conflictBooks = db.getBookTagDao().queryTags(arrayOf(conflict.id))
                val conflictCount = conflictBooks?.size?: 0
                var addBooks = tagBooks     // Assume we will update the books for tag
                val set = HashSet<Long>()   // Use this to check for duplicates
                if (conflictCount > tagCount) {
                    // Convert tag links to conflict
                    // First delete original links
                    if (tagCount > 0)
                        db.getBookTagDao().deleteTagsForTags(arrayOf(tag.id))
                    // Delete the original tag
                    delete(tag.id)
                    // Set the tag id to the conflict id
                    tag.id = conflict.id
                    // Keep track of books conflict id is already used
                    set.addAll(conflictBooks!!.map { it.bookId })
                } else {
                    // Convert conflict links to tag link
                    addBooks = conflictBooks
                    // Delete the existing links for conflict
                    if (conflictCount > 0)
                        db.getBookTagDao().deleteTagsForTags(arrayOf(conflict.id))
                    // Delete the conflicting tag
                    delete(conflict.id)
                    // Keep tack of book tag id is already used
                    tagBooks?.let {list -> set.addAll(list.map { it.bookId }) }
                }

                // For all of the books that we need to add
                addBooks?.let {books ->
                    val list = ArrayList<Any>()     // keep track of book-tag links we need to delete
                    for (book in books) {
                        // If the book isn't already tagged, then tag it with the new id
                        if (!set.contains(book.bookId)) {
                            book.tagId = tag.id
                            db.getBookTagDao().add(book)
                        } else {
                            // Delete this book-tag link
                            list.add(book.id)
                        }
                    }

                    // Delete all of the links we kept from above
                    if (list.isNotEmpty())
                        db.getBookTagDao().deleteById(list.toArray())
                }
            }
        }

        // Update or add the tag
        if (tag.id == 0L)
            tag.id = internalAdd(tag)
        else if (internalUpdate(tag) <= 0)
            tag.id = 0L
        return tag.id
    }

    @Transaction
    open suspend fun copy(bookId: Long): Long {
        return 0L
    }

    // Class used to query just the tag id for a tag
    protected data class TagId(
        @ColumnInfo(name = TAGS_ID_COLUMN) val id: Long
    )

    /**
     * Query Tag ids for a list of tags
     * @param query SQLite query used to get the tag ids
     */
    @RawQuery(observedEntities = [TagEntity::class])
    protected abstract suspend fun queryTagIds(query: SupportSQLiteQuery): List<TagId>?

    /**
     * Query Tag ids for a list of tags
     * @param tagIds A list of tag ids. Null means selected tags.
     */
    suspend fun queryTagIds(tagIds: Array<Any>?): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $TAGS_ID_COLUMN FROM $TAGS_TABLE",  // SQLite command
            TAGS_ID_COLUMN,                                       // Column to query
            selectedIdSubQuery,                                   // Selected tag ids sub-query
            tagIds,                                               // Ids to query
            false                                                 // Query for ids, or not ids
        )?.let {query -> queryTagIds(query)?.map { it.id } }
    }

    /**
     * Count the number of rows for a query
     * @param query The delete query
     */
    @RawQuery(observedEntities = [TagEntity::class])
    protected abstract suspend fun queryTagCount(query: SupportSQLiteQuery): Int?

    /**
     * Delete a tag using the tag id
     * @param tagId The tag id to delete
     */
    @Query("DELETE FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN = :tagId")
    abstract suspend fun delete(tagId: Long): Int

    /**
     * Query the number of tags
     * @return The number of tags
     */
    @Transaction
    open suspend fun querySelectedTagCount(): Int {
        return BookDatabase.buildQueryForIds(
            "SELECT COUNT($TAGS_ID_COLUMN) FROM $TAGS_TABLE",      // SQLite Command
            TAGS_ID_COLUMN,                           // Column to query
            selectedIdSubQuery,                       // Selected tag ids sub-query
            null,                                     // Ids to delete
            false                                     // Delete ids or not ids
        )?.let { queryTagCount(it) }?: 0
    }

    /**
     * Delete multiple tags
     */
    @Transaction
    open suspend fun deleteSelected(): Int {
        db.getBookTagDao().deleteTagsForTags(null)
        return db.execUpdateDelete(
            BookDatabase.buildQueryForIds(
            "DELETE FROM $TAGS_TABLE",      // SQLite Command
                TAGS_ID_COLUMN,                           // Column to query
                selectedIdSubQuery,                       // Selected tag ids sub-query
                null,                                     // Ids to delete
                false                                     // Delete ids or not ids
            ))
    }

    /**
     * Find an tag by name
     * @param name The name to search for
     * This uses a case insensitive compare
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE"
            + " WHERE $TAGS_NAME_COLUMN LIKE :name ESCAPE '\\' LIMIT 1")
    protected abstract suspend fun doFindByName(name: String): TagEntity?

    /**
     * Find an tag by name
     * @param name The name to search for
     * This uses a case insensitive compare
     */
    suspend fun findByName(name: String): TagEntity? {
        return doFindByName(PredicateDataDescription.escapeLikeWildCards(name))
    }

    /**
     * Query to count bits in the flags column
     */
    @RawQuery(observedEntities = [TagEntity::class])
    protected abstract suspend fun countBits(query: SupportSQLiteQuery): Int?

    /**
     * Query to count bits in the flags column
     */
    @RawQuery(observedEntities = [TagEntity::class])
    protected abstract fun countBitsLive(query: SupportSQLiteQuery): LiveData<Int?>

    /**
     * Query to count bits in the flags column
     * @param bits The bits we are checking
     * @param value The value we are checking
     * @param include True to include values that match. False to exclude values that match.
     * @param id A tag id to count
     * @return The count in a LiveData
     */
    open suspend fun countBits(bits: Int, value: Int, include: Boolean, id: Long?): Int? {
        // Build the selection from the bits
        val condition = StringBuilder().idWithFilter(id, null, TAGS_ID_COLUMN)
            .selectByFlagBits(bits, value, include, TAGS_FLAGS)
            .toString()
        // Return the query
        return countBits(SimpleSQLiteQuery(
            "SELECT COUNT($TAGS_ID_COLUMN) FROM $TAGS_TABLE$condition"
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
    open suspend fun countBitsLive(bits: Int, value: Int, include: Boolean, id: Long?): LiveData<Int?> {
        // Run query on query thread
        return withContext(db.queryExecutor.asCoroutineDispatcher()) {
            // Build the selection from the bits
            val condition = StringBuilder().idWithFilter(id, null, TAGS_ID_COLUMN)
                .selectByFlagBits(bits, value, include, TAGS_FLAGS)
                .toString()
            // Return the query
            countBitsLive(
                SimpleSQLiteQuery(
                    "SELECT COUNT($TAGS_ID_COLUMN) FROM $TAGS_TABLE$condition"
                )
            )
        }
    }

    /**
     * Change bits in the flags column
     * @param operation The operation to perform. True to set, false to clear, null to toggle
     * @param mask The bits to change
     * @param id The id of the tag to change. Null to change all
     * @return The number of rows changed
     */
    @Transaction
    open suspend fun changeBits(operation: Boolean?, mask: Int, id: Long?): Int? {
        val condition = StringBuilder().idWithFilter(id, null, TAGS_ID_COLUMN)
        // Only select the rows where the change will make a difference
        if (operation == true)
            condition.selectByFlagBits(mask, mask, false, TAGS_FLAGS)
        else if (operation == false)
            condition.selectByFlagBits(mask, 0, false, TAGS_FLAGS)
        val bits = BookDatabase.changeBits(operation, TAGS_FLAGS, mask)
        return db.execUpdateDelete(SimpleSQLiteQuery("UPDATE $TAGS_TABLE $bits$condition"))
    }

    companion object {
        /** Sub-query that returns the selected tag ids **/
        val selectedIdSubQuery: (invert: Boolean) -> String = {
            "SELECT $TAGS_ID_COLUMN FROM $TAGS_TABLE WHERE ( " +
            "( $TAGS_FLAGS & ${TagEntity.SELECTED} ) ${if (it) "==" else "!="} 0 )"
        }
    }
}

/**
 * Room data access object for the Book Tags table
 */
@Dao
abstract class BookTagDao(private val db:BookDatabase) {
    /**
     * Add a tag to a book
     * @param bookAndTag The book and tag entity to add
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun add(bookAndTag: BookAndTagEntity): Long

    /**
     * Delete multiple book tag entities
     * @param bookTagIds A list of ids for BookTagEntities.
     */
    @Transaction
    open suspend fun deleteById(bookTagIds: Array<Any>): Int {
        return db.execUpdateDelete(BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_TAGS_TABLE",
            BOOK_TAGS_ID_COLUMN,
            null,
            bookTagIds,
            false))
    }

    @Transaction
    open suspend fun copy(bookId: Long): Long {
        return 0L
    }

    /**
     * Delete multiple tags from multiple books
     * @param bookIds A list of book ids. Null means use the selected books
     * @param filter A filter to restrict the book ids
     * @param tagIds An optional list of tag ids to be removed for the books
     * @param tagsInvert A flag used to indicate whether tagIds contains the tag ids to be deleted,
     *                   when tagsInvert is false, or all tag ids except those in tagIds are to be deleted,
     *                   when tagsInvert is true.
     * If you call this method without specifying tagIds and tagsInvert it will removed
     * all tags from the books identified by booksIds
     */
    @Transaction
    open suspend fun deleteTagsForBooks(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null,
                                                tagIds: Array<Any>? = null, tagsInvert: Boolean = true): Int {
        return db.execUpdateDelete(BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_TAGS_TABLE",
            filter,
            BOOK_TAGS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false,
            BOOK_TAGS_TAG_ID_COLUMN,
            null,
            tagIds,
            tagsInvert))
    }

    /**
     * Delete selected tags from multiple books
     * @param bookIds A list of book ids. Null means use the selected books
     * @param filter A filter to restrict the book ids
     * @param tagIds An optional list of tag ids to be removed for the books.
     *               null means use the selected tags.
     * @param invert True to remove the tags that match tagIds. False to remove the tags not matched by tagIds.
     */
    @Transaction
    open suspend fun deleteSelectedTagsForBooks(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null,
                                                tagIds: Array<Any>? = null, invert: Boolean = false): Int {
        return db.execUpdateDelete(BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_TAGS_TABLE",
            filter,
            BOOK_TAGS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false,
            BOOK_TAGS_TAG_ID_COLUMN,
            TagDao.selectedIdSubQuery,
            tagIds,
            invert))
    }

    /**
     * Delete tags from all books
     * @param tagIds A list of tag ids. Null means selected.
     */
    @Transaction
    open suspend fun deleteTagsForTags(tagIds: Array<Any>?): Int {
        return db.execUpdateDelete(BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_TAGS_TABLE",
            BOOK_TAGS_TAG_ID_COLUMN,
            TagDao.selectedIdSubQuery,
            tagIds,
            false))
    }

    /**
     * Query tags for a book
     * @param query The SQLite query
     */
    @RawQuery(observedEntities = [BookAndTagEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<Long>?

    /**
     * Query tag ids for a set of books
     * @param bookIds A list of book ids. Null means use the selected books.
     * @param filter A filter to restrict the book ids
     */
    suspend fun queryBookIds(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_TAGS_TAG_ID_COLUMN FROM $BOOK_TAGS_TABLE",
            filter,
            BOOK_TAGS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false)?.let { queryBookIds(it) }
    }

    /**
     * Query book tag links for a set of tags
     * @param query The SQLite query
     */
    @RawQuery(observedEntities = [BookAndTagEntity::class])
    protected abstract suspend fun queryTags(query: SupportSQLiteQuery): List<BookAndTagEntity>?

    /**
     * Query book tag links for a set of tags
     * @param tagIds A list of tag ids
     */
    suspend fun queryTags(tagIds: Array<Any>): List<BookAndTagEntity>? {
        return BookDatabase.buildQueryForIds(
            "SELECT * FROM $BOOK_TAGS_TABLE",
            BOOK_TAGS_TAG_ID_COLUMN,
            null,
            tagIds,
            false)?.let { queryTags(it) }
    }

    /**
     * Delete books for a tag
     * @param tagId The tag whose books are delete
     */
    @Query("DELETE FROM $BOOK_TAGS_TABLE WHERE $BOOK_TAGS_TAG_ID_COLUMN = :tagId")
    protected abstract suspend fun deleteTag(tagId: Long): Int

    /**
     * Add a single tag for a book
     * @param bookId The id of the book
     * @param tagId The id of the tag
     */
    @Transaction
    open suspend fun addTagToBook(bookId: Long, tagId: Long): Long {
        return add(BookAndTagEntity(0, tagId, bookId))
    }

    /**
     * Add multiple tags for a book
     * @param bookId The id of the book
     * @param tagIds List of ids for the tags
     */
    @Transaction
    open suspend fun addTagsToBook(bookId: Long, tagIds: List<Long>?) {
        if (tagIds != null) {
            for (tag in tagIds)
                addTagToBook(bookId, tag)
        }
    }

    /**
     * Add multiple tags to multiple books
     * @param bookIds A list of ids for books
     * @param tagIds A list of id for tags
     */
    @Transaction
    open suspend fun addTagsToBooks(bookIds: List<Long>?, tagIds: List<Long>?) {
        if (bookIds != null) {
            for (book in bookIds)
                addTagsToBook(book, tagIds)
        }
    }

    /**
     * Add multiple tags to multiple books
     * @param bookIds A list of book ids. Null means use selected books.
     * @param tagIds A list of tag ids to be removed for the books. Null means use selected tags.
     * @param filter A filter to restrict the book ids
     */
    @Transaction
    open suspend fun addTagsToBooks(bookIds: Array<Any>?, tagIds: Array<Any>?, filter: BookFilter.BuiltFilter?) {
        val bookList = db.getBookDao().queryBookIds(bookIds, filter)
        val tagList = db.getTagDao().queryTagIds(tagIds)
        addTagsToBooks(bookList, tagList)
    }

    /**
     * Delete all tags from books
     * @param bookIds A list of book ids. Null means use the selected books.
     * @param deleteTags A flag to indicate whether the tag in the Tags table should be deleted
     *                   if all of its books have been deleted
     * @param filter A filter to restrict the book ids
     */
    @Transaction
    open suspend fun deleteSelectedBooks(bookIds: Array<Any>?, deleteTags: Boolean = false, filter: BookFilter.BuiltFilter? = null): Int {
        val count: Int
        // Do we want to delete any books
        if (deleteTags) {
            // Yes, get the ids of tags that are affected
            val tags = queryBookIds(bookIds, filter)
            // Delete the book tag links
            count = deleteTagsForBooks(bookIds, filter)
            tags?.let {
                // Check for empty tags and delete them
                for (tag in it) {
                    val list: List<BookAndTagEntity> = findById(tag, 1)
                    if (list.isEmpty())
                        db.getTagDao().delete(tag)
                }
            }
        } else {
            // Don't delete tags, so just delete the links
            count = deleteTagsForBooks(bookIds, filter)
        }

        return count
    }

    /**
     * Get the book tag links for a tag id
     * @param tagId The tag id
     * @param limit A flag to limit the number of tag links returned
     */
    @Query("SELECT * FROM $BOOK_TAGS_TABLE WHERE $BOOK_TAGS_TAG_ID_COLUMN = :tagId LIMIT :limit")
    abstract suspend fun findById(tagId: Long, limit: Int = -1): List<BookAndTagEntity>
}

/**
 * The Author data access object
 */
@Dao
abstract class AuthorDao(private val db: BookDatabase) {
    /**
     * Get the list of authors
     */
    @Query(value = "SELECT * FROM $AUTHORS_TABLE ORDER BY $LAST_NAME_COLUMN, $REMAINING_COLUMN")
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
     */
    @Transaction
    open suspend fun add(bookId: Long, authors: List<AuthorEntity>) {
         // Delete current authors of the books
        deleteBooks(arrayOf(bookId), null)
        // Add new authors
        for (author in authors)
            add(bookId, author)
    }

    @Transaction
    open suspend fun copy(bookId: Long): Long {
        return 0L
    }

    /**
     * Add a single author for a book
     * @param bookId The id of the book
     * @param author The author entity
     */
    @Transaction
    open suspend fun add(bookId: Long, author: AuthorEntity) {
        // Find the author
        author.lastName = author.lastName.trim { it <= ' ' }
        author.remainingName = author.remainingName.trim { it <= ' ' }
        val list: List<AuthorEntity> = findByName(author.lastName, author.remainingName)
        // Get the author id if it isn't empty, otherwise add the author
        author.id = if (list.isNotEmpty()) {
            list[0].id
        } else {
            add(author)
        }
        // Add the link
        add(BookAndAuthorEntity(0, author.id, bookId))
    }

    /**
     * Delete all authors from books
     * @param bookIds A list of book ids. Null means use the selected books
     * @param filter A filter to restrict the book ids
    */
    @Transaction
    protected open suspend fun deleteBooks(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): Int {
        return db.execUpdateDelete(BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_AUTHORS_TABLE",
            filter,
            BOOK_AUTHORS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false))
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
            filter,
            BOOK_AUTHORS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false)?.let { queryBookIds(it) }
    }

    /**
     * Delete books for an author
     * @param authorId The id of the authors whose books are deleted
     */
    @Query("DELETE FROM $AUTHORS_TABLE WHERE $AUTHORS_ID_COLUMN = :authorId")
    protected abstract suspend fun deleteAuthor(authorId: Long): Int

    /**
     * Find an author by name
     * @param last The author last name
     * @param remaining The rest of the author name
     */
    @Query(value = "SELECT * FROM $AUTHORS_TABLE"
        + " WHERE $LAST_NAME_COLUMN LIKE :last ESCAPE '\\' AND $REMAINING_COLUMN LIKE :remaining ESCAPE '\\'")
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
    open suspend fun delete(bookIds: Array<Any>?, deleteAuthors: Boolean = true, filter: BookFilter.BuiltFilter? = null): Int {
        // Do we want to delete authors with no books?
        val count: Int
        if (deleteAuthors) {
            // Yes, get the ids of the authors that are affects
            val authors = queryBookIds(bookIds, filter)
            // Delete the authors
            count = deleteBooks(bookIds, filter)
            authors?.let {
                // Delete authors with no books
                for (author in it) {
                    val list: List<BookAndAuthorEntity> = findById(author, 1)
                    if (list.isEmpty())
                        deleteAuthor(author)
                }
            }
        } else {
            // No, just delete the links
            count = deleteBooks(bookIds, filter)
        }

        return count
    }
}

/**
 * Category data access object
 */
@Dao
abstract class CategoryDao(private val db: BookDatabase) {
    /**
     * Get the list of categories
     */
    @Query(value = "SELECT * FROM $CATEGORIES_TABLE ORDER BY $CATEGORY_COLUMN")
    abstract suspend fun get(): List<CategoryEntity>?

    /**
     * Add a category to the categories table
     * @param category The category to add
     */
    @Insert
    abstract suspend fun add(category: CategoryEntity): Long

    /**
     * Add a book category link to the category table
     * @param bookCategory The book category link to add
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun add(bookCategory: BookAndCategoryEntity) : Long

    /**
     * Add multiple categories for a book
     * @param bookId The id of the book
     * @param categories The list of categories to add
     */
    @Transaction
    open suspend fun add(bookId: Long, categories: List<CategoryEntity>) {
        deleteBooks(arrayOf(bookId), null)
        for (cat in categories)
            add(bookId, cat)
    }

    @Transaction
    open suspend fun copy(bookId: Long): Long {
        return 0L
    }

    /**
     * Add a single category for a book
     * @param bookId The id o the book
     * @param category The category to add
     */
    @Transaction
    open suspend fun add(bookId: Long, category: CategoryEntity) {
        // Find the category
        category.category = category.category.trim { it <= ' ' }
        val list: List<CategoryEntity> = findByName(category.category)
        // Use existing id, or add the category
        category.id = if (list.isNotEmpty()) {
            list[0].id
        } else {
            add(category)
        }
        // Add the link
        add(BookAndCategoryEntity(
            id = 0,
            categoryId = category.id,
            bookId = bookId
        ))
    }

    /**
     * Delete all categories from books
     * @param bookIds A list of book ids. Null means use selected books.
     * @param filter A filter to restrict the book ids
     */
    @Transaction
    protected open suspend fun deleteBooks(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): Int {
        return db.execUpdateDelete(BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_CATEGORIES_TABLE",
            filter,
            BOOK_CATEGORIES_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false))
    }

    /**
     * Query categories for a book
     * @param query The SQLite query to get the ids
     */
    @RawQuery(observedEntities = [BookAndCategoryEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<Long>?

    /**
     * Query author ids for a set of books
     * @param bookIds A list of book ids. Null means use the selected books.
     * @param filter A filter to restrict the book ids
     */
    private suspend fun queryBookIds(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_CATEGORIES_CATEGORY_ID_COLUMN FROM $BOOK_CATEGORIES_TABLE",
            filter,
            BOOK_CATEGORIES_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false)?.let { queryBookIds(it) }
    }

    /**
     * Delete all books for a category
     * @param categoryId The id of the category
     */
    @Query("DELETE FROM $CATEGORIES_TABLE WHERE $CATEGORIES_ID_COLUMN = :categoryId")
    protected abstract suspend fun deleteCategory(categoryId: Long): Int

    /**
     * Find a category by name
     * @param category The name of the category
     */
    @Query(value = "SELECT * FROM $CATEGORIES_TABLE"
            + " WHERE $CATEGORY_COLUMN LIKE :category ESCAPE '\\'")
    abstract suspend fun doFindByName(category: String): List<CategoryEntity>

    /**
     * Find a category by name
     * @param category The name of the category
     */
    suspend fun findByName(category: String): List<CategoryEntity> {
        return doFindByName(PredicateDataDescription.escapeLikeWildCards(category))
    }

    /**
     * Find books by category
     * @param categoryId The id of the category
     * @param limit A limit on the number of book category links to return
     */
    @Query("SELECT * FROM $BOOK_CATEGORIES_TABLE WHERE $BOOK_CATEGORIES_CATEGORY_ID_COLUMN = :categoryId LIMIT :limit")
    abstract suspend fun findById(categoryId: Long, limit: Int = -1): List<BookAndCategoryEntity>

    /**
     * Delete all categories from books
     * @param bookIds A list of book ids. Null means use the selected books.
     * @param deleteCategories A flag to indicate whether the category in the Categories table should be deleted
     *                      if all of its books have been deleted
     * @param filter A filter to restrict the book ids
     */
    @Transaction
    open suspend fun delete(bookIds: Array<Any>?, deleteCategories: Boolean = true, filter: BookFilter.BuiltFilter? = null): Int {
        val count: Int
        // Should we delete categories
        if (deleteCategories) {
            // Yes get the id of the categories affected
            val categories = queryBookIds(bookIds, filter)
            // Delete the book category links
            count = deleteBooks(bookIds, filter)
            categories?.let {
                // Delete any empty categories
                for (category in it) {
                    val list: List<BookAndCategoryEntity> = findById(category, 1)
                    if (list.isEmpty())
                        deleteCategory(category)
                }
            }
        } else {
            // No, just delete the book category links
            count = deleteBooks(bookIds, filter)
        }

        return count
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
    protected open suspend fun deleteBooks(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null): Int {
        return db.execUpdateDelete(BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_TABLE",
            filter,
            BOOK_ID_COLUMN,
            selectedIdSubQuery,
            bookIds,
            false))
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
            filter,
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
     * Class to get unique columns in the book table
     */
    @TypeConverters(DateConverters::class)
    protected data class ConflictIds(
        @ColumnInfo(name = BOOK_ID_COLUMN) val id: Long,
        @ColumnInfo(name = VOLUME_ID_COLUMN) val volumeId: String?,
        @ColumnInfo(name = SOURCE_ID_COLUMN) val sourceId: String?,
        @ColumnInfo(name = ISBN_COLUMN) val ISBN: String?,
        @ColumnInfo(name = DATE_ADDED_COLUMN) val added: Date
    )

    /**
     * Look for existing book
     * @param query The SQLite query to find the existing book
     */
    @RawQuery
    protected abstract suspend fun findConflict(query: SupportSQLiteQuery): ConflictIds?

    /**
     * Find a conflicting book
     * @param volumeId The conflicting volume id
     * @param sourceId The conflicting source id
     * @param ISBN The conflicting ISBN
     */
    private suspend fun findConflict(volumeId: String?, sourceId: String?, ISBN: String?): ConflictIds? {
        // If this book doesn't have a volume id or ISBN, Then we can't conflict
        if (volumeId == null && ISBN == null)
            return null
        val args = ArrayList<String?>(3)

        // Build query to look for conflict
        var query =
            "SELECT $BOOK_ID_COLUMN, $VOLUME_ID_COLUMN, $SOURCE_ID_COLUMN, $ISBN_COLUMN, $DATE_ADDED_COLUMN FROM $BOOK_TABLE" +
            " WHERE"

        // If there is a volume id, then look for it
        if (volumeId != null) {
            query += " ( $VOLUME_ID_COLUMN = ? AND $SOURCE_ID_COLUMN = ? )"
            args.add(volumeId)
            args.add(sourceId)
        }

        // If there is an ISBN look for it
        if (ISBN != null) {
            if (volumeId != null)
                query += " OR"
            query += " $ISBN_COLUMN = ?"
            args.add(ISBN)
        }

        // Only need one row to conflict
        query += " LIMIT 1"

        return findConflict(SimpleSQLiteQuery(query, args.toArray()))
    }

    /**
     * Add or update a book in the data base
     */
    @Transaction
    protected open suspend fun addOrUpdate(book: BookEntity, callback: (suspend CoroutineScope.(conflict: BookEntity) -> Boolean)? = null): Long {
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
        if (ids == null) {
            // Didn't find a conflict add the book
            book.id = 0
            book.added = time
            book.modified = time
            book.id = add(book)
        } else if (callConflict(book, callback, true)){
            // Had a conflict, get the id and time added and update the book
            book.id = ids.id
            book.added = ids.added
            book.modified = time
            if (update(book) == 0)
                book.id = 0L
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
    open suspend fun addOrUpdate(book: BookAndAuthors, tagIds: Array<Any>? = null, callback: (suspend CoroutineScope.(conflict: BookEntity) -> Boolean)? = null): Long {
        // Add or update the book
        val id = addOrUpdate(book.book, callback)

        if (id != 0L) {
            // Delete existing thumbnails, if any
            thumbnails.deleteThumbFile(book.book.id, true)
            thumbnails.deleteThumbFile(book.book.id, false)

            // Add categories
            db.getCategoryDao().add(book.book.id, book.categories)
            // Add Authors
            db.getAuthorDao().add(book.book.id, book.authors)
            // Add Tags from book along with additional tags
            db.getTagDao().add(book.book.id, book.tags, tagIds)
        }

        return id
    }

    /**
     * Delete books
     * @param filter A filter to restrict the book ids
     * @param bookIds Optional bookIds to delete. Null means delete selected books
     */
    @Transaction
    open suspend fun deleteSelected(filter: BookFilter.BuiltFilter?, bookIds: Array<Any>?): Int {
        // Delete all tags for the books - keep tags with no books
        db.getBookTagDao().deleteSelectedBooks(bookIds, false, filter)
        // Delete all authors for the books - delete authors with no books
        db.getAuthorDao().delete(bookIds, true, filter)
        // Delete all categories for the book - delete categories with no books
        db.getCategoryDao().delete(bookIds, true, filter)

        // Delete all thumbnails
        queryBookIds(bookIds, filter)?.let {
            for (book in it) {
                thumbnails.deleteThumbFile(book, true)
                thumbnails.deleteThumbFile(book, false)
            }
        }

        // Finally delete the books
        return deleteBooks(bookIds, filter)

    }

    /**
     * Get a book by id
     * @param bookId The id of the book
     * BookAndAuthors contains optional fields that are not in all queries,
     * so ignore CURSOR_MISMATCH warnings
     */
    @Transaction
    @Query(value = "SELECT * FROM $BOOK_TABLE"
            + " WHERE $BOOK_ID_COLUMN = :bookId LIMIT 1")
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    abstract suspend fun getBook(bookId: Long): BookAndAuthors?

    /**
     * Get books
     * @param query The SQLite query to get the books
     */
    @RawQuery(observedEntities = [BookAndAuthors::class])
    protected abstract fun getBooks(query: SupportSQLiteQuery): PagingSource<Int, BookAndAuthors>

    /**
     * Get books
     */
    fun getBooks(): PagingSource<Int, BookAndAuthors> {
        return getBooks(SimpleSQLiteQuery("SELECT * FROM $BOOK_TABLE"))
    }

    /**
     * Get books
     * @param filter The filter description used to filter and order the books
     */
    fun getBooks(filter: BookFilter, context: Context): PagingSource<Int, BookAndAuthors> {
        if (filter.orderList.isEmpty() && filter.filterList.isEmpty())
            return getBooks()
        return getBooks(BookFilter.buildFilterQuery(filter, context))
    }

    /**
     * Get the small thumbnail url for a book
     * @param bookId The book id
     */
    @Query("SELECT $SMALL_THUMB_COLUMN FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = :bookId LIMIT 1")
    protected abstract suspend fun getSmallThumbnailUrl(bookId: Long): String?

    /**
     * Get the large thumbnail url for a book
     * @param bookId The book id
     */
    @Query("SELECT $LARGE_THUMB_COLUMN FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = :bookId LIMIT 1")
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
                .selectByFlagBits(bits, value, include, BOOK_FLAGS)
                .toString()
            countBitsLive(SimpleSQLiteQuery(
                "SELECT COUNT($BOOK_ID_COLUMN) FROM $BOOK_TABLE$condition", filter?.args
            ))
        }
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
        // Only select the rows where the change will make a difference
        if (operation == true)
            condition.selectByFlagBits(mask, mask, false, BOOK_FLAGS)
        else if (operation == false)
            condition.selectByFlagBits(mask, 0, false, BOOK_FLAGS)
        val bits = BookDatabase.changeBits(operation, BOOK_FLAGS, mask)
        return db.execUpdateDelete(SimpleSQLiteQuery("UPDATE $BOOK_TABLE $bits$condition", filter?.args))
    }

    @Transaction
    open suspend fun copy(bookId: Long): Long {
        return 0L
    }

    companion object {
        /** Sub-query that returns the selected book ids **/
        val selectedIdSubQuery: (invert: Boolean) -> String = {
            "SELECT $BOOK_ID_COLUMN FROM $BOOK_TABLE WHERE ( " +
            "( $BOOK_FLAGS & ${BookEntity.SELECTED} ) ${if (it) "==" else "!="} 0 )"
        }
    }
}

@Dao
abstract class ViewDao(private val db: BookDatabase) {
    /**
     * Get list of views
     */
    @Query(value = "SELECT $VIEWS_NAME_COLUMN FROM $VIEWS_TABLE ORDER BY $VIEWS_NAME_COLUMN")
    abstract fun doGetViewNames(): LiveData<List<String>>

    /**
     * Get list of views
     */
    suspend fun getViewNames(): LiveData<List<String>> {
        return withContext(db.queryExecutor.asCoroutineDispatcher()) {
            doGetViewNames()
        }
    }

    @Transaction
    open suspend fun copy(bookId: Long): Long {
        return 0L
    }

    /**
     * Add a view
     * @param view the View to add or update
     * @return The id of the view
     */
    @Insert
    protected abstract suspend fun doAdd(view: ViewEntity): Long

    /**
     * Update a view
     * @param view the View to add or update
     * @return The id of the view
     */
    @Update
    protected abstract suspend fun doUpdate(view: ViewEntity): Int

    /**
     * Delete a view by name
     * @param viewName The name with % and _ escaped
     * @return The number of views delete
     */
    @Transaction
    @Query(value = "DELETE FROM $VIEWS_TABLE WHERE $VIEWS_NAME_COLUMN LIKE :viewName ESCAPE '\\'")
    protected abstract suspend fun doDelete(viewName: String): Int

    /**
     * Delete a view by name
     * @param viewName The name of the view. % and _ are escaped before deleting
     * @return The number of views delete
     */
    suspend fun delete(viewName: String): Int {
        return doDelete(PredicateDataDescription.escapeLikeWildCards(viewName))
    }

    /**
     * Add or update a view to the database
     * @param view The view to add
     * @param onConflict A lambda function to respond to a conflict when adding. Return
     *                   true to accept the conflict or false to abort the add
     * @return The id of the view in the database, or 0L if the add was aborted
     */
    suspend fun addOrUpdate(view: ViewEntity, onConflict: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)?): Long {
        // Look for a conflicting view
        view.name = view.name.trim { it <= ' ' }
        val conflict = findByName(view.name)
        if (conflict != null) {
            // Found one. If the conflict id and view id are the same
            // we treat this as a rename.
            if (conflict.id != view.id) {
                //  Ask the onConflict handler to resolve the conflict
                if (!callConflict(conflict, onConflict))
                    return 0L       // onConflict says not to add or update
                view.id = conflict.id
            }
            // Update the view
            if (doUpdate(view) != 1)
                return 0L
        } else {
            // Add the new view
            view.id = 0L
            view.id = doAdd(view)
        }
        return view.id
    }

    /**
     * Find a view by name
     * @param name The name of the view
     * @return The view or null if it wasn't found
     */
    @Query(value = "SELECT * FROM $VIEWS_TABLE WHERE $VIEWS_NAME_COLUMN LIKE :name ESCAPE '\\'")
    protected abstract suspend fun doFindByName(name: String): ViewEntity?

    /**
     * Find a view by name
     * @param name The name of the view
     * @return The view or null if it wasn't found
     */
    suspend fun findByName(name: String): ViewEntity? {
        return doFindByName(PredicateDataDescription.escapeLikeWildCards(name))
    }
}

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
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_books_volume_id_books_source_id` ON `books` (`books_volume_id`, `books_source_id`)")
                    database.execSQL("DROP INDEX IF EXISTS `index_books_books_isbn`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_books_isbn`")
                    database.execSQL("DROP INDEX IF EXISTS `index_authors_authors_last_name_authors_remaining`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_authors_authors_last_name_authors_remaining` ON `authors` (`authors_last_name`, `authors_remaining`)")
                    database.execSQL("DROP INDEX IF EXISTS `index_tags_tags_name`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_tags_name`")
                    database.execSQL("DROP INDEX IF EXISTS `index_categories_categories_category`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_categories_category` ON `categories` (`categories_category`)")
                    database.execSQL("DROP INDEX IF EXISTS `index_views_views_name`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_views_views_name` ON `views` (`views_name`)")
                }
            }
        )
    }
}
