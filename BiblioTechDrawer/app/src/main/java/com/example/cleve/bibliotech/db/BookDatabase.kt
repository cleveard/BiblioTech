package com.example.cleve.bibliotech.db

import android.content.Context
import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

private const val BOOK_TABLE = "books"
private const val BOOK_ID_COLUMN = "books_id"
private const val VOLUME_ID_COLUMN = "books_volume_id"
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

private val SORT_ORDER: HashMap<String, Comparator<BookInView>> = hashMapOf(
    BookEntity.SORT_BY_AUTHOR_LAST_FIRST to compareBy(
        { if (it.book.authors.isNotEmpty()) it.book.authors[0].lastName else "" },
        { if (it.book.authors.isNotEmpty()) it.book.authors[0].remainingName else "" }
    )
)

@Entity(tableName = BOOK_TABLE,
    indices = [
        Index(value = [BOOK_ID_COLUMN],unique = true),
        Index(value = [VOLUME_ID_COLUMN],unique = true),
        Index(value = [ISBN_COLUMN],unique = true)
    ])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOK_ID_COLUMN) var id: Long,
    @ColumnInfo(name = VOLUME_ID_COLUMN) val volumeId: String?,
    @ColumnInfo(name = ISBN_COLUMN) val ISBN: String?,
    @ColumnInfo(name = TITLE_COLUMN,defaultValue = "") val title: String,
    @ColumnInfo(name = SUBTITLE_COLUMN,defaultValue = "") val subTitle: String,
    @ColumnInfo(name = DESCRIPTION_COLUMN,defaultValue = "") val description: String,
    @ColumnInfo(name = PAGE_COUNT_COLUMN,defaultValue = "0") val pageCount: Int,
    @ColumnInfo(name = BOOK_COUNT_COLUMN,defaultValue = "1") val bookCount: Int,
    @ColumnInfo(name = VOLUME_LINK,defaultValue = "") val linkUrl: String,
    @ColumnInfo(name = RATING_COLUMN,defaultValue = "-1.0") val rating: Double,
    @ColumnInfo(name = DATE_ADDED_COLUMN,defaultValue = "0") var added: Long,
    @ColumnInfo(name = DATE_MODIFIED_COLUMN,defaultValue = "0") var modified: Long,
    @ColumnInfo(name = SMALL_THUMB_COLUMN) val smallThumb: String?,
    @ColumnInfo(name = LARGE_THUMB_COLUMN) val largeThumb: String?
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
    @ColumnInfo(name = LAST_NAME_COLUMN,defaultValue = "") val lastName: String,
    @ColumnInfo(name = REMAINING_COLUMN,defaultValue = "") val remainingName: String
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
    @ColumnInfo(name = CATEGORY_COLUMN,defaultValue = "") val category: String
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

@Entity(tableName = VIEWS_TABLE,
    indices = [
        Index(value = [VIEWS_ID_COLUMN],unique = true)
    ])
data class ViewEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = VIEWS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = VIEWS_NAME_COLUMN) val name: String,
    @ColumnInfo(name = VIEWS_ORDER_COLUMN) val order: Int,
    @ColumnInfo(name = VIEWS_SORT_COLUMN) val sort: String
)

@Entity(tableName = BOOK_VIEWS_TABLE,
    foreignKeys = [
        ForeignKey(entity = BookEntity::class,
            parentColumns = [BOOK_ID_COLUMN],
            childColumns = [BOOK_VIEWS_BOOK_ID_COLUMN],
            onDelete = CASCADE),
        ForeignKey(entity = ViewEntity::class,
            parentColumns = [VIEWS_ID_COLUMN],
            childColumns = [BOOK_VIEWS_VIEW_ID_COLUMN],
            onDelete = CASCADE)
    ],
    indices = [
        Index(value = [BOOK_VIEWS_ID_COLUMN],unique = true),
        Index(value = [BOOK_VIEWS_BOOK_ID_COLUMN,BOOK_VIEWS_VIEW_ID_COLUMN],unique = true),
        Index(value = [BOOK_VIEWS_VIEW_ID_COLUMN])
    ])
