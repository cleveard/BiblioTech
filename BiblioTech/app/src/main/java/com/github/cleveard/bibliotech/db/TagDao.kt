package com.github.cleveard.bibliotech.db

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.github.cleveard.bibliotech.db.BookDatabase.Companion.idWithFilter
import com.github.cleveard.bibliotech.db.BookDatabase.Companion.selectByFlagBits
import com.github.cleveard.bibliotech.db.BookDatabase.Companion.selectVisible
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import java.util.*
import java.lang.StringBuilder
import kotlin.collections.ArrayList

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
        const val PRESERVE = 0
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
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class,
            parentColumns = [TAGS_ID_COLUMN],
            childColumns = [BOOK_TAGS_TAG_ID_COLUMN],
            onDelete = ForeignKey.CASCADE)
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
 * Room data access object for the tags table
 */
@Dao
abstract class TagDao(private val db: BookDatabase) {
    /**
     * Get a tag using its incrementing id
     * @param tagId The id of the tag to retrieve
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN = :tagId AND ( ( $TAGS_FLAGS & ${TagEntity.HIDDEN} ) = 0 ) LIMIT 1")
    abstract suspend fun get(tagId: Long): TagEntity?

    /**
     * Get the paging source for all tags
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE WHERE ( ( $TAGS_FLAGS & ${TagEntity.HIDDEN} ) = 0 ) ORDER BY $TAGS_NAME_COLUMN")
    abstract fun get(): PagingSource<Int, TagEntity>

    /**
     * Get a LiveData of the list of tags ordered by name
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE WHERE ( ( $TAGS_FLAGS & ${TagEntity.HIDDEN} ) = 0 ) ORDER BY $TAGS_NAME_COLUMN")
    protected abstract fun doGetLive(): LiveData<List<TagEntity>>

    /**
     * Get a LiveData of the list of tags ordered by name
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE WHERE ( $TAGS_FLAGS & ${TagEntity.SELECTED or TagEntity.HIDDEN} ) == ${TagEntity.SELECTED} ORDER BY $TAGS_NAME_COLUMN")
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
    @Insert(onConflict = OnConflictStrategy.IGNORE)
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
    open suspend fun addWithUndo(bookId: Long, tags: List<TagEntity>, tagIds: Array<Any>?) {
        // Add or find all of the tags
        for (tag in tags) {
            tag.id = findByName(tag.name)?.id ?: addWithUndo(tag)
        }

        // Build a set of all tag ids for the book
        val set = HashSet<Any>()
        // Add ids from tagIds
        tagIds?.toCollection(set)?: db.getTagDao().queryTagIds(null)?.toCollection(set)
        // Add ids from tags
        tags.mapTo(set) { it.id }
        val list = set.toArray()

        // Delete the tags that we don't want to keep
        db.getBookTagDao().deleteTagsForBooksWithUndo(arrayOf(bookId), null, list, true)

        // Add the tags that we want
        db.getTagDao().queryTagIds(list)?.let {
            db.getBookTagDao().addTagsToBookWithUndo(bookId, it)
        }
    }

    @Transaction
    /**
     * Add/update a tag and resolve conflict
     * @param tag The tag to be added
     * @param callback A callback used to ask what to do if the tag already exists
     * This is also be used to rename existing tags. If the renamed tag conflicts with an
     * existing tag, then we will merge the books for the two tags into a single tag
     */
    open suspend fun addWithUndo(tag: TagEntity, callback: (suspend CoroutineScope.(conflict: TagEntity) -> Boolean)? = null): Long {
        // Empty tag is not accepted
        tag.name = tag.name.trim { it <= ' ' }
        if (tag.name.isEmpty())
            return 0
        // See if there is a conflicting tag with the same name.
        val conflict = findByName(tag.name)
        if (conflict != null && conflict.id != tag.id) {
            // Yep, ask caller what to do, null or false return means do nothing
            if (!BookDatabase.callConflict(conflict, callback))
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
                        db.getBookTagDao().deleteTagsForTagsWithUndo(arrayOf(tag.id))
                    // Delete the original tag
                    deleteWithUndo(tag.id)
                    // Set the tag id to the conflict id
                    tag.id = conflict.id
                    // Keep track of books conflict id is already used
                    set.addAll(conflictBooks!!.map { it.bookId })
                } else {
                    // Convert conflict links to tag link
                    addBooks = conflictBooks
                    // Delete the existing links for conflict
                    if (conflictCount > 0)
                        db.getBookTagDao().deleteTagsForTagsWithUndo(arrayOf(conflict.id))
                    // Delete the conflicting tag
                    deleteWithUndo(conflict.id)
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
                            db.getBookTagDao().addTagToBookWithUndo(book.bookId, book.tagId)
                        } else {
                            // Delete this book-tag link
                            list.add(book.id)
                        }
                    }

