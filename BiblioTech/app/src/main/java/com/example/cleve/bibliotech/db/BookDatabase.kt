package com.example.cleve.bibliotech.db

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcel
import android.os.Parcelable
import androidx.paging.PagingSource
import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.cleve.bibliotech.MainActivity
import kotlinx.coroutines.Dispatchers
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

const val BOOK_TABLE = "books"
const val BOOK_ID_COLUMN = "books_id"
const val VOLUME_ID_COLUMN = "books_volume_id"
const val SOURCE_ID_COLUMN = "books_source_id"
const val ISBN_COLUMN = "books_isbn"
const val TITLE_COLUMN = "books_title"
const val SUBTITLE_COLUMN = "books_subtitle"
const val DESCRIPTION_COLUMN = "books_description"
const val PAGE_COUNT_COLUMN = "books_page_count"
const val BOOK_COUNT_COLUMN = "books_count"
const val VOLUME_LINK = "books_volume_link"
const val RATING_COLUMN = "books_rating"
const val DATE_ADDED_COLUMN = "books_date_added"
const val DATE_MODIFIED_COLUMN = "books_date_modified"
const val SMALL_THUMB_COLUMN = "books_small_thumb"
const val LARGE_THUMB_COLUMN = "books_large_thumb"
val ALL_BOOK_COLUMNS = arrayOf(
    BOOK_ID_COLUMN, VOLUME_ID_COLUMN, SOURCE_ID_COLUMN,
    ISBN_COLUMN, TITLE_COLUMN, SUBTITLE_COLUMN,
    DESCRIPTION_COLUMN, PAGE_COUNT_COLUMN, BOOK_COUNT_COLUMN,
    VOLUME_LINK, RATING_COLUMN, DATE_ADDED_COLUMN,
    DATE_MODIFIED_COLUMN, SMALL_THUMB_COLUMN, LARGE_THUMB_COLUMN)
const val AUTHORS_TABLE = "authors"
const val AUTHORS_ID_COLUMN = "authors_id"
const val LAST_NAME_COLUMN = "authors_last_name"
const val REMAINING_COLUMN = "authors_remaining"
const val BOOK_AUTHORS_TABLE = "book_authors"
const val BOOK_AUTHORS_ID_COLUMN = "book_authors_id"
const val BOOK_AUTHORS_BOOK_ID_COLUMN = "book_authors_book_id"
const val BOOK_AUTHORS_AUTHOR_ID_COLUMN = "book_authors_author_id"
const val CATEGORIES_TABLE = "categories"
const val CATEGORIES_ID_COLUMN = "categories_id"
const val CATEGORY_COLUMN = "categories_category"
const val BOOK_CATEGORIES_TABLE = "book_categories"
const val BOOK_CATEGORIES_ID_COLUMN = "book_categories_id"
const val BOOK_CATEGORIES_BOOK_ID_COLUMN = "book_categories_book_id"
const val BOOK_CATEGORIES_CATEGORY_ID_COLUMN = "book_categories_category_id"
const val TAGS_TABLE = "tags"
const val TAGS_ID_COLUMN = "tags_id"
const val TAGS_NAME_COLUMN = "tags_name"
const val TAGS_DESC_COLUMN = "tags_desc"
const val BOOK_TAGS_TABLE = "book_tags"
const val BOOK_TAGS_ID_COLUMN = "book_tags_id"
const val BOOK_TAGS_TAG_ID_COLUMN = "book_tags_tag_id"
const val BOOK_TAGS_BOOK_ID_COLUMN = "book_tags_book_id"

const val kSmallThumb = ".small.png"
const val kThumb = ".png"

const val kDesc = "DESC"
const val kAsc = "ASC"

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