data class BookAndViewEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOK_VIEWS_ID_COLUMN) var id: Long,
    @ColumnInfo(name = BOOK_VIEWS_BOOK_ID_COLUMN) var bookId: Long,
    @ColumnInfo(name = BOOK_VIEWS_VIEW_ID_COLUMN) var viewId: Long,
    @ColumnInfo(name = SELECTED_COLUMN) val isSelected: Boolean,
    @ColumnInfo(name = OPEN_COLUMN) val isOpen: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookAndViewEntity

        if (id != other.id) return false
        if (bookId != other.bookId) return false
        if (viewId != other.viewId) return false
        if (isSelected != other.isSelected) return false
        if (isOpen != other.isOpen) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + bookId.hashCode()
        result = 31 * result + viewId.hashCode()
        result = 31 * result + isSelected.hashCode()
        result = 31 * result + isOpen.hashCode()
        return result
    }
}

@DatabaseView(viewName = BOOK_AUTHORS_VIEW,
    value = " SELECT *, GROUP_CONCAT(($LAST_NAME_COLUMN || ', ' || $REMAINING_COLUMN), ',\n') AS $ALL_AUTHORS_COLUMN"
            + " FROM ( SELECT * FROM $BOOK_TABLE"
            + " LEFT JOIN $BOOK_AUTHORS_TABLE"
            + " ON ($BOOK_AUTHORS_BOOK_ID_COLUMN = $BOOK_ID_COLUMN)"
            + " LEFT JOIN $AUTHORS_TABLE"
            + " ON ($AUTHORS_ID_COLUMN = $BOOK_AUTHORS_AUTHOR_ID_COLUMN)"
            + ") GROUP BY $BOOK_ID_COLUMN;")
data class BookView(
    @Embedded val book: BookEntity,
    @ColumnInfo(name = ALL_AUTHORS_COLUMN) val authors: String?
)

open class BookAndAuthors(
    @Embedded val book: BookEntity,
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
    val authors: List<AuthorEntity>,
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
    val categories: List<CategoryEntity>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookAndAuthors

        if (book != other.book) return false
        if (authors != other.authors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = book.hashCode()
        result = 31 * result + authors.hashCode()
        return result
    }
}

class BookInView(
    @Embedded val bookInView: BookAndViewEntity,
    @Relation(
        entity = BookEntity::class,
        parentColumn = BOOK_VIEWS_VIEW_ID_COLUMN,
        entityColumn = BOOK_ID_COLUMN
    )
    val book: BookAndAuthors
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookInView

        if (bookInView != other.bookInView) return false
        if (book != other.book) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bookInView.hashCode()
        result = 31 * result + book.hashCode()
        return result
    }
}

data class BookListInView(
    @Embedded val view: ViewEntity,
    @Relation(
        entity = BookAndViewEntity::class,
        parentColumn = VIEWS_ID_COLUMN,
        entityColumn = BOOK_VIEWS_BOOK_ID_COLUMN
    )
    val books: MutableList<BookInView>
)

@Dao
abstract class ViewDao {
    // Select all books for a view
    @Transaction
    @Query(value = "SELECT * FROM $VIEWS_TABLE"
            + " WHERE $VIEWS_ID_COLUMN = :viewId")
    abstract fun getBooksForView(viewId: Long): BookListInView?

    // Get the list of views
    @Query(value = "SELECT * FROM $VIEWS_TABLE ORDER BY $VIEWS_ORDER_COLUMN")
    abstract fun get(): List<ViewEntity>

    // Add a view
    @Insert
    abstract fun add(view: ViewEntity): Long

    // Add a book to a view
    @Insert
    abstract fun add(bookAndView: BookAndViewEntity): Long

    // Add a book to a view
    //@Transaction
    fun add(viewId: Long, bookId: Long, selected: Boolean = false, open: Boolean = false) {
        add(BookAndViewEntity(0, bookId, viewId, selected, open))
    }

    //@Transaction
    fun getBooksForView(viewId: Long, sort: String): List<BookInView> {
        val bookInView = getBooksForView(viewId) ?: return ArrayList(0)
        val compare = SORT_ORDER[sort]
        if (compare != null)
            bookInView.books.sortWith(compare)
        return bookInView.books
    }

}

@Dao
abstract class AuthorDao {
    // Add multiple authors for a book
    //@Transaction
    fun add(bookId: Long, authors: List<AuthorEntity>) {
        for (author in authors)
            add(bookId, author)
    }

