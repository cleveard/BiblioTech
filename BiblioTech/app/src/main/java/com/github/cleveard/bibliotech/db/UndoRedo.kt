package com.github.cleveard.bibliotech.db

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.lang.IllegalStateException

// Names for the undo transaction table
const val UNDO_TABLE = "undo_table"
const val TRANSACTION_ID_COLUMN = "transaction_id"
const val TRANSACTION_UNDO_ID_COLUMN = "transaction_undo_id"
const val TRANSACTION_DESC_COLUMN = "transaction_desc"
const val TRANSACTION_FLAGS_COLUMN = "transaction_flags"

// Names for the undo operation table
const val OPERATION_TABLE = "operation_table"
const val OPERATION_ID_COLUMN = "operation_id"
const val OPERATION_UNDO_ID_COLUMN = "operation_undo_id"
const val OPERATION_OPERATION_ID_COLUMN = "operation_operation_id"
const val OPERATION_TYPE_COLUMN = "operation_type"
const val OPERATION_CUR_ID_COLUMN = "operation_cur_id"
const val OPERATION_OLD_ID_COLUMN = "operation_old_id"
const val OPERATION_MOD_TIME_COLUMN = "operation_mod_time"

/**
 * Holder for a WHERE clause with arguments
 * @param expression The clause with leading ' WHERE ', if it isn't empty
 * @param args Arguments for the WHERE clause
 */
data class WhereExpression(val expression: String, val args: Array<Any>) {
    /** @inheritDoc */
    override fun hashCode(): Int {
        return expression.hashCode() * 31 + BookFilter.hashArray(args)
    }

    /** @inheritDoc */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WhereExpression

        if (expression != other.expression) return false
        if (!BookFilter.equalArray(args, other.args)) return false
        return true
    }
}

/** Type convert to convert between UndoRedoDao.OperationType and integers */
class EnumConverters {
    @TypeConverter
    fun operationFromInt(value: Int): UndoRedoDao.OperationType {
        return UndoRedoDao.OperationType.values()[value]
    }

    @TypeConverter
    fun operationToInt(value: UndoRedoDao.OperationType): Int {
        return value.ordinal
    }
}

/** UndoRedoOperationEntity for the operation table */
@TypeConverters(EnumConverters::class)
@Entity(tableName = OPERATION_TABLE,
    indices = [
        Index(value = [OPERATION_ID_COLUMN],unique = true),
        Index(value = [OPERATION_UNDO_ID_COLUMN, OPERATION_OPERATION_ID_COLUMN])
    ])
data class UndoRedoOperationEntity(
    @ColumnInfo(name = OPERATION_UNDO_ID_COLUMN) var undoId: Int,
    @ColumnInfo(name = OPERATION_OPERATION_ID_COLUMN) var operationId: Int,
    @ColumnInfo(name = OPERATION_TYPE_COLUMN) var type: UndoRedoDao.OperationType,
    @ColumnInfo(name = OPERATION_CUR_ID_COLUMN) var curId: Long,
    @ColumnInfo(name = OPERATION_OLD_ID_COLUMN, defaultValue = "0") var oldId: Long = 0,
    @ColumnInfo(name = OPERATION_MOD_TIME_COLUMN, defaultValue = "NULL") var modTime: Long? = null,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = OPERATION_ID_COLUMN) var id: Long = 0L
)

/** UndoRedoTransactionEntity for the undo table */
@Entity(tableName = UNDO_TABLE,
    indices = [
        Index(value = [TRANSACTION_ID_COLUMN],unique = true),
        Index(value = [TRANSACTION_UNDO_ID_COLUMN])
    ])
data class UndoTransactionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TRANSACTION_ID_COLUMN) var id: Long,
    @ColumnInfo(name = TRANSACTION_UNDO_ID_COLUMN) var undoId: Int,
    @ColumnInfo(name = TRANSACTION_DESC_COLUMN) var desc: String,
    @ColumnInfo(name = TRANSACTION_FLAGS_COLUMN) var flags: Int = 0
) {
    companion object {
        const val IS_REDO = 1
    }

    var isRedo: Boolean
        get() { return (flags and IS_REDO) != 0 }
        set(v) {
            flags = if (v)
                flags or IS_REDO
            else
                flags and IS_REDO.inv()
        }
}