interface Selectable {
    val id: Long
    var selected: Boolean
}

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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookEntity

        if (id != other.id) return false
        if (volumeId != other.volumeId) return false
        if (ISBN != other.ISBN) return false
        if (title != other.title) return false
        if (subTitle != other.subTitle) return false
        if (description != other.description) return false
        if (smallThumb != other.smallThumb) return false
        if (largeThumb != other.largeThumb) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (volumeId?.hashCode() ?: 0)
        result = 31 * result + (ISBN?.hashCode() ?: 0)
        result = 31 * result + title.hashCode()
        result = 31 * result + subTitle.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + (smallThumb?.hashCode() ?: 0)
        result = 31 * result + (largeThumb?.hashCode() ?: 0)
        return result
    }


}

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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuthorEntity

        if (id != other.id) return false
        if (lastName != other.lastName) return false
        if (remainingName != other.remainingName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + remainingName.hashCode()
        return result
    }
}

open class Author(
    @Embedded var author: AuthorEntity
) : Selectable {
    @Ignore override var selected = false
    override val id: Long
        get() = author.id
}

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

@Entity(tableName = CATEGORIES_TABLE,
    indices = [
        Index(value = [CATEGORIES_ID_COLUMN],unique = true),
        Index(value = [CATEGORY_COLUMN],unique = true)
    ])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = CATEGORIES_ID_COLUMN) var id: Long,
    @ColumnInfo(name = CATEGORY_COLUMN,defaultValue = "",collate = ColumnInfo.NOCASE) var category: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CategoryEntity

        if (id != other.id) return false
        if (category != other.category) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + category.hashCode()
        return result
    }
}

open class Category(
    @Embedded var category: CategoryEntity
) : Selectable {
    @Ignore override var selected = false
    override val id: Long
        get() = category.id
}

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


open class Tag(
    @Embedded var tag: TagEntity
) : Selectable {
    @Ignore override var selected = false
    override val id: Long
        get() = tag.id

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Tag

        if (tag != other.tag) return false
        if (selected != other.selected) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tag.hashCode()
        result = 31 * result + selected.hashCode()
        return result
    }
}

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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookAndTagEntity

        if (id != other.id) return false
        if (bookId != other.bookId) return false
        if (tagId != other.tagId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + bookId.hashCode()
        result = 31 * result + tagId.hashCode()
        return result
    }
}

open class BookAndAuthors(
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
    var tags: List<TagEntity>
) : Parcelable, Selectable {
    @Ignore override var selected: Boolean = false
    override val id: Long
        get() { return book.id }

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

@Dao
abstract class TagDao(private val db: BookDatabase) {
    // Get the list of tags
    @Query(value = "SELECT * FROM $TAGS_TABLE ORDER BY $TAGS_NAME_COLUMN")
    abstract fun get(): PagingSource<Int, Tag>

    @Query(value = "SELECT * FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN = :tagId LIMIT 1")
    abstract suspend fun get(tagId: Long): TagEntity?

    // Add a tag
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun internalAdd(tag: TagEntity): Long


    // Add a single tag for a book
    @Transaction
    open suspend fun add(bookId: Long, tag: TagEntity) {
        // Find the author
        val existing: TagEntity? = findByName(tag.name)
        tag.id = existing?.id ?: add(tag)
        db.getBookTagDao().add(BookAndTagEntity(0, tag.id, bookId))
    }

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
    // Add/update a tag and resolve conflict
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

    protected data class TagId(
        @ColumnInfo(name = TAGS_ID_COLUMN) val id: Long
    )

    // Query authors for a book
    @RawQuery(observedEntities = [TagEntity::class])
    protected abstract suspend fun queryTagIds(query: SupportSQLiteQuery): List<TagId>?

    suspend fun queryTagIds(tagIds: Array<Any>, invert: Boolean = false): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $TAGS_ID_COLUMN FROM $TAGS_TABLE",
            TAGS_ID_COLUMN,
            tagIds,
            invert)?.let {query -> queryTagIds(query)?.map { it.id } }
    }

    // Delete tags
    @RawQuery(observedEntities = [TagEntity::class])
    protected abstract suspend fun delete(query: SupportSQLiteQuery): Int?

    // Delete a tag
    @Query("DELETE FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN = :tagId")
    abstract suspend fun delete(tagId: Long): Int

    @Transaction
    open suspend fun delete(tagIds: Array<Any>, invert: Boolean): Int? {
        db.getBookTagDao().deleteTagsForTags(tagIds, invert)
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $TAGS_TABLE",
            TAGS_ID_COLUMN,
            tagIds,
            invert)?.let { delete(it) }?: 0
    }

    // Find an tag by name
    @Query(value = "SELECT * FROM $TAGS_TABLE"
            + " WHERE $TAGS_NAME_COLUMN LIKE :name LIMIT 1")
    abstract suspend fun findByName(name: String): TagEntity?
}

