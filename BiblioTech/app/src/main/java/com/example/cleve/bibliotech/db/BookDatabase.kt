package com.example.cleve.bibliotech.db

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.cleve.bibliotech.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.*
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

// A note about arrays of ids with an invert flag
// We use arrays of ids to identify entities that we want to operate on along with an invert flag.
// When the invert flag is false, the array of ids is the ids of the objects that are operated on.
// When the invert flag is true, all objects except the ones with ids in the array of ids
// are operated on.

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
val ALL_BOOK_COLUMNS = arrayOf(                         // Array of all book columns, used building queries
    BOOK_ID_COLUMN, VOLUME_ID_COLUMN, SOURCE_ID_COLUMN,
    ISBN_COLUMN, TITLE_COLUMN, SUBTITLE_COLUMN,
    DESCRIPTION_COLUMN, PAGE_COUNT_COLUMN, BOOK_COUNT_COLUMN,
    VOLUME_LINK, RATING_COLUMN, DATE_ADDED_COLUMN,
    DATE_MODIFIED_COLUMN, SMALL_THUMB_COLUMN, LARGE_THUMB_COLUMN)

// Authors table name and its column names
const val AUTHORS_TABLE = "authors"                                 // Authors table name
const val AUTHORS_ID_COLUMN = "authors_id"                          // Incrementing id
const val LAST_NAME_COLUMN = "authors_last_name"                    // Author last name
const val REMAINING_COLUMN = "authors_remaining"                    // First and middle names

// Book Authors table name and its column names
const val BOOK_AUTHORS_TABLE = "book_authors"                       // Book Authors table name
const val BOOK_AUTHORS_ID_COLUMN = "book_authors_id"                // Increment id
const val BOOK_AUTHORS_BOOK_ID_COLUMN = "book_authors_book_id"      // Book row incrementing id
const val BOOK_AUTHORS_AUTHOR_ID_COLUMN = "book_authors_author_id"  // Authors row incrementing id

// Categories table name and its column names
const val CATEGORIES_TABLE = "categories"               // Categories table name
const val CATEGORIES_ID_COLUMN = "categories_id"        // Incrementing id
const val CATEGORY_COLUMN = "categories_category"       // Category name

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

const val BOOK_TAGS_TABLE = "book_tags"                     // Book tags table name
const val BOOK_TAGS_ID_COLUMN = "book_tags_id"              // Incrementing id
const val BOOK_TAGS_BOOK_ID_COLUMN = "book_tags_book_id"    // Book row incrementing id
const val BOOK_TAGS_TAG_ID_COLUMN = "book_tags_tag_id"      // Tag row incrementing id

// Extensions for thumbnail files in the cache.
const val kSmallThumb = ".small.png"
const val kThumb = ".png"

// SQL names for descending and ascending order
const val kDesc = "DESC"
const val kAsc = "ASC"

// Type convert to convert between dates in the data base, which are longs, and Date objects
class Converters {
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
 * Interface used for selectable objects
 */
interface Selectable {
    /**
     * The id of the object used to select it
     */
    val id: Long

    /**
     * Identifies whether the object is selected
     */
    var selected: Boolean
}

/**
 * Room Entity for the Books table
 */
@TypeConverters(Converters::class)
@Entity(tableName = BOOK_TABLE,
    indices = [
        Index(value = [BOOK_ID_COLUMN],unique = true),
        Index(value = [VOLUME_ID_COLUMN, SOURCE_ID_COLUMN],unique = true),
        Index(value = [ISBN_COLUMN],unique = true)
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
    @ColumnInfo(name = LARGE_THUMB_COLUMN) var largeThumb: String?
)

/**
 * Room Entity for the Authors table
 */
@Entity(tableName = AUTHORS_TABLE,
    indices = [
        Index(value = [AUTHORS_ID_COLUMN],unique = true),
        Index(value = [LAST_NAME_COLUMN,REMAINING_COLUMN],unique = true)
    ])
data class AuthorEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = AUTHORS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = LAST_NAME_COLUMN,defaultValue = "",collate = ColumnInfo.NOCASE) var lastName: String,
    @ColumnInfo(name = REMAINING_COLUMN,defaultValue = "",collate = ColumnInfo.NOCASE) var remainingName: String
) {
    constructor(id: Long, in_name: String): this(id, "", "") {
        setAuthor(in_name)
    }

    /**
     * Set the first and remainging author values from a string
     * @param in_name The string
     */
    fun setAuthor(in_name: String) {
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
                lastName = name.substring(lastIndex)
                remainingName = name.substring(0, lastIndex).trim { it <= ' ' }
            }
        }
    }
}

/**
 * Selectable Author class
 */
