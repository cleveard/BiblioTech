package com.github.cleveard.bibliotech.testutils

import com.github.cleveard.bibliotech.db.UndoRedoDao
import com.github.cleveard.bibliotech.db.UndoRedoOperationEntity
import com.github.cleveard.bibliotech.db.UndoTransactionEntity
import com.google.common.truth.Truth.assertWithMessage

class UndoTracker(var dao: UndoRedoDao) {
    private data class Transaction(
        val trans: UndoTransactionEntity,
        val ops: List<UndoRedoOperationEntity>
    )
    class ArrayListRemoveRange<T>: ArrayList<T>() {
        public override fun removeRange(fromIndex: Int, toIndex: Int) {
            super.removeRange(fromIndex, toIndex)
        }
    }
    private var transactions = ArrayListRemoveRange<Transaction>()

    suspend fun <T> record(desc: String, expectUndo: Boolean = true, op: suspend () -> T): T {
        val isRecording = arrayOf(false)
        return dao.withUndo(desc, isRecording, op).also {
            if (!isRecording[0]) {
                record(desc, expectUndo, desc)
            }
        }
    }

    suspend fun record(message: String, expectUndo: Boolean = true, desc: String? = null) {
        if (dao.isRecording)
            return

        val undo = dao.getNextUndo()
        val ops = undo?.let { dao.getOperationsAsc(undo.undoId, undo.undoId) }?.sortedWith(compareBy({ it.operationId }, { it.id }))
        assertWithMessage("Undo: %s", message).apply {
            if (expectUndo && dao.maxUndoLevels > 0) {
                that(undo).isNotNull()
                that(ops).isNotNull()
                undo!!
                ops!!
                var nextId = if (transactions.isEmpty()) 1 else transactions.last().trans.undoId + 1
                if (transactions.size >= dao.maxUndoLevels)
                    transactions.removeRange(0, transactions.size + 1 - dao.maxUndoLevels)
                if (nextId >= dao.resetUndoAt) {
                    val offset = transactions.first().trans.undoId - 1
                    nextId -= offset
                    for (t in transactions) {
                        t.trans.undoId -= offset
                        for (o in t.ops) {
                            o.undoId -= offset
                        }
                    }
                }
                that(undo.undoId).isEqualTo(nextId)
                transactions.add(Transaction(undo, ops))
                if (desc != null)
                    that(undo.desc).isEqualTo(desc)
            } else if (transactions.isEmpty()) {
                that(undo).isNull()
                that(ops).isNull()
            } else {
                that(undo).isEqualTo(transactions.last().trans)
                that(ops).isEqualTo(transactions.last().ops)
            }
        }
    }

    suspend fun clearUndo(message: String) {
        val l = dao.maxUndoLevels
        dao.setMaxUndoLevels(0)
        dao.setMaxUndoLevels(l)
        transactions.clear()
        assertWithMessage("Clear Undo %s", message).apply {
            that(dao.getTransactions().isNullOrEmpty())
            that(dao.maxUndoId).isEqualTo(0)
            that(dao.undoId).isEqualTo(0)
            that(dao.minUndoId).isEqualTo(1)
        }
    }

    private suspend fun getTransactions(message: String, vararg args: Any): MutableList<Transaction> {
        val trans = dao.getTransactions()
        var opCount = 0
        return if (trans != null) {
            val ops = dao.getOperationsAsc(Int.MIN_VALUE, Int.MAX_VALUE)!!
            val list = trans.map { t ->
                Transaction(t, ops.subList(
                    ops.indexOfFirst { it.undoId == t.undoId },
                    ops.indexOfLast { it.undoId == t.undoId } + 1
                ).toList()).also {
                    opCount += it.ops.size
                }
            }.toMutableList()

            assertWithMessage("Sync Undo $message", *args).apply {
                that(opCount).isEqualTo(ops.size)
                if (list.isEmpty()) {
                    that(dao.minUndoId).isIn(listOf(-1, dao.undoId + 1))
                    that(dao.maxUndoId).isEqualTo(dao.undoId)
                } else {
                    that(dao.minUndoId).isEqualTo(list.first().trans.undoId)
                    that(dao.maxUndoId).isEqualTo(list.last().trans.undoId)
                    that(dao.undoId).isEqualTo(list.indexOfLast { !it.trans.isRedo } + dao.minUndoId)
                }
            }
            list
        } else
            ArrayList()
    }

    suspend fun syncUndo(message: String) {
        transactions = ArrayListRemoveRange<Transaction>().apply {
            addAll(getTransactions(message))
        }
    }

    fun undo(): Boolean {
        val next = transactions.lastOrNull { !it.trans.isRedo }?: return false
        next.trans.isRedo = true
        return true
    }

    fun redo(): Boolean {
        val next = transactions.firstOrNull { it.trans.isRedo }?: return false
        next.trans.isRedo = false
        return true
    }

    fun undoLevelsChanged(count: Int) {
        if (transactions.size <= count)
            return

        val redo = transactions.indexOfFirst { it.trans.isRedo }.let {
            if (it < 0)
                transactions.size
            else
                it
        }
        if (redo > 0 && transactions.size > count)
            transactions.removeRange(0, redo.coerceAtMost(transactions.size - count))
        if (redo < transactions.size && transactions.size > count)
            transactions.removeRange(count, transactions.size)
    }

    suspend fun checkUndo(message: String, vararg args: Any) {
        val trans = getTransactions(message, *args)
        assertWithMessage("Check Undo $message", *args).that(trans).isEqualTo(transactions)
    }
}