@Dao
abstract class BookTagDao(private val db:BookDatabase) {
    // Add a tag to a book
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun add(bookAndTag: BookAndTagEntity): Long

    // Delete tags for a book
    @RawQuery(observedEntities = [BookAndTagEntity::class])
    protected abstract suspend fun delete(query: SupportSQLiteQuery): Int?

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

    @Transaction
    open suspend fun deleteTagsForTags(tagIds: Array<Any>, invert: Boolean = false): Int {
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_TAGS_TABLE",
            BOOK_TAGS_TAG_ID_COLUMN,
            tagIds,
            invert)?.let { delete(it) }?: 0
    }

    // Query tags for a book
    @RawQuery(observedEntities = [BookAndTagEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<Long>?

    private suspend fun queryBookIds(bookIds: Array<Any>, invert: Boolean = false): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_TAGS_TAG_ID_COLUMN FROM $BOOK_TAGS_TABLE",
            BOOK_TAGS_BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { queryBookIds(it) }
    }

    // Query tags for a book
    @RawQuery(observedEntities = [BookAndTagEntity::class])
    protected abstract suspend fun queryTags(query: SupportSQLiteQuery): List<BookAndTagEntity>?

    suspend fun queryTags(tagIds: Array<Any>, invert: Boolean = false): List<BookAndTagEntity>? {
        return BookDatabase.buildQueryForIds(
            "SELECT * FROM $BOOK_TAGS_TABLE",
            BOOK_TAGS_TAG_ID_COLUMN,
            tagIds,
            invert)?.let { queryTags(it) }
    }

    // Delete books for a tag
    @Query("DELETE FROM $BOOK_TAGS_TABLE WHERE $BOOK_TAGS_TAG_ID_COLUMN = :tagId")
    protected abstract suspend fun deleteTag(tagId: Long): Int

    // Add a single tag for a book
    @Transaction
    open suspend fun addTagToBook(bookId: Long, tagId: Long) {
        add(BookAndTagEntity(0, tagId, bookId))
    }

    @Transaction
    open suspend fun addTagsToBook(bookId: Long, tagIds: List<Long>?) {
        if (tagIds != null) {
            for (tag in tagIds)
                addTagToBook(bookId, tag)
        }
    }

    @Transaction
    open suspend fun addTagsToBooks(bookIds: List<Long>?, tagIds: List<Long>?) {
        if (bookIds != null) {
            for (book in bookIds)
                addTagsToBook(book, tagIds)
        }
    }

    @Transaction
    open suspend fun addTagsToBooks(bookIds: Array<Any>, tagIds: Array<Any>,
                                    booksInvert: Boolean = false, tagsInvert: Boolean = false) {
        val bookList = db.getBookDao().queryBookIds(bookIds, booksInvert)
        val tagList = db.getTagDao().queryTagIds(tagIds, tagsInvert)
        addTagsToBooks(bookList, tagList)
    }

    @Transaction
    open suspend fun deleteBooks(bookIds: Array<Any>, invert: Boolean, deleteTags: Boolean = false) {
        if (deleteTags) {
            val tags = queryBookIds(bookIds, invert)
            deleteTagsForBooks(bookIds, invert)
            tags?.let {
                for (tag in it) {
                    val list: List<BookAndTagEntity> = findById(tag, 1)
                    if (list.isEmpty())
                        db.getTagDao().delete(tag)
                }
            }
        } else {
            deleteTagsForBooks(bookIds, invert)
        }
    }

    @Query("SELECT * FROM $BOOK_TAGS_TABLE WHERE $BOOK_TAGS_TAG_ID_COLUMN = :tagId LIMIT :limit")
    abstract suspend fun findById(tagId: Long, limit: Int = -1): List<BookAndTagEntity>
}

