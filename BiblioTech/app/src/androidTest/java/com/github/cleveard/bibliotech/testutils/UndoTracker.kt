package com.github.cleveard.bibliotech.testutils

import com.github.cleveard.bibliotech.db.UndoRedoDao
import com.github.cleveard.bibliotech.db.UndoRedoOperationEntity
import com.github.cleveard.bibliotech.db.UndoTransactionEntity
import com.google.common.truth.Truth.assertWithMessage

/**
 * Class for tracking the contents of the undo transaction and operation tables
 * @param dao The UndoRedoDao
 * This class doesn't verify that the transaction contain any specific values, just
 * that they are changing in the expected way as transactions are recorded
 */
class UndoTracker(
    /** The UndoRedoDao */
    var dao: UndoRedoDao
) {
    /**
     * One transaction with its operations
     */
    private data class Transaction(
        /** The transaction entity */
        val trans: UndoTransactionEntity,
        /** The operation entities for this transaction */
        val ops: List<UndoRedoOperationEntity>
    )

    /** ArrayList with removeRange made public */
    class ArrayListRemoveRange<T>: ArrayList<T>() {
        public override fun removeRange(fromIndex: Int, toIndex: Int) {
            super.removeRange(fromIndex, toIndex)
        }
    }

    /** The current undo transactions */
    private var transactions = ArrayListRemoveRange<Transaction>()

    /**
     * Run an operation and record its undo transaction
     * @param desc Description of the undo transaction
     * @param expectUndo True, if the operation is expected to create an undo
     *                   transaction. False otherwise
     * @param op The operation to run
     */
    suspend fun <T> record(desc: String, expectUndo: Boolean = true, op: suspend () -> T): T {
        // This array is used to return whether this function was called
        // while undo recording was already started
        val isRecording = arrayOf(false)
        // Run the operation while recording undo
        return dao.withUndo(desc, isRecording, op).also {
            // If we were already recording, then we can't record the
            // undo transaction here, because it isn't finished yet.
            if (!isRecording[0]) {
                // Record the transaction
                record(desc, expectUndo, desc)
            }
        }
    }

    /**
     * Record the most recent undo transaction
     * @param message A messsage for assert failures
     * @param expectUndo True, if the operation is expected to create an undo
     *                   transaction. False otherwise
     * @param desc If not null, the undo description. If null then we don't check the descrption.
     */
    suspend fun record(message: String, expectUndo: Boolean = true, desc: String? = null) {
        // If we are recording, then we can't capture the transaction
        if (dao.isRecording)
            return

        // Get the next undo transaction
        val undo = dao.getNextUndo()
        // And the operations. Sort uniquely for later comparisons
        val ops = undo?.let { dao.getOperationsAsc(undo.undoId, undo.undoId) }?.sortedWith(compareBy({ it.operationId }, { it.id }))
        assertWithMessage("Undo: %s", message).apply {
            if (expectUndo && dao.maxUndoLevels > 0) {
                // We expect a transaction and we are recording, so undo and ops should not be null
                that(undo).isNotNull()
                that(ops).isNotNull()
                undo!!
                ops!!
                // Get the last undo we recorded
                val lastUndo = transactions.indexOfLast { !it.trans.isRedo }
                var nextId = if (transactions.isEmpty())
                    1                   // No transactions should be 1
                else if (lastUndo == -1)
                    transactions.first().trans.undoId   // No undo, should be the most first redo
                else
                    transactions[lastUndo].trans.undoId + 1 // Should be last undo plus 1
                // Remove any redo transactions
                if (lastUndo + 1 < transactions.size)
                    transactions.removeRange(lastUndo + 1, transactions.size)
                // If the number of transactions is more than that max, then remove some
                if (transactions.size >= dao.maxUndoLevels)
                    transactions.removeRange(0, transactions.size + 1 - dao.maxUndoLevels)
                // If we reset the undo ids, then do it for this copy
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
                // Make sure that the undo transaction has the next id.
                that(undo.undoId).isEqualTo(nextId)
                // Add the transaction to the tracked list
                transactions.add(Transaction(undo, ops))
                // Make sure the description is correct
                if (desc != null)
                    that(undo.desc).isEqualTo(desc)
            } else if (transactions.isEmpty()) {
                // We don't expect a transaction and none have been recorded
                // so make sure the transaction and ops are null
                that(undo).isNull()
                that(ops).isNull()
            } else {
                // We don't expect a transaction and some have been recorded
                // so make sure we got the last transaction again.
                that(undo).isEqualTo(transactions.last().trans)
                that(ops).isEqualTo(transactions.last().ops)
            }
        }
    }

    /**
     * Clear all of the undo transactions in the data base
     * @param message A message for assertion failures.
     */
    suspend fun clearUndo(message: String) {
        // Clear the transactions by setting the max levels to 0 and then back to what it was before
        val l = dao.maxUndoLevels
        dao.setMaxUndoLevels(0)
        dao.setMaxUndoLevels(l)
        // Clear the tracked list
        transactions.clear()
        assertWithMessage("Clear Undo %s", message).apply {
            // Make sure there are no transacton and the max, min and undo id are correct
            that(dao.getTransactions().isNullOrEmpty())
            that(dao.maxUndoId).isEqualTo(0)
            that(dao.undoId).isEqualTo(0)
            that(dao.minUndoId).isEqualTo(1)
        }
    }

    /**
     * Get all of the transactions currently in the database
     * @param message A message for assertion failures
     * @param args Arguments replaces in message using %s
     */
    private suspend fun getTransactions(message: String, vararg args: Any): MutableList<Transaction> {
        // Get all of the transactions
        val trans = dao.getTransactions()
        var opCount = 0
        return if (trans != null) {
            // We have some transactions, get all of the operations
            val ops = dao.getOperationsAsc(Int.MIN_VALUE, Int.MAX_VALUE)!!.sortedWith(compareBy({ it.undoId }, { it.operationId }, { it.id }))
            // map trans to a list of Transaction
            val list = trans.map { t ->
                // Create the ops list from the first and last position of the undo Id
                Transaction(t, ops.subList(
                    ops.indexOfFirst { it.undoId == t.undoId },
                    ops.indexOfLast { it.undoId == t.undoId } + 1
                ).toList()).also {
                    // Count the number of ops we collect in transactions
                    opCount += it.ops.size
                }
            }.toMutableList()

            assertWithMessage("Sync Undo $message", *args).apply {
                // Make sure we collected all of the ops
                that(opCount).isEqualTo(ops.size)
                if (list.isEmpty()) {
                    // No undo transaction make sure the min and max are valid
                    // The -1 check, handles the case were undo isn't initialized yet
                    that(dao.minUndoId).isIn(listOf(-1, dao.undoId + 1))
                    that(dao.maxUndoId).isEqualTo(dao.undoId)
                } else {
                    // Make sure the min max and undo ids are all correct
                    that(dao.minUndoId).isEqualTo(list.first().trans.undoId)
                    that(dao.maxUndoId).isEqualTo(list.last().trans.undoId)
                    that(dao.undoId).isEqualTo(list.indexOfLast { !it.trans.isRedo } + dao.minUndoId)
                }
            }
            list
        } else
            ArrayList() // No transaction return an empty list
    }

    /**
     * Set the tracked undo transactions to the current ones in the database
     * @param message A message for assertion failures
     * @param args Arguments replaces in message using %s
     * Used to initialize the tracker from an unknown state
     */
    suspend fun syncUndo(message: String, vararg args: Any) {
        transactions = ArrayListRemoveRange<Transaction>().apply {
            addAll(getTransactions(message, *args))
        }
    }

    /**
     * Change the tracked transactions in response to an undo()
     */
    fun undo(): Boolean {
        val next = transactions.lastOrNull { !it.trans.isRedo }?: return false
        next.trans.isRedo = true
        return true
    }

    /**
     * Change the tracked transactions in response to a redo()
     */
    fun redo(): Boolean {
        val next = transactions.firstOrNull { it.trans.isRedo }?: return false
        next.trans.isRedo = false
        return true
    }

    /**
     * Change the tracked transactions in response to changing the max undo levels
     * @param count The new max undo level count
     */
    fun undoLevelsChanged(count: Int) {
        // Size is smaller than count, so don't do anything
        if (transactions.size <= count)
            return

        // Find the first redo
        val redo = transactions.indexOfFirst { it.trans.isRedo }.let {
            // Convert -1 (no redoes) to the end of the list
            if (it < 0)
                transactions.size
            else
                it
        }
        // Remove undo transaction
        if (redo > 0 && transactions.size > count)
            transactions.removeRange(0, redo.coerceAtMost(transactions.size - count))
        // Remove redo transaction
        if (redo < transactions.size && transactions.size > count)
            transactions.removeRange(count, transactions.size)
    }

    /**
     * Check that the undo transactions are exactly what we are tracking
     * @param message A message for assertion failures
     * @param args Arguments replaces in message using %s
     */
    suspend fun checkUndo(message: String, vararg args: Any) {
        val trans = getTransactions(message, *args)
        assertWithMessage("Check Undo $message", *args).that(trans).isEqualTo(transactions)
    }
}
