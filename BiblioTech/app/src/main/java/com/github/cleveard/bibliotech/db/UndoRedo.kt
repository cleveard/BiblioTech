package com.github.cleveard.bibliotech.db

import android.util.Log
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import java.lang.IllegalStateException

const val UNDO_TABLE = "undo_table"
const val TRANSACTION_ID_COLUMN = "transaction_id"
const val TRANSACTION_UNDO_ID_COLUMN = "transaction_undo_id"
const val TRANSACTION_DESC_COLUMN = "transaction_desc"
const val TRANSACTION_FLAGS_COLUMN = "transaction_flags"

const val OPERATION_TABLE = "operation_table"
const val OPERATION_ID_COLUMN = "operation_id"
const val OPERATION_UNDO_ID_COLUMN = "operation_undo_id"
const val OPERATION_OPERATION_ID_COLUMN = "operation_operation_id"
const val OPERATION_TYPE_COLUMN = "operation_type"
const val OPERATION_CUR_ID_COLUMN = "operation_cur_id"
const val OPERATION_OLD_ID_COLUMN = "operation_old_id"

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

// Type convert to convert between filters and strings
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
    enum class OperationType(val desc: OperationDescriptor) {
        ADD_BOOK(AddDataDescriptor(BookDatabase.bookTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        DELETE_BOOK(DeleteDataDescriptor(BookDatabase.bookTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: (WhereExpression) -> Int): Int {
                return dao.recordDelete(this, expression, delete)
            }
        },
        CHANGE_BOOK(ChangeDataDescriptor(BookDatabase.bookTable) {
            swapBook(it.curId, it.oldId)
        }) {
            override suspend fun recordUpdate(dao: UndoRedoDao, id: Long, update: suspend () -> Boolean): Boolean {
                if (dao.started > 0)
                    return dao.recordUpdate(this, id, dao.copyForBookUndo(id), update)
                return update()
            }
        },
        ADD_AUTHOR(AddDataDescriptor(BookDatabase.authorsTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        DELETE_AUTHOR(DeleteDataDescriptor(BookDatabase.authorsTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, curId: Long, delete: suspend (Long) -> Int): Int {
                return dao.recordDelete(this, curId, delete)
            }
        },
        ADD_CATEGORY(AddDataDescriptor(BookDatabase.categoriesTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        DELETE_CATEGORY(DeleteDataDescriptor(BookDatabase.categoriesTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, curId: Long, delete: suspend suspend (Long) -> Int): Int {
                return dao.recordDelete(this, curId, delete)
            }
        },
        ADD_TAG(AddDataDescriptor(BookDatabase.tagsTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        DELETE_TAG(DeleteDataDescriptor(BookDatabase.tagsTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, curId: Long, delete: suspend (Long) -> Int): Int {
                return dao.recordDelete(this, curId, delete)
            }

            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: (WhereExpression) -> Int): Int {
                return dao.recordDelete(this, expression, delete)
            }
        },
        CHANGE_TAG(ChangeDataDescriptor(BookDatabase.tagsTable) {
            swapTag(it.curId, it.oldId)
        }) {
            override suspend fun recordUpdate(dao: UndoRedoDao, id: Long, update: suspend () -> Boolean): Boolean {
                if (dao.started > 0)
                    return dao.recordUpdate(this, id, dao.copyForTagUndo(id), update)
                return update()
            }
        },
        ADD_VIEW(AddDataDescriptor(BookDatabase.viewsTable)) {
            override suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
                dao.record(this, id)
            }
        },
        DELETE_VIEW(DeleteDataDescriptor(BookDatabase.viewsTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: (WhereExpression) -> Int): Int {
                return dao.recordDelete(this, expression, delete)
            }
        },
        CHANGE_VIEW(ChangeDataDescriptor(BookDatabase.viewsTable) {
            swapView(it.curId, it.oldId)
        }) {
            override suspend fun recordUpdate(dao: UndoRedoDao, id: Long, update: suspend () -> Boolean): Boolean {
                if (dao.started > 0)
                    return dao.recordUpdate(this, id, dao.copyForViewUndo(id), update)
                return update()
            }
        },
        ADD_BOOK_AUTHOR_LINK(AddLinkDescriptor(BookDatabase.bookAuthorsTable)) {
            override suspend fun recordLink(dao: UndoRedoDao, bookId: Long, linkId: Long) {
                dao.record(this, bookId, linkId)
            }
        },
        DELETE_BOOK_AUTHOR_LINK(DeleteLinkDescriptor(BookDatabase.bookAuthorsTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: (WhereExpression) -> Int): Int {
                return dao.recordDeleteLinks(this, expression, delete)
            }
        },
        ADD_BOOK_CATEGORY_LINK(AddLinkDescriptor(BookDatabase.bookCategoriesTable)) {
            override suspend fun recordLink(dao: UndoRedoDao, bookId: Long, linkId: Long) {
                dao.record(this, bookId, linkId)
            }
        },
        DELETE_BOOK_CATEGORY_LINK(DeleteLinkDescriptor(BookDatabase.bookCategoriesTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: (WhereExpression) -> Int): Int {
                return dao.recordDeleteLinks(this, expression, delete)
            }
        },
        ADD_BOOK_TAG_LINK(AddLinkDescriptor(BookDatabase.bookTagsTable)) {
            override suspend fun recordLink(dao: UndoRedoDao, bookId: Long, linkId: Long) {
                dao.record(this, bookId, linkId)
            }
        },
        DELETE_BOOK_TAG_LINK(DeleteLinkDescriptor(BookDatabase.bookTagsTable)) {
            override suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: (WhereExpression) -> Int): Int {
                return dao.recordDeleteLinks(this, expression, delete)
            }
        };

        open suspend fun recordAdd(dao: UndoRedoDao, id: Long) {
            throw IllegalRecordingException("RecordAdd not implemented for ${desc.table.name}")
        }
        open suspend fun recordUpdate(dao: UndoRedoDao, id: Long, update: suspend () -> Boolean): Boolean {
            throw IllegalRecordingException("RecordUpdate not implemented for ${desc.table.name}")
        }
        open suspend fun recordDelete(dao: UndoRedoDao, curId: Long, delete: suspend (Long) -> Int): Int {
            throw IllegalRecordingException("RecordDelete not implemented for ${desc.table.name}")
        }
        open suspend fun recordDelete(dao: UndoRedoDao, expression: WhereExpression, delete: (WhereExpression) -> Int): Int {
            throw IllegalRecordingException("RecordDelete not implemented for ${desc.table.name}")
        }
        open suspend fun recordLink(dao: UndoRedoDao, bookId: Long, linkId: Long) {
            throw IllegalRecordingException("RecordUpdate not implemented for ${desc.table.name}")
        }
    }

    class IllegalRecordingException(message: String): IllegalStateException(message)
    class IllegalUndoException(message: String): IllegalStateException(message)
    class IllegalRedoException(message: String): IllegalStateException(message)
    class UndoStartException(message: String): Exception(message)

    private var started = 0
    private var errorInUndo = false
    val isRecording: Boolean
        get() = started > 0
    var minUndoId: Int = -1
        private set
    var undoId: Int = -1
        private set
    var maxUndoId: Int = -1
        private set
    var maxUndoLevels = 20
        private set
    var resetUndoAt = 30
        private set
    private var operationCount = 0

    /** Initialize the undo from the database */
    private suspend fun initUndo() {
        if (undoId >= 0)
            return

        // Get the next undo and last redo transaction
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
                var first = transactions.first()
                var last = first
                // Discard the undoes and redoes
                for (t in transactions.drop(1)) {
                    if (t.isRedo != first.isRedo) {
                        discard(first.isRedo, first.undoId, last.undoId)
                        first = t
                    }
                    last = t
                }
                // Get the last one
                discard(first.isRedo, first.undoId, last.undoId)
                undoId = 0
                maxUndoId = 0
                minUndoId = 1
            }
        }
    }

    private suspend fun record(type: OperationType, curId: Long, oldId: Long = 0L) {
        if (started > 0) {
            val op = UndoRedoOperationEntity(
                id = 0L,
                undoId = undoId,
                operationId = operationCount,
                type = type,
                curId = curId,
                oldId = oldId
            )
            addOperation(op).also {
                if (it == 0L)
                    throw IllegalRecordingException("Unexpected error recording an operation")
                ++operationCount
            }
        }
    }

    private suspend fun recordDelete(type: OperationType, id: Long, delete: suspend (Long) -> Int): Int {
        return if (started > 0) {
            delete(id).also {
                if (it != 0)
                    record(type, id)
            }
        } else {
            db.execUpdateDelete(SimpleSQLiteQuery(
                "DELETE FROM ${type.desc.table.name} WHERE ${type.desc.table.idColumn} = ? AND ( ( ${type.desc.table.flagColumn} & ${type.desc.table.flagValue} ) = 0 )", arrayOf(id)
            ))
        }
    }

    private fun recordDelete(type: OperationType, expression: WhereExpression, delete: (WhereExpression) -> Int): Int {
        if (started <= 0) {
            return db.execUpdateDelete(SimpleSQLiteQuery(
                "DELETE FROM ${type.desc.table.name}${expression.expression}", expression.args
            ))
        }

        db.execUpdateDelete(SimpleSQLiteQuery(
            """INSERT OR ABORT INTO $OPERATION_TABLE ( $OPERATION_ID_COLUMN, $OPERATION_UNDO_ID_COLUMN, $OPERATION_OPERATION_ID_COLUMN, $OPERATION_TYPE_COLUMN, $OPERATION_CUR_ID_COLUMN )
            | SELECT null, $undoId, $operationCount, ${type.ordinal}, ${type.desc.table.idColumn} FROM ${type.desc.table.name}${expression.expression}
        """.trimMargin(),
            expression.args
        )).also {
            if (it != 0)
                ++operationCount
        }
        return delete(expression)
    }

    private suspend fun recordUpdate(type: OperationType, id: Long, copyId: Long, update: suspend () -> Boolean): Boolean {
        val updated = update()
        if (updated)
            record(type, id,  copyId)
         else {
             db.execUpdateDelete(SimpleSQLiteQuery(
                 "DELETE FROM ${type.desc.table.name} WHERE ${type.desc.table.idColumn} = ?"
                 , arrayOf(copyId)
             ))
        }
        return updated

    }

    private fun recordDeleteLinks(type: OperationType, expression: WhereExpression, delete: (WhereExpression) -> Int): Int {
        if (started > 0) {
            db.execUpdateDelete(SimpleSQLiteQuery(
                """INSERT OR ABORT INTO $OPERATION_TABLE ( $OPERATION_ID_COLUMN, $OPERATION_UNDO_ID_COLUMN, $OPERATION_OPERATION_ID_COLUMN, $OPERATION_TYPE_COLUMN, $OPERATION_CUR_ID_COLUMN, $OPERATION_OLD_ID_COLUMN )
                    | SELECT null, $undoId, $operationCount, ${type.ordinal}, ${type.desc.table.bookIdColumn}, ${type.desc.table.linkIdColumn} FROM ${type.desc.table.name}${expression.expression}
                """.trimMargin(),
                expression.args
            )).also {
                if (it != 0)
                    ++operationCount
            }
        }
        return delete(expression)
    }

    /** Can we undo */
    fun canUndo(): Boolean {
        return undoId >= 0 && undoId >= minUndoId
    }

    /** Can we redo */
    fun canRedo(): Boolean {
        return undoId >= 0 && undoId < maxUndoId
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
            return operation()

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
            // Start a transaction to turn off undo. This is for thread safety
            db.withTransaction {
                initUndo()
                // If we didn't finish, then there was an error
                if (!finished)
                    errorInUndo = true
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

    suspend fun undo(): Boolean {
        if (db.inTransaction())
            throw IllegalUndoException("Undoing during transaction is not allowed")
        return db.withTransaction {
            initUndo()
            if (undoId >= minUndoId) {
                if (started > 0)
                    throw IllegalUndoException("Undoing during started transaction is not allowed")
                getNextUndo()?.let { undo ->
                    getOperationsDesc(undo.undoId, undo.undoId)?.let {
                        for (op in it) {
                            op.type.desc.undo(this, op)
                        }
                    }
                    undo.isRedo = true
                    update(undo)
                    undoId = undo.undoId - 1
                    true
                } == true
            } else
                false
        }
    }

    suspend fun redo(): Boolean {
        if (db.inTransaction())
            throw IllegalRedoException("Redoing during transaction is not allowed")
        return db.withTransaction {
            initUndo()
            if (undoId < maxUndoId) {
                if (started > 0)
                    throw IllegalUndoException("Redoing during started transaction is not allowed")
                getNextRedo()?.let { redo ->
                    getOperationsAsc(redo.undoId, redo.undoId)?.let {
                        for (op in it) {
                            op.type.desc.redo(this, op)
                        }
                    }
                    redo.isRedo = false
                    update(redo)
                    undoId = redo.undoId
                    true
                } == true
            } else
                false
        }
    }

    suspend fun setMaxUndoLevels(maxLevels: Int, resetThreshold: Int = 0) {
        db.withTransaction {
            if (started > 0)
                throw IllegalStateException("Cannot set undo levels while recording")
            initUndo()
            if (maxLevels != maxUndoLevels) {
                maxUndoLevels = maxLevels
                val deleteUndo = maxUndoId - maxUndoLevels
                if (deleteUndo >= minUndoId) {
                    val limit = deleteUndo.coerceAtMost(undoId)
                    if (limit >= minUndoId) {
                        discard(false, minUndoId, limit)
                        minUndoId = limit + 1
                    }
                }
                val deleteRedo = minUndoId + maxUndoLevels
                if (deleteRedo <= maxUndoId) {
                    discard(true, deleteRedo, maxUndoId)
                    maxUndoId = deleteRedo - 1
                }
                if (maxUndoId < minUndoId) {
                    minUndoId = 1
                    maxUndoId = 0
                    undoId = 0
                }
            }
            if (resetThreshold != 0)
                resetUndoAt = resetThreshold
            // Check for numeric overflow and set a big value
            if (resetUndoAt <= maxUndoLevels)
                resetUndoAt = Int.MAX_VALUE - 10
        }
    }

    private suspend fun discard(redo: Boolean, min: Int, max: Int) {
        if (min <= max) {
            val get = if (redo) this::getOperationsAsc else this::getOperationsDesc
            val dis = if (redo) OperationDescriptor::discardRedo else OperationDescriptor::discardUndo
            get(min, max)?.let {
                for (op in it) {
                    dis.invoke(op.type.desc, this, op)
                }
            }
            delete(min, max)
        }
    }

    @Transaction
    private suspend fun resetUndo(offset: Int) {
        Log.d("UNDO_REDO", "resetUndo: offset=$offset, minUndoId=$minUndoId, undoId=$undoId, maxUndoId=$maxUndoId")
        if (offset != 0) {
            db.withTransaction {
                transactionQuery(
                    "UPDATE $OPERATION_TABLE SET $OPERATION_UNDO_ID_COLUMN = $OPERATION_UNDO_ID_COLUMN - ?",
                    offset
                )
                transactionQuery(
                    "UPDATE $UNDO_TABLE SET $TRANSACTION_UNDO_ID_COLUMN = $TRANSACTION_UNDO_ID_COLUMN - ?",
                    offset
                )
                minUndoId -= offset
                undoId -= offset
                maxUndoId -= offset
            }
        }
    }

    @Insert
    protected abstract suspend fun addOperation(op: UndoRedoOperationEntity): Long

    @Insert
    protected abstract suspend fun addUndoTransaction(transaction: UndoTransactionEntity): Long

    @Update
    protected abstract suspend fun update(transaction: UndoTransactionEntity): Int

    @Query("SELECT * FROM $UNDO_TABLE ORDER BY $TRANSACTION_UNDO_ID_COLUMN ASC")
    abstract suspend fun getTransactions(): List<UndoTransactionEntity>?

    @Query("SELECT * FROM $UNDO_TABLE WHERE ( ( $TRANSACTION_FLAGS_COLUMN & ${UndoTransactionEntity.IS_REDO} ) = 0) ORDER BY $TRANSACTION_UNDO_ID_COLUMN DESC LIMIT 1")
    abstract suspend fun getNextUndo(): UndoTransactionEntity?

    @Query("SELECT * FROM $UNDO_TABLE WHERE ( ( $TRANSACTION_FLAGS_COLUMN & ${UndoTransactionEntity.IS_REDO} ) != 0) ORDER BY $TRANSACTION_UNDO_ID_COLUMN ASC LIMIT 1")
    abstract suspend fun getNextRedo(): UndoTransactionEntity?

    @Query("SELECT * FROM $OPERATION_TABLE WHERE $OPERATION_UNDO_ID_COLUMN BETWEEN :min and :max ORDER BY $OPERATION_UNDO_ID_COLUMN ASC, $OPERATION_OPERATION_ID_COLUMN ASC")
    abstract suspend fun getOperationsAsc(min: Int, max: Int): List<UndoRedoOperationEntity>?

    @Query("SELECT * FROM $OPERATION_TABLE WHERE $OPERATION_UNDO_ID_COLUMN BETWEEN :min and :max ORDER BY $OPERATION_UNDO_ID_COLUMN DESC, $OPERATION_OPERATION_ID_COLUMN DESC")
    abstract suspend fun getOperationsDesc(min: Int, max: Int): List<UndoRedoOperationEntity>?

    @Delete
    protected abstract fun delete(transaction: UndoTransactionEntity): Int

    @Transaction
    protected open fun transactionQuery(query: String, vararg args: Any): Int {
        return db.execUpdateDelete(SimpleSQLiteQuery(query, args))
    }

    @Query("SELECT * FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN IN ( :id1, :id2 )")
    protected abstract suspend fun queryBook(id1: Long, id2: Long): List<BookEntity>
    @Update
    protected abstract suspend fun updateBook(entity: BookEntity): Int
    @Transaction
    protected open suspend fun swapBook(curId: Long, copyId: Long): Int {
        fun swap(entity1: BookEntity, entity2: BookEntity): Boolean {
            if (entity2.id != copyId)
                return false
            entity2.flags = entity1.flags
            entity1.flags = BookEntity.HIDDEN
            entity1.id = entity2.id.also { entity2.id = entity1.id }
            return true
        }

        val list = queryBook(curId, copyId)
        if (list.size == 2) {
            if (list[0].id == curId) {
                if (!swap(list[0], list[1]))
                    return 0
            } else if (list[1].id == curId) {
                if (!swap(list[1], list[0]))
                    return 0
            } else
                return 0
            return updateBook(list[0]) + updateBook(list[1])
        }
        return 0
    }

    @Query("SELECT * FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN IN ( :id1, :id2 )")
    protected abstract suspend fun queryTag(id1: Long, id2: Long): List<TagEntity>
    @Update
    protected abstract suspend fun updateTag(entity: TagEntity): Int
    @Transaction
    protected open suspend fun swapTag(curId: Long, copyId: Long): Int {
        fun swap(entity1: TagEntity, entity2: TagEntity): Boolean {
            if (entity2.id != copyId)
                return false
            entity2.flags = entity1.flags
            entity1.flags = TagEntity.HIDDEN
            entity1.id = entity2.id.also { entity2.id = entity1.id }
            return true
        }

        val list = queryTag(curId, copyId)
        if (list.size == 2) {
            if (list[0].id == curId) {
                if (!swap(list[0], list[1]))
                    return 0
            } else if (list[1].id == curId) {
                if (!swap(list[1], list[0]))
                    return 0
            } else
                return 0
            return updateTag(list[0]) + updateTag(list[1])
        }
        return 0
    }

    @Query("SELECT * FROM $VIEWS_TABLE WHERE $VIEWS_ID_COLUMN IN ( :id1, :id2 )")
    protected abstract suspend fun queryView(id1: Long, id2: Long): List<ViewEntity>
    @Update
    protected abstract suspend fun updateView(entity: ViewEntity): Int
    @Transaction
    protected open suspend fun swapView(curId: Long, copyId: Long): Int {
        fun swap(entity1: ViewEntity, entity2: ViewEntity): Boolean {
            if (entity2.id != copyId)
                return false
            entity2.flags = entity1.flags
            entity1.flags = ViewEntity.HIDDEN
            entity1.id = entity2.id.also { entity2.id = entity1.id }
            return true
        }

        val list = queryView(curId, copyId)
        if (list.size == 2) {
            if (list[0].id == curId) {
                if (!swap(list[0], list[1]))
                    return 0
            } else if (list[1].id == curId) {
                if (!swap(list[1], list[0]))
                    return 0
            } else
                return 0
            return updateView(list[0]) + updateView(list[1])
        }
        return 0
    }

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

    @Transaction
    protected open suspend fun copyForBookUndo(bookId: Long): Long {
        return db.execInsert(SimpleSQLiteQuery(
            """INSERT INTO $BOOK_TABLE ( $BOOK_ID_COLUMN, $VOLUME_ID_COLUMN, $SOURCE_ID_COLUMN, $ISBN_COLUMN, $TITLE_COLUMN, $SUBTITLE_COLUMN, $DESCRIPTION_COLUMN, $PAGE_COUNT_COLUMN, $BOOK_COUNT_COLUMN, $VOLUME_LINK, $RATING_COLUMN, $DATE_ADDED_COLUMN, $DATE_MODIFIED_COLUMN, $SMALL_THUMB_COLUMN, $LARGE_THUMB_COLUMN, $BOOK_FLAGS )
                | SELECT NULL, $VOLUME_ID_COLUMN, $SOURCE_ID_COLUMN, $ISBN_COLUMN, $TITLE_COLUMN, $SUBTITLE_COLUMN, $DESCRIPTION_COLUMN, $PAGE_COUNT_COLUMN, $BOOK_COUNT_COLUMN, $VOLUME_LINK, $RATING_COLUMN, $DATE_ADDED_COLUMN, $DATE_MODIFIED_COLUMN, $SMALL_THUMB_COLUMN, $LARGE_THUMB_COLUMN, $BOOK_FLAGS | ${BookEntity.HIDDEN} FROM $BOOK_TABLE WHERE $BOOK_ID_COLUMN = ?
            """.trimMargin(),
            arrayOf(bookId)
        ))
    }

    @Transaction
    protected open suspend fun copyForTagUndo(tagId: Long): Long {
        return db.execInsert(SimpleSQLiteQuery(
            """INSERT INTO $TAGS_TABLE ( $TAGS_ID_COLUMN, $TAGS_NAME_COLUMN, $TAGS_DESC_COLUMN, $TAGS_FLAGS )
                | SELECT NULL, $TAGS_NAME_COLUMN, $TAGS_DESC_COLUMN, $TAGS_FLAGS | ${TagEntity.HIDDEN} FROM $TAGS_TABLE WHERE $TAGS_ID_COLUMN = ?
            """.trimMargin(),
            arrayOf(tagId)
        ))
    }

    @Transaction
    protected open suspend fun copyForViewUndo(bookId: Long): Long {
        return db.execInsert(SimpleSQLiteQuery(
            """INSERT INTO $VIEWS_TABLE ( $VIEWS_ID_COLUMN, $VIEWS_NAME_COLUMN, $VIEWS_DESC_COLUMN, $VIEWS_FLAGS )
                | SELECT NULL, $VIEWS_NAME_COLUMN, $VIEWS_DESC_COLUMN, $VIEWS_FLAGS | ${ViewEntity.HIDDEN} FROM $VIEWS_TABLE WHERE $VIEWS_ID_COLUMN = ?
            """.trimMargin(),
            arrayOf(bookId)
        ))
    }

    interface OperationDescriptor {
        val table: BookDatabase.TableDescription

        suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity)
        suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity)
        suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity)
        suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity)
    }

    private class AddDataDescriptor(override val table: BookDatabase.TableDescription): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "UPDATE ${table.name} SET ${table.flagColumn} = ${table.flagColumn} | ${table.flagValue} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "UPDATE ${table.name} SET ${table.flagColumn} = ${table.flagColumn} & ~${table.flagValue} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "DELETE FROM ${table.name} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }
    }

    private open class DeleteDataDescriptor(override val table: BookDatabase.TableDescription): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "UPDATE ${table.name} SET ${table.flagColumn} = ${table.flagColumn} & ~${table.flagValue} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "DELETE FROM ${table.name} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "UPDATE ${table.name} SET ${table.flagColumn} = ${table.flagColumn} | ${table.flagValue} WHERE ${table.idColumn} = ?",
                op.curId
            )
        }

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }
    }

    private class ChangeDataDescriptor(
        override val table: BookDatabase.TableDescription,
        val swap: suspend UndoRedoDao.(op: UndoRedoOperationEntity) -> Unit
    ): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = swap(dao, op)

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = discard(dao, op)

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = swap(dao, op)

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) = discard(dao, op)

        private fun discard(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "DELETE FROM ${table.name} WHERE ${table.idColumn} = ?",
                op.oldId
            )
        }
    }

    private class AddLinkDescriptor(override val table: BookDatabase.TableDescription): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "DELETE FROM ${table.name} WHERE ${table.bookIdColumn} = ? AND ${table.linkIdColumn} = ?",
                op.curId, op.oldId
            )
        }

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "INSERT OR ABORT INTO ${table.name} ( ${table.idColumn}, ${table.bookIdColumn}, ${table.linkIdColumn} ) VALUES ( null, ?, ? )",
                op.curId, op.oldId
            )
        }

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }
    }

    private class DeleteLinkDescriptor(override val table: BookDatabase.TableDescription): OperationDescriptor {
        override suspend fun undo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "INSERT OR ABORT INTO ${table.name} ( ${table.idColumn}, ${table.bookIdColumn}, ${table.linkIdColumn} ) VALUES ( null, ?, ? )",
                op.curId, op.oldId
            )
        }

        override suspend fun discardUndo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }

        override suspend fun redo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            dao.transactionQuery(
                "DELETE FROM ${table.name} WHERE ${table.bookIdColumn} = ? AND ${table.linkIdColumn} = ?",
                op.curId, op.oldId
            )
        }

        override suspend fun discardRedo(dao: UndoRedoDao, op: UndoRedoOperationEntity) {
            // Nothing required
        }
    }
}