@Dao
abstract class AuthorDao {
    // Get the list of authors
    @Query(value = "SELECT * FROM $AUTHORS_TABLE ORDER BY $LAST_NAME_COLUMN, $REMAINING_COLUMN")
    abstract suspend fun get(): List<Author>?

    // Add multiple authors for a book
    @Transaction
    open suspend fun add(bookId: Long, authors: List<AuthorEntity>) {
        deleteBooks(arrayOf(bookId), false)
        for (author in authors)
            add(bookId, author)
    }

    // Add a single author for a book
    @Transaction
    open suspend fun add(bookId: Long, author: AuthorEntity) {
        // Find the author
        val list: List<AuthorEntity> = findByName(author.lastName, author.remainingName)
        author.id = if (list.isNotEmpty()) {
            list[0].id
        } else {
            add(author)
        }
        add(BookAndAuthorEntity(0, author.id, bookId))
    }

    // Delete authors for a book
    @RawQuery(observedEntities = [BookAndAuthorEntity::class])
    protected abstract suspend fun delete(query: SupportSQLiteQuery): Int?

    @Transaction
    open suspend fun deleteBooks(bookIds: Array<Any>, invert: Boolean = false): Int {
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_AUTHORS_TABLE",
            BOOK_AUTHORS_BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { delete(it) }?: 0
    }

    // Query authors for a book
    @RawQuery(observedEntities = [BookAndAuthorEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<Long>?

    private suspend fun queryBookIds(bookIds: Array<Any>, invert: Boolean = false): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_AUTHORS_AUTHOR_ID_COLUMN FROM $BOOK_AUTHORS_TABLE",
            BOOK_AUTHORS_BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { queryBookIds(it) }
    }

    // Delete books for an author
    @Query("DELETE FROM $BOOK_AUTHORS_TABLE WHERE $BOOK_AUTHORS_AUTHOR_ID_COLUMN = :authorId")
    protected abstract suspend fun deleteAuthor(authorId: Long): Int

    @Delete
    protected abstract suspend fun delete(author: AuthorEntity)

    // Find an author by name
    @Query(value = "SELECT * FROM $AUTHORS_TABLE"
        + " WHERE $LAST_NAME_COLUMN = :last AND $REMAINING_COLUMN = :remaining")
    abstract suspend fun findByName(last: String, remaining: String): List<AuthorEntity>

    @Query("SELECT * FROM $BOOK_AUTHORS_TABLE WHERE $BOOK_AUTHORS_AUTHOR_ID_COLUMN = :authorId LIMIT :limit")
    abstract suspend fun findById(authorId: Long, limit: Int = -1): List<BookAndAuthorEntity>

    // add an author
    @Insert
    abstract suspend fun add(author: AuthorEntity) : Long

    // add a book and author relationship
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun add(bookAndAuthor: BookAndAuthorEntity): Long

    @Transaction
    open suspend fun delete(bookIds: Array<Any>, invert: Boolean, deleteAuthors: Boolean = true) {
        if (deleteAuthors) {
            val authors = queryBookIds(bookIds, invert)
            deleteBooks(bookIds, invert)
            authors?.let {
                for (author in it) {
                    val list: List<BookAndAuthorEntity> = findById(author, 1)
                    if (list.isEmpty())
                        deleteAuthor(author)
                }
            }
        } else {
            deleteBooks(bookIds, invert)
        }
    }
}

@Dao
abstract class CategoryDao {
    // Get the list of authors
    @Query(value = "SELECT * FROM $CATEGORIES_TABLE ORDER BY $CATEGORY_COLUMN")
    abstract suspend fun get(): List<Category>?