@Dao
abstract class UndoRedoDao(private val db: BookDatabase) {
    /**
     * Operation Type enum for undo operations
     * @param desc Descriptor of the operation. This object has the methods to
     *             record, undo, redo, discard undo and discard redo for an operation
     */
    enum class OperationType(val desc: OperationDescriptor) {
        /** Add a BookEntity operation */
        ADD_BOOK(object: AddDataDescriptor(BookDatabase.bookTable) {
            override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
                dao.db.getBookDao().invalidateThumbnails(op.curId)
                super.undo(dao, op)
            }

            override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
                dao.db.getBookDao().invalidateThumbnails(op.curId)
                super.redo(dao, op)
            }
        }) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        /** Delete BookEntities operation */
        DELETE_BOOK(object: DeleteDataDescriptor(BookDatabase.bookTable) {
            override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
                dao.db.getBookDao().invalidateThumbnails(op.curId)
                super.undo(dao, op)
            }

            override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
                dao.db.getBookDao().invalidateThumbnails(op.curId)
                super.redo(dao, op)
            }
        }) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
                return dao.recordDelete(this, expression, delete)
            }
        },
        /** Update a BookEntity operation */
        CHANGE_BOOK(object: ChangeDataDescriptor(BookDatabase.bookTable, {
            swapBook(it.curId, it.oldId)
        }) {
            override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
                dao.db.getBookDao().invalidateThumbnails(op.curId)
                super.undo(dao, op)
            }

            override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
                dao.db.getBookDao().invalidateThumbnails(op.curId)
                super.redo(dao, op)
            }
        }) {
            override suspend fun recordUpdate(dao: UndoRedoDao, id: Long, update: suspend () -> Boolean): Boolean {
                if (dao.started > 0)
                    return dao.recordUpdate(this, id, dao.copyForBookUndo(id), update)
                return update()
            }
        },
        /** Add an AuthorEntity operation */
        ADD_AUTHOR(AddDataDescriptor(BookDatabase.authorsTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        /** Delete an AuthorEntity operation */
        DELETE_AUTHOR(DeleteDataDescriptor(BookDatabase.authorsTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, curId: Long, delete: suspend (Long) -> Int): Int {
                return dao.recordDelete(this, curId, delete)
            }
        },
        /** Add a CategoryEntity operation */
        ADD_CATEGORY(AddDataDescriptor(BookDatabase.categoriesTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        /** Delete a CategoryEntity operation */
        DELETE_CATEGORY(DeleteDataDescriptor(BookDatabase.categoriesTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, curId: Long, delete: suspend (Long) -> Int): Int {
                return dao.recordDelete(this, curId, delete)
            }
        },
        /** Add a TagEntity operation */
        ADD_TAG(AddDataDescriptor(BookDatabase.tagsTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        /** Delete TagEntities operation */
        DELETE_TAG(DeleteDataDescriptor(BookDatabase.tagsTable)) {
            /**
             * @inheritDoc
             * Delete one tag entity
             */
            override suspend fun recordDelete(dao: UndoRedoDao, curId: Long, delete: suspend (Long) -> Int): Int {
                return dao.recordDelete(this, curId, delete)
            }

            /**
             * @inheritDoc
             * Delete multiple tag entities
             */
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
                return dao.recordDelete(this, expression, delete)
            }
        },
        /** Update a TagEntity operation */
        CHANGE_TAG(ChangeDataDescriptor(BookDatabase.tagsTable) {
            swapTag(it.curId, it.oldId)
        }) {
            override suspend fun recordUpdate(dao: UndoRedoDao, id: Long, update: suspend () -> Boolean): Boolean {
                if (dao.started > 0)
                    return dao.recordUpdate(this, id, dao.copyForTagUndo(id), update)
                return update()
            }
        },
        /** Add a ViewEntity operation */
        ADD_VIEW(AddDataDescriptor(BookDatabase.viewsTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        /** Delete ViewEntities operation */
        DELETE_VIEW(DeleteDataDescriptor(BookDatabase.viewsTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
                return dao.recordDelete(this, expression, delete)
            }
        },
        /** Update a ViewEntity operation */
        CHANGE_VIEW(ChangeDataDescriptor(BookDatabase.viewsTable) {
            swapView(it.curId, it.oldId)
        }) {
            override suspend fun recordUpdate(dao: UndoRedoDao, id: Long, update: suspend () -> Boolean): Boolean {
                if (dao.started > 0)
                    return dao.recordUpdate(this, id, dao.copyForViewUndo(id), update)
                return update()
            }
        },
        /** Add a BookAuthorEntity operation */
        ADD_BOOK_AUTHOR_LINK(AddLinkDescriptor(BookDatabase.bookAuthorsTable)) {
            override suspend fun recordLink(dao: UndoRedoDao, bookId: Long, linkId: Long) {
                dao.record(this, bookId, linkId)
            }
        },
        /** Delete BookAuthorEntities operation */
        DELETE_BOOK_AUTHOR_LINK(DeleteLinkDescriptor(BookDatabase.bookAuthorsTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
                return dao.recordDeleteLinks(this, expression, delete)
            }
        },
        /** Add a BookCategoryEntity operation */
        ADD_BOOK_CATEGORY_LINK(AddLinkDescriptor(BookDatabase.bookCategoriesTable)) {
            override suspend fun recordLink(dao: UndoRedoDao, bookId: Long, linkId: Long) {
                dao.record(this, bookId, linkId)
            }
        },
        /** Delete BookCategoryEntities operation */
        DELETE_BOOK_CATEGORY_LINK(DeleteLinkDescriptor(BookDatabase.bookCategoriesTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
                return dao.recordDeleteLinks(this, expression, delete)
            }
        },
        /** Add a BookTagEntity operation */
        ADD_BOOK_TAG_LINK(AddLinkDescriptor(BookDatabase.bookTagsTable)) {
            override suspend fun recordLink(dao: UndoRedoDao, bookId: Long, linkId: Long) {
                dao.record(this, bookId, linkId)
            }
        },
        /** Delete BookTagEntities operation */
        DELETE_BOOK_TAG_LINK(DeleteLinkDescriptor(BookDatabase.bookTagsTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
                return dao.recordDeleteLinks(this, expression, delete)
            }
        },
        /** Add a BookEntity operation */
        ADD_ISBN(AddDataDescriptor(BookDatabase.isbnTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        /** Delete BookEntities operation */
        DELETE_ISBN(DeleteDataDescriptor(BookDatabase.isbnTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, curId: Long, delete: suspend (Long) -> Int): Int {
                return dao.recordDelete(this, curId, delete)
            }
        },
        /** Add a BookCategoryEntity operation */
        ADD_BOOK_ISBN_LINK(AddLinkDescriptor(BookDatabase.bookIsbnsTable)) {
            override suspend fun recordLink(dao: UndoRedoDao, bookId: Long, linkId: Long) {
                dao.record(this, bookId, linkId)
            }
        },
        /** Delete BookCategoryEntities operation */
        DELETE_BOOK_ISBN_LINK(DeleteLinkDescriptor(BookDatabase.bookIsbnsTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
                return dao.recordDeleteLinks(this, expression, delete)
            }
        },
        /** Add a SeriesEntity operation */
        ADD_SERIES(AddDataDescriptor(BookDatabase.seriesTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        /** Delete a SeriesEntity operation */
        DELETE_SERIES(DeleteDataDescriptor(BookDatabase.seriesTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
                return dao.recordDelete(this, expression, delete)
            }
        },
        /** Update a SeriesEntity operation */
        CHANGE_SERIES(ChangeDataDescriptor(BookDatabase.seriesTable) {
            swapSeries(it.curId, it.oldId)
        }) {
            override suspend fun recordUpdate(dao: UndoRedoDao, id: Long, update: suspend () -> Boolean): Boolean {
                if (dao.started > 0)
                    return dao.recordUpdate(this, id, dao.copyForSeriesUndo(id), update)
                return update()
            }
        },
        UPDATE_MOD_TIME_BOOK(UpdateModTimeDescriptor(BookDatabase.bookTable)) {
            override suspend fun recordModTimeUpdate(dao: UndoRedoDao, e: WhereExpression, t: Long, update: suspend (expression: WhereExpression, time: Long) -> Int): Int {
                return dao.recordModTimeUpdate(this, e, t, update)
            }
        };

        /**
         * Record an add entity operation
         * @param dao The UndoRedoDao
         * @param id The id of the entity. 0 if the add failed
         */
        open suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
            throw IllegalRecordingException("RecordAdd not implemented for ${desc.table.name}")
        }

        /**
         * Record an update entity operation
         * @param dao The UndoRedoDao
         * @param id The id of the entity being updated.
         * @param update A lambda used to update the entity. It should return true if the
         *               update is successful and false if it is not.
         */
        open suspend fun recordUpdate(dao: UndoRedoDao, id: Long, update: suspend () -> Boolean): Boolean {
            throw IllegalRecordingException("RecordUpdate not implemented for ${desc.table.name}")
        }

        /**
         * Record a delete entity operation
         * @param dao The UndoRedoDao
         * @param curId The id of the entity being deleted.
         * @param delete A lambda used to delete the entity. It should return the number of entities deleted.
         */
        open suspend fun recordDelete(dao: UndoRedoDao, curId: Long, delete: suspend (Long) -> Int): Int {
            throw IllegalRecordingException("RecordDelete not implemented for ${desc.table.name}")
        }

        /**
         * Record a delete entities operation
         * @param dao The UndoRedoDao
         * @param expression A WHERE clause used to select the entities deleted
         * @param delete A lambda used to delete the entity. It should return the number of entities deleted.
         */
        open suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
            throw IllegalRecordingException("RecordDelete not implemented for ${desc.table.name}")
        }

        /**
         * Record an add link entity operation
         * @param dao The UndoRedoDao
         * @param bookId The id of the book in the link entity
         * @param linkId The id of the linked entity in the link entity
         */
        open suspend fun recordLink(dao: UndoRedoDao, bookId: Long, linkId: Long) {
            throw IllegalRecordingException("RecordUpdate not implemented for ${desc.table.name}")
        }

        /**
         * Record a modified time update
         * @param dao The UndoRedoDao
         * @param e A WHERE clause used to select the entities modified
         * @param t The current time
         * @param update A lambda used to update the entity. It should return -1 for failure,
         *               or the number of modified entities updated
         */
        open suspend fun recordModTimeUpdate(dao: UndoRedoDao, e: WhereExpression, t: Long, update: suspend (expression: WhereExpression, time: Long) -> Int): Int {
            throw IllegalRecordingException("RecordModTimeUpdate not implemented for ${desc.table.name}")
        }
    }

    /** Exception used to signal when recording an operation had an error */
    class IllegalRecordingException(message: String): IllegalStateException(message)
    /** Exception used to signal when executing an undo is illegal */
    class IllegalUndoException(message: String): IllegalStateException(message)
    /** Exception used to signal when executing a redo is illegal */
    class IllegalRedoException(message: String): IllegalStateException(message)
    /** Exception used to signal when starting an undo recording is illegal */
    class UndoStartException(message: String): Exception(message)

    /** Number of times nested withUndo() calls were made */
    private var started = 0
    /** Flag to remember when exceptions happen while recording undo */
    private var errorInUndo = false
    /** Is undo recording now */
    val isRecording: Boolean
        get() = started > 0
    /** Oldest undo recording */
    var minUndoId: Int = -1
        private set
    /** Last or current undo recording */
    var undoId: Int = -1
        private set
    /** Newest undo recording */
    var maxUndoId: Int = -1
        private set
    /** Maximum number of undo levels we keep in the database */
    var maxUndoLevels = BookDatabase.UNDO_LEVEL_INITIAL
        private set
    /** Undo id that triggers resetting undo ids in the database */
    var resetUndoAt = Int.MAX_VALUE - 10
        private set
    /** The operation count in the current undo transaction */
    private var operationCount = 0
    /** Set of modified book ids */
    private val modifiedIds: MutableSet<Long> = mutableSetOf()

    /** Initialize the undo from the database */
    private suspend fun initUndo() {
        // Return if already called
        if (undoId >= 0)
            return

        // Get the undo and redo transactions
        var error = false
        val transactions = getTransactions()
        if (transactions.isNullOrEmpty()) {
            // No transactions, initialize the min, max and current undo
            undoId = 0
            minUndoId = 1
            maxUndoId = 0
        } else {
            // We have transaction, initialize the min max and current undo
            minUndoId = transactions.first().undoId
            maxUndoId = transactions.last().undoId
            undoId = minUndoId - 1

            if (minUndoId < 1 || maxUndoId < 1)
                error = true
            else {
                var lastId = undoId
                for (t in transactions) {
                    // Transaction ids should increment by one and
                    // have all undoes followed by all redoes
                    if (t.undoId != lastId + 1 || (!t.isRedo && undoId != lastId)) {
                        error = true
                        break
                    }
                    lastId = t.undoId
                    if (!t.isRedo)
                        undoId = lastId
                }
            }

            // If something was wrong, throw out all of the undoes
            if (error) {
                // Discard the undoes and redoes in batches of consecutive undo or redo transactions
                var first = transactions.first()
                var last = first
                for (t in transactions.drop(1)) {
                    // Break consecutive batch
                    if (t.isRedo != first.isRedo) {
                        // Yes, discard the transactions
                        discard(first.isRedo, first.undoId, last.undoId)
                        // Start next batch
                        first = t
                    }
                    // track the last transaction in the batch
                    last = t
                }
                // Get the last batch
                discard(first.isRedo, first.undoId, last.undoId)
                // Reset the max, min and undo ids
                undoId = 0
                maxUndoId = 0
                minUndoId = 1
            }
        }
    }

    /**
     * Record an operation
     * @param type The operation type
     * @param curId The id of the entity
     * @param oldId The id of the related entity. For updates, this is the id of the copy
     *              For links it is the id of the linked entity
     */
    private suspend fun record(type: OperationType, curId: Long, oldId: Long = 0L) {
        // Only record if recording is turned on
        if (started > 0) {
            val op = UndoRedoOperationEntity(
                id = 0L,
                undoId = undoId,
                operationId = operationCount,
                type = type,
                curId = curId,
                oldId = oldId
            )
            // Add the operation to the database and update the operation count
            addOperation(op).also {
                if (it == 0L)
                    throw IllegalRecordingException("Unexpected error recording an operation")
                ++operationCount
            }
        }
    }

    /**
     * Record a delete operation
     * @param type The operation type
     * @param id The id of the entity
     * @param delete Lambda to perform the delete operation. It returns the number of entities deleted
     * @return The number of entities deleted
     */
    private suspend fun recordDelete(type: OperationType, id: Long, delete: suspend (Long) -> Int): Int {
        return if (started > 0) {
            // If we are recording, then call delete to delete the entity
            delete(id).also {
                // Record the operation if something was delete
                if (it != 0)
                    record(type, id)
            }
        } else {
            // Not recording, just delete the entity
            db.execUpdateDelete(SimpleSQLiteQuery(
                "DELETE FROM ${type.desc.table.name} WHERE ${type.desc.table.idColumn} = ? AND ( ( ${type.desc.table.flagColumn} & ${type.desc.table.flagValue} ) = 0 )", arrayOf(id)
            ))
        }
    }

    /**
     * Record a delete operation
     * @param type The operation type
     * @param expression A WHERE clause to select the entities to delete
     * @param delete Lambda to perform the delete operation. It returns the number of entities deleted
     * @return The number of entities deleted
     */
    private suspend fun recordDelete(type: OperationType, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
        if (started <= 0) {
            // If we are not recording, use the WHERE clause to directly delete the entities
            return db.execUpdateDelete(SimpleSQLiteQuery(
                "DELETE FROM ${type.desc.table.name}${expression.expression}", expression.args
            ))
        }

        // Use an INSERT command to add operation entities for each entity deleted
        // Since this command uses the same WHERE clause as the delete, we won't insert anything
        // if nothing is deleted.
        db.execUpdateDelete(SimpleSQLiteQuery(
            """INSERT OR ABORT INTO $OPERATION_TABLE ( $OPERATION_ID_COLUMN, $OPERATION_UNDO_ID_COLUMN, $OPERATION_OPERATION_ID_COLUMN, $OPERATION_TYPE_COLUMN, $OPERATION_CUR_ID_COLUMN )
            | SELECT null, $undoId, $operationCount, ${type.ordinal}, ${type.desc.table.idColumn} FROM ${type.desc.table.name}${expression.expression}
        """.trimMargin(),
            expression.args
        )).also {
            // Only bump the operation count if something was inserted
            if (it != 0)
                ++operationCount
        }
        // Actually delete the entities
        return delete(expression)
    }


    /**
     * Record an update operation
     * @param type The operation type
     * @param id The id of the entity being updated
     * @param copyId the id of the copy of the entity
     * @param update Lambda used to update the entity
     * @return True if the update succeeded
     */
    private suspend fun recordUpdate(type: OperationType, id: Long, copyId: Long, update: suspend () -> Boolean): Boolean {
        // Do the update
        val updated = update()
        // Record the update if it succeeded
        if (updated)
            record(type, id,  copyId)
         else {
             // Delete the copy if it didn't succeed
             db.execUpdateDelete(SimpleSQLiteQuery(
                 "DELETE FROM ${type.desc.table.name} WHERE ${type.desc.table.idColumn} = ?"
                 , arrayOf(copyId)
             ))
        }
        return updated

    }

    /**
     * Record an update operation
     * @param undoType The undo operation type
     * @param e The expression to selected the modified entities
     * @param t The current time
     * @param update A Lambda used to perform the update
     * @return The number of entities updated or -1 on error
     */
    private suspend fun recordModTimeUpdate(undoType: OperationType, e: WhereExpression, t: Long, update: suspend (expression: WhereExpression, time: Long) -> Int): Int {
        val column = undoType.desc.table.modTimeColumn?: return 0
        if (started <= 0) {
            // If we are not recording, use the WHERE clause to directly update the entities
            return update(e, t)
        }

        // Use an INSERT command to add operation entities for each entity deleted
        // Since this command uses the same WHERE clause as the delete, we won't insert anything
        // if nothing is deleted.
        db.execUpdateDelete(SimpleSQLiteQuery(
            """INSERT OR ABORT INTO $OPERATION_TABLE ( $OPERATION_ID_COLUMN, $OPERATION_UNDO_ID_COLUMN, $OPERATION_OPERATION_ID_COLUMN, $OPERATION_TYPE_COLUMN, $OPERATION_CUR_ID_COLUMN, $OPERATION_OLD_ID_COLUMN, $OPERATION_MOD_TIME_COLUMN )
            | SELECT null, $undoId, $operationCount, ${undoType.ordinal}, ${undoType.desc.table.idColumn}, $column, $t FROM ${undoType.desc.table.name}${e.expression}
        """.trimMargin(),
            e.args
        )).also {
            // Only bump the operation count if something was inserted
            if (it != 0)
                ++operationCount
        }

        // Actually update the entities
        return update(e, t)
    }

    /**
     * Record a delete link operation
     * @param type The operation type
     * @param expression A WHERE clause to select the links to delete
     * @param delete Lambda to perform the delete operation. It returns the number of entities deleted
     * @return The number of entities deleted
     */
    private suspend fun recordDeleteLinks(type: OperationType, expression: WhereExpression, delete: suspend (WhereExpression) -> Int): Int {
        if (started > 0) {
            // We recording, so insert operations to record all of the links deleted
            // Since this command uses the same WHERE clause as the delete, we won't insert anything
            // if nothing is deleted.
            db.execUpdateDelete(SimpleSQLiteQuery(
                """INSERT OR ABORT INTO $OPERATION_TABLE ( $OPERATION_ID_COLUMN, $OPERATION_UNDO_ID_COLUMN, $OPERATION_OPERATION_ID_COLUMN, $OPERATION_TYPE_COLUMN, $OPERATION_CUR_ID_COLUMN, $OPERATION_OLD_ID_COLUMN )
                    | SELECT null, $undoId, $operationCount, ${type.ordinal}, ${type.desc.table.bookIdColumn}, ${type.desc.table.linkIdColumn} FROM ${type.desc.table.name}${expression.expression}
                """.trimMargin(),
                expression.args
            )).also {
                // Only bump the operation count if something was inserted
                if (it != 0)
                    ++operationCount
            }
        }
        // Actually delete the links
        return delete(expression)
    }

    /** Can we undo */
    fun canUndo(): Boolean {
        return undoId >= 0 && undoId >= minUndoId
    }

    /** Can we redo */
    fun canRedo(): Boolean {
        return undoId in 0 until maxUndoId
    }

    fun setBooksModified(bookIds: List<Long>) {
        modifiedIds.addAll(bookIds)
    }

    suspend fun setBooksModified(bookIds: Array<Any>?, filter: BookFilter.BuiltFilter?) {
        db.getBookDao().queryBookIds(bookIds, filter)?.let { modifiedIds.addAll(it) }
    }

    /**
     * Run a transaction with undo
     * @param desc Description of the transaction
     * @param isRecording Array to return whether undo is still being recorded
     * @param operation The operation to run
     */
    suspend fun <T> withUndo(desc: String, isRecording: Array<Boolean>? = null, operation: suspend () -> T): T {
        // If undo is disable, just run the operation
        if (maxUndoLevels == 0)
            return operation().also {
                // Update the modified books modified time
                if (modifiedIds.isNotEmpty() && started <= 1)
                    db.getBookDao().updateModifiedTimeWithUndo(modifiedIds)
            }

        var finished = false    // Did we finish the operation
        try {
            // Start a transaction to turn on undo. This is for thread safety
            db.withTransaction {
                initUndo()
                // Increment counter
                ++started
                // If there was an error, then throw an exception
                if (errorInUndo)
                    throw UndoStartException("Start undo after error")
                // If this isn't nested, then setup the undo
                if (started == 1) {
                    // Bump the undo id and set the number of recorded operations to 0
                    ++undoId
                    operationCount = 0
                    // Add the undo transaction to the database
                    if (addUndoTransaction(UndoTransactionEntity(0L, undoId, desc)) == 0L)
                        throw UndoStartException("Unexpected error starting undo")
                    // Discard any redoes
                    if (undoId <= maxUndoId)
                        discard(true, undoId, maxUndoId)
                    maxUndoId = undoId
                    // Clear the modified book ids
                    modifiedIds.clear()
                }
            }

            // Run the operation
            return operation().also {
                // Good we complete
                finished = true
            }
        // } catch (e: Exception) {
        //     throw UndoException(e)
        } finally {
            withContext(NonCancellable) {
                // Start a transaction to turn off undo. This is for thread safety
                db.withTransaction {
                    initUndo()
                    // If we didn't finish, then there was an error
                    if (!finished)
                        errorInUndo = true
                    // Update the modified books, but don't decrement started yet
                    if (modifiedIds.isNotEmpty() && !errorInUndo)
                        db.getBookDao().updateModifiedTimeWithUndo(modifiedIds)
                    // Are we at the outermost undo
                    if (--started <= 0) {
                        // Yes, make sure started is 0
                        started = 0
                        // Did we get an error, or not record anything
                        if (errorInUndo || operationCount == 0) {
                            // Yes, discard this undo, and reset the error flag
                            errorInUndo = false
                            --undoId
                            maxUndoId = undoId
                            discard(false, undoId + 1, undoId + 1)
                        } else {
                            // We recorded something, discard extra undo levels
                            maxUndoId = undoId
                            if (maxUndoId - minUndoId >= maxUndoLevels) {
                                discard(false, minUndoId, maxUndoId - maxUndoLevels)
                                minUndoId = maxUndoId - maxUndoLevels + 1
                            }
                            // Once the undo counter gets high enough, set the min back to 1
                            if (maxUndoId >= resetUndoAt)
                                resetUndo(minUndoId - 1)
                        }
                    }
                    isRecording?.set(0, started > 0)
                }
            }
        }
    }

    /**
     * Undo the next undo
     */
    suspend fun undo(): Boolean {
        if (db.inTransaction())
            throw IllegalUndoException("Undoing during transaction is not allowed")
        // Start a transaction to handle multi-threading issues
        return db.withTransaction {
            // Make sure undo is initialized
            initUndo()
            // Do we have something to undo
            if (undoId >= minUndoId) {
                // Yes, make sure we aren't recording
                if (started > 0)
                    throw IllegalUndoException("Undoing during started transaction is not allowed")
                // Get the next transaction
                getNextUndo()?.let { undo ->
                    // Get the list of operations and undo all of them
                    // We undo them backward from the order they are recorded
                    getOperationsDesc(undo.undoId, undo.undoId)?.let {
                        for (op in it) {
                            op.type.desc.undo(this, op)
                        }
                    }
                    // Mark the undo transaction as a redo transaction and update it
                    undo.isRedo = true
                    update(undo)
                    // Update the undo id
                    undoId = undo.undoId - 1
                    true
                } == true // Only successful if we found an undo transaction
            } else
                false
        }
    }

    /**
     * Redo the next redo
     */
    suspend fun redo(): Boolean {
        if (db.inTransaction())
            throw IllegalRedoException("Redoing during transaction is not allowed")
        // Start a transaction to handle multi-threading issues
        return db.withTransaction {
            // Make sure undo is initialized
            initUndo()
            // Do we have something to redo
            if (undoId < maxUndoId) {
                // Yes, make sure we aren't recording
                if (started > 0)
                    throw IllegalRedoException("Redoing during started transaction is not allowed")
                // Get the next transaction
                getNextRedo()?.let { redo ->
                    // Get the list of operations and redo all of them
                    // We redo them in the order they are recorded
                    getOperationsAsc(redo.undoId, redo.undoId)?.let {
                        for (op in it) {
                            op.type.desc.redo(this, op)
                        }
                    }
                    // Mark the undo transaction as an undo transaction and update it
                    redo.isRedo = false
                    update(redo)
                    // Update the undo id
                    undoId = redo.undoId
                    true
                } == true // Only successful if we found a redo transaction
            } else
                false
        }
    }

    /**
     * Set the maximum undo levels kept in the database
     * @param maxLevels The new maximum
     * @param resetThreshold The threshold to reset undo ids
     */
    @Transaction
    open suspend fun setMaxUndoLevels(maxLevels: Int, resetThreshold: Int = 0) {
        // Make sure undo is initialized
        initUndo()
        // Did levels change
        if (maxLevels != maxUndoLevels) {
            // Yes, make sure we aren't recording
            if (started > 0)
                throw IllegalStateException("Cannot set undo levels while recording")
            // We are changing the number of levels
            maxUndoLevels = maxLevels
            // First delete undoes from oldest to newest to satisfy the number of levels
            val deleteUndo = maxUndoId - maxUndoLevels
            if (deleteUndo >= minUndoId) {
                // Need to delete some levels, but not beyond undoId
                val limit = deleteUndo.coerceAtMost(undoId)
                if (limit >= minUndoId) {
                    // Delete the levels and reset the minUndoId
                    discard(false, minUndoId, limit)
                    minUndoId = limit + 1
                }
            }

            // Next delete the redoes from newest to oldest to satisfy the number of levels
            val deleteRedo = minUndoId + maxUndoLevels
            if (deleteRedo <= maxUndoId) {
                // Delete the newer redoes
                discard(true, deleteRedo, maxUndoId)
                maxUndoId = deleteRedo - 1
            }
            // If we delete everything, then reset the min, max and undo id
            if (maxUndoId < minUndoId) {
                minUndoId = 1
                maxUndoId = 0
                undoId = 0
            }
        }
        // Set the reset threshold if it isn't 0
        if (resetThreshold != 0)
            resetUndoAt = resetThreshold
        // If the reset threshold is too small, set a big value
        if (resetUndoAt <= maxUndoLevels)
            resetUndoAt = Int.MAX_VALUE - 10
    }

    /**
     * Delete all undo from a table and link table
     */
    private fun deleteUndo(table: BookDatabase.TableDescription, linkTable: BookDatabase.TableDescription) {
        // Delete links for hidden rows in book table
        db.execUpdateDelete(SimpleSQLiteQuery("""
            |DELETE FROM ${linkTable.name} WHERE ${linkTable.bookIdColumn} IN (
            | SELECT $BOOK_ID_COLUMN FROM $BOOK_TABLE WHERE ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) != 0 )
            |)
            """.trimMargin()))
        // Delete links for hidden rows in table
        db.execUpdateDelete(SimpleSQLiteQuery("""
            |DELETE FROM ${linkTable.name} WHERE ${linkTable.linkIdColumn} IN (
            | SELECT ${table.idColumn} FROM ${table.name} WHERE ( ( ${table.flagColumn} & ${table.flagValue} ) != 0 )
            |)
            """.trimMargin()))
        // Delete hidden rows in table
        db.execUpdateDelete(SimpleSQLiteQuery("DELETE FROM ${table.name} WHERE ( ( ${table.flagColumn} & ${table.flagValue} ) != 0 )"))
    }

    /**
     * Clear all of the undo info in the database
     */
    @Transaction
    open suspend fun clear() {
        // We clear without assuming the undo data is consistent.
        // We delete all of the undo transactions and operations
        // Then clear all of the links that mistakenly linked to
        // hidden entities. Then clear all of the hidden entities

        // Delete all of the undo info
        delete(Int.MIN_VALUE, Int.MAX_VALUE)

        // Delete author undo
        deleteUndo(BookDatabase.authorsTable, BookDatabase.bookAuthorsTable)

        // Delete tag undo
        deleteUndo(BookDatabase.tagsTable, BookDatabase.bookTagsTable)

        // Delete category undo
        deleteUndo(BookDatabase.categoriesTable, BookDatabase.bookCategoriesTable)

        // Delete isbns undo
        deleteUndo(BookDatabase.isbnTable, BookDatabase.bookIsbnsTable)

        // Delete hidden books
        db.execUpdateDelete(SimpleSQLiteQuery("DELETE FROM $BOOK_TABLE WHERE ( ( $BOOK_FLAGS & ${BookEntity.HIDDEN} ) != 0 )"))

        // Reset the undo ids
        minUndoId = 1
        maxUndoId = 0
        undoId = 0
    }

    /**
     * Discard undo transactions
     * @param redo True if the transactions are redoes
     * @param min Id of first transaction
     * @param max Id of last transaction
     * All transaction from min through max inclusive are discarded. Should only be called
     * within a database transaction
     */
    private suspend fun discard(redo: Boolean, min: Int, max: Int) {
        // Make sure there is something do discard
        if (min <= max) {
            // Function used to get the operations
            val get = if (redo) this::getOperationsAsc else this::getOperationsDesc
            // Function used to discard each operation
            val dis = if (redo) OperationDescriptor::discardRedo else OperationDescriptor::discardUndo
            // Get the operations
            get(min, max)?.let {
                // And discard each one
                for (op in it) {
                    dis.invoke(op.type.desc, this, op)
                }
            }
            // Delete the operations and transactions
            delete(min, max)
        }
    }

    /**
     * Reset all undo ids using an offset
     * @param offset The amount subtracted from each undo id
     */
    private suspend fun resetUndo(offset: Int) {
        if (offset != 0) {
            db.withTransaction {
                // Update the operations
                transactionQuery(
                    "UPDATE $OPERATION_TABLE SET $OPERATION_UNDO_ID_COLUMN = $OPERATION_UNDO_ID_COLUMN - ?",
                    offset
                )
                // Update the transactions
                transactionQuery(
                    "UPDATE $UNDO_TABLE SET $TRANSACTION_UNDO_ID_COLUMN = $TRANSACTION_UNDO_ID_COLUMN - ?",
                    offset
                )
                // Update the min, max and undo ids
                minUndoId -= offset
                undoId -= offset
                maxUndoId -= offset
            }
        }
    }

    /**
     * Add an operation to the operation table
     * @param op The operation
     */
    @Insert
    protected abstract suspend fun addOperation(op: UndoRedoOperationEntity): Long

    /**
     * Add a transaction to the transaction table
     * @param transaction The transaction
     */
    @Insert
    protected abstract suspend fun addUndoTransaction(transaction: UndoTransactionEntity): Long

    /**
     * Update a transaction in the transaction table
     * @param transaction The transaction
     */
    @Update
    protected abstract suspend fun update(transaction: UndoTransactionEntity): Int

    /**
     * Get a list of all transactions
     */
    @Query("SELECT * FROM $UNDO_TABLE ORDER BY $TRANSACTION_UNDO_ID_COLUMN ASC")
    abstract suspend fun getTransactions(): List<UndoTransactionEntity>

    /**
     * Get a list of all transactions
     */
    @Query("SELECT * FROM $UNDO_TABLE ORDER BY $TRANSACTION_UNDO_ID_COLUMN ASC")
    abstract fun getTransactionsLive(): LiveData<List<UndoTransactionEntity>>

    /**
     * Get the next undo transaction
     */
    @Query("SELECT * FROM $UNDO_TABLE WHERE ( ( $TRANSACTION_FLAGS_COLUMN & ${UndoTransactionEntity.IS_REDO} ) = 0) ORDER BY $TRANSACTION_UNDO_ID_COLUMN DESC LIMIT 1")
    abstract suspend fun getNextUndo(): UndoTransactionEntity?

    /**
     * Get the next redo transaction
     */
    @Query("SELECT * FROM $UNDO_TABLE WHERE ( ( $TRANSACTION_FLAGS_COLUMN & ${UndoTransactionEntity.IS_REDO} ) != 0) ORDER BY $TRANSACTION_UNDO_ID_COLUMN ASC LIMIT 1")
    abstract suspend fun getNextRedo(): UndoTransactionEntity?

    /**
     * Get a list of operations in a range of undo ids in ascending undo id, operation order
     * @param min The inclusive min undo id in the range
     * @param max The inclusive max undo id in the range
     */
    @Query("SELECT * FROM $OPERATION_TABLE WHERE $OPERATION_UNDO_ID_COLUMN BETWEEN :min and :max ORDER BY $OPERATION_UNDO_ID_COLUMN ASC, $OPERATION_OPERATION_ID_COLUMN ASC")
    abstract suspend fun getOperationsAsc(min: Int, max: Int): List<UndoRedoOperationEntity>

    /**
     * Get a list of operations in a range of undo ids in descending undo id, operation order
     * @param min The inclusive min undo id in the range
     * @param max The inclusive max undo id in the range
     */
    @Query("SELECT * FROM $OPERATION_TABLE WHERE $OPERATION_UNDO_ID_COLUMN BETWEEN :min and :max ORDER BY $OPERATION_UNDO_ID_COLUMN DESC, $OPERATION_OPERATION_ID_COLUMN DESC")
    abstract suspend fun getOperationsDesc(min: Int, max: Int): List<UndoRedoOperationEntity>

    /**
     * Run an update/delete SQLite command
     * @param query The SQLite command
     * @param args The command arguments
     */
    @Transaction
    protected open fun transactionQuery(query: String, vararg args: Any): Int {
        return db.execUpdateDelete(SimpleSQLiteQuery(query, args))
    }

    /**
     * Swap a book entity with its copy
     * @param list The list of the two entities
     * @param curIndex Index of the current entity
     * @param swap Lambda to swap flags and ids. This is the current entity and it is the copy
     * @return The list of entities, or null if it couldn't swap
     */
    private fun <T> swapEntity(list: List<T>, curIndex: Int, swap: T.(T) -> Unit): List<T>? {
        return if (list.size == 2) {
            list[curIndex].swap(list[1 - curIndex])
            return list
        } else
            null
    }

    /**
     * Get a book entity and its copy
     */
    @Query("SELECT * FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN IN ( :id1, :id2 ) ORDER BY $BOOK_ID_COLUMN")
    protected abstract suspend fun queryBook(id1: Long, id2: Long): List<BookEntity>
    /**
     * Update a book entity
     */
    @Update
    protected abstract suspend fun updateBook(entity: BookEntity): Int
    /**
     * Swap a book entity with its copy
     */
    @Transaction
    protected open suspend fun swapBook(curId: Long, copyId: Long): Int {
        return swapEntity(queryBook(curId, copyId), if (curId < copyId) 0 else 1) {copy ->
            // Copy flags from current to copy
            copy.flags = (copy.flags and BookEntity.PRESERVE) or (flags and BookEntity.PRESERVE.inv())
            // Hide current
            flags = (flags and BookEntity.PRESERVE) or BookEntity.HIDDEN
            // Swap ids
            id = copy.id.also {copy.id = id }
        }?.let {
            // Update the books
            updateBook(it[0]) + updateBook(it[1])
        }?: 0
    }

    /**
     * Get a tag entity and its copy
     */
    @Query("SELECT * FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN IN ( :id1, :id2 ) ORDER BY $TAGS_ID_COLUMN")
    protected abstract suspend fun queryTag(id1: Long, id2: Long): List<TagEntity>
    /**
     * Update a tag entity
     */
    @Update
    protected abstract suspend fun updateTag(entity: TagEntity): Int
    /**
     * Swap a tag entity with its copy
     */
    @Transaction
    protected open suspend fun swapTag(curId: Long, copyId: Long): Int {
        return swapEntity(queryTag(curId, copyId), if (curId < copyId) 0 else 1) {copy ->
            // Copy flags from current to copy
            copy.flags = (copy.flags and TagEntity.PRESERVE) or (flags and TagEntity.PRESERVE.inv())
            // Hide current
            flags = (flags and TagEntity.PRESERVE) or TagEntity.HIDDEN
            // Swap ids
            id = copy.id.also {copy.id = id }
        }?.let {
            // Update the tags
            updateTag(it[0]) + updateTag(it[1])
        }?: 0
    }

    /**
     * Get a view entity and its copy
     */
    @Query("SELECT * FROM $VIEWS_TABLE WHERE $VIEWS_ID_COLUMN IN ( :id1, :id2 ) ORDER BY $VIEWS_ID_COLUMN")
    protected abstract suspend fun queryView(id1: Long, id2: Long): List<ViewEntity>
    /**
     * Update a view entity
     */
    @Update
    protected abstract suspend fun updateView(entity: ViewEntity): Int

    /**
     * Swap a view entity with its copy
     */
    @Transaction
    protected open suspend fun swapView(curId: Long, copyId: Long): Int {
        return swapEntity(queryView(curId, copyId), if (curId < copyId) 0 else 1) {copy ->
            // Copy flags from current to copy
            copy.flags = (copy.flags and ViewEntity.PRESERVE) or (flags and ViewEntity.PRESERVE.inv())
            // Hide current
            flags = (flags and ViewEntity.PRESERVE) or ViewEntity.HIDDEN
            // Swap ids
            id = copy.id.also {copy.id = id }
        }?.let {
            // Update the views
            updateView(it[0]) + updateView(it[1])
        }?: 0
    }

    /**
     * Get a view entity and its copy
     */
    @Query("SELECT * FROM $SERIES_TABLE WHERE $SERIES_ID_COLUMN IN ( :id1, :id2 ) ORDER BY $SERIES_ID_COLUMN")
    protected abstract suspend fun querySeries(id1: Long, id2: Long): List<SeriesEntity>
    /**
     * Update a view entity
     */
    @Update
    protected abstract suspend fun updateSeries(entity: SeriesEntity): Int

    /**
     * Swap a view entity with its copy
     */
    @Transaction
    protected open suspend fun swapSeries(curId: Long, copyId: Long): Int {
        return swapEntity(querySeries(curId, copyId), if (curId < copyId) 0 else 1) {copy ->
            // Copy flags from current to copy
            copy.flags = (copy.flags and SeriesEntity.PRESERVE) or (flags and SeriesEntity.PRESERVE.inv())
            // Hide current
            flags = (flags and SeriesEntity.PRESERVE) or SeriesEntity.HIDDEN
            // Swap ids
            id = copy.id.also {copy.id = id }
        }?.let {
            // Update the series
            updateSeries(it[0]) + updateSeries(it[1])
        }?: 0
    }

    /**
     * Delete a range of transactions and operations
     * @param min The inclusive min undo id in the range
     * @param max The inclusive max undo id in the range
     * @return the number of transactions and operations deleted
     */
    protected open suspend fun delete(min: Int, max: Int): Int {
        return transactionQuery(
            "DELETE FROM $OPERATION_TABLE WHERE $OPERATION_UNDO_ID_COLUMN BETWEEN ? and ?",
            min, max
        ) +
        transactionQuery(
            "DELETE FROM $UNDO_TABLE WHERE $TRANSACTION_UNDO_ID_COLUMN BETWEEN ? AND ?",
            min, max
        )
    }

    /**
     * Make a copy of a book entity
     * @param bookId The id of the book entity
     * @return The id of the copy
     */
    @Transaction
    protected open suspend fun copyForBookUndo(bookId: Long): Long {
        return db.execInsert(SimpleSQLiteQuery(
            """INSERT INTO $BOOK_TABLE ( $BOOK_ID_COLUMN, $VOLUME_ID_COLUMN, $SOURCE_ID_COLUMN, $TITLE_COLUMN, $SUBTITLE_COLUMN, $DESCRIPTION_COLUMN, $PAGE_COUNT_COLUMN, $BOOK_COUNT_COLUMN, $VOLUME_LINK, $RATING_COLUMN, $BOOK_SERIES_COLUMN, $SERIES_ORDER_COLUMN, $DATE_ADDED_COLUMN, $DATE_MODIFIED_COLUMN, $SMALL_THUMB_COLUMN, $LARGE_THUMB_COLUMN, $BOOK_FLAGS )
                | SELECT NULL, $VOLUME_ID_COLUMN, $SOURCE_ID_COLUMN, $TITLE_COLUMN, $SUBTITLE_COLUMN, $DESCRIPTION_COLUMN, $PAGE_COUNT_COLUMN, $BOOK_COUNT_COLUMN, $VOLUME_LINK, $RATING_COLUMN, $BOOK_SERIES_COLUMN, $SERIES_ORDER_COLUMN, $DATE_ADDED_COLUMN, $DATE_MODIFIED_COLUMN, $SMALL_THUMB_COLUMN, $LARGE_THUMB_COLUMN, $BOOK_FLAGS | ${BookEntity.HIDDEN} FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = ?
            """.trimMargin(),
            arrayOf(bookId)
        ))
    }

    /**
     * Make a copy of a tag entity
     * @param tagId The id of the tag entity
     * @return The id of the copy
     */
    @Transaction
    protected open suspend fun copyForTagUndo(tagId: Long): Long {
        return db.execInsert(SimpleSQLiteQuery(
            """INSERT INTO $TAGS_TABLE ( $TAGS_ID_COLUMN, $TAGS_NAME_COLUMN, $TAGS_DESC_COLUMN, $TAGS_FLAGS )
                | SELECT NULL, $TAGS_NAME_COLUMN, $TAGS_DESC_COLUMN, $TAGS_FLAGS | ${TagEntity.HIDDEN} FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN = ?
            """.trimMargin(),
            arrayOf(tagId)
        ))
    }

    /**
     * Make a copy of a tag entity
     * @param seriesId The id of the tag entity
     * @return The id of the copy
     */
    @Transaction
    protected open suspend fun copyForSeriesUndo(seriesId: Long): Long {
        return db.execInsert(SimpleSQLiteQuery(
            """INSERT INTO $SERIES_TABLE ( $SERIES_ID_COLUMN, $SERIES_SERIES_ID_COLUMN, $SERIES_TITLE_COLUMN, $SERIES_FLAG_COLUMN )
                | SELECT NULL, $SERIES_SERIES_ID_COLUMN, $SERIES_TITLE_COLUMN, $SERIES_FLAG_COLUMN | ${SeriesEntity.HIDDEN} FROM $SERIES_TABLE WHERE $SERIES_ID_COLUMN = ?
            """.trimMargin(),
            arrayOf(seriesId)
        ))
    }

    /**
     * Make a copy of a view entity
     * @param viewId The id of the view entity
     * @return The id of the copy
     */
    @Transaction
    protected open suspend fun copyForViewUndo(viewId: Long): Long {
        return db.execInsert(SimpleSQLiteQuery(
            """INSERT INTO $VIEWS_TABLE ( $VIEWS_ID_COLUMN, $VIEWS_NAME_COLUMN, $VIEWS_DESC_COLUMN, $VIEWS_FILTER_COLUMN, $VIEWS_FLAGS )
                | SELECT NULL, $VIEWS_NAME_COLUMN, $VIEWS_DESC_COLUMN, $VIEWS_FILTER_COLUMN, $VIEWS_FLAGS | ${ViewEntity.HIDDEN} FROM $VIEWS_TABLE WHERE $VIEWS_ID_COLUMN = ?
            """.trimMargin(),
            arrayOf(viewId)
        ))
    }

    /**
     * Interface for an operation
     */
    interface OperationDescriptor {
        /** The table the operation record */
        val table: BookDatabase.TableDescription

        /**
         * Undo the operation
         * @param dao The UndoRedoDao
         * @param op The operation entity to be undone
         */
        suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity)

        /**
         * Discard an undo the operation
         * @param dao The UndoRedoDao
         * @param op The operation entity to be discarded
         */
        suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity)

        /**
         * Redo the operation
         * @param dao The UndoRedoDao
         * @param op The operation entity to be redone
         */
        suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity)

        /**
         * Discard a redo the operation
         * @param dao The UndoRedoDao
         * @param op The operation entity to be discarded
         */
        suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity)
    }

    /**
     * Descriptor for add operations
     * @param table The table the operation applies to
     */
    private open class AddDataDescriptor(override val table: BookDatabase.TableDescription): OperationDescriptor {
        /** @inheritDoc */
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // To undo an add set the hidden flag
            dao.transactionQuery(
                "UPDATE ${table.name} SET ${table.flagColumn} = ${table.flagColumn} | ${table.flagValue} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }

        /** @inheritDoc */
        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }

        /** @inheritDoc */
        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // To redo an add clear the hidden flag
            dao.transactionQuery(
                "UPDATE ${table.name} SET ${table.flagColumn} = ${table.flagColumn} & ~${table.flagValue} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }

        /** @inheritDoc */
        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Delete the hidden entity
            dao.transactionQuery(
                "DELETE FROM ${table.name} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }
    }

    /**
     * Descriptor for delete operations
     * @param table The table the operation applies to
     */
    private open class DeleteDataDescriptor(override val table: BookDatabase.TableDescription): OperationDescriptor {
        /** @inheritDoc */
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // To undo a delete clear the hidden flag
            dao.transactionQuery(
                "UPDATE ${table.name} SET ${table.flagColumn} = ${table.flagColumn} & ~${table.flagValue} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }

        /** @inheritDoc */
        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Delete the hidden entity
            dao.transactionQuery(
                "DELETE FROM ${table.name} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }

        /** @inheritDoc */
        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // To undo an add set the hidden flag
            dao.transactionQuery(
                "UPDATE ${table.name} SET ${table.flagColumn} = ${table.flagColumn} | ${table.flagValue} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }

        /** @inheritDoc */
        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }
    }

    /**
     * Descriptor for update operations
     * @param table The table the operation applies to
     * @param swap Lambda to swap the current and copy entities
     */
    private open class ChangeDataDescriptor(
        override val table: BookDatabase.TableDescription,
        val swap: suspend UndoRedoDao.(op: UndoRedoOperationEntity) -> Unit
    ): OperationDescriptor {
        /** @inheritDoc */
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = swap(dao, op)

        /** @inheritDoc */
        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = discard(dao, op)

        /** @inheritDoc */
        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = swap(dao, op)

        /** @inheritDoc */
        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = discard(dao, op)

        private fun discard(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // To discard undo or redo, delete the copied entity
            dao.transactionQuery(
                "DELETE FROM ${table.name} WHERE ${table.idColumn} = ?",
                op.oldId
            )
        }
    }

    /**
     * Descriptor for add link operations
     * @param table The table the operation applies to
     */
    private class AddLinkDescriptor(override val table: BookDatabase.TableDescription): OperationDescriptor {
        /** @inheritDoc */
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // To undo adding a link, delete the link
            dao.transactionQuery(
                "DELETE FROM ${table.name} WHERE ${table.bookIdColumn} = ? AND ${table.linkIdColumn} = ?",
                op.curId, op.oldId
            )
        }

        /** @inheritDoc */
        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }

        /** @inheritDoc */
        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // To redo adding a link, add the link
            dao.transactionQuery(
                "INSERT OR ABORT INTO ${table.name} ( ${table.idColumn}, ${table.bookIdColumn}, ${table.linkIdColumn} ) VALUES ( null, ?, ? )",
                op.curId, op.oldId
            )
        }

        /** @inheritDoc */
        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }
    }

    /**
     * Descriptor for delete link operations
     * @param table The table the operation applies to
     */
    private class DeleteLinkDescriptor(override val table: BookDatabase.TableDescription): OperationDescriptor {
        /** @inheritDoc */
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // To undo deleting a link. add the link
            dao.transactionQuery(
                "INSERT OR ABORT INTO ${table.name} ( ${table.idColumn}, ${table.bookIdColumn}, ${table.linkIdColumn} ) VALUES ( null, ?, ? )",
                op.curId, op.oldId
            )
        }

        /** @inheritDoc */
        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }

        /** @inheritDoc */
        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // To redo deleting a link, delete the link
            dao.transactionQuery(
                "DELETE FROM ${table.name} WHERE ${table.bookIdColumn} = ? AND ${table.linkIdColumn} = ?",
                op.curId, op.oldId
            )
        }

        /** @inheritDoc */
        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }
    }

    /**
     * Descriptor for modified time updates
     * @param table The table the operation applies to
     */
    private open class UpdateModTimeDescriptor(override val table: BookDatabase.TableDescription): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.db.execUpdateDelete(SimpleSQLiteQuery(
                "UPDATE OR IGNORE $BOOK_TABLE SET $DATE_MODIFIED_COLUMN = ${op.oldId} WHERE $BOOK_ID_COLUMN = ${op.curId}"
            ))
        }

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
        }

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.db.execUpdateDelete(SimpleSQLiteQuery(
                "UPDATE OR IGNORE $BOOK_TABLE SET $DATE_MODIFIED_COLUMN = ${op.modTime} WHERE $BOOK_ID_COLUMN = ${op.curId}"
            ))
        }

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
        }
    }
}
