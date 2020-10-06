package com.example.cleve.bibliotech.db

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.cleve.bibliotech.MainActivity
import java.io.*
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.collections.ArrayList


private const val BOOK_TABLE = "books"
private const val BOOK_ID_COLUMN = "books_id"
private const val VOLUME_ID_COLUMN = "books_volume_id"
private const val SOURCE_ID_COLUMN = "books_source_id"
private const val ISBN_COLUMN = "books_isbn"
private const val TITLE_COLUMN = "books_title"
private const val SUBTITLE_COLUMN = "books_subtitle"
private const val DESCRIPTION_COLUMN = "books_description"
private const val PAGE_COUNT_COLUMN = "books_page_count"
private const val BOOK_COUNT_COLUMN = "books_count"
private const val VOLUME_LINK = "books_volume_link"
private const val RATING_COLUMN = "books_rating"
private const val DATE_ADDED_COLUMN = "books_date_added"
private const val DATE_MODIFIED_COLUMN = "books_date_modified"
private const val SMALL_THUMB_COLUMN = "books_small_thumb"
private const val LARGE_THUMB_COLUMN = "books_large_thumb"
private const val AUTHORS_TABLE = "authors"
private const val AUTHORS_ID_COLUMN = "authors_id"
private const val LAST_NAME_COLUMN = "authors_last_name"
private const val REMAINING_COLUMN = "authors_remaining"
private const val BOOK_AUTHORS_TABLE = "book_authors"
private const val BOOK_AUTHORS_ID_COLUMN = "book_authors_id"
private const val BOOK_AUTHORS_BOOK_ID_COLUMN = "book_authors_book_id"
private const val BOOK_AUTHORS_AUTHOR_ID_COLUMN = "book_authors_author_id"
private const val CATEGORIES_TABLE = "categories"
private const val CATEGORIES_ID_COLUMN = "categories_id"
private const val CATEGORY_COLUMN = "categories_category"
private const val BOOK_CATEGORIES_TABLE = "book_categories"
private const val BOOK_CATEGORIES_ID_COLUMN = "book_categories_id"
private const val BOOK_CATEGORIES_BOOK_ID_COLUMN = "book_categories_book_id"
private const val BOOK_CATEGORIES_CATEGORY_ID_COLUMN = "book_categories_category_id"
private const val TAGS_TABLE = "tags"
private const val TAGS_ID_COLUMN = "tags_id"
private const val TAGS_NAME_COLUMN = "tags_name"
private const val BOOK_TAGS_TABLE = "book_tags"
private const val BOOK_TAGS_ID_COLUMN = "book_tags_id"
private const val BOOK_TAGS_TAG_ID_COLUMN = "book_tags_tag_id"
private const val BOOK_TAGS_BOOK_ID_COLUMN = "book_tags_book_id"

private const val kSmallThumb = ".small.png"
private const val kThumb = ".png"

