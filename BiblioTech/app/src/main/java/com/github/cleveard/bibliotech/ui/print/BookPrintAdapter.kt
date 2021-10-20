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

class BookPrintAdapter(private val pdfPrinter: PDFPrinter, private val context: Context, private val scope: CoroutineScope): PrintDocumentAdapter() {
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
        val job = scope.launch {
            try {
                val changed = pdfPrinter.changeLayout(newAttributes)
                val pages = pdfPrinter.computePageCount()

                PrintDocumentInfo.Builder("print_books.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pages)
                    .build()
                    .also {
                        callback.onLayoutFinished(it, changed)
                    }
            } catch (e: Exception) {
                callback.onLayoutFailed(e.toString())
            } finally {
                cancellationSignal?.setOnCancelListener(null)
            }
        }
        cancellationSignal?.setOnCancelListener {
            job.cancel()
            callback.onLayoutCancelled()
        }
    }

    override fun onWrite(pages: Array<PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
        val job = scope.launch {
            val writtenRanges = ArrayList<PageRange>()
            try {
                withContext(Dispatchers.IO) {
                    val pdf = pdfPrinter.writePDF(context, pages ?: arrayOf(PageRange(1, pdfPrinter.pageCount!!)), writtenRanges)

                    @Suppress("BlockingMethodInNonBlockingContext")
                    pdf.writeTo(FileOutputStream(destination.fileDescriptor))
                }
            } catch (e: Exception) {
                callback.onWriteFailed(e.toString())
            } finally {
                pdfPrinter.close()
                cancellationSignal?.setOnCancelListener(null)
            }
            callback.onWriteFinished(writtenRanges.toTypedArray())
        }
        cancellationSignal?.setOnCancelListener {
            job.cancel()
            callback.onWriteCancelled()
        }
    }
}
