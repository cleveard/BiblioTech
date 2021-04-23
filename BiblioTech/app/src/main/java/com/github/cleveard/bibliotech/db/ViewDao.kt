package com.github.cleveard.bibliotech.db

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.*
import java.lang.Exception

// Views Table name and column names
const val VIEWS_TABLE = "views"                         // Views table name
const val VIEWS_ID_COLUMN = "views_id"                  // Incrementing id
const val VIEWS_NAME_COLUMN = "views_name"              // View name
const val VIEWS_DESC_COLUMN = "views_desc"              // View description
const val VIEWS_FILTER_COLUMN = "views_filter"          // View filter
const val VIEWS_FLAGS = "views_flags"                   // Flags for Views

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

@Dao
abstract class ViewDao(private val db: BookDatabase) {
    /**
     * Get list of views
     */
    @Query(value = "SELECT $VIEWS_NAME_COLUMN FROM $VIEWS_TABLE WHERE ( ( $VIEWS_FLAGS & ${ViewEntity.HIDDEN} ) = 0 ) ORDER BY $VIEWS_NAME_COLUMN")
    abstract fun getViewNames(): LiveData<List<String>>

    /**
     * Get list of views
     */
    @Query(value = "SELECT * FROM $VIEWS_TABLE WHERE ( ( $VIEWS_FLAGS & ${ViewEntity.HIDDEN} ) = 0 )")
    suspend abstract fun getViews(): List<ViewEntity>

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
    protected open suspend fun doDeleteWithUndo(viewName: String): Int {
        val expression = WhereExpression(" WHERE $VIEWS_NAME_COLUMN LIKE ? ESCAPE '\\' AND ( $VIEWS_FLAGS & ${ViewEntity.HIDDEN} ) = 0",
            arrayOf(viewName))
        return UndoRedoDao.OperationType.DELETE_VIEW.recordDelete(db.getUndoRedoDao(), expression) {
            db.execUpdateDelete(
                SimpleSQLiteQuery("UPDATE $VIEWS_TABLE SET $VIEWS_FLAGS = ${ViewEntity.HIDDEN}${it.expression}",
                    it.args
                )
            )
        }
    }

    /**
     * Delete a view by name
     * @param viewName The name of the view. % and _ are escaped before deleting
     * @return The number of views delete
     */
    open suspend fun deleteWithUndo(viewName: String): Int {
        return doDeleteWithUndo(PredicateDataDescription.escapeLikeWildCards(viewName))
    }

    /**
     * Add or update a view to the database
     * @param view The view to add
     * @param onConflict A lambda function to respond to a conflict when adding. Return
     *                   true to accept the conflict or false to abort the add
     * @return The id of the view in the database, or 0L if the add was aborted
     */
    suspend fun addOrUpdateWithUndo(view: ViewEntity, onConflict: (suspend CoroutineScope.(conflict: ViewEntity) -> Boolean)? = null): Long {
        // Look for a conflicting view
        view.name = view.name.trim { it <= ' ' }
        val conflict = findByName(view.name)
        if (conflict != null) {
            // Found one. If the conflict id and view id are the same
            // we treat this as a rename.
            if (conflict.id != view.id) {
                //  Ask the onConflict handler to resolve the conflict
                if (!BookDatabase.callConflict(conflict, onConflict))
                    return 0L       // onConflict says not to add or update
                view.id = conflict.id
            }
            // Update the view
            if (!UndoRedoDao.OperationType.CHANGE_VIEW.recordUpdate(db.getUndoRedoDao(), view.id) {
                doUpdate(view) > 0
            }) {
                view.id = 0L
            }
        } else {
            // Add the new view
            view.id = 0L
            view.id = doAdd(view)
            if (view.id != 0L)
                UndoRedoDao.OperationType.ADD_VIEW.recordAdd(db.getUndoRedoDao(), view.id)
        }
        return view.id
    }

    /**
     * Find a view by name
     * @param name The name of the view
     * @return The view or null if it wasn't found
     */
    @Query(value = "SELECT * FROM $VIEWS_TABLE WHERE $VIEWS_NAME_COLUMN LIKE :name ESCAPE '\\' AND ( ( $VIEWS_FLAGS & ${ViewEntity.HIDDEN} ) = 0 )")
    protected abstract suspend fun doFindByName(name: String): ViewEntity?

    /**
     * Find a view by name
     * @param name The name of the view
     * @return The view or null if it wasn't found
     */
    suspend fun findByName(name: String): ViewEntity? {
        return doFindByName(PredicateDataDescription.escapeLikeWildCards(name))
    }

    /**
     * Find a view by name
     * @param name The name of the view
     * @return The view or null if it wasn't found
     */
    @Query(value = "SELECT * FROM $VIEWS_TABLE WHERE $VIEWS_NAME_COLUMN LIKE :name ESCAPE '\\' AND ( ( $VIEWS_FLAGS & ${ViewEntity.HIDDEN} ) = 0 )")
    protected abstract fun doFindByNameLive(name: String): LiveData<List<ViewEntity>>

    /**
     * Find a view by name
     * @param name The name of the view
     * @return The view or null if it wasn't found
     */
    fun findByNameLive(name: String): LiveData<List<ViewEntity>> {
        return doFindByNameLive(PredicateDataDescription.escapeLikeWildCards(name))
    }
}
