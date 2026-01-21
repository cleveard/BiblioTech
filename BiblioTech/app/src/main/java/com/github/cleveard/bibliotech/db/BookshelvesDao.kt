package com.github.cleveard.bibliotech.db

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.protobuf.ProtoBuf

const val BOOKSHELVES_TABLE = "bookshelves"
const val BOOKSHELVES_ID_COLUMN = "bookshelves_id"
const val BOOKSHELVES_BOOKSHELF_ID_COLUMN = "bookshelves_bookshelf_id"
const val BOOKSHELVES_TITLE_COLUMN = "bookshelves_title"
const val BOOKSHELVES_DESCRIPTION_COLUMN = "bookshelves_description"
const val BOOKSHELVES_SELF_LINK_COLUMN = "bookshelves_self_link"
const val BOOKSHELVES_MODIFIED_COLUMN = "bookshelves_modified"
const val BOOKSHELVES_BOOKS_MODIFIED_COLUMN = "bookshelves_books_modified"
const val BOOKSHELVES_BOOKS_LAST_UPDATE_COLUMN = "bookshelves_books_last_update"
const val BOOKSHELVES_LAST_SYNC_VOLUMES_COLUMN = "bookshelves_volumes_last_sync"
const val BOOKSHELVES_FLAG_COLUMN = "bookshelves_flag"

// This join of the bookshelves and tags tables will associate the bookshelf and tag and exclude tag versions hidden for undo/redo
const val BOOKSHELVES_TAGS_JOIN = "$BOOKSHELVES_TABLE LEFT JOIN $TAGS_TABLE ON $BOOKSHELVES_ID_COLUMN = $TAGS_BOOKSHELF_COLUMN AND ( $TAGS_FLAGS_COLUMN & ${TagEntity.HIDDEN} ) = 0"

val serializer = SetSerializer(String.serializer())

/**
 * Entity for a bookshelf
 * This entity is used only for defining the table in the database.
 * The class we use for bookshelves in the app is Bookshelf, which doesn't
 * include the last synced volumes, to reduce access to a possibly large blob
 * Bookshelves to not participate in undo/redo, but the tagging/untagging
 * when refreshing bookshelves can be undone/redone.
 */
