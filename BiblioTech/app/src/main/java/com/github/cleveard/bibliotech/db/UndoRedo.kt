package com.github.cleveard.bibliotech.db

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import java.lang.IllegalStateException


const val UNDO_TABLE = "undo_table"
const val REDO_TABLE = "redo_table"
const val TRANSACTION_ID_COLUMN = "transaction_id"
const val TRANSACTION_UNDO_ID_COLUMN = "transaction_undo_id"
const val TRANSACTION_DESC_COLUMN = "transaction_desc"

const val OPERATION_TABLE = "operation_table"
const val OPERATION_ID_COLUMN = "operation_id"
const val OPERATION_UNDO_ID_COLUMN = "operation_undo_id"
const val OPERATION_OPERATION_ID_COLUMN = "operation_operation_id"
const val OPERATION_TYPE_COLUMN = "operation_type"
const val OPERATION_CUR_ID_COLUMN = "operation_cur_id"
const val OPERATION_OLD_ID_COLUMN = "operation_old_id"

// Type convert to convert between filters and strings
class EnumConverters {
    @TypeConverter
    fun OperationFromInt(value: Int): UndoRedoDao.OperationType {
        return UndoRedoDao.OperationType.values()[value]
    }

    @TypeConverter
    fun OperationToInt(value: UndoRedoDao.OperationType): Int {
        return value.ordinal
    }
}

@TypeConverters(EnumConverters::class)
@Entity(tableName = OPERATION_TABLE,
    indices = [
        Index(value = [OPERATION_ID_COLUMN],unique = true),
        Index(value = [OPERATION_UNDO_ID_COLUMN])
    ])
data class UndoRedoOperationEntity(
    @ColumnInfo(name = OPERATION_UNDO_ID_COLUMN) var undoId: Int,
    @ColumnInfo(name = OPERATION_OPERATION_ID_COLUMN) var operationId: Int,
    @ColumnInfo(name = OPERATION_TYPE_COLUMN) var type: UndoRedoDao.OperationType,
    @ColumnInfo(name = OPERATION_CUR_ID_COLUMN) var curId: Long,
    @ColumnInfo(name = OPERATION_OLD_ID_COLUMN) var oldId: Long = 0,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = OPERATION_ID_COLUMN) var id: Long = 0L
)

@Entity(tableName = UNDO_TABLE,
    indices = [
        Index(value = [TRANSACTION_ID_COLUMN],unique = true),
        Index(value = [TRANSACTION_UNDO_ID_COLUMN])
    ])
data class UndoTransactionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TRANSACTION_ID_COLUMN) var id: Long,
    @ColumnInfo(name = TRANSACTION_UNDO_ID_COLUMN) var undoId: Int,
    @ColumnInfo(name = TRANSACTION_DESC_COLUMN) var desc: String
) {
    constructor(redo: RedoTransactionEntity): this(redo.id, redo.undoId, redo.desc)
}

@Entity(tableName = REDO_TABLE,
    indices = [
        Index(value = [TRANSACTION_ID_COLUMN],unique = true),
        Index(value = [TRANSACTION_UNDO_ID_COLUMN])
    ])
data class RedoTransactionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TRANSACTION_ID_COLUMN) var id: Long,
    @ColumnInfo(name = TRANSACTION_UNDO_ID_COLUMN) var undoId: Int,
    @ColumnInfo(name = TRANSACTION_DESC_COLUMN) var desc: String
) {
    constructor(undo: UndoTransactionEntity): this(undo.id, undo.undoId, undo.desc)
}

@Dao
abstract class UndoRedoDao(private val db: BookDatabase) {
    enum class OperationType(val desc: OperationDescriptor) {
        ADD_BOOK(AddDataDescriptor(BOOK_TABLE, BOOK_ID_COLUMN, BOOK_FLAGS, BookEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, bookId: Long): Long {
                return dao.record(this, bookId)
            }
        },
        DELETE_BOOK(DeleteDataDescriptor(BOOK_TABLE, BOOK_ID_COLUMN, BOOK_FLAGS, BookEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, bookId: Long): Long {
                return dao.record(this, bookId)
            }
        },
        CHANGE_BOOK(ChangeDataDescriptor(BOOK_TABLE, BOOK_ID_COLUMN, BOOK_FLAGS, BookEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, bookId: Long): Long {
                return dao.record(this, bookId, dao.db.getBookDao().copy(bookId))
            }
        },
        ADD_AUTHOR(AddDataDescriptor(AUTHORS_TABLE, AUTHORS_ID_COLUMN, AUTHORS_FLAGS, AuthorEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, authorId: Long): Long {
                return dao.record(this, authorId)
            }
        },
        DELETE_AUTHOR(DeleteDataDescriptor(AUTHORS_TABLE, AUTHORS_ID_COLUMN, AUTHORS_FLAGS, AuthorEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, authorId: Long): Long {
                return dao.record(this, authorId)
            }
        },
        ADD_CATEGORY(AddDataDescriptor(CATEGORIES_TABLE, CATEGORIES_ID_COLUMN, CATEGORIES_FLAGS, CategoryEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, categoryId: Long): Long {
                return dao.record(this, categoryId)
            }
        },
        DELETE_CATEGORY(DeleteDataDescriptor(CATEGORIES_TABLE, CATEGORIES_ID_COLUMN, CATEGORIES_FLAGS, CategoryEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, categoryId: Long): Long {
                return dao.record(this, categoryId)
            }
        },
        ADD_TAG(AddDataDescriptor(TAGS_TABLE, TAGS_ID_COLUMN, TAGS_FLAGS, TagEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, tagId: Long): Long {
                return dao.record(this, tagId)
            }
        },
        DELETE_TAG(DeleteDataDescriptor(TAGS_TABLE, TAGS_ID_COLUMN, TAGS_FLAGS, TagEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, tagId: Long): Long {
                return dao.record(this, tagId)
            }
        },
        CHANGE_TAG(ChangeDataDescriptor(TAGS_TABLE, TAGS_ID_COLUMN, TAGS_FLAGS, TagEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, tagId: Long): Long {
                return dao.record(this, tagId, dao.db.getTagDao().copy(tagId))
            }
        },
        ADD_VIEW(AddDataDescriptor(VIEWS_TABLE, VIEWS_ID_COLUMN, VIEWS_FLAGS, ViewEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, viewId: Long): Long {
                return dao.record(this, viewId)
            }
        },
        DELETE_VIEW(DeleteDataDescriptor(VIEWS_TABLE, VIEWS_ID_COLUMN, VIEWS_FLAGS, ViewEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, viewId: Long): Long {
                return dao.record(this, viewId)
            }
        },
        CHANGE_VIEW(ChangeDataDescriptor(VIEWS_TABLE, VIEWS_ID_COLUMN, VIEWS_FLAGS, ViewEntity.HIDDEN)) {
            suspend fun record(dao: UndoRedoDao, viewId: Long): Long {
                return dao.record(this, viewId, dao.db.getViewDao().copy(viewId))
            }
        },
        ADD_BOOK_AUTHOR_LINK(AddLinkDescriptor(BOOK_AUTHORS_TABLE, BOOK_AUTHORS_ID_COLUMN, BOOK_AUTHORS_BOOK_ID_COLUMN, BOOK_AUTHORS_AUTHOR_ID_COLUMN)) {
            suspend fun record(dao: UndoRedoDao, bookId: Long, authorId: Long): Long {
                return dao.record(this, bookId, authorId)
            }
        },
        DELETE_BOOK_AUTHOR_LINK(DeleteLinkDescriptor(BOOK_AUTHORS_TABLE, BOOK_AUTHORS_ID_COLUMN, BOOK_AUTHORS_BOOK_ID_COLUMN, BOOK_AUTHORS_AUTHOR_ID_COLUMN)) {
            suspend fun record(dao: UndoRedoDao, bookId: Long, authorId: Long): Long {
                return dao.record(this, bookId, authorId)
            }
        },
        ADD_BOOK_CATEGORY_LINK(AddLinkDescriptor(BOOK_CATEGORIES_TABLE, BOOK_CATEGORIES_ID_COLUMN, BOOK_CATEGORIES_BOOK_ID_COLUMN, BOOK_CATEGORIES_CATEGORY_ID_COLUMN)) {
            suspend fun record(dao: UndoRedoDao, bookId: Long, categoryId: Long): Long {
                return dao.record(this, bookId, categoryId)
            }
        },
        DELETE_BOOK_CATEGORY_LINK(AddLinkDescriptor(BOOK_CATEGORIES_TABLE, BOOK_CATEGORIES_ID_COLUMN, BOOK_CATEGORIES_BOOK_ID_COLUMN, BOOK_CATEGORIES_CATEGORY_ID_COLUMN)) {
            suspend fun record(dao: UndoRedoDao, bookId: Long, categoryId: Long): Long {
                return dao.record(this, bookId, categoryId)
            }
        },
        ADD_BOOK_TAG_LINK(AddLinkDescriptor(BOOK_TAGS_TABLE, BOOK_TAGS_ID_COLUMN, BOOK_TAGS_BOOK_ID_COLUMN, BOOK_TAGS_TAG_ID_COLUMN)) {
            suspend fun record(dao: UndoRedoDao, bookId: Long, tagId: Long): Long {
                return dao.record(this, bookId, tagId)
            }
        },
        DELETE_BOOK_TAG_LINK(AddLinkDescriptor(BOOK_TAGS_TABLE, BOOK_TAGS_ID_COLUMN, BOOK_TAGS_BOOK_ID_COLUMN, BOOK_TAGS_TAG_ID_COLUMN)) {
            suspend fun record(dao: UndoRedoDao, bookId: Long, tagId: Long): Long {
                return dao.record(this, bookId, tagId)
            }
        };
    }

