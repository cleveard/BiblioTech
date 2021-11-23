package com.github.cleveard.bibliotech.ui.print

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Shader
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.github.cleveard.bibliotech.print.PDFPrinter

class PrintViewModel : ViewModel() {
    /** The available print layouts */
    lateinit var printLayouts: PrintLayouts
    /** The pdf printer */
    lateinit var pdfPrinter: PDFPrinter

    /**
     * Initialize the view model
     * @param context The fragment context
     * @param getThumbnailCallback Callback used to load thumbnails
     */
    fun initialize(context: Context, getThumbnailCallback: suspend (bookId: Long, large: Boolean) -> Bitmap?) {
        printLayouts = PrintLayouts(context)
        pdfPrinter = PDFPrinter(printLayouts, getThumbnailCallback, PreferenceManager.getDefaultSharedPreferences(context))
    }
}
