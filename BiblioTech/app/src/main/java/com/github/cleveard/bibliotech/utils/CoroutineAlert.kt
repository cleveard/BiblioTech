package com.github.cleveard.bibliotech.utils

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import kotlinx.coroutines.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Type alias for the onClickCallbacks
 * @param alert The CoroutineAlert for the callback
 * @param dialog The dialog that got the click
 * @param which The control that got the click
 * @return True to dismiss the dialog. False to keep it open.
 * When the lambda is called this is set to the CoroutineAlert object
 */
typealias onClickCallback<T> = suspend CoroutineScope.(alert: CoroutineAlert<T>, dialog: DialogInterface, which: Int) -> Boolean

/**
 * AlertDialog builder for use in coroutines
 * @param <T> The value type returned by show()
 */
abstract class CoroutineAlert<T> {
    /**
     * The dialog builder
     */
    abstract val builder: AlertDialog.Builder

    /**
     * The result of the dialog
     */
    abstract var result: T

    /**
     * Set the listener for the positive button
     * @param listener The listener
     */
    abstract fun setPosListener(listener: onClickCallback<T>): CoroutineAlert<T>

    /**
     * Set the listener for the negative button
     * @param listener The listener
     */
    abstract fun setNegListener(listener: onClickCallback<T>): CoroutineAlert<T>

    /**
     * Show the dialog and return the result
     * @param onShow An optional lambda called when the dialog is shown
     * @return The result of the dialog
     */
    abstract suspend fun show(onShow: ((DialogInterface) -> Unit)? = null): T
}

private val mainContext = MainScope().coroutineContext

/**
 * AlertDialog builder for use in coroutines
 * @param context The context the AlertDialog.Builder uses
 * @param getCancelVal The value returned if the dialog is canceled,
 *               or if the result isn't set when handling a click to a button
 * @param setup A lambda the sets up the AlertDialog using the Builder. When the lambda is called
 *              this is set to the CoroutineAlert object
 * @param <T> The value type returned by show()
 * @return The CoroutineAlert for the dialog
 */
fun <T> CoroutineScope.coroutineAlert(
    context: Context,
    getCancelVal: () -> T,
    setup: CoroutineScope.(alert: CoroutineAlert<T>) -> Unit
): CoroutineAlert<T> {
    /**
     * AlertDialog builder for use in coroutines
     * @param context The context the AlertDialog.Builder uses
     * @param getCancelVal The value returned if the dialog is canceled,
     *               or if the result isn't set when handling a click to a button
     * @param <R> The value type returned by show()
     */
    class CoroutineAlertImpl<R>(
        context: Context,
        private val getCancelVal: () -> R,
        private val scope: CoroutineScope
    ) : CoroutineAlert<R>() {
        /**
         * The dialog builder
         */
        override val builder = AlertDialog.Builder(context)

        /**
         * The result of the dialog
         */
        override var result: R = this.getCancelVal()

        /**
         * The listener for the positive click
         */
        private var posListener: onClickCallback<R>? = null

        /**
         * The listener for the negative click
         */
        private var negListener: onClickCallback<R>? = null

        /**
         * Set the listener for the positive button
         * @param listener The listener
         */
        override fun setPosListener(listener: onClickCallback<R>): CoroutineAlert<R> {
            posListener = listener
            return this
        }

        /**
         * Set the listener for the negative button
         * @param listener The listener
         */
        override fun setNegListener(listener: onClickCallback<R>): CoroutineAlert<R> {
            negListener = listener
            return this
        }

        /**
         * Show the dialog and return the result
         * @param onShow An optional lambda called when the dialog is shown
         * @return The result of the dialog
         */
        override suspend fun show(onShow: ((DialogInterface) -> Unit)?): R {
            // Make sure we run the dialog in the scope context
            return withContext(mainContext) {
                // Suspend the thread until the dialog is finished
                suspendCoroutine { cont ->
                    var c: Continuation<R>? = cont
                    builder.setOnDismissListener {
                        // Finish with the last result we got
                        c?.resume(result)
                        c = null
                    }.setOnCancelListener {
                        // Finish with the cancel result
                        c?.resume(this@CoroutineAlertImpl.getCancelVal())
                        c = null
                    }

                    // Create the dialog and set the onShowListener
                    val dialog = builder.create()
                    dialog.setOnShowListener {
                        // If we have an onShow callback, call it
                        onShow?.let { it(dialog) }
                        // Setup out listeners
                        setupListeners(dialog)
                    }
                    dialog.show()
                }
            }
        }

        /**
         * Setup the click listeners
         * @param dialog The dialog we are showing
         */
        private fun setupListeners(dialog: AlertDialog) {
            val pos = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val neg = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)

            pos.setOnClickListener {
                handleClick(posListener, dialog, DialogInterface.BUTTON_POSITIVE)
            }

            neg.setOnClickListener {
                result = this.getCancelVal()
                handleClick(negListener, dialog, DialogInterface.BUTTON_NEGATIVE)
            }
        }

        /**
         * Handle click on a button
         * @param listener The registered listener
         * @param dialog The dialog
         * @param which The button id clicked
         */
        private fun handleClick(
            listener: onClickCallback<R>?,
            dialog: AlertDialog,
            which: Int
        ) {
            scope.launch {
                // Set the result to the cancel result
                // Call the listener if there is one
                if (listener?.let { it(this@CoroutineAlertImpl, dialog, which) } != false) {
                    // If listener is null or returns true, dismiss the dialog
                    dialog.dismiss()
                }
            }
        }
    }

    return CoroutineAlertImpl(context, getCancelVal, this).also { setup(it) }
}