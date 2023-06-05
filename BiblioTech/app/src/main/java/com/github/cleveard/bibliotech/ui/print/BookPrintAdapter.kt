package com.github.cleveard.bibliotech.ui.print

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import com.github.cleveard.bibliotech.print.PDFPrinter
import kotlinx.coroutines.*
import java.io.FileOutputStream
import java.lang.Exception

/**
 * Adapter for the print manager to print a book list
 * @param pdfPrinter The printer used for printing
 * @param context The context used to create the pdf document
 * @param scope The coroutine scope used by the adapter
 */
open class BookPrintAdapter(
    /** The printer used for printing */
    private val pdfPrinter: PDFPrinter,
    /** The context used to create the pdf document */
    private val context: Context,
    /** The coroutine scope used by the adapter */
    private val scope: CoroutineScope
): PrintDocumentAdapter() {
    /**
     * @inheritDoc
     * The layout is done in a coroutine
     */
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
        val job = scope.launch {
            try {
                // Setup the new attributes
                val changed = pdfPrinter.changeLayout(context, newAttributes)
                // Layout the pages based on the current attributes
                val pages = pdfPrinter.computePageCount()

                // Return the result
                PrintDocumentInfo.Builder("print_books.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pages)
                    .build()
                    .also {
                        // Let the print manager know whether something changed
                        callback.onLayoutFinished(it, changed)
                    }
            } catch (e: Exception) {
                // Got an exception display a message
                if (e is CancellationException)
                    callback.onLayoutCancelled()
                else
                    callback.onLayoutFailed(e.toString())
            } finally {
                // Remove the cancellation listener
                cancellationSignal?.setOnCancelListener(null)
            }
        }

        // Set the cancellation listener
        cancellationSignal?.setOnCancelListener {
            // Cancel the layout job and return status to print manager
            job.cancel()
        }
    }

    /**
     * @inheritDoc
     * Writing to the pdf is done in a coroutine
     */
    override fun onWrite(pages: Array<PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
        val job = scope.launch {
            // This is where the written page ranges are collected
            val writtenRanges = ArrayList<PageRange>()
            try {
                // Work on a background thread
                withContext(Dispatchers.IO) {
                    // Write the pages
                    val pdf = pdfPrinter.writePDF(context, pages, writtenRanges)

                    // Save the written pdf
                    pdf.writeTo(FileOutputStream(destination.fileDescriptor))
                }
            } catch (e: Exception) {
                // Exception - output a message
                if (e is CancellationException)
                    callback.onWriteCancelled()
                else
                    callback.onWriteFailed(e.toString())
            } finally {
                // Done close the printer
                pdfPrinter.close()
                // And remove the cancellation listener
                cancellationSignal?.setOnCancelListener(null)
            }
            // Return the written ranges to the print manager
            callback.onWriteFinished(writtenRanges.toTypedArray())
        }

        // Set the cancellation listener
        cancellationSignal?.setOnCancelListener {
            // Cancel the job
            job.cancel()
        }
    }
}
