package com.github.cleveard.bibliotech.ui.print

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.github.cleveard.bibliotech.db.BookEntity
import com.github.cleveard.bibliotech.db.BookRepository
import com.github.cleveard.bibliotech.print.PDFPrinter

class PrintViewModel : ViewModel() {
    /** The available print layouts */
    private lateinit var printLayouts: PrintLayouts
    /** The pdf printer */
    lateinit var pdfPrinter: PDFPrinter
    /** Filters and counters for the selected filter */
    lateinit var printCount: BookRepository.FilteredBookCount

    /**
     * Initialize the view model
     * @param repo The book repository
     * @param context The fragment context
     * @param getThumbnailCallback Callback used to load thumbnails
     */
    fun initialize(repo: BookRepository, context: Context, getThumbnailCallback: suspend (bookId: Long, large: Boolean) -> Bitmap?) {
        printLayouts = PrintLayouts(context)
        pdfPrinter = PDFPrinter(printLayouts, getThumbnailCallback, PreferenceManager.getDefaultSharedPreferences(context))
        printCount = repo.FilteredBookCount(repo.bookFlags, BookEntity.SELECTED, viewModelScope)
    }
}