    class IllegalRecordingException(message: String): IllegalStateException(message)
    class IllegalUndoException(message: String): IllegalStateException(message)
    class IllegalRedoException(message: String): IllegalStateException(message)
    class UndoStartException(message: String): Exception(message)
    class UndoEndException(message: String): Exception(message)

    private var started = false
    private var minUndoId = 1
    private var undoId = 0
    private var maxUndoId = 0
    private var maxUndoLevels = 20
    private var resetUndosAt = 30
    private var operationCount = 0

    private suspend fun record(type: OperationType, curId: Long, oldId: Long = 0L): Long {
        if (undoId == 0)
            throw IllegalRecordingException("Recording when undo is not started")
        if (!db.inTransaction())
            throw IllegalRecordingException("Recording outside of a transaction")
        val op = UndoRedoOperationEntity(
            id = 0L,
            undoId = undoId,
            operationId = operationCount,
            type = type,
            curId = curId,
            oldId = oldId
        )
        return addOperation(op).also {
            if (it == 0L)
                throw IllegalRecordingException("Unexpected error recording an operation")
            ++operationCount
        }
    }

    suspend fun <T> withUndo(desc: String, operation: suspend () -> T): T {
        if (db.inTransaction()) {
            if (started)
                return operation()
            throw UndoStartException("Starting undo when database transaction is already started")
        }

        return db.withTransaction {
            var transaction: UndoTransactionEntity? = null
            if (started)
                throw UndoStartException("Asynchronous database access?")
            var finished = false
            try {
                started = true
                operationCount = 0
                if (undoId < maxUndoId)
                    discardRedo(undoId + 1, maxUndoId)
                transaction = UndoTransactionEntity(0L, ++undoId, desc)
                transaction.id = addUndoTransaction(transaction)
                if (transaction.id != 0L)
                    throw UndoStartException("Unexpected error starting undo")

                operation().also {
                    maxUndoId = undoId
                    if (maxUndoId - minUndoId >= maxUndoLevels) {
                        discardUndo(minUndoId, maxUndoId - maxUndoLevels)
                    }
                    if (maxUndoId >= resetUndosAt)
                        resetUndo(minUndoId - 1)
                    finished = true
                }
            } finally {
                started = false
                if (!finished || operationCount == 0) {
                    if (transaction != null && finished)
                        delete(transaction)
                    --undoId
                }
            }
        }
    }

    suspend fun undo() {
        if (db.inTransaction())
            throw IllegalUndoException("Undoing during transaction is not allowed")
        if (undoId > minUndoId) {
            db.withTransaction {
                if (started)
                    throw IllegalUndoException("Undoing during started transaction is not allowed")
                getNextUndo()?.let {undo ->
                    getOperationsDesc(undo.undoId, undo.undoId)?.let {
                        for (op in it) {
                            op.type.desc.undo(this, op)
                        }
                    }
                    delete(undo)
                    addRedoTransaction(RedoTransactionEntity(undo))
                    undoId = undo.undoId - 1
                }
            }
        }
    }