@Entity(tableName = BOOKSHELVES_TABLE,
    indices = [
        Index(value = [BOOKSHELVES_ID_COLUMN], unique = true),
        Index(value = [BOOKSHELVES_BOOKSHELF_ID_COLUMN]),
    ]
)
data class BookshelfEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BOOKSHELVES_ID_COLUMN) var id: Long,
    @ColumnInfo(name = BOOKSHELVES_BOOKSHELF_ID_COLUMN, defaultValue = "0") var bookshelfId: Int,
    @ColumnInfo(name = BOOKSHELVES_TITLE_COLUMN, defaultValue = "") var title: String,
    @ColumnInfo(name = BOOKSHELVES_DESCRIPTION_COLUMN, defaultValue = "NULL") var description: String?,
    @ColumnInfo(name = BOOKSHELVES_SELF_LINK_COLUMN, defaultValue = "NULL") var selfLink: String?,
    @ColumnInfo(name = BOOKSHELVES_MODIFIED_COLUMN, defaultValue = "0") var modified: Long,
    @ColumnInfo(name = BOOKSHELVES_BOOKS_MODIFIED_COLUMN, defaultValue = "0") var booksModified: Long,
    @ColumnInfo(name = BOOKSHELVES_BOOKS_LAST_UPDATE_COLUMN, defaultValue = "0") var booksLastUpdate: Long,
    @ColumnInfo(name = BOOKSHELVES_LAST_SYNC_VOLUMES_COLUMN) var lastSyncVolumes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BookshelfEntity

        if (id != other.id) return false
        if (bookshelfId != other.bookshelfId) return false
        if (modified != other.modified) return false
        if (booksModified != other.booksModified) return false
        if (booksLastUpdate != other.booksLastUpdate) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (selfLink != other.selfLink) return false
        if (!lastSyncVolumes.contentEquals(other.lastSyncVolumes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + bookshelfId
        result = 31 * result + modified.hashCode()
        result = 31 * result + booksModified.hashCode()
        result = 31 * result + booksLastUpdate.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (selfLink?.hashCode() ?: 0)
        result = 31 * result + (lastSyncVolumes?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Class for dealing with bookshelves
 * This class is missing the last synced volumes field.
 * Get and update the last synced volumes field via the specific methods for it.
 */
data class Bookshelf(
    @ColumnInfo(name = BOOKSHELVES_ID_COLUMN) var id: Long,
    @ColumnInfo(name = BOOKSHELVES_BOOKSHELF_ID_COLUMN, defaultValue = "0") var bookshelfId: Int,
    @ColumnInfo(name = BOOKSHELVES_TITLE_COLUMN, defaultValue = "") var title: String,
    @ColumnInfo(name = BOOKSHELVES_DESCRIPTION_COLUMN, defaultValue = "NULL") var description: String?,
    @ColumnInfo(name = BOOKSHELVES_SELF_LINK_COLUMN, defaultValue = "NULL") var selfLink: String?,
    @ColumnInfo(name = BOOKSHELVES_MODIFIED_COLUMN, defaultValue = "0") var modified: Long,
    @ColumnInfo(name = BOOKSHELVES_BOOKS_MODIFIED_COLUMN, defaultValue = "0") var booksModified: Long,
    @ColumnInfo(name = BOOKSHELVES_BOOKS_LAST_UPDATE_COLUMN, defaultValue = "0") var booksLastUpdate: Long,
)

/**
 * Bookshelf entity with associated tag entity
 */
data class BookshelfAndTag(
    @Embedded val bookshelf: Bookshelf,
    @Embedded var tag: TagEntity?
)

@Dao
abstract class BookshelvesDao(private val db: BookDatabase) {
    /**
     * Get the paging source for all bookshelves
     */
    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(value = "SELECT * FROM $BOOKSHELVES_TAGS_JOIN ORDER BY $BOOKSHELVES_TITLE_COLUMN")
    abstract fun getShelvesAndTags(): PagingSource<Int, BookshelfAndTag>

    /**
     * Get the paging source for all bookshelves
     */
    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(value = "SELECT * FROM $BOOKSHELVES_TAGS_JOIN WHERE $BOOKSHELVES_ID_COLUMN == :bookshelfId LIMIT 1")
    abstract suspend fun getShelfAndTag(bookshelfId: Long): BookshelfAndTag?

    /**
     * Get a LiveData of the list of bookshelves ordered by name
     */
    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(value = "SELECT * FROM $BOOKSHELVES_TAGS_JOIN ORDER BY $BOOKSHELVES_TITLE_COLUMN")
    abstract fun getShelvesAndTagsLive(): LiveData<List<BookshelfAndTag>>

    /**
     * Get the complete list of bookshelves
     */
    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $BOOKSHELVES_TAGS_JOIN ORDER BY $BOOKSHELVES_TITLE_COLUMN")
    abstract suspend fun getShelves(): List<BookshelfAndTag>

    /**
     * Add a new bookshelf to the database
     * @param bookshelfEntity The bookshelf
     * @return The id for the added bookshelf
     */
    @Insert(entity = BookshelfEntity::class)
    protected abstract suspend fun internalAdd(bookshelfEntity: Bookshelf): Long

    /**
     * Update a bookshelf
     */
    @Update(entity = BookshelfEntity::class)
    protected abstract suspend fun update(bookshelfEntity: Bookshelf): Int

    suspend fun updateBookshelf(bookshelf: Bookshelf): Boolean {
        return update(bookshelf) > 0
    }

    /**
     * Find a bookshelf entity using the bookshelf id
     * @param bookshelfId The bookshelf id
     * @return The BookshelfEntity or null if not found
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM $BOOKSHELVES_TABLE WHERE $BOOKSHELVES_BOOKSHELF_ID_COLUMN = :bookshelfId LIMIT 1")
    abstract suspend fun findBookshelfByBookshelfId(bookshelfId: Int): Bookshelf?

    @Transaction
    open suspend fun add(bookshelfEntity: Bookshelf): Long {
        // Add the new bookshelf entity
        bookshelfEntity.id = 0L
        bookshelfEntity.id = internalAdd(bookshelfEntity)
        return bookshelfEntity.id
    }

    /**
     * Toggle whether a bookshelf is linked to a tag
     * @param shelf The bookshelf with its linked tag
     */
    @Transaction
    open suspend fun toggleTagAndBookshelfLink(shelf: BookshelfAndTag) {
        // Get the bookshelf and tag
        shelf.tag?.let { tag ->
            // We have a tag, so unlink it
            tag.bookshelf = null
            shelf.tag = null
            db.getTagDao().addWithUndo(tag) { true }
        } ?: run {
            // We don't have a tag, so link to a tag. Create the tag if needed
            shelf.tag = TagEntity(0L, shelf.bookshelf.title, shelf.bookshelf.description?: "", 0, shelf.bookshelf.id).also {
                // Update the tag
                it.id = db.getTagDao().addWithUndo(it) { true }
            }
        }
        // Set the last sync volumes to null and the last update time to 0
        updateLastShelfVolumes(shelf.bookshelf.id, 0, null)
    }

    /**
     * Delete a bookshelf from the database
     */
    @Query("DELETE FROM $BOOKSHELVES_TABLE WHERE $BOOKSHELVES_ID_COLUMN = :id")
    protected abstract suspend fun delete(id: Long): Int

    /**
     * Delete bookshelf and unlink tag if needed.
     */
    @Transaction
    open suspend fun delete(bookshelf: BookshelfAndTag): Int {
        // If the bookshelf doesn't have and id, then return
        if (bookshelf.bookshelf.id == 0L)
            return 0
        // If the bookshelf is linked to a tag, unlink it.
        bookshelf.tag?.let {
            toggleTagAndBookshelfLink(bookshelf)
        }

        // Delete the shelf.
        return delete(bookshelf.bookshelf.id)
    }

    /**
     * Get the blob holding the last synced volumes
     */
    @Query("SELECT $BOOKSHELVES_LAST_SYNC_VOLUMES_COLUMN FROM $BOOKSHELVES_TABLE WHERE $BOOKSHELVES_ID_COLUMN = :id")
    abstract suspend fun internalGetLastShelfVolumes(id: Long): ByteArray?

    /**
     * Get the set of last synced volumes
     */
    suspend fun getLastShelfVolumes(id: Long): Set<String> {
        // Get the blob and convert to a set
        return internalGetLastShelfVolumes(id).toStringSet()
    }

    /**
     * Update the last synced volumes blob and last update time
     */
    @Update(entity = BookshelfEntity::class)
    protected abstract suspend fun updateLastShelfVolumes(volumes: LastShelfVolumes): Int

    /**
     * Update the last synced volumes set and the last update time
     */
    suspend fun updateLastShelfVolumes(id: Long, booksLastUpdate: Long, volumes: Set<String>?): Int {
        // Convert set to blob and update the database
        return updateLastShelfVolumes(
            LastShelfVolumes(
                id = id,
                booksLastUpdate = booksLastUpdate,
                lastSyncVolumes = volumes.fromStringSet()
            )
        )
    }

    /**
     * Class used to get and update the last synced volumes of a bookshelf
     */
    protected data class LastShelfVolumes(
        @ColumnInfo(name = BOOKSHELVES_ID_COLUMN) var id: Long,
        @ColumnInfo(name = BOOKSHELVES_BOOKS_LAST_UPDATE_COLUMN, defaultValue = "0") var booksLastUpdate: Long,
        @ColumnInfo(name = BOOKSHELVES_LAST_SYNC_VOLUMES_COLUMN) var lastSyncVolumes: ByteArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as LastShelfVolumes

            if (id != other.id) return false
            if (!lastSyncVolumes.contentEquals(other.lastSyncVolumes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + (lastSyncVolumes?.contentHashCode() ?: 0)
            return result
        }
    }

    companion object {
        // Type convert to convert between dates in the data base, which are longs, and Date objects
        @OptIn(ExperimentalSerializationApi::class)
        fun ByteArray?.toStringSet(): Set<String> {
            return if (this == null || isEmpty())
                emptySet()
            else
                ProtoBuf.decodeFromByteArray(serializer, this)
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun Set<String>?.fromStringSet(): ByteArray? {
            return if (this == null || isEmpty())
                null
            else
                ProtoBuf.encodeToByteArray(serializer, this)
        }
    }
}
