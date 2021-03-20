package com.github.cleveard.bibliotech.db

import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import androidx.sqlite.db.SupportSQLiteQuery

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