    // Add a single author for a book
    //@Transaction
    fun add(bookId: Long, author: AuthorEntity) {
        // Find the author
        val list: List<AuthorEntity> = findByName(author.lastName, author.remainingName)
        author.id = if (list.isNotEmpty()) {
            list[0].id
        } else {
            add(AuthorEntity(0, author.lastName, author.remainingName))
        }
        add(bookId, author.id)
    }

    // Get all authors
    @Query(value = "SELECT * FROM $AUTHORS_TABLE ORDER BY $LAST_NAME_COLUMN")
    abstract fun getAll(): List<AuthorEntity>

    // Find an author by name
    @Query(value = "SELECT * FROM $AUTHORS_TABLE"
        + " WHERE $LAST_NAME_COLUMN = :last AND $REMAINING_COLUMN = :remaining")
    abstract fun findByName(last: String, remaining: String): List<AuthorEntity>

    // add an author
    @Insert
    abstract fun add(author: AuthorEntity) : Long

    // add a book and author relationship
    @Insert
    abstract fun add(bookAndAuthor: BookAndAuthorEntity): Long

    // add a book and author relationship
    fun add(authorId: Long, bookId: Long) {
        add(BookAndAuthorEntity(0, authorId, bookId))
    }
}

@Dao
abstract class CategoryDao {
    @Insert
    abstract fun add(category: CategoryEntity): Long

    @Insert
    abstract fun add(bookCategory: BookAndCategoryEntity) : Long

    // Add multiple categories for a book
    //@Transaction
    fun add(bookId: Long, categories: List<CategoryEntity>) {
        for (cat in categories)
            add(bookId, cat)
    }

    // Add a single categories for a book
    //@Transaction
    fun add(bookId: Long, category: CategoryEntity) {
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

    // Find an author by name
    @Query(value = "SELECT * FROM $CATEGORIES_TABLE"
            + " WHERE $CATEGORY_COLUMN = :category")
    abstract fun findByName(category: String): List<CategoryEntity>
}

@Dao
abstract class BookDao(private val db: BookDatabase) {
    //@Query(value = "SELECT * FROM ($BOOK_VIEWS_TABLE"
    //        + " LEFT JOIN $BOOK_AUTHORS_VIEW"
    //        + " ON ($BOOK_ID_COLUMN = $BOOK_VIEWS_BOOK_ID_COLUMN) )"
    //        + " WHERE $BOOK_VIEWS_VIEW_ID_COLUMN = :view.id"
    //        + " ORDER BY :view.sort")
    //fun getBooksForView(view: ViewEntity): List<BookView>

    // Add a book to the data base
    @Insert
    abstract fun add(book: BookEntity): Long

    // Add book from description
    //@Transaction
    fun add(book: BookAndAuthors, viewId: Long? = null, selected: Boolean = false, open: Boolean = false): Long {
        val time = Calendar.getInstance().time.time
        book.book.added = time
        book.book.modified = time
        book.book.id = add(book.book)

        db.getCategoryDao().add(book.book.id, book.categories)
        db.getAuthorDao().add(book.book.id, book.authors)

        if (viewId != null)
            db.getViewDao().add(viewId, book.book.id, selected, open)

        return book.book.id
    }

    @Transaction
    @Query(value = "SELECT * FROM $BOOK_TABLE"
            + " WHERE $BOOK_ID_COLUMN = :bookId")
    abstract fun getBook(bookId: Long): BookAndAuthors?
}

@Database(
    entities = [
        BookEntity::class,
        AuthorEntity::class,
        BookAndAuthorEntity::class,
        ViewEntity::class,
        BookAndViewEntity::class,
        CategoryEntity::class,
        BookAndCategoryEntity::class
    ],
    version = 1,
    exportSchema = false,
    views = [BookView::class]
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

        fun create(context: Context): BookDatabase {
            return Room.databaseBuilder(
                context, BookDatabase::class.java, DATABASE_FILENAME
            ).build()
        }
    }

    abstract fun getBookDao(): BookDao
    abstract fun getViewDao(): ViewDao
    abstract fun getAuthorDao(): AuthorDao
    abstract fun getCategoryDao(): CategoryDao
}
