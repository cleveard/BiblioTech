package com.example.cleve.bibliotech

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.*
import java.util.concurrent.Executors
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

private val SORT_ORDER: HashMap<String, Comparator<BookAndAuthors>> = hashMapOf<String, Comparator<BookAndAuthors>>(
    BookEntity.SORT_BY_AUTHOR_LAST_FIRST to compareBy(
        { if (it.authors.size > 0) it.authors[0].lastName else "" },
        { if (it.authors.size > 0) it.authors[0].remainingName else "" }
    )
)

@Entity(tableName = BOOK_TABLE,
    indices = [
        Index(value = [BOOK_ID_COLUMN],unique = true),
        Index(value = [VOLUME_ID_COLUMN],unique = true),
        Index(value = [ISBN_COLUMN],unique = true)
    ])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOK_ID_COLUMN) val id: Long,
    @ColumnInfo(name = VOLUME_ID_COLUMN) val volume: String?,
    @ColumnInfo(name = ISBN_COLUMN) val ISBN: String?,
    @ColumnInfo(name = TITLE_COLUMN,defaultValue = "") val title: String,
    @ColumnInfo(name = SUBTITLE_COLUMN,defaultValue = "") val subTitle: String,
    @ColumnInfo(name = DESCRIPTION_COLUMN,defaultValue = "") val description: String,
    @ColumnInfo(name = SMALL_THUMB_COLUMN) val smallThumb: String?,
    @ColumnInfo(name = LARGE_THUMB_COLUMN) val largeThumb: String?
) {
    companion object {
        const val SORT_BY_AUTHOR_LAST_FIRST = "authorsLastFirst"
    }
}

@Entity(tableName = AUTHORS_TABLE,
    indices = [
        Index(value = [AUTHORS_ID_COLUMN],unique = true),
        Index(value = [LAST_NAME_COLUMN,REMAINING_COLUMN],unique = true)
    ])
data class AuthorEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = AUTHORS_ID_COLUMN) val id: Long,
    @ColumnInfo(name = LAST_NAME_COLUMN,defaultValue = "") val lastName: String,
    @ColumnInfo(name = REMAINING_COLUMN,defaultValue = "") val remainingName: String
)

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
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOK_AUTHORS_ID_COLUMN) val id: Long,
    @ColumnInfo(name = BOOK_AUTHORS_AUTHOR_ID_COLUMN) val authorId: Long,
    @ColumnInfo(name = BOOK_AUTHORS_BOOK_ID_COLUMN) val bookId: Long
)

@Entity(tableName = VIEWS_TABLE,
    indices = [
        Index(value = [VIEWS_ID_COLUMN],unique = true)
    ])
data class ViewEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = VIEWS_ID_COLUMN) val id: Long,
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
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOK_VIEWS_ID_COLUMN) val id: Long,
    @ColumnInfo(name = BOOK_VIEWS_BOOK_ID_COLUMN) val bookId: Long,
    @ColumnInfo(name = BOOK_VIEWS_VIEW_ID_COLUMN) val viewId: Long,
    @ColumnInfo(name = SELECTED_COLUMN) val selected: Boolean,
    @ColumnInfo(name = OPEN_COLUMN) val open: Boolean
)

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

class BookAndAuthors(
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
    val authors: List<AuthorEntity>
)

data class BookInView(
    @Embedded val view: ViewEntity,
    @Relation(
        entity = BookEntity::class,
        parentColumn = VIEWS_ID_COLUMN,
        entityColumn = BOOK_ID_COLUMN,
        associateBy = Junction(
            BookAndViewEntity::class,
            parentColumn = BOOK_VIEWS_VIEW_ID_COLUMN,
            entityColumn = BOOK_VIEWS_BOOK_ID_COLUMN
        )
    )
    val books: MutableList<BookAndAuthors>
)

@Dao
abstract class ViewDao {
    // Select all books for a view
    @Transaction
    @Query(value = "SELECT * FROM $VIEWS_TABLE"
            + " WHERE $VIEWS_ID_COLUMN = :viewId")
    abstract fun getBooksForView(viewId: Long): BookInView?

    // Get the list of views
    @Query(value = "SELECT * FROM $VIEWS_TABLE ORDER BY $VIEWS_ORDER_COLUMN")
    abstract fun get(): LiveData<List<ViewEntity>>

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
    fun getBooksForView(viewId: Long, sort: String): List<BookAndAuthors> {
        val bookInView = getBooksForView(viewId)
        if (bookInView == null)
            return ArrayList(0)
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
    fun addAuthors(bookId: Long, authors: List<String>) {
        for (author in authors)
            addAuthor(bookId, author)
    }

    // Add a single author for a book
    //@Transaction
    fun addAuthor(bookId: Long, author: String) {
        val last = StringBuilder()
        val remaining = StringBuilder()
        // Separate the author into last name and remaining
        separateAuthor(author, last, remaining)

        // Find the author
        val list: List<AuthorEntity> = findByName(last.toString(), remaining.toString())
        val authorId: Long = if (list.isNotEmpty()) {
            list[0].id
        } else {
            add(AuthorEntity(0, last.toString(), remaining.toString()))
        }
        add(bookId, authorId)
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

    // Break authors name in to last and rest.
    private fun separateAuthor(
        in_name: String,
        last: StringBuilder,
        remaining: StringBuilder
    ) {
        var name = in_name
        name = name.trim { it <= ' ' }
        // Look for a , assume last, remaining if found
        var lastIndex = name.lastIndexOf(',')
        if (lastIndex > 0) {
            last.append(name.substring(0, lastIndex).trim { it <= ' ' })
            remaining.append(name.substring(lastIndex + 1).trim { it <= ' ' })
        } else { // look for a space, assume remaining last if found
            lastIndex = name.lastIndexOf(' ')
            if (lastIndex > 0) {
                last.append(name.substring(lastIndex))
                remaining.append(name.substring(0, lastIndex).trim { it <= ' ' })
            } else { // No space or commas, only last name
                last.append(name)
            }
        }
    }
}

@Dao
abstract class BookDao(private val db: BookRoomDatabase) {
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
    fun add(book: Book, viewId: Long? = null, selected: Boolean = false, open: Boolean = false): Long {
        val bookId: Long = add(BookEntity(
            0,
            book.mVolumeID,
            book.mISBN,
            book.mTitle,
            book.mSubTitle,
            book.mDescription,
            book.mThumbnails[0],
            book.mThumbnails[1]
        ))

        db.getAuthorDao().addAuthors(bookId, book.mAuthors)

        if (viewId != null)
            db.getViewDao().add(viewId, bookId, selected, open)

        return bookId
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
        BookAndViewEntity::class
    ],
    version = 1,
    exportSchema = false,
    views = [BookView::class]
)

abstract class BookRoomDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_FILENAME = "books_database.db"
        fun create(context: Context): BookRoomDatabase {
            var create = false;
            val db = Room.databaseBuilder(
                context, BookRoomDatabase::class.java, DATABASE_FILENAME
            ).addCallback(
                object: RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        create = true;
                    }
                }
            ).build();
            db.mCreated = create
            return db;
        }
    }

    abstract fun getBookDao(): BookDao
    abstract fun getViewDao(): ViewDao
    abstract fun getAuthorDao(): AuthorDao

    private var mCreated: Boolean = false
    val created: Boolean
        get() = mCreated

}
