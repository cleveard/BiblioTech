package com.github.cleveard.bibliotech.db

import androidx.room.*
import java.lang.StringBuilder

const val SERIES_TABLE = "series"
const val SERIES_ID_COLUMN = "series_id"
const val SERIES_SERIES_ID_COLUMN = "series_series_id"
const val SERIES_TITLE_COLUMN = "series_title"

const val SERIES_FLAG_COLUMN = "series_flag"

@Entity(tableName = SERIES_TABLE,
    indices = [
        Index(value = [SERIES_ID_COLUMN], unique = true),
        Index(value = [SERIES_SERIES_ID_COLUMN])
    ]
)
data class SeriesEntity (
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = SERIES_ID_COLUMN) var id: Long,
    @ColumnInfo(name = SERIES_SERIES_ID_COLUMN) var seriesId: String,
    @ColumnInfo(name = SERIES_TITLE_COLUMN) var title: String,
    @ColumnInfo(name = SERIES_FLAG_COLUMN, defaultValue = "0") var flags: Int
) {
    companion object {
        const val HIDDEN = 1
        const val PRESERVE = 0
    }
}

@Dao
abstract class SeriesDao(private val db: BookDatabase) {
    @Query("SELECT * FROM $SERIES_TABLE WHERE (($SERIES_FLAG_COLUMN & ${SeriesEntity.HIDDEN}) = 0)")
    abstract suspend fun get(): List<SeriesEntity>

    /**
     * Add a new series to the database
     * @param seriesEntity The series
     * @return The id for the added series
     */
    @Insert
    protected abstract fun add(seriesEntity: SeriesEntity): Long

    @Update
    protected abstract fun update(seriesEntity: SeriesEntity): Int

    /**
     * Find a series entity using the series id
     * @param seriesId The series id
     * @return The SeriesEntity or null if not found
     */
    @Query("SELECT * FROM $SERIES_TABLE WHERE ( $SERIES_SERIES_ID_COLUMN = :seriesId ) AND ( ( $SERIES_FLAG_COLUMN & ${SeriesEntity.HIDDEN} ) = 0 ) LIMIT 1")
    abstract suspend fun findSeriesBySeriesId(seriesId: String): SeriesEntity?

    @Transaction
    open suspend fun addWithUndo(seriesEntity: SeriesEntity): Long {
        // If the series is already in the database, then use it
        (findSeriesBySeriesId(seriesEntity.seriesId)?.also {
            seriesEntity.id = it.id
            if (it.title != seriesEntity.title
                && !UndoRedoDao.OperationType.CHANGE_SERIES.recordUpdate(db.getUndoRedoDao(), it.id) {
                    update(seriesEntity) > 0
                }
            ) {
                seriesEntity.id = 0L
            }
        })?: run {
            // Add the new series entity
            seriesEntity.id = 0L
            seriesEntity.id = add(seriesEntity).also {
                if (it != 0L)
                    UndoRedoDao.OperationType.ADD_SERIES.recordAdd(db.getUndoRedoDao(), it)
            }
        }
        return seriesEntity.id
    }

    /**
     * Return a list of series ids that are not reference by a book
     */
    @Query("SELECT $SERIES_ID_COLUMN FROM $SERIES_TABLE WHERE ( ( $SERIES_FLAG_COLUMN & ${SeriesEntity.HIDDEN} ) = 0 ) AND NOT EXISTS ( SELECT NULL FROM $BOOK_TABLE WHERE ( $SERIES_ID_COLUMN = $BOOK_SERIES_COLUMN AND ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN}) = 0) ) )")
    protected abstract fun queryUnusedSeriesIds(): List<Long>

    /**
     * Delete all series from books
     * @param deleteSeries A flag to indicate whether the isbn in the Isbns table should be deleted
     *                      if all of its books have been deleted
     */
    @Transaction
    open suspend fun deleteWithUndo(deleteSeries: Boolean): Int {
        var count = 0
        // Should we delete series
        if (deleteSeries) {
            // Yes get the id of the series affected
            val series = queryUnusedSeriesIds()?.toTypedArray<Any>()
            series?.let {seriesIds ->
                var size = seriesIds.size
                if (size > 0) {
                    val builder = StringBuilder(" WHERE ( $SERIES_ID_COLUMN IN ( ?")
                    while (--size > 0)
                        builder.append(", ?")
                    builder.append(" ) )")
                    count = UndoRedoDao.OperationType.DELETE_SERIES.recordDelete(db.getUndoRedoDao(), WhereExpression(builder.toString(), seriesIds)) {
                        db.setHidden(BookDatabase.seriesTable, it)
                    }
                }
            }
        }

        return count
    }
}