    @Insert
    abstract suspend fun add(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun add(bookCategory: BookAndCategoryEntity) : Long

    // Add multiple categories for a book
    @Transaction
    open suspend fun add(bookId: Long, categories: List<CategoryEntity>) {
        deleteBooks(arrayOf(bookId), false)
        for (cat in categories)
            add(bookId, cat)
    }

    // Add a single categories for a book
    @Transaction
    open suspend fun add(bookId: Long, category: CategoryEntity) {
        // Find the author
        val list: List<CategoryEntity> = findByName(category.category)
        category.id = if (list.isNotEmpty()) {
            list[0].id
        } else {
            add(category)
        }
        add(BookAndCategoryEntity(
            id = 0,
            categoryId = category.id,
            bookId = bookId
        ))
    }

    // Delete categories for a book
    @RawQuery(observedEntities = [BookAndCategoryEntity::class])
    protected abstract suspend fun delete(query: SupportSQLiteQuery): Int?

    @Transaction
    open suspend fun deleteBooks(bookIds: Array<Any>, invert: Boolean = false): Int {
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_CATEGORIES_TABLE",
            BOOK_CATEGORIES_BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { delete(it) }?: 0
    }

    // Query categories for a book
    @RawQuery(observedEntities = [BookAndCategoryEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<Long>?

    private suspend fun queryBookIds(bookIds: Array<Any>, invert: Boolean = false): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_CATEGORIES_CATEGORY_ID_COLUMN FROM $BOOK_CATEGORIES_TABLE",
            BOOK_CATEGORIES_BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { queryBookIds(it) }
    }

    // Delete books for a category
    @Query("DELETE FROM $BOOK_CATEGORIES_TABLE WHERE $BOOK_CATEGORIES_CATEGORY_ID_COLUMN = :categoryId")
    protected abstract suspend fun deleteCategory(categoryId: Long): Int

    @Delete
    protected abstract suspend fun delete(category: CategoryEntity)

    // Find an author by name
    @Query(value = "SELECT * FROM $CATEGORIES_TABLE"
            + " WHERE $CATEGORY_COLUMN = :category")
    abstract suspend fun findByName(category: String): List<CategoryEntity>

    @Query("SELECT * FROM $BOOK_CATEGORIES_TABLE WHERE $BOOK_CATEGORIES_CATEGORY_ID_COLUMN = :categoryId LIMIT :limit")
    abstract suspend fun findById(categoryId: Long, limit: Int = -1): List<BookAndCategoryEntity>

    @Transaction
    open suspend fun delete(bookIds: Array<Any>, invert: Boolean, deleteCategories: Boolean = true) {
        if (deleteCategories) {
            val categories = queryBookIds(bookIds, invert)
            deleteBooks(bookIds)
            categories?.let {
                for (category in it) {
                    val list: List<BookAndCategoryEntity> = findById(category, 1)
                    if (list.isEmpty())
                        deleteCategory(category)
                }
            }
        } else {
            deleteBooks(bookIds)
        }
    }
}

@Dao
abstract class BookDao(private val db: BookDatabase) {
    // Add a book to the data base
    @Insert
    protected abstract suspend fun add(book: BookEntity): Long

    @RawQuery(observedEntities = [BookAndAuthors::class])
    protected abstract suspend fun delete(query: SupportSQLiteQuery): Int?

    @Transaction
    open suspend fun deleteBooks(bookIds: Array<Any>, invert: Boolean = false): Int {
        return BookDatabase.buildQueryForIds(
            "DELETE FROM $BOOK_TABLE",
            BOOK_ID_COLUMN,
            bookIds,
            invert)?.let { delete(it) }?: 0
    }

    protected data class BookId(
        @ColumnInfo(name = BOOK_ID_COLUMN) val id: Long
    )
    // Query authors for a book
    @RawQuery(observedEntities = [BookEntity::class])
    protected abstract suspend fun queryBookIds(query: SupportSQLiteQuery): List<BookId>?

    suspend fun queryBookIds(bookIds: Array<Any>, invert: Boolean = false): List<Long>? {
        return BookDatabase.buildQueryForIds(
            "SELECT $BOOK_ID_COLUMN FROM $BOOK_TABLE",
            BOOK_ID_COLUMN,
            bookIds,
            invert)?.let {query -> queryBookIds(query)?.map { it.id } }
    }

    @Delete
    protected abstract suspend fun delete(book: BookEntity)

    @Update
    protected abstract suspend fun update(book: BookEntity)

    @TypeConverters(Converters::class)
    protected data class ConflictIds(
        @ColumnInfo(name = BOOK_ID_COLUMN) val id: Long,
        @ColumnInfo(name = VOLUME_ID_COLUMN) val volumeId: String?,
        @ColumnInfo(name = SOURCE_ID_COLUMN) val sourceId: String?,
        @ColumnInfo(name = ISBN_COLUMN) val ISBN: String?,
        @ColumnInfo(name = DATE_ADDED_COLUMN) val added: Date
    )

    @RawQuery
    protected abstract suspend fun findConflict(query: SupportSQLiteQuery): ConflictIds?

    private suspend fun findConflict(volumeId: String?, sourceId: String?, ISBN: String?): ConflictIds? {
        if (volumeId == null && ISBN == null)
            return null
        val args = ArrayList<String?>(3)

        var query =
            "SELECT $BOOK_ID_COLUMN, $VOLUME_ID_COLUMN, $SOURCE_ID_COLUMN, $ISBN_COLUMN, $DATE_ADDED_COLUMN FROM $BOOK_TABLE" +
            " WHERE"

        if (volumeId != null) {
            query += " ( $VOLUME_ID_COLUMN = ? AND $SOURCE_ID_COLUMN = ? )"
            args.add(volumeId)
            args.add(sourceId)
        }

        if (ISBN != null) {
            if (volumeId != null)
                query += " OR"
            query += " $ISBN_COLUMN = ?"
            args.add(ISBN)
        }

        query += " LIMIT 1"

        return findConflict(SimpleSQLiteQuery(query, args.toArray()))
    }

    @Transaction
    protected open suspend fun addOrUpdate(book: BookEntity) {
        val time = Calendar.getInstance().time
        book.added = time
        book.modified = time
        if (book.volumeId != null || book.sourceId != null) {
            book.volumeId = book.volumeId?: ""
            book.sourceId = book.sourceId?: ""
        }
        val ids = findConflict(book.volumeId, book.sourceId, book.ISBN)
        if (ids == null) {
            book.id = 0
            book.id = add(book)
        } else {
            book.id = ids.id
            book.added = ids.added
            update(book)
        }
    }

    // Add book from description
    @Transaction
    open suspend fun addOrUpdate(book: BookAndAuthors, tagIds: Array<Any>? = null, invert: Boolean = false) {
        addOrUpdate(book.book)
        deleteThumbFile(book.book.id, true)
        deleteThumbFile(book.book.id, false)

        db.getCategoryDao().add(book.book.id, book.categories)
        db.getAuthorDao().add(book.book.id, book.authors)
        db.getTagDao().add(book.book.id, book.tags, tagIds, invert)
    }

    // Delete book from description
    @Transaction
    open suspend fun delete(bookIds: Array<Any>, invert: Boolean) {
        db.getBookTagDao().deleteBooks(bookIds, invert)
        db.getAuthorDao().delete(bookIds, invert, true)
        db.getCategoryDao().delete(bookIds, invert, true)

        queryBookIds(bookIds, invert)?.let {
            for (book in it) {
                deleteThumbFile(book, true)
                deleteThumbFile(book, false)
            }
        }
        deleteBooks(bookIds, invert)

    }

    @Transaction
    @Query(value = "SELECT * FROM $BOOK_TABLE"
            + " WHERE $BOOK_ID_COLUMN = :bookId LIMIT 1")
    abstract suspend fun getBook(bookId: Long): BookAndAuthors?

    @Transaction
    @Query(value = "SELECT * FROM $BOOK_TABLE"
            + " WHERE $ISBN_COLUMN = :ISBN LIMIT 1")
    abstract suspend fun getBookByISBN(ISBN: String): BookAndAuthors?

    @Transaction
    @Query(value = "SELECT * FROM $BOOK_TABLE"
            + " WHERE $VOLUME_ID_COLUMN = :volumeId LIMIT 1")
    abstract suspend fun getBookByVolume(volumeId: String): BookAndAuthors?

    @RawQuery
    protected abstract suspend fun getBooksRaw(query: SupportSQLiteQuery): List<BookAndAuthors>?

    @RawQuery(observedEntities = [BookAndAuthors::class])
    abstract fun getBooks(query: SupportSQLiteQuery): PagingSource<Int, BookAndAuthors>

    fun getBooks(filter: BookFilter?): PagingSource<Int, BookAndAuthors> {
        return getBooks(BookFilter.buildFilterQuery(filter))
    }

    // Thumbnails
    @Query("SELECT $SMALL_THUMB_COLUMN FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = :bookId LIMIT 1")
    protected abstract suspend fun getSmallThumbnailUrl(bookId: Long): String?

    @Query("SELECT $LARGE_THUMB_COLUMN FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = :bookId LIMIT 1")
    protected abstract suspend fun getLargeThumbnailUrl(bookId: Long): String?

    private fun getThumbFile(bookId: Long, large: Boolean): File {
        return File(MainActivity.cache, "BiblioTech.Thumb.$bookId${if (large) kThumb else kSmallThumb}")
    }

    private suspend fun deleteThumbFile(bookId: Long, large: Boolean) {
        try {
            withContext(Dispatchers.IO) { getThumbFile(bookId, large).delete() }
        } catch (e: Exception) {}
    }

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

    // Load bitmap from a file
    // If the file doesn't exist, return null.
    private suspend fun loadBitmap(file: File): Bitmap? {
        return withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
    }

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
        private const val DATABASE_FILENAME = "books_database.db"
        private var mDb: BookDatabase? = null
        val db: BookDatabase
            get() = mDb!!

        fun initialize(context: Context) {
            if (mDb == null) {
                mDb = create(context)
            }
        }

        fun close() {
            mDb?.close()
            mDb = null
        }

        private fun create(context: Context): BookDatabase {
            return Room.databaseBuilder(
                context, BookDatabase::class.java, DATABASE_FILENAME
            ).build()
        }

        private fun getExpression(column: String?, ids: Array<Any>?, invert: Boolean?, args: MutableList<Any>): String? {
            if (column == null || (invert != true && ids?.isEmpty() != false))
                return null

            val builder = StringBuilder()
            if (ids?.isNotEmpty() == true) {
                builder.append("( ")
                builder.append(column)
                if (invert == true)
                    builder.append(" NOT")
                builder.append(" IN ( ")
                builder.append(Array(ids.size) { "?" }.joinToString()).append(" ) )")
                args.addAll(ids)
            }
            return builder.toString()
        }

        internal fun buildQueryForIds(command: String, vararg condition: Any?): SupportSQLiteQuery? {
            val builder = StringBuilder()
            val args = ArrayList<Any>()
            builder.append(command)
            var prefix = " WHERE "
            var select = false
            for (i in 0..condition.size - 3 step 3) {
                @Suppress("UNCHECKED_CAST")
                getExpression(
                    condition[i] as? String,
                    condition[i + 1] as? Array<Any>,
                    condition[i + 2] as? Boolean,
                    args
                )?.let {expr ->
                    select = true
                    if (expr != "") {
                        builder.append(prefix)
                        builder.append(expr)
                        prefix = " AND "
                    }
                }
            }

            return if (select)
                SimpleSQLiteQuery(builder.toString(), args.toArray())
            else
                return null
        }
    }

    abstract fun getBookDao(): BookDao
    abstract fun getTagDao(): TagDao
    abstract fun getBookTagDao(): BookTagDao
    abstract fun getAuthorDao(): AuthorDao
    abstract fun getCategoryDao(): CategoryDao
}