private val SORT_ORDER: HashMap<String, Comparator<BookAndAuthors>> = hashMapOf(
    BookEntity.SORT_BY_AUTHOR_LAST_FIRST to compareBy(
        { if (it.authors.isNotEmpty()) it.authors[0].lastName else "" },
        { if (it.authors.isNotEmpty()) it.authors[0].remainingName else "" }
    )
)

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
    companion object {
        const val SORT_BY_AUTHOR_LAST_FIRST = "authorsLastFirst"
    }

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
    @ColumnInfo(name = LAST_NAME_COLUMN,defaultValue = "") var lastName: String,
    @ColumnInfo(name = REMAINING_COLUMN,defaultValue = "") var remainingName: String
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
    @ColumnInfo(name = CATEGORY_COLUMN,defaultValue = "") var category: String
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
    @ColumnInfo(name = TAGS_NAME_COLUMN) var name: String
)

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
) : Parcelable {
    @Ignore var selected: Boolean = false

    constructor(book: BookEntity, selected: Boolean, authors: List<AuthorEntity>,
                categories: List<CategoryEntity>, tags: List<TagEntity>) : this(book, authors, categories, tags) {
        this.selected = selected
    }

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

    override fun hashCode(): Int {
        var result = book.hashCode()
        result = 31 * result + authors.hashCode()
        result = 31 * result + categories.hashCode()
        result = 31 * result + tags.hashCode()
        return result
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        writeBook(dest, book)
        dest.writeInt(if (selected) 1 else 0)
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
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<BookAndAuthors> {
            override fun createFromParcel(src: Parcel): BookAndAuthors {
                val book = readBook(src)
                val selected = src.readInt() != 0
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

                return BookAndAuthors(book, selected, authors, categories, tags)
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
            return TagEntity(
                id = id,
                name = name?:""
            )
        }
    }
}

@Dao
abstract class TagDao {
    // Get the list of tags
    @Query(value = "SELECT * FROM $TAGS_TABLE ORDER BY $TAGS_NAME_COLUMN")
    abstract fun get(): List<TagEntity>

    // Add a tag
    @Insert
    abstract fun add(tag: TagEntity): Long

    // Add a tag to a book
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract fun add(bookAndTag: BookAndTagEntity): Long

    // Delete tags for a book
    @Query("DELETE FROM $BOOK_TAGS_TABLE WHERE $BOOK_TAGS_BOOK_ID_COLUMN = :bookId")
    protected abstract fun deleteBook(bookId: Long): Int

    // Delete books for a tag
    @Query("DELETE FROM $BOOK_TAGS_TABLE WHERE $BOOK_TAGS_TAG_ID_COLUMN = :tagId")
    protected abstract fun deleteTag(tagId: Long): Int

    @Delete
    protected abstract fun deleteTag(tag: TagEntity)

    // Add a single tag for a book
    @Transaction
    open fun add(bookId: Long, tag: TagEntity) {
        // Find the author
        val list: List<TagEntity> = findByName(tag.name)
        tag.id = if (list.isNotEmpty()) {
            list[0].id
        } else {
            add(tag)
        }
        add(BookAndTagEntity(0, tag.id, bookId))
    }

    @Transaction
    open fun add(bookId: Long, tags: List<TagEntity>) {
        deleteBook(bookId)
        for (tag in tags)
            add(bookId, tag)
    }

    @Transaction
    open fun delete(book: BookAndAuthors, deleteTags: Boolean = false) {
        deleteBook(book.book.id)
        if (deleteTags) {
            for (tag in book.tags) {
                val list: List<BookAndTagEntity> = findById(tag.id, 1)
                if (list.isEmpty())
                    deleteTag(tag)
            }
        }
    }

    @Transaction
    open fun delete(tag: TagEntity) {
        deleteTag(tag.id)
        deleteTag(tag)
    }

    // Find an tag by name
    @Query(value = "SELECT * FROM $TAGS_TABLE"
            + " WHERE $TAGS_NAME_COLUMN = :name")
    abstract fun findByName(name: String): List<TagEntity>

    @Query("SELECT * FROM $BOOK_TAGS_TABLE WHERE $BOOK_TAGS_TAG_ID_COLUMN = :tagId LIMIT :limit")
    abstract fun findById(tagId: Long, limit: Int = -1): List<BookAndTagEntity>
}

@Dao
abstract class AuthorDao {
    // Add multiple authors for a book
    @Transaction
    open fun add(bookId: Long, authors: List<AuthorEntity>) {
        deleteBook(bookId)
        for (author in authors)
            add(bookId, author)
    }

    // Add a single author for a book
    @Transaction
    open fun add(bookId: Long, author: AuthorEntity) {
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
    @Query("DELETE FROM $BOOK_AUTHORS_TABLE WHERE $BOOK_AUTHORS_BOOK_ID_COLUMN = :bookId")
    abstract fun deleteBook(bookId: Long): Int

    // Delete books for an author
    @Query("DELETE FROM $BOOK_AUTHORS_TABLE WHERE $BOOK_AUTHORS_AUTHOR_ID_COLUMN = :authorId")
    protected abstract fun deleteAuthor(authorId: Long): Int

    @Delete
    protected abstract fun delete(author: AuthorEntity)

    // Get all authors
    @Query(value = "SELECT * FROM $AUTHORS_TABLE ORDER BY $LAST_NAME_COLUMN")
    abstract fun getAll(): List<AuthorEntity>

    // Find an author by name
    @Query(value = "SELECT * FROM $AUTHORS_TABLE"
        + " WHERE $LAST_NAME_COLUMN = :last AND $REMAINING_COLUMN = :remaining")
    abstract fun findByName(last: String, remaining: String): List<AuthorEntity>

    @Query("SELECT * FROM $BOOK_AUTHORS_TABLE WHERE $BOOK_AUTHORS_AUTHOR_ID_COLUMN = :authorId LIMIT :limit")
    abstract fun findById(authorId: Long, limit: Int = -1): List<BookAndAuthorEntity>

    // add an author
    @Insert
    abstract fun add(author: AuthorEntity) : Long

    // add a book and author relationship
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract fun add(bookAndAuthor: BookAndAuthorEntity): Long

    @Transaction
    open fun delete(book: BookAndAuthors, deleteAuthors: Boolean = true) {
        deleteBook(book.book.id)
        if (deleteAuthors) {
            for (author in book.authors) {
                val list: List<BookAndAuthorEntity> = findById(author.id, 1)
                if (list.isEmpty())
                    delete(author)
            }
        }
    }
}

@Dao
abstract class CategoryDao {
    @Insert
    abstract fun add(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun add(bookCategory: BookAndCategoryEntity) : Long

    // Add multiple categories for a book
    @Transaction
    open fun add(bookId: Long, categories: List<CategoryEntity>) {
        deleteBook(bookId)
        for (cat in categories)
            add(bookId, cat)
    }

    // Add a single categories for a book
    @Transaction
    open fun add(bookId: Long, category: CategoryEntity) {
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
    @Query("DELETE FROM $BOOK_CATEGORIES_TABLE WHERE $BOOK_CATEGORIES_BOOK_ID_COLUMN = :bookId")
    abstract fun deleteBook(bookId: Long): Int

    // Delete books for a category
    @Query("DELETE FROM $BOOK_CATEGORIES_TABLE WHERE $BOOK_CATEGORIES_CATEGORY_ID_COLUMN = :categoryId")
    protected abstract fun deleteCategory(categoryId: Long): Int

    @Delete
    protected abstract fun delete(category: CategoryEntity)

    // Find an author by name
    @Query(value = "SELECT * FROM $CATEGORIES_TABLE"
            + " WHERE $CATEGORY_COLUMN = :category")
    abstract fun findByName(category: String): List<CategoryEntity>

    @Query("SELECT * FROM $BOOK_CATEGORIES_TABLE WHERE $BOOK_CATEGORIES_CATEGORY_ID_COLUMN = :categoryId LIMIT :limit")
    abstract fun findById(categoryId: Long, limit: Int = -1): List<BookAndCategoryEntity>

    @Transaction
    open fun delete(book: BookAndAuthors, deleteCategories: Boolean = true) {
        deleteBook(book.book.id)
        if (deleteCategories) {
            for (category in book.categories) {
                val list: List<BookAndCategoryEntity> = findById(category.id, 1)
                if (list.isEmpty())
                    delete(category)
            }
        }
    }
}

@Dao
abstract class BookDao(private val db: BookDatabase) {
    // Add a book to the data base
    @Insert
    protected abstract fun add(book: BookEntity): Long

    @Delete
    protected abstract fun delete(book: BookEntity)

    @Update
    protected abstract fun update(book: BookEntity)

    @TypeConverters(Converters::class)
    protected data class Ids(
        @ColumnInfo(name = BOOK_ID_COLUMN) val id: Long,
        @ColumnInfo(name = VOLUME_ID_COLUMN) val volumeId: String?,
        @ColumnInfo(name = SOURCE_ID_COLUMN) val sourceId: String?,
        @ColumnInfo(name = ISBN_COLUMN) val ISBN: String?,
        @ColumnInfo(name = DATE_ADDED_COLUMN) val added: Date
    )

    @RawQuery
    protected abstract fun findConflict(query: SupportSQLiteQuery): Ids?

    private fun findConflict(volumeId: String?, sourceId: String?, ISBN: String?): Ids? {
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
    protected open fun addOrUpdate(book: BookEntity) {
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
    open fun addOrUpdate(book: BookAndAuthors) {
        addOrUpdate(book.book)
        deleteThumbFile(book.book.id, true)
        deleteThumbFile(book.book.id, false)

        db.getCategoryDao().add(book.book.id, book.categories)
        db.getAuthorDao().add(book.book.id, book.authors)
        db.getTagDao().add(book.book.id, book.tags)
    }

    // Delete book from description
    @Transaction
    open fun delete(book: BookAndAuthors) {
        db.getTagDao().delete(book)
        db.getAuthorDao().delete(book)
        db.getCategoryDao().delete(book)

        deleteThumbFile(book.book.id, true)
        deleteThumbFile(book.book.id, false)
        delete(book.book)

    }

    @Transaction
    @Query(value = "SELECT * FROM $BOOK_TABLE"
            + " WHERE $BOOK_ID_COLUMN = :bookId LIMIT 1")
    abstract fun getBook(bookId: Long): BookAndAuthors?

    @Transaction
    @Query(value = "SELECT * FROM $BOOK_TABLE"
            + " WHERE $ISBN_COLUMN = :ISBN LIMIT 1")
    abstract fun getBookByISBN(ISBN: String): BookAndAuthors?

    @Transaction
    @Query(value = "SELECT * FROM $BOOK_TABLE"
            + " WHERE $VOLUME_ID_COLUMN = :volumeId LIMIT 1")
    abstract fun getBookByVolume(volumeId: String): BookAndAuthors?

    @Transaction
    @RawQuery
    protected abstract fun getBooksRaw(query: SupportSQLiteQuery): List<BookAndAuthors>

    @Transaction
    @RawQuery(observedEntities = [BookAndAuthors::class])
    protected abstract fun getBooksRawLive(query: SupportSQLiteQuery): LiveData<List<BookAndAuthors>>

    fun getBooks(): LiveData<List<BookAndAuthors>> {
        return getBooksRawLive(SimpleSQLiteQuery("SELECT * FROM $BOOK_TABLE"))
    }

    // Thumbnails
    @Query("SELECT $SMALL_THUMB_COLUMN FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = :bookId LIMIT 1")
    protected abstract fun getSmallThumbnailUrl(bookId: Long): String?

    @Query("SELECT $LARGE_THUMB_COLUMN FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = :bookId LIMIT 1")
    protected abstract fun getLargeThumbnailUrl(bookId: Long): String?

    private fun getThumbFile(bookId: Long, large: Boolean): File {
        return File(MainActivity.cache, "BiblioTech.Thumb.$bookId${if (large) kThumb else kSmallThumb}")
    }

    private fun deleteThumbFile(bookId: Long, large: Boolean) {
        try {
            getThumbFile(bookId, large).delete()
        } catch (e: Exception) {}
    }

    fun getThumbnail(bookId: Long, large: Boolean): Bitmap? {
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
    private fun loadBitmap(file: File): Bitmap? {
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    @Transaction
    protected open fun getThumbUrl(bookId: Long, file: File, large: Boolean): URL? {
        // Query the data base to get the URL. Return null if there isn't one
        val urlString = if (large) getLargeThumbnailUrl(bookId) else getSmallThumbnailUrl(bookId)
        urlString?: return null
        try {
            // Make sure the url is valid
            val url = URL(urlString)
            // Create a new file. If it is deleted while downloading the bitmap
            // Then, the data base was updated and we try to get the bitmap again
            file.createNewFile()
            return url
        } catch (e: Exception) {}
        return null
    }

    @Transaction
    protected open fun moveFile(from: File, to: File): Boolean {
        // If the file, which we created before was deleted, then
        // the book was updated, and we need to try getting the thumbnail again
        if (!to.exists())
            return false

        try {
            // Move the tmp file to the real file
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {}
        return true
    }

    private fun downloadBitmap(url: URL, file: File): File? {
        var result = false
        var connection: HttpURLConnection? = null
        var output: BufferedOutputStream? = null
        var stream: InputStream? = null
        var buffered: BufferedInputStream? = null
        val tmpFile = File.createTempFile("tmp_bitmap", null, file.parentFile)?: return null
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
            return tmpFile

        tmpFile.delete()
        return null
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
    }

    abstract fun getBookDao(): BookDao
    abstract fun getTagDao(): TagDao
    abstract fun getAuthorDao(): AuthorDao
    abstract fun getCategoryDao(): CategoryDao
}