data class Author(
    @Embedded var author: AuthorEntity,
    @Ignore override var selected: Boolean
) : Selectable {
    /**
     * This constructor is needed to use @Ignore in Room
     */
    constructor(author: AuthorEntity): this(author, false)

    /**
     * Get the id of the Author
     */
    override val id: Long
        get() = author.id
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
        Index(value = [CATEGORY_COLUMN],unique = true)
    ])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = CATEGORIES_ID_COLUMN) var id: Long,
    @ColumnInfo(name = CATEGORY_COLUMN,defaultValue = "",collate = ColumnInfo.NOCASE) var category: String
)

/**
 * Selectable category object
 */
data class Category(
    @Embedded var category: CategoryEntity,
    @Ignore override var selected: Boolean = false
) : Selectable {
    /**
     * This constructor is needed to use @Ignore in Room
     */
    constructor(category: CategoryEntity): this(category, false)

    /**
     * Get the id of the category
     */
    override val id: Long
        get() = category.id
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
        Index(value = [TAGS_ID_COLUMN],unique = true)
    ])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TAGS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = TAGS_NAME_COLUMN,collate = ColumnInfo.NOCASE) var name: String,
    @ColumnInfo(name = TAGS_DESC_COLUMN,defaultValue = "") var desc: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TagEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (desc != other.desc) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + desc.hashCode()
        return result
    }
}

/**
 * Selectable tag object
 */
data class Tag(
    @Embedded var tag: TagEntity,
    @Ignore override var selected: Boolean = false
) : Selectable {
    /**
     * This constructor is needed to use @Ignore in Room
     */
    constructor(tag: TagEntity): this(tag, false)

    /**
     * Get the id of the tag
     */
    override val id: Long
        get() = tag.id
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
    @ColumnInfo(name= REMAINING_COLUMN) var sortFirst: String?,
    @ColumnInfo(name= LAST_NAME_COLUMN) var sortLast: String?,
    @ColumnInfo(name= TAGS_NAME_COLUMN) var sortTag: String?,
    @ColumnInfo(name= CATEGORY_COLUMN) var sortCategory: String?,
    @Ignore override var selected: Boolean
) : Parcelable, Selectable {
    /**
     * This constructor is needed to allow Room to work with @Ignore
     */
    constructor(
        book: BookEntity,
        authors: List<AuthorEntity>,
        categories: List<CategoryEntity>,
        tags: List<TagEntity>,
        sortFirst: String? = null,
        sortLast: String? = null,
        sortTag: String? = null,
        sortCategory: String? = null
    ): this(book, authors, categories, tags, sortFirst, sortLast, sortTag, sortCategory, false)

    /**
     * Get the id of the book
     */
    override val id: Long
        get() { return book.id }

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
        if (selected != other.selected) return false

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
        result = 31 * result + selected.hashCode()
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
    }

    companion object {
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
                largeThumb = largeThumb
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
            return TagEntity(
                id = id,
                name = name?:"",
                desc = desc?:""
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
     * Get the list of all tags
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE ORDER BY $TAGS_NAME_COLUMN")
    abstract fun get(): PagingSource<Int, Tag>

    /**
     * Get a tag using its incrementing id
     * @param tagId The id of the tag to retrieve
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN = :tagId LIMIT 1")
    abstract suspend fun get(tagId: Long): TagEntity?

    /**
     * Add a tag
     * @param tag The tag to be added
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun internalAdd(tag: TagEntity): Long


    /**
     * Add multiple tags for a book
     * @param bookId The id of the book
     * @param tags A list of possibly new tags for the book
     * @param tagIds An optional list of tag ids
     * @param invert A flag used to indicate whether tagIds contains the tags for the book,
     *               when invert is false, all tags except those in tagIds are added to the book,
     *               when invert is true
     */
    @Transaction
    open suspend fun add(bookId: Long, tags: List<TagEntity>, tagIds: Array<Any>? = null, invert: Boolean = false) {
        // Add or find all of the tags
        for (tag in tags) {
            tag.id = findByName(tag.name)?.id ?: add(tag)
        }

        // Build a set of all tag ids for the book
        val set = HashSet<Any>()
        // Add ids from tagIds
        tagIds?.let { set.addAll(tagIds) }
        if (invert) {
            // If tagIds is the unselected ids, then remove
            // the ids in tags so they will be selected
            for (tag in tags)
                set.remove(tag.id)
        } else {
            // Otherwise add the ids in tags
            tags.mapTo(set) { it.id }
        }
        val list = set.toArray()

        // Delete the tags that we don't want to keep
        db.getBookTagDao().deleteTagsForBooks(arrayOf(bookId), false, list, !invert)

        // Add the tags that we want
        val tagList = db.getTagDao().queryTagIds(list, invert)
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
    open suspend fun add(tag: TagEntity, callback: (suspend (conflict: TagEntity) -> Boolean)? = null): Long {
        // Empty tag is not accepted
        if (tag.name == "")
            return 0
        // See if there is a conflicting tag with the same name.
        val conflict = findByName(tag.name)
        if (conflict != null && conflict.id != tag.id) {
            // Yep, ask caller what to do, null or false return means do nothing
            if (callback == null || !callback(conflict))
                return 0

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
                        db.getBookTagDao().deleteTagsForBooks(list.toArray())
                }
            }
        }

        // Update or add the tag
        return internalAdd(tag)
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
     * @param tagIds A list of tag ids
     * @param invert A flag used to indicate whether tagIds contains the tag ids to be returned,
     *               when invert is false, or all tag ids except those in tagIds,
     *               when invert is true
     */
    suspend fun queryTagIds(tagIds: Array<Any>, invert: Boolean = false): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $TAGS_ID_COLUMN FROM $TAGS_TABLE",  // SQLite command
            TAGS_ID_COLUMN,                                       // Column to query
            tagIds,                                               // Ids to query
            invert                                                // Query for ids, or not ids
        )?.let {query -> queryTagIds(query)?.map { it.id } }
    }

    /**
     * Run a delete query to delete tags
     * @param query The delete query
     */
    @RawQuery(observedEntities = [TagEntity::class])
    protected abstract suspend fun delete(query: SupportSQLiteQuery): Int?

    /**
     * Delete a tag using the tag id
     * @param tagId The tag id to delete
     */
    @Query("DELETE FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN = :tagId")
    abstract suspend fun delete(tagId: Long): Int

    /**
     * Delete multiple tags
     * @param tagIds A list of tag ids
     * @param invert A flag used to indicate whether tagIds contains the tag ids to be deleted,
     *               when invert is false, or all tag ids except those in tagIds are to be deleted,
     *               when invert is true
     */
    @Transaction
    open suspend fun delete(tagIds: Array<Any>, invert: Boolean): Int? {
        db.getBookTagDao().deleteTagsForTags(tagIds, invert)
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $TAGS_TABLE",      // SQLite Command
            TAGS_ID_COLUMN,                           // Column to query
            tagIds,                                   // Ids to delete
            invert                                    // Delete ids or not ids
        )?.let { delete(it) }?: 0
    }

    /**
     * Find an tag by name
     * @param name The name to search for
     * This uses a case insensitive compare
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE"
            + " WHERE $TAGS_NAME_COLUMN LIKE :name LIMIT 1")
    abstract suspend fun findByName(name: String): TagEntity?
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
     * Delete tags for books
     * @param query SQLite query to delete tags
     */
    @RawQuery(observedEntities = [BookAndTagEntity::class])
    protected abstract suspend fun delete(query: SupportSQLiteQuery): Int?

    /**
     * Delete multiple tags from multiple books
     * @param bookIds A list of book ids
     * @param booksInvert A flag used to indicate whether bookIds contains the books ids whose
     *                    tags are to be deleted, when invert is false, or all book ids except
     *                    those in bookIds are to have their tags deleted, when invert is true
     * @param tagIds An optional list of tag ids to be removed for the books
     * @param tagsInvert A flag used to indicate whether tagIds contains the tag ids to be deleted,
     *                   when invert is false, or all tag ids except those in tagIds are to be deleted,
     *                   when invert is true
     * If you call this method without specifying tagIds and tagsInvert it will removed
     * all tags from the books identified by booksIds and booksInvert
     */
    @Transaction
    open suspend fun deleteTagsForBooks(bookIds: Array<Any>, booksInvert: Boolean = false,
                                        tagIds: Array<Any>? = null, tagsInvert: Boolean = true): Int {
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_TAGS_TABLE",
            BOOK_TAGS_BOOK_ID_COLUMN,
            bookIds,
            booksInvert,
            BOOK_TAGS_TAG_ID_COLUMN,
            tagIds,
            tagsInvert)?.let { delete(it) }?: 0
    }

    /**
     * Delete tags from all books
     * @param tagIds A list of tag ids
     * @param invert A flag used to indicate whether tagIds contains the tag ids to be deleted,
     *               when invert is false, or all tag ids except those in tagIds are to be deleted,
     *               when invert is true
     */
    @Transaction
    open suspend fun deleteTagsForTags(tagIds: Array<Any>, invert: Boolean = false): Int {
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_TAGS_TABLE",
            BOOK_TAGS_TAG_ID_COLUMN,
            tagIds,
            invert)?.let { delete(it) }?: 0
    }

    /**
     * Query tags for a book
     * @param query The SQLite query
     */
    @RawQuery(observedEntities = [BookAndTagEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<Long>?

    /**
     * Query tag ids for a set of books
     * @param bookIds A list of book ids
     * @param invert A flag used to indicate whether bookIds contains the books ids whose
     *               tags are to be returned, when invert is false, or all book ids except
     *               those in bookIds are to have their tags returned, when invert is true
     */
    private suspend fun queryBookIds(bookIds: Array<Any>, invert: Boolean = false): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_TAGS_TAG_ID_COLUMN FROM $BOOK_TAGS_TABLE",
            BOOK_TAGS_BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { queryBookIds(it) }
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
     * @param invert A flag used to indicate whether tagIds contains the tag ids to be searched,
     *               when invert is false, or all tag ids except those in tagIds are to be searched,
     *               when invert is true
     */
    suspend fun queryTags(tagIds: Array<Any>, invert: Boolean = false): List<BookAndTagEntity>? {
        return BookDatabase.buildQueryForIds(
            "SELECT * FROM $BOOK_TAGS_TABLE",
            BOOK_TAGS_TAG_ID_COLUMN,
            tagIds,
            invert)?.let { queryTags(it) }
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
    open suspend fun addTagToBook(bookId: Long, tagId: Long) {
        add(BookAndTagEntity(0, tagId, bookId))
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
     * @param bookIds A list of book ids
     * @param tagIds A list of tag ids to be removed for the books
     * @param booksInvert A flag used to indicate whether bookIds contains the books ids where
     *                    tags are to be added, when invert is false, or all book ids except
     *                    those in bookIds to have their tags added, when invert is true
     * @param tagsInvert A flag used to indicate whether tagIds contains the tag ids to be added,
     *                   when invert is false, or all tag ids except those in tagIds are to be added,
     *                   when invert is true
     */
    @Transaction
    open suspend fun addTagsToBooks(bookIds: Array<Any>, tagIds: Array<Any>,
                                    booksInvert: Boolean = false, tagsInvert: Boolean = false) {
        val bookList = db.getBookDao().queryBookIds(bookIds, booksInvert)
        val tagList = db.getTagDao().queryTagIds(tagIds, tagsInvert)
        addTagsToBooks(bookList, tagList)
    }

    /**
     * Delete all tags from books
     * @param bookIds A list of book ids
     * @param invert A flag used to indicate whether bookIds contains the books ids whose
     *               tags are to be deleted, when invert is false, or all book ids except
     *               those in bookIds are to have their tags deleted, when invert is true
     * @param deleteTags A flag to indicate whether the tag in the Tags table should be deleted
     *                   if all of its books have been deleted
     */
    @Transaction
    open suspend fun deleteBooks(bookIds: Array<Any>, invert: Boolean, deleteTags: Boolean = false) {
        // Do we want to delete any books
        if (deleteTags) {
            // Yes, get the ids of tags that are affected
            val tags = queryBookIds(bookIds, invert)
            // Delete the book tag links
            deleteTagsForBooks(bookIds, invert)
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
            deleteTagsForBooks(bookIds, invert)
        }
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
abstract class AuthorDao {
    /**
     * Get the list of authors
     */
    @Query(value = "SELECT * FROM $AUTHORS_TABLE ORDER BY $LAST_NAME_COLUMN, $REMAINING_COLUMN")
    abstract suspend fun get(): List<Author>?

    /**
     * Raw Query for author cursor
     */
    @RawQuery(observedEntities = arrayOf(AuthorEntity::class))
    abstract fun getCursor(query: SupportSQLiteQuery): Cursor

    /**
     * Add multiple authors for a book
     * @param bookId The id of the book
     * @param authors The list of authors
     */
    @Transaction
    open suspend fun add(bookId: Long, authors: List<AuthorEntity>) {
         // Delete current authors of the books
        deleteBooks(arrayOf(bookId), false)
        // Add new authors
        for (author in authors)
            add(bookId, author)
    }

    /**
     * Add a single author for a book
     * @param bookId The id of the book
     * @param author The author entity
     */
    @Transaction
    open suspend fun add(bookId: Long, author: AuthorEntity) {
        // Find the author
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
     * Delete authors for a book
     * @param query The SQLite delete command that will delete the author links
     */
    @RawQuery(observedEntities = [BookAndAuthorEntity::class])
    protected abstract suspend fun delete(query: SupportSQLiteQuery): Int?

    /**
     * Delete all authors from books
     * @param bookIds A list of book ids
     * @param invert A flag used to indicate whether bookIds contains the books ids whose
     *               authors are to be deleted, when invert is false, or all book ids except
     *               those in bookIds are to have their authors deleted, when invert is true
    */
    @Transaction
    open suspend fun deleteBooks(bookIds: Array<Any>, invert: Boolean = false): Int {
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_AUTHORS_TABLE",
            BOOK_AUTHORS_BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { delete(it) }?: 0
    }

    /**
     * Query authors for books
     * @param query SQLite query to query the authors
     */
    @RawQuery(observedEntities = [BookAndAuthorEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<Long>?

    /**
     * Query author ids for a set of books
     * @param bookIds A list of book ids
     * @param invert A flag used to indicate whether bookIds contains the books ids whose
     *               authors are to be returned, when invert is false, or all book ids except
     *               those in bookIds are to have their authors returned, when invert is true
    */
    private suspend fun queryBookIds(bookIds: Array<Any>, invert: Boolean = false): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_AUTHORS_AUTHOR_ID_COLUMN FROM $BOOK_AUTHORS_TABLE",
            BOOK_AUTHORS_BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { queryBookIds(it) }
    }

    /**
     * Delete books for an author
     * @param authorId The id of the authors whose books are deleted
     */
    @Query("DELETE FROM $BOOK_AUTHORS_TABLE WHERE $BOOK_AUTHORS_AUTHOR_ID_COLUMN = :authorId")
    protected abstract suspend fun deleteAuthor(authorId: Long): Int

    /**
     * Delete an author
     * @param author The author entity to be deleted
     */
    @Delete
    protected abstract suspend fun delete(author: AuthorEntity)

    /**
     * Find an author by name
     * @param last The author last name
     * @param remaining The rest of the author name
     */
    @Query(value = "SELECT * FROM $AUTHORS_TABLE"
        + " WHERE $LAST_NAME_COLUMN LIKE :last AND $REMAINING_COLUMN LIKE :remaining")
    abstract suspend fun findByName(last: String, remaining: String): List<AuthorEntity>

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
    abstract suspend fun add(author: AuthorEntity) : Long

    /**
     * Add a book and author relationship
     * @param bookAndAuthor The link to be added
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun add(bookAndAuthor: BookAndAuthorEntity): Long

    /**
     * Delete all authors from books
     * @param bookIds A list of book ids
     * @param invert A flag used to indicate whether bookIds contains the books ids whose
     *               authors are to be deleted, when invert is false, or all book ids except
     *               those in bookIds are to have their authors deleted, when invert is true
     * @param deleteAuthors A flag to indicate whether the author in the Authors table should be deleted
     *                      if all of its books have been deleted
     */
    @Transaction
    open suspend fun delete(bookIds: Array<Any>, invert: Boolean, deleteAuthors: Boolean = true) {
        // Do we want to delete authors with no books?
        if (deleteAuthors) {
            // Yes, get the ids of the authors that are affects
            val authors = queryBookIds(bookIds, invert)
            // Delete the authors
            deleteBooks(bookIds, invert)
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
            deleteBooks(bookIds, invert)
        }
    }
}

/**
 * Category data access object
 */
@Dao
abstract class CategoryDao {
    /**
     * Get the list of categories
     */
    @Query(value = "SELECT * FROM $CATEGORIES_TABLE ORDER BY $CATEGORY_COLUMN")
    abstract suspend fun get(): List<Category>?

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
        deleteBooks(arrayOf(bookId), false)
        for (cat in categories)
            add(bookId, cat)
    }

    /**
     * Add a single category for a book
     * @param bookId The id o the book
     * @param category The category to add
     */
    @Transaction
    open suspend fun add(bookId: Long, category: CategoryEntity) {
        // Find the category
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
     * Delete categories for books
     * @param query The SQLite query to delete the categories
     */
    @RawQuery(observedEntities = [BookAndCategoryEntity::class])
    protected abstract suspend fun delete(query: SupportSQLiteQuery): Int?

    /**
     * Delete all categories from books
     * @param bookIds A list of book ids
     * @param invert A flag used to indicate whether bookIds contains the books ids whose
     *               categories are to be deleted, when invert is false, or all book ids except
     *               those in bookIds are to have their categories deleted, when invert is true
     */
    @Transaction
    open suspend fun deleteBooks(bookIds: Array<Any>, invert: Boolean = false): Int {
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_CATEGORIES_TABLE",
            BOOK_CATEGORIES_BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { delete(it) }?: 0
    }

    /**
     * Query categories for a book
     * @param query The SQLite query to get the ids
     */
    @RawQuery(observedEntities = [BookAndCategoryEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<Long>?

    /**
     * Query author ids for a set of books
     * @param bookIds A list of book ids
     * @param invert A flag used to indicate whether bookIds contains the books ids whose
     *               categories are to be returned, when invert is false, or all book ids except
     *               those in bookIds are to have their categories returned, when invert is true
     */
    private suspend fun queryBookIds(bookIds: Array<Any>, invert: Boolean = false): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_CATEGORIES_CATEGORY_ID_COLUMN FROM $BOOK_CATEGORIES_TABLE",
            BOOK_CATEGORIES_BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { queryBookIds(it) }
    }

    /**
     * Delete all books for a category
     * @param categoryId The id of the category
     */
    @Query("DELETE FROM $BOOK_CATEGORIES_TABLE WHERE $BOOK_CATEGORIES_CATEGORY_ID_COLUMN = :categoryId")
    protected abstract suspend fun deleteCategory(categoryId: Long): Int

    /**
     * Delete a category
     * @param category The category to delete
     */
    @Delete
    protected abstract suspend fun delete(category: CategoryEntity)

    /**
     * Find a category by name
     * @param category The name of the category
     */
    @Query(value = "SELECT * FROM $CATEGORIES_TABLE"
            + " WHERE $CATEGORY_COLUMN LIKE :category")
    abstract suspend fun findByName(category: String): List<CategoryEntity>

    /**
     * Find books by category
     * @param categoryId The id of the category
     * @param limit A limit on the number of book category links to return
     */
    @Query("SELECT * FROM $BOOK_CATEGORIES_TABLE WHERE $BOOK_CATEGORIES_CATEGORY_ID_COLUMN = :categoryId LIMIT :limit")
    abstract suspend fun findById(categoryId: Long, limit: Int = -1): List<BookAndCategoryEntity>

    /**
     * Delete all categories from books
     * @param bookIds A list of book ids
     * @param invert A flag used to indicate whether bookIds contains the books ids whose
     *               categories are to be deleted, when invert is false, or all book ids except
     *               those in bookIds are to have their categories deleted, when invert is true
     * @param deleteCategories A flag to indicate whether the category in the Categories table should be deleted
     *                      if all of its books have been deleted
     */
    @Transaction
    open suspend fun delete(bookIds: Array<Any>, invert: Boolean, deleteCategories: Boolean = true) {
        // Should we delete categories
        if (deleteCategories) {
            // Yes get the id of the categories affected
            val categories = queryBookIds(bookIds, invert)
            // Delete the book category links
            deleteBooks(bookIds)
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
            deleteBooks(bookIds)
        }
    }
}

@Dao
abstract class BookDao(private val db: BookDatabase) {
    /**
     * Add a book to the data base
     * @param book The book to add
     */
    @Insert
    protected abstract suspend fun add(book: BookEntity): Long

    /**
     * Delete books from the book table
     * @param query The SQLite query to delete the books
     */
    @RawQuery(observedEntities = [BookAndAuthors::class])
    protected abstract suspend fun delete(query: SupportSQLiteQuery): Int?

    /**
     * Delete books
     * @param bookIds A list of book ids
     * @param invert A flag used to indicate whether bookIds contains the books ids
     *               to be deleted, when invert is false, or all book ids except
     *               those in bookIds are to be deleted, when invert is true
     */
    @Transaction
    open suspend fun deleteBooks(bookIds: Array<Any>, invert: Boolean = false): Int {
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_TABLE",
            BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { delete(it) }?: 0
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
     * @param bookIds A list of book ids
     * @param invert A flag to return the ids in bookIds, when invert is false, or the
     *               ifs not in bookIds, when invert is true
     */
    suspend fun queryBookIds(bookIds: Array<Any>, invert: Boolean = false): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_ID_COLUMN FROM $BOOK_TABLE",
            BOOK_ID_COLUMN,
            bookIds,
            invert)?.let {query -> queryBookIds(query)?.map { it.id } }
    }

    /**
     * Delete a single book from the book table
     * @param book The book to delete
     */
    @Delete
    protected abstract suspend fun delete(book: BookEntity)

    /**
     * Update a book in the book table
     * @param book The book to update
     */
    @Update
    protected abstract suspend fun update(book: BookEntity)

    /**
     * Class to get unique columns in the book table
     */
    @TypeConverters(Converters::class)
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
    protected open suspend fun addOrUpdate(book: BookEntity) {
        // Assume adding, set the added and modified time to now
        val time = Calendar.getInstance().time
        book.added = time
        book.modified = time

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
            book.id = add(book)
        } else {
            // Had a conflict, get the id and time added and update the book
            book.id = ids.id
            book.added = ids.added
            update(book)
        }
    }

    /**
     * Add book with additional tags
     * @param book The book to add
     * @param tagIds The list of tag ids
     * @param invert A flag indicating whether to add the tags in tagIds or to add the tags
     *               not in tagIds
     */
    @Transaction
    open suspend fun addOrUpdate(book: BookAndAuthors, tagIds: Array<Any>? = null, invert: Boolean = false) {
        // Add or update the book
        addOrUpdate(book.book)

        // Delete existing thumbnails, if any
        deleteThumbFile(book.book.id, true)
        deleteThumbFile(book.book.id, false)

        // Add categories
        db.getCategoryDao().add(book.book.id, book.categories)
        // Add Authors
        db.getAuthorDao().add(book.book.id, book.authors)
        // Add Tags from book along with additional tags
        db.getTagDao().add(book.book.id, book.tags, tagIds, invert)
    }

    /**
     * Delete books
     * @param bookIds List of book ids
     * @param invert A flag to indicate whether to delete the books in bookIds, or to delete
     *               the books not in booksIds
     */
    @Transaction
    open suspend fun delete(bookIds: Array<Any>, invert: Boolean) {
        // Delete all tags for the books - keep tags with no books
        db.getBookTagDao().deleteBooks(bookIds, invert)
        // Delete all authors for the books - delete authors with no books
        db.getAuthorDao().delete(bookIds, invert, true)
        // Delete all categories for the book - delete categories with no books
        db.getCategoryDao().delete(bookIds, invert, true)

        // Delete all thumbnails
        queryBookIds(bookIds, invert)?.let {
            for (book in it) {
                deleteThumbFile(book, true)
                deleteThumbFile(book, false)
            }
        }

        // Finally delete the books
        deleteBooks(bookIds, invert)

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
    abstract fun getBooks(query: SupportSQLiteQuery): PagingSource<Int, BookAndAuthors>

    /**
     * Get books
     * @param filter The filter description used to filter and order the books
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
     * Get the thumbnail cache file for a book
     * @param bookId The book id
     * @param large True to get the large thumbnail file. False for the small thumbnail file.
     */
    private fun getThumbFile(bookId: Long, large: Boolean): File {
        return File(MainActivity.cache, "BiblioTech.Thumb.$bookId${if (large) kThumb else kSmallThumb}")
    }

    /**
     * Delete a thumbnail file for a book from the cache
     * @param bookId The book id
     * @param large True to get the large thumbnail file. False for the small thumbnail file.
     */
    private suspend fun deleteThumbFile(bookId: Long, large: Boolean) {
        try {
            withContext(Dispatchers.IO) { getThumbFile(bookId, large).delete() }
        } catch (e: Exception) {}
    }

    /**
     * Get a thumbnail for a book
     * @param bookId The book id
     * @param large True to get the large thumbnail file. False for the small thumbnail file.
     */
    suspend fun getThumbnail(bookId: Long, large: Boolean): Bitmap? {
        // Get the file path
        val file = getThumbFile(bookId, large)
        // Load the bitmap return null, if the load succeeds return the bitmap
        val result = loadBitmap(file)
        if (result != null)
                return result
        // If the file already exists, then don't try to download it again
        if (file.exists())
            return null

        var tmpFile: File
        do {
            // Get the URL to the image, return null if it fails
            val url = getThumbUrl(bookId, file, large)?: return null

            // Download the bitmap, return null if files
            tmpFile = downloadBitmap(url, file)?: return null

            // Move the downloaded bitmap to the proper file, retry if it fails
        } while (!moveFile(tmpFile, file))

        return loadBitmap(file)
    }

    /**
     * Load bitmap from a file
     * @param file The file holding the bitmap
     * @return The bitmap of null if the file doesn't exist
     */
    private suspend fun loadBitmap(file: File): Bitmap? {
        return withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
    }

    /**
     * Get the thumbnail URL for a book
     * @param bookId The book id
     * @param large True to get the large thumbnail file. False for the small thumbnail file.
     */
    @Transaction
    protected open suspend fun getThumbUrl(bookId: Long, file: File, large: Boolean): URL? {
        // Query the data base to get the URL. Return null if there isn't one
        val urlString = if (large) getLargeThumbnailUrl(bookId) else getSmallThumbnailUrl(bookId)
        urlString?: return null
        @Suppress("BlockingMethodInNonBlockingContext")
        return withContext(Dispatchers.IO)  {
            var url: URL? = null
            try {
                // Make sure the url is valid
                val tmpUrl = URL(urlString)
                // Create a new file. If it is deleted while downloading the bitmap
                // Then, the data base was updated and we try to get the bitmap again
                file.createNewFile()
                url = tmpUrl
            } catch (e: Exception) {}
            url
        }
    }

    /**
     * Move one file to another
     * @param from The source file
     * @param to The destination file
     */
    @Transaction
    protected open suspend fun moveFile(from: File, to: File): Boolean {
        // If the file, which we created before was deleted, then
        // the book was updated, and we need to try getting the thumbnail again
        if (!to.exists())
            return false

        try {
            // Move the tmp file to the real file
            @Suppress("BlockingMethodInNonBlockingContext")
            withContext(Dispatchers.IO) {
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {}
        return true
    }

    /**
     * Download the bitmap for a URL and cache
     * @param url The url to download
     * @param file The cache file
     */
    private suspend fun downloadBitmap(url: URL, file: File): File? {
        @Suppress("BlockingMethodInNonBlockingContext")
        return withContext(Dispatchers.IO) {
            var result = false
            var connection: HttpURLConnection? = null
            var output: BufferedOutputStream? = null
            var stream: InputStream? = null
            var buffered: BufferedInputStream? = null
            val tmpFile = File.createTempFile("tmp_bitmap", null, file.parentFile) ?: return@withContext null
            try {
                connection = url.openConnection() as HttpURLConnection
                output = BufferedOutputStream(FileOutputStream(tmpFile))
                stream = connection.inputStream
                buffered = BufferedInputStream(stream!!)
                val kBufSize = 4096
                val buf = ByteArray(kBufSize)
                var size: Int
                while (buffered.read(buf).also { size = it } >= 0) {
                    if (size > 0) output.write(buf, 0, size)
                }
                result = true
            } catch (e: MalformedURLException) {
            } catch (e: IOException) {
            }
            if (output != null) {
                try {
                    output.close()
                } catch (e: IOException) {
                    result = false
                }
            }
            if (buffered != null) {
                try {
                    buffered.close()
                } catch (e: IOException) {
                }
            }
            if (stream != null) {
                try {
                    stream.close()
                } catch (e: IOException) {
                }
            }
            connection?.disconnect()
            if (result)
                return@withContext tmpFile

            null
        }
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
        BookAndCategoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BookDatabase : RoomDatabase() {
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
         */
        fun initialize(context: Context) {
            if (mDb == null) {
                mDb = create(context)
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
         */
        private fun create(context: Context): BookDatabase {
            return Room.databaseBuilder(
                context, BookDatabase::class.java, DATABASE_FILENAME
            ).build()
        }

        /**
         * Create an expression for a query
         * @param column The column to query
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
        private fun getExpression(column: String?, ids: Array<Any>?, invert: Boolean?, args: MutableList<Any>): String? {
            // Return null if we select nothing
            if (column == null || (invert != true && ids?.isEmpty() != false))
                return null

            // Build the expression string
            val builder = StringBuilder()
            if (ids?.isNotEmpty() == true) {
                builder.append("( ")
                builder.append(column)
                if (invert == true)
                    builder.append(" NOT")
                builder.append(" IN ( ")
                // Semi-clever way to get n ?, ?, ... in a string
                builder.append(Array(ids.size) { "?" }.joinToString()).append(" ) )")
                args.addAll(ids)
            }
            return builder.toString()
        }

        /**
         * Build a query
         * @param command The SQLite command
         * @param condition The conditions of the query.
         * Each condition is 3 values in this order:
         *    column name
         *    value array
         *    invert boolean
         * Multiple conditions are joined by AND
         */
        internal fun buildQueryForIds(command: String, vararg condition: Any?): SupportSQLiteQuery? {
            val builder = StringBuilder()
            val args = ArrayList<Any>()

            // Start with the command
            builder.append(command)
            // Then the conditions
            var prefix = " WHERE "
            var select = false
            // Loop through the conditions in the varargs
            for (i in 0..condition.size - 3 step 3) {
                @Suppress("UNCHECKED_CAST")
                // Get the expression and ignore conditions that select nothing
                getExpression(
                    condition[i] as? String,
                    condition[i + 1] as? Array<Any>,
                    condition[i + 2] as? Boolean,
                    args
                )?.let {expr ->
                    // We got an expression
                    select = true
                    if (expr != "") {
                        // Start with WHERE and then use AND to separate expressions
                        builder.append(prefix)
                        builder.append(expr)
                        prefix = " AND "
                    }
                }
            }

            // Return the query if there was one
            return if (select)
                SimpleSQLiteQuery(builder.toString(), args.toArray())
            else
                return null     // No conditions selected anything
        }
    }

    abstract fun getBookDao(): BookDao
    abstract fun getTagDao(): TagDao
    abstract fun getBookTagDao(): BookTagDao
    abstract fun getAuthorDao(): AuthorDao
    abstract fun getCategoryDao(): CategoryDao
}