    suspend fun redo() {
        if (db.inTransaction())
            throw IllegalRedoException("Redoing during transaction is not allowed")
        if (undoId < maxUndoId) {
            db.withTransaction {
                if (started)
                    throw IllegalUndoException("Redoing during started transaction is not allowed")
                getNextRedo()?.let {redo ->
                    getOperationsAsc(redo.undoId, redo.undoId)?.let {
                        for (op in it) {
                            op.type.desc.redo(this, op)
                        }
                    }
                    delete(redo)
                    addUndoTransaction(UndoTransactionEntity(redo))
                    undoId = redo.undoId
                }
            }
        }
    }

    private suspend fun discardUndo(min: Int, max: Int) {
        if (min <= max) {
            getOperationsDesc(min, max)?.let {
                for (op in it) {
                    op.type.desc.discardUndo(this, op)
                }
            }
            deleteOperations(min, max)
            transactionQuery(SimpleSQLiteQuery(
                "DELETE FROM $UNDO_TABLE WHERE $TRANSACTION_UNDO_ID_COLUMN BETWEEN ? AND ?",
                arrayOf(min, max)
            ))
        }
    }

    private suspend fun discardRedo(min: Int, max: Int) {
        if (min <= max) {
            getOperationsAsc(min, max)?.let {
                for (op in it) {
                    op.type.desc.discardRedo(this, op)
                }
            }
            deleteOperations(min, max)
            transactionQuery(SimpleSQLiteQuery(
                "DELETE FROM $REDO_TABLE WHERE $TRANSACTION_UNDO_ID_COLUMN BETWEEN ? AND ?",
                arrayOf(min, max)
            ))
        }
    }

    private suspend fun resetUndo(offset: Int) {
        if (offset != 0) {
            transactionQuery(SimpleSQLiteQuery(
                "UPDATE $OPERATION_TABLE SET $OPERATION_UNDO_ID_COLUMN = $OPERATION_UNDO_ID_COLUMN - ?",
                arrayOf(offset)
            ))
            transactionQuery(SimpleSQLiteQuery(
                "UPDATE $UNDO_TABLE SET $TRANSACTION_UNDO_ID_COLUMN = $TRANSACTION_UNDO_ID_COLUMN - ?",
                arrayOf(offset)
            ))
            transactionQuery(SimpleSQLiteQuery(
                "UPDATE $REDO_TABLE SET $TRANSACTION_UNDO_ID_COLUMN = $TRANSACTION_UNDO_ID_COLUMN - ?",
                arrayOf(offset)
            ))
            minUndoId -= offset
            undoId -= offset
            maxUndoId -= offset
        }
    }

    private suspend fun deleteOperations(min: Int, max: Int): Int {
        return transactionQuery(SimpleSQLiteQuery(
            "DELETE FROM $OPERATION_TABLE WHERE $OPERATION_UNDO_ID_COLUMN BETWEEN ? and ?",
            arrayOf(min, max)
        ))
    }

    @Insert
    protected abstract suspend fun addOperation(op: UndoRedoOperationEntity): Long

    @Insert
    protected abstract suspend fun addUndoTransaction(transaction: UndoTransactionEntity): Long

    @Insert
    protected abstract suspend fun addRedoTransaction(transaction: RedoTransactionEntity): Long
    
    @Query("SELECT * FROM $UNDO_TABLE WHERE $TRANSACTION_UNDO_ID_COLUMN BETWEEN :min AND :max ORDER BY $TRANSACTION_UNDO_ID_COLUMN ASC")
    protected abstract suspend fun getLastUndo(min: Int, max: Int): List<UndoTransactionEntity>?

    @Query("SELECT * FROM $UNDO_TABLE ORDER BY $TRANSACTION_UNDO_ID_COLUMN DESC LIMIT 1")
    protected abstract suspend fun getNextUndo(): UndoTransactionEntity?

    @Query("SELECT * FROM $REDO_TABLE WHERE $TRANSACTION_UNDO_ID_COLUMN BETWEEN :min AND :max ORDER BY $TRANSACTION_UNDO_ID_COLUMN DESC")
    protected abstract suspend fun getLastRedo(min: Int, max: Int): List<RedoTransactionEntity>?

    @Query("SELECT * FROM $REDO_TABLE ORDER BY $TRANSACTION_UNDO_ID_COLUMN ASC LIMIT 1")
    protected abstract suspend fun getNextRedo(): RedoTransactionEntity?