                    // Delete all of the links we kept from above
                    if (list.isNotEmpty())
                        db.getBookTagDao().deleteByIdWithUndo(list.toArray())
                }
            }
        }

        // Update or add the tag
        if (tag.id == 0L) {
            tag.id = internalAdd(tag)
            if (tag.id != 0L)
                UndoRedoDao.OperationType.ADD_TAG.recordAdd(db.getUndoRedoDao(), tag.id)
        } else if (!UndoRedoDao.OperationType.CHANGE_TAG.recordUpdate(db.getUndoRedoDao(), tag.id) {
            internalUpdate(tag) > 0
        }) {
            tag.id = 0L
        }
        return tag.id
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
            TAGS_FLAGS, TagEntity.HIDDEN,                         // Select visible tags
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

    suspend fun deleteWithUndo(tagId: Long): Int {
        return UndoRedoDao.OperationType.DELETE_TAG.recordDelete(db.getUndoRedoDao(), tagId) {
            db.setHidden(BookDatabase.tagsTable, tagId)
        }
    }

    /**
     * Query the number of tags
     * @return The number of tags
     */
    @Transaction
    open suspend fun querySelectedTagCount(): Int {
        return BookDatabase.buildQueryForIds(
            "SELECT COUNT($TAGS_ID_COLUMN) FROM $TAGS_TABLE",      // SQLite Command
            TAGS_FLAGS, TagEntity.HIDDEN,             // Select visible tags
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
    open suspend fun deleteSelectedWithUndo(): Int {
        return BookDatabase.buildWhereExpressionForIds(
            TAGS_FLAGS, TagEntity.HIDDEN, null, // Select visible tags
            TAGS_ID_COLUMN,                           // Column to query
            selectedIdSubQuery,                       // Selected tag ids sub-query
            null,                                     // Ids to delete
            false                                     // Delete ids or not ids
        )?.let {e ->
            UndoRedoDao.OperationType.DELETE_TAG.recordDelete(db.getUndoRedoDao(), e) {
                db.setHidden(BookDatabase.tagsTable, e)
            }
        }?: 0
    }

    /**
     * Find an tag by name
     * @param name The name to search for
     * This uses a case insensitive compare
     */
    @Query(value = "SELECT * FROM $TAGS_TABLE"
            + " WHERE $TAGS_NAME_COLUMN LIKE :name ESCAPE '\\' AND ( ( $TAGS_FLAGS & ${TagEntity.HIDDEN} ) = 0 ) LIMIT 1")
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
    protected abstract suspend fun countBits(query: SupportSQLiteQuery): Int

    /**
     * Query to count bits in the flags column
     */
    @RawQuery(observedEntities = [TagEntity::class])
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
        val condition = StringBuilder().idWithFilter(id, null, TAGS_ID_COLUMN)
            .selectVisible(TAGS_FLAGS, TagEntity.HIDDEN)
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
    open fun countBitsLive(bits: Int, value: Int, include: Boolean, id: Long?): LiveData<Int> {
        // Build the selection from the bits
        val condition = StringBuilder().idWithFilter(id, null, TAGS_ID_COLUMN)
            .selectVisible(TAGS_FLAGS, TagEntity.HIDDEN)
            .selectByFlagBits(bits, value, include, TAGS_FLAGS)
            .toString()
        // Return the query
        return countBitsLive(
            SimpleSQLiteQuery(
                "SELECT COUNT($TAGS_ID_COLUMN) FROM $TAGS_TABLE$condition"
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
        val condition = StringBuilder().idWithFilter(id, null, TAGS_ID_COLUMN)
            .selectVisible(TAGS_FLAGS, TagEntity.HIDDEN)
        // Only select the rows where the change will make a difference
        condition.selectByFlagBits(mask, value, false, TAGS_FLAGS)
        return db.execUpdateDelete(SimpleSQLiteQuery("UPDATE $TAGS_TABLE SET $TAGS_FLAGS = ( $TAGS_FLAGS & ${mask.inv()} ) | $value$condition"))
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
        val condition = StringBuilder().idWithFilter(id, null, TAGS_ID_COLUMN)
            .selectVisible(TAGS_FLAGS, TagEntity.HIDDEN)
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
            "SELECT $TAGS_ID_COLUMN FROM $TAGS_TABLE WHERE ( ( $TAGS_FLAGS & ${TagEntity.HIDDEN} ) = 0 " +
            "AND ( $TAGS_FLAGS & ${TagEntity.SELECTED} ) ${if (it) "==" else "!="} 0 )"
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
    open suspend fun deleteByIdWithUndo(bookTagIds: Array<Any>): Int {
        return BookDatabase.buildWhereExpressionForIds(
            null, 0, null,
            BOOK_TAGS_ID_COLUMN,
            null,
            bookTagIds,
            false
        )?.let {e ->
            UndoRedoDao.OperationType.DELETE_BOOK_TAG_LINK.recordDelete(db.getUndoRedoDao(), e) {
                db.execUpdateDelete(
                    SimpleSQLiteQuery("DELETE FROM $BOOK_TAGS_TABLE${it.expression}", it.args)
                )
            }
        }?: 0
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
    open suspend fun deleteTagsForBooksWithUndo(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null,
                                                tagIds: Array<Any>? = null, tagsInvert: Boolean = true): Int {
        return BookDatabase.buildWhereExpressionForIds(
            null, 0, filter,
            BOOK_TAGS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false,
            BOOK_TAGS_TAG_ID_COLUMN,
            null,
            tagIds,
            tagsInvert
        )?.let {e ->
            UndoRedoDao.OperationType.DELETE_BOOK_TAG_LINK.recordDelete(db.getUndoRedoDao(), e) {
                db.execUpdateDelete(
                    SimpleSQLiteQuery("DELETE FROM $BOOK_TAGS_TABLE${it.expression}", it.args)
                )
            }
        }?: 0
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
    open suspend fun deleteSelectedTagsForBooksWithUndo(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter? = null,
                                                        tagIds: Array<Any>? = null, invert: Boolean = false): Int {
        return BookDatabase.buildWhereExpressionForIds(
            null, 0, filter,
            BOOK_TAGS_BOOK_ID_COLUMN,
            BookDao.selectedIdSubQuery,
            bookIds,
            false,
            BOOK_TAGS_TAG_ID_COLUMN,
            TagDao.selectedIdSubQuery,
            tagIds,
            invert)?.let {e ->
            UndoRedoDao.OperationType.DELETE_BOOK_TAG_LINK.recordDelete(db.getUndoRedoDao(), e) {
                db.execUpdateDelete(
                    SimpleSQLiteQuery("DELETE FROM $BOOK_TAGS_TABLE${it.expression}", it.args)
                ).also {deleted ->
                    if (deleted > 0)
                        db.getUndoRedoDao().setBooksModified(bookIds, filter)
                }
            }
        }?: 0
    }

    /**
     * Delete tags from all books
     * @param tagIds A list of tag ids. Null means selected.
     */
    @Transaction
    open suspend fun deleteTagsForTagsWithUndo(tagIds: Array<Any>?): Int {
        return BookDatabase.buildWhereExpressionForIds(
            null, 0, null,
            BOOK_TAGS_TAG_ID_COLUMN,
            TagDao.selectedIdSubQuery,
            tagIds,
            false
        )?.let {e ->
            UndoRedoDao.OperationType.DELETE_BOOK_TAG_LINK.recordDelete(db.getUndoRedoDao(), e) {
                db.execUpdateDelete(
                    SimpleSQLiteQuery("DELETE FROM $BOOK_TAGS_TABLE${it.expression}", it.args)
                )
            }
        }?: 0
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
            null, 0, filter,
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
            null, 0,
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
     * @return The id of the link
     */
    @Transaction
    open suspend fun addTagToBookWithUndo(bookId: Long, tagId: Long): Long {
        return add(BookAndTagEntity(0, tagId, bookId)).let {
            if (it != 0L && it != -1L) {
                UndoRedoDao.OperationType.ADD_BOOK_TAG_LINK.recordLink(db.getUndoRedoDao(), bookId, tagId)
                it
            } else
                0L
        }
    }

    /**
     * Add multiple tags for a book
     * @param bookId The id of the book
     * @param tagIds List of ids for the tags
     * @return The number of links added
     */
    @Transaction
    open suspend fun addTagsToBookWithUndo(bookId: Long, tagIds: List<Long>): Int {
        return tagIds.asFlow().map { tag ->
            if (addTagToBookWithUndo(bookId, tag) != 0L) 1 else 0
        }.fold(0) { a, v -> a + v }
    }

    /**
     * Add multiple tags to multiple books
     * @param bookIds A list of ids for books
     * @param tagIds A list of id for tags
     * @return The number of links added
     */
    @Transaction
    open suspend fun addTagsToBooksWithUndo(bookIds: List<Long>, tagIds: List<Long>): Int {
        return bookIds.asFlow().map {book ->
            addTagsToBookWithUndo(book, tagIds)
        }.fold(0) { a, v -> a + v }
    }

    /**
     * Add multiple tags to multiple books
     * @param bookIds A list of book ids. Null means use selected books.
     * @param tagIds A list of tag ids to be removed for the books. Null means use selected tags.
     * @param filter A filter to restrict the book ids
     * @return The number of links added
     */
    @Transaction
    open suspend fun addTagsToBooksWithUndo(bookIds: Array<Any>?, tagIds: Array<Any>?, filter: BookFilter.BuiltFilter?): Int {
        val bookList = db.getBookDao().queryBookIds(bookIds, filter)?: return 0
        val tagList = db.getTagDao().queryTagIds(tagIds)?: return 0
        return addTagsToBooksWithUndo(bookList, tagList).also {
            if (it > 0)
                db.getUndoRedoDao().setBooksModified(bookList)
        }
    }

    /**
     * Delete all tags from books
     * @param bookIds A list of book ids. Null means use the selected books.
     * @param deleteTags A flag to indicate whether the tag in the Tags table should be deleted
     *                   if all of its books have been deleted
     * @param filter A filter to restrict the book ids
     */
    @Transaction
    open suspend fun deleteSelectedBooksWithUndo(bookIds: Array<Any>?, deleteTags: Boolean = false, filter: BookFilter.BuiltFilter? = null): Int {
        val count: Int
        // Do we want to delete any books
        if (deleteTags) {
            // Yes, get the ids of tags that are affected
            val tags = queryBookIds(bookIds, filter)
            // Delete the book tag links
            count = deleteTagsForBooksWithUndo(bookIds, filter)
            tags?.let {
                // Check for empty tags and delete them
                for (tag in it) {
                    val list: List<BookAndTagEntity> = findById(tag, 1)
                    if (list.isEmpty())
                        db.getTagDao().deleteWithUndo(tag)
                }
            }
        } else {
            // Don't delete tags, so just delete the links
            count = deleteTagsForBooksWithUndo(bookIds, filter)
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