    @Query("SELECT * FROM $OPERATION_TABLE WHERE $OPERATION_UNDO_ID_COLUMN BETWEEN :min and :max ORDER BY $OPERATION_UNDO_ID_COLUMN ASC, $OPERATION_OPERATION_ID_COLUMN ASC")
    protected abstract suspend fun getOperationsAsc(min: Int, max: Int): List<UndoRedoOperationEntity>?

    @Query("SELECT * FROM $OPERATION_TABLE WHERE $OPERATION_UNDO_ID_COLUMN BETWEEN :min and :max ORDER BY $OPERATION_UNDO_ID_COLUMN DESC, $OPERATION_OPERATION_ID_COLUMN DESC")
    protected abstract suspend fun getOperationsDesc(min: Int, max: Int): List<UndoRedoOperationEntity>?

    @Delete
    protected abstract fun delete(transaction: UndoTransactionEntity): Int

    @Delete
    protected abstract fun delete(transaction: RedoTransactionEntity): Int

    private suspend fun transactionQuery(query: String, vararg args: Any): Int {
        return transactionQuery(SimpleSQLiteQuery(query, args))
    }

    @Transaction
    protected open suspend fun transactionQuery(query: SupportSQLiteQuery): Int {
        return db.execUpdateDelete(query)
    }

    interface OperationDescriptor {
        suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity)
        suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity)
        suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity)
        suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity)
    }

    private class AddDataDescriptor(val table: String, val idColumn: String, val flagColumn: String, val flagValue: Int): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "UPDATE $table SET $flagColumn = $flagColumn | $flagValue WHERE $idColumn = ?",
                arrayOf(op.curId)
            )
        }

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "UPDATE $table SET $flagColumn = $flagColumn & ~$flagValue WHERE $idColumn = ?",
                arrayOf(op.curId)
            )
        }

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "DELETE FROM $table WHERE $idColumn = ?",
                arrayOf(op.curId)
            )
        }
    }

    private class DeleteDataDescriptor(val table: String, val idColumn: String, val flagColumn: String, val flagValue: Int): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "UPDATE $table SET $flagColumn = $flagColumn & ~$flagValue WHERE $idColumn = ?",
                arrayOf(op.curId)
            )
        }

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "DELETE FROM $table WHERE $idColumn = ?",
                arrayOf(op.curId)
            )
        }

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "UPDATE $table SET $flagColumn = $flagColumn | $flagValue WHERE $idColumn = ?",
                arrayOf(op.curId)
            )
        }

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }
    }

    private class ChangeDataDescriptor(val table: String, val idColumn: String, val flagColumn: String, val flagValue: Int): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = swap(dao, op)

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = discard(dao, op)

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = swap(dao, op)

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = discard(dao, op)

        private suspend fun swap(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                """UPDATE $table
                    SET $idColumn = -$idColumn,
                    SET $flagColumn = (CASE $idColumn WHEN ? THEN $flagColumn & ~$flagValue ELSE $flagColumn | $flagValue
                    WHERE $idColumn IN ( ?, ? )""".trimMargin(),
                arrayOf(op.oldId, op.curId, op.oldId)
            )
            dao.transactionQuery(
                """UPDATE $table
                    SET $idColumn = -$idColumn,
                    WHERE $idColumn IN ( ?, ? )""".trimMargin(),
                arrayOf(op.curId, op.oldId)
            )
        }

        private suspend fun discard(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "DELETE FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = ?",
                arrayOf(op.oldId)
            )
        }
    }

    private class AddLinkDescriptor(val table: String, val idColumn: String, val bookIdColumn: String, val linkToIdColumn: String): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "DELETE FROM $table WHERE $bookIdColumn = ? AND $linkToIdColumn = ?",
                arrayOf(op.curId, op.oldId)
            )
        }

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "INSERT INTO $table ( $idColumn, $bookIdColumn, $linkToIdColumn ) VALUES ( 0, ?, ? )",
                arrayOf(op.curId, op.oldId)
            )
        }

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }
    }

    private class DeleteLinkDescriptor(val table: String, val idColumn: String, val bookIdColumn: String, val linkToIdColumn: String): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "INSERT INTO $table ( $idColumn, $bookIdColumn, $linkToIdColumn ) VALUES ( 0, ?, ? )",
                arrayOf(op.curId, op.oldId)
            )
        }

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "DELETE FROM $table WHERE $bookIdColumn = ? AND $linkToIdColumn = ?",
                arrayOf(op.curId, op.oldId)
            )
        }

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }
    }
}
