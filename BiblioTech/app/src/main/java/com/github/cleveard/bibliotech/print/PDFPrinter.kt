package com.github.cleveard.bibliotech.print

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.print.PageRange
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import android.text.*
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.Column
import com.github.cleveard.bibliotech.ui.print.PrintLayouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.lang.IllegalStateException
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** Class for printing to a PDF */
class PDFPrinter(
    private val layouts: PrintLayouts,
    private val getThumbnailCallback: suspend (bookId: Long, large: Boolean) -> Bitmap?,
    private val preferences: SharedPreferences
): Closeable {
    val preferenceEditor: SharedPreferences.Editor = preferences.edit()
    class NoPagesException: IllegalStateException("No pages were found to print")
    class NoBooksException: IllegalStateException("A list of books must be selected to print")
    /** The pdf document we are printing to */
    private var pdf: PrintedPdfDocument? = null
        private set(v) {
            field.let {
                field = v
                if (it != v) {
                    // Field changed, close previous document
                    it?.close()
                    // If setting document to null, recalculate the pages
                    if (v == null) {
                        pages = null
                    }
                }
            }
        }

    /** The page margins */
    private val margins: RectF = RectF(
        preferences.getFloat(PREF_MARGINS_LEFT, 72.0f),
        preferences.getFloat(PREF_MARGINS_TOP, 72.0f),
        preferences.getFloat(PREF_MARGINS_RIGHT, 72.0f),
        preferences.getFloat(PREF_MARGINS_BOTTOM, 72.0f)
    )

    /** The print attributes */
    var attributes: PrintAttributes = defaultAttributes
        private set(v) {
            // If the print attributes change, reprint the document
            if (v != field) {
                invalidateLayout()
            }
            field = v
        }
    /** The list of books */
    var bookList: List<BookAndAuthors>? = null
        // If the book list changes, reprint the document
        set(v) { if (v != field) invalidateLayout(); field = v }
    /** List of laid out pages */
    private var pages: List<PageLayoutHandler.Page>? = null
    /** Number of pages */
    val pageCount: Int
        get() = pages?.size?: 0
    /** Distance in points to separate the books horizontally */
    var horizontalSeparation: Float = preferences.getFloat(PREF_HORIZONTAL_SEPERATION, 18.0f)
        // If the horizontal separation changes, reprint the document
        set(v) {
            if (v != field) {
                invalidateLayout()
                preferenceEditor.putFloat(PREF_HORIZONTAL_SEPERATION, v)
                preferenceEditor.commit()
            }
            field = v
        }
    /** Distance in points to separate print columns vertically */
    var verticalSeparation: Float = preferences.getFloat(PREF_VERTICAL_SEPARATION, 9.0f)
        // If the vertical separation changes, reprint the document
        set(v) {
            if (v != field) {
                invalidateLayout()
                preferenceEditor.putFloat(PREF_VERTICAL_SEPARATION, v)
                preferenceEditor.commit()
            }
            field = v
        }
    /** The bounds of the drawing area on the page */
    val pageDrawBounds = RectF()
    /**
     * The width of a separator line
     * Set to 0 to prevent the line from printing
     */
    var separatorLineWidth: Float = preferences.getFloat(PREF_SEP_LINE_WIDTH, 0.5f)
        // If the separator line changes, reprint the document
        set(v) {
            if (v != field) {
                invalidateLayout()
                preferenceEditor.putFloat(PREF_SEP_LINE_WIDTH, v)
                preferenceEditor.commit()
            }
            field = v
        }
    /** The number of print columns */
    var numberOfColumns: Int = preferences.getInt(PREF_NUMBER_OF_COLUMNS, 2)
        // If the number of columns changes, reprint the document
        set(v) {
            if (v != field) {
                invalidateLayout()
                preferenceEditor.putInt(PREF_NUMBER_OF_COLUMNS, v)
                preferenceEditor.commit()
            }
            field = v
        }
    /** The smallest number of lines that aren't orphans */
    var orphans: Int = preferences.getInt(PREF_ORPHANS, 1)
        // If the orphan count changes, reprint the document
        set(v) {
            if (v != field) {
                invalidateLayout()
                preferenceEditor.putInt(PREF_ORPHANS, v)
                preferenceEditor.commit()
            }
            field = v
        }
    /** Set of visible fields in layout */
    val visibleFields: MutableSet<String> = HashSet<String>().apply {
        addAll(preferences.getStringSet(PREF_INCLUDED_FIELDS,
            setOf(
                "SmallThumb",
                Column.TITLE.name,
                Column.SUBTITLE.name,
                Column.FIRST_NAME.name
            )
        )!!)
    }
    /** The base paint for printing the document */
    val basePaint = TextPaint().apply {
        textSize = preferences.getFloat(PREF_TEXT_SIZE, 10.0f)
    }

    /**
     * Convert points to horizontal pixels
     * @param length Length in points
     * @return Horizontal length in pixels
     */
    fun pointsToHorizontalPixels(length: Float): Int {
        return pointsToPixels(length)
    }

    /**
     * Convert points to vertical pixels
     * @param length Length in points
     * @return Vertical length in pixels
     */
    fun pointsToVerticalPixels(length: Float): Int {
        return pointsToPixels(length)
    }

    /**
     * Convert horizontal pixels to points
     * @param length Length in horizontal pixels
     * @return Length in points
     */
    fun horizontalPixelsToPoints(length: Int): Float {
        return pixelsToPoints(length)
    }

    /**
     * Convert vertical pixels to points
     * @param length Length in vertical pixels
     * @return Length in points
     */
    fun verticalPixelsToPoints(length: Int): Float {
        return pixelsToPoints(length)
    }

    /**
     * Close the printer
     */
    override fun close() {
        pdf = null
    }

    /**
     * Invalidate the current layout
     */
    fun invalidateLayout() {
        pdf = null
        pages = null
    }

    /**
     * Get a layout based on the column width
     * @param columnWidth The width of a column
     */
    fun getLayout(columnWidth: Float): LayoutDescription = layouts.getLayout(columnWidth)

    /**
     * Change the print attributes and margins
     * @param context A context to get the media size label
     * @param newAttributes The new print attributes
     * @param newMargins The new margins
     * @return True if the document needs to be reprinted
     */
    fun changeLayout(context: Context, newAttributes: PrintAttributes? = null, newMargins: RectF? = null): Boolean {
        attributes = newAttributes?: defaultAttributes
        preferenceEditor.putString(PREF_PAGE_SIZE, attributes.mediaSize?.id?: "")
        preferenceEditor.putBoolean(PREF_PORTRAIT, attributes.mediaSize?.isPortrait != false)
        preferenceEditor.putString(PREF_PRINT_PAGE_SIZE_LABEL, attributes.mediaSize?.getLabel(context.packageManager)?: "")
        if (newMargins != null && newMargins != margins) {
            margins.set(newMargins)
            invalidateLayout()
        }
        return pdf == null
    }

    /**
     * Layout the pages
     * @return The number of pages
     */
    suspend fun computePageCount(): Int {
        return withContext(Dispatchers.IO) {
            layoutPages().size
        }
    }

    /**
     * Write the PDF
     * @param context The context for creating the PDF document
     * @param pageRanges The ranges of pages that are to be written
     * @param writtenRanges A list where the ranges of the pages that
     *                      are actually written are returned
     * @return The PDF document
     */
    suspend fun writePDF(
        context: Context,
        pageRanges: Array<PageRange>,
        writtenRanges: MutableList<PageRange>
    ): PrintedPdfDocument {
        // If the pdf is still there, then it is valid, return it
        pdf?.let { return@writePDF it }
        // Create a new document
        val doc = PrintedPdfDocument(context, attributes)
        // Keep it here
        pdf = doc
        // Write the document, if the book list isn't null
        bookList?.let { _ ->
            withContext(Dispatchers.IO) {
                // Layout the pages
                pages = layoutPages().also {
                    // And write each one
                    processPages(it, pageRanges, writtenRanges)
                }
            }
        }
        return doc
    }

    /**
     * Convert the margins to the page draw bounds
     */
    private fun calculateDrawBounds(): RectF {
        // Get the media size and minimum margins
        val size = attributes.mediaSize?: defaultAttributes.mediaSize!!
        val minMargins = attributes.minMargins?: defaultAttributes.minMargins!!
        return RectF(
            // Left is the left margin, but at least the minimum
            margins.left.coerceAtLeast(minMargins.leftMils.toFloat() * 72.0f / 1000.0f),
            // Top is the top margins, but at least the minimum
            margins.top.coerceAtLeast(minMargins.topMils.toFloat() * 72.0f / 1000.0f),
            // Right is the page width minus the right margin, but at least the minimum
            size.widthMils.toFloat() * 72.0f / 1000.0f - margins.right.coerceAtLeast(minMargins.rightMils.toFloat() * 72.0f / 1000.0f),
            // Bottom is the page height minimum the bottom margin, but at least the minimum
            size.heightMils.toFloat() * 72.0f / 1000.0f - margins.bottom.coerceAtLeast(minMargins.bottomMils.toFloat() * 72.0f / 1000.0f)
        ).also {
            // Set the new margins
            pageDrawBounds.set(it)
        }
    }

    /**
     * Layout the pages
     * @return A list of the pages
     * The returned list is also assigned to this.pages
     */
    suspend fun layoutPages(): List<PageLayoutHandler.Page> {
        // Only layout if there are some books
        return bookList?.let {books ->
            // If pages is not null, the previous layout is valid, return it
            pages?: run {
                // Get a new layout handler
                val pageLayoutHandler = PageLayoutHandler(this, calculateDrawBounds())
                // Use it to layout the pages
                pageLayoutHandler.layoutPages(books).let {pages ->
                    // No pages, through an exception
                    if (pages.isEmpty())
                        throw NoPagesException()
                    // Remember the layout
                    this.pages = pages
                    // Return the pages
                    pages
                }
            }
        }?: throw NoBooksException()
    }

    /**
     * Process the pages we are printing
     * @param pages The layouts of all of the pages
     * @param pageRanges The ranges for the pages to be printed
     * @param writtenRanges The ranges for the pages actually written
     */
    private suspend fun processPages(pages: List<PageLayoutHandler.Page>, pageRanges: Array<PageRange>, writtenRanges: MutableList<PageRange>) {
        // If pdf is not null, the the document is valid, just return it
        pdf?.let { doc ->
            // We had better have some books
            bookList?.let { books ->
                // start and end of the current print range
                var start = Int.MIN_VALUE
                var end = Int.MIN_VALUE

                // Create a layout handler to layout the books for printing
                val handler = PageLayoutHandler(this, pageDrawBounds)
                // When changing the layout I am getting cases where the pageRanges
                // goes beyond the number of pages that were laid out. If we don't
                // output all of the pages, then the print manager detects that as
                // an error. So we always loop over the pages in the page range
                var nextPage = Int.MIN_VALUE
                for (range in pageRanges.sortedBy { it.start }) {
                    if (range.end >= nextPage) {
                        // For each page
                        for (pageNumber in range.start.coerceAtLeast(nextPage)..range.end) {
                            nextPage = pageNumber + 1
                            // If the page is not in the range we are printing, go to the next page
                            if (pageRanges.indexOfFirst { it.start <= pageNumber && it.end >= pageNumber } < 0)
                                continue
                            // If we are continuing a range, then just bump the end
                            if (end + 1 == pageNumber)
                                ++end
                            else {
                                // Write out the current range, if there is one
                                if (start >= 0)
                                    writtenRanges.add(PageRange(start, end))
                                // Set the start and end of the current range
                                start = pageNumber
                                end = pageNumber
                            }

                            // Get the page
                            val page = doc.startPage(pageNumber)

                            // Draw the page for printing
                            if (pageNumber < pages.size)
                                drawPage(page.canvas, pages[pageNumber], books, handler)

                            // Finish the page
                            doc.finishPage(page)
                        }
                    }
                }

                // save the last range we printed
                if (start >= 0)
                    writtenRanges.add(PageRange(start, end))
            }
        }
    }

    /**
     * Draw a page into a canvas
     * @param canvas The canvas to draw into
     * @param page The page to draw
     * @param books The list of books to draw
     * @param handler The layout handler used to layout each book
     * Canvas must be scaled to use points as the unit for coordinates
     */
    suspend fun drawPage(canvas: Canvas, page: PageLayoutHandler.Page, books: List<BookAndAuthors>, handler: PageLayoutHandler) {
        canvas.translate(pageDrawBounds.left, pageDrawBounds.top)

        // Debugging - Draw border of margin area
        //canvas.drawRect(-0.5f, -0.5f, pageDrawBounds.width() + 0.5f, 0.0f, basePaint)
        //canvas.drawRect(-0.5f, 0.0f, 0.0f, pageDrawBounds.height(), basePaint)
        //canvas.drawRect(pageDrawBounds.width(), 0.0f, pageDrawBounds.width() + 0.5f, pageDrawBounds.height(), basePaint)
        //canvas.drawRect(-0.5f, pageDrawBounds.height(), pageDrawBounds.width() + 0.5f, pageDrawBounds.height() + 0.5f, basePaint)

        // Print each book on the page
        for (bp in page.books) {
            // Make sure the job isn't canceled
            coroutineContext.ensureActive()
            // Draw the book
            bp.draw(canvas, books, handler)
        }
    }

    /**
     * Get a thumbnail for a book
     * @param bookId The id of the book
     * @param large True to get the large thumbnail. False for the small one.
     */
    suspend fun getThumbnail(bookId: Long, large: Boolean): Bitmap? {
        return getThumbnailCallback(bookId, large)
    }

    /** Default attributes for printing */
    private val defaultAttributes: PrintAttributes
        get() {
            val id = preferences.getString(PREF_PAGE_SIZE, null)
            val mediaSize = id?.let {
                paperSizes.firstOrNull {size -> id == size.id  }
            }?: PrintAttributes.MediaSize.NA_LETTER
            val portrait = preferences.getBoolean(PREF_PORTRAIT, mediaSize.isPortrait)
            // If the paper size is square, or the orientation isn't changing
            // then the size is OK, otherwise swap the width and height to match the portrait setting
            val newSize = if (mediaSize.widthMils == mediaSize.heightMils || portrait == (mediaSize.heightMils > mediaSize.widthMils))
                mediaSize
            else {
                PrintAttributes.MediaSize(mediaSize.id, preferences.getString(PREF_PRINT_PAGE_SIZE_LABEL, "")!!, mediaSize.heightMils, mediaSize.widthMils)
            }
            return PrintAttributes.Builder()
                .setMediaSize(newSize)
                .setResolution(PrintAttributes.Resolution("1", "300DPI", 300, 300))
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins(500, 500, 500, 500))
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build()
        }

    companion object {
        /**
         * Convert pixels at a resolution to points
         * @param length Length in pixels
         * @param res Resolution in DPI
         * @return Length in points
         */
        fun pixelsToPoints(length: Int, res: Int = 72): Float {
            return length.toFloat() * 72.0f / res.toFloat()
        }

        /**
         * Convert points to pixels at a resolution
         * @param length Length in points
         * @param res Resolution in DPI
         * @return Length in pixels
         */
        fun pointsToPixels(length: Float, res: Int = 72): Int {
            return ((length * res) / 72.0f).roundToInt()
        }

        val paperSizes = arrayOf(
            PrintAttributes.MediaSize.NA_FOOLSCAP,
            PrintAttributes.MediaSize.NA_GOVT_LETTER,
            PrintAttributes.MediaSize.NA_INDEX_3X5,
            PrintAttributes.MediaSize.NA_INDEX_4X6,
            PrintAttributes.MediaSize.NA_INDEX_5X8,
            PrintAttributes.MediaSize.NA_JUNIOR_LEGAL,
            PrintAttributes.MediaSize.NA_LEDGER,
            PrintAttributes.MediaSize.NA_LEGAL,
            PrintAttributes.MediaSize.NA_LETTER,
            PrintAttributes.MediaSize.NA_MONARCH,
            PrintAttributes.MediaSize.NA_QUARTO,
            PrintAttributes.MediaSize.NA_TABLOID,
            PrintAttributes.MediaSize.ISO_A0,
            PrintAttributes.MediaSize.ISO_A1,
            PrintAttributes.MediaSize.ISO_A10,
            PrintAttributes.MediaSize.ISO_A2,
            PrintAttributes.MediaSize.ISO_A3,
            PrintAttributes.MediaSize.ISO_A4,
            PrintAttributes.MediaSize.ISO_A5,
            PrintAttributes.MediaSize.ISO_A6,
            PrintAttributes.MediaSize.ISO_A7,
            PrintAttributes.MediaSize.ISO_A8,
            PrintAttributes.MediaSize.ISO_A9,
            PrintAttributes.MediaSize.ISO_B0,
            PrintAttributes.MediaSize.ISO_B1,
            PrintAttributes.MediaSize.ISO_B10,
            PrintAttributes.MediaSize.ISO_B2,
            PrintAttributes.MediaSize.ISO_B3,
            PrintAttributes.MediaSize.ISO_B4,
            PrintAttributes.MediaSize.ISO_B5,
            PrintAttributes.MediaSize.ISO_B6,
            PrintAttributes.MediaSize.ISO_B7,
            PrintAttributes.MediaSize.ISO_B8,
            PrintAttributes.MediaSize.ISO_B9,
            PrintAttributes.MediaSize.ISO_C0,
            PrintAttributes.MediaSize.ISO_C1,
            PrintAttributes.MediaSize.ISO_C10,
            PrintAttributes.MediaSize.ISO_C2,
            PrintAttributes.MediaSize.ISO_C3,
            PrintAttributes.MediaSize.ISO_C4,
            PrintAttributes.MediaSize.ISO_C5,
            PrintAttributes.MediaSize.ISO_C6,
            PrintAttributes.MediaSize.ISO_C7,
            PrintAttributes.MediaSize.ISO_C8,
            PrintAttributes.MediaSize.ISO_C9,
            PrintAttributes.MediaSize.JPN_CHOU2,
            PrintAttributes.MediaSize.JPN_CHOU3,
            PrintAttributes.MediaSize.JPN_CHOU4,
            PrintAttributes.MediaSize.JPN_HAGAKI,
            PrintAttributes.MediaSize.JIS_B0,
            PrintAttributes.MediaSize.JIS_B1,
            PrintAttributes.MediaSize.JIS_B10,
            PrintAttributes.MediaSize.JIS_B2,
            PrintAttributes.MediaSize.JIS_B3,
            PrintAttributes.MediaSize.JIS_B4,
            PrintAttributes.MediaSize.JIS_B5,
            PrintAttributes.MediaSize.JIS_B6,
            PrintAttributes.MediaSize.JIS_B7,
            PrintAttributes.MediaSize.JIS_B8,
            PrintAttributes.MediaSize.JIS_B9,
            PrintAttributes.MediaSize.JIS_EXEC,
            PrintAttributes.MediaSize.JPN_KAHU,
            PrintAttributes.MediaSize.JPN_KAKU2,
            PrintAttributes.MediaSize.JPN_OUFUKU,
            PrintAttributes.MediaSize.JPN_YOU4,
            PrintAttributes.MediaSize.OM_DAI_PA_KAI,
            PrintAttributes.MediaSize.OM_JUURO_KU_KAI,
            PrintAttributes.MediaSize.OM_PA_KAI,
            PrintAttributes.MediaSize.PRC_1,
            PrintAttributes.MediaSize.PRC_10,
            PrintAttributes.MediaSize.PRC_16K,
            PrintAttributes.MediaSize.PRC_2,
            PrintAttributes.MediaSize.PRC_3,
            PrintAttributes.MediaSize.PRC_4,
            PrintAttributes.MediaSize.PRC_5,
            PrintAttributes.MediaSize.PRC_6,
            PrintAttributes.MediaSize.PRC_7,
            PrintAttributes.MediaSize.PRC_8,
            PrintAttributes.MediaSize.PRC_9,
            PrintAttributes.MediaSize.ROC_16K,
            PrintAttributes.MediaSize.ROC_8K
        )

        private const val PREF_MARGINS_LEFT = "print_margins_left"
        private const val PREF_MARGINS_TOP = "print_margins_top"
        private const val PREF_MARGINS_RIGHT = "print_margins_right"
        private const val PREF_MARGINS_BOTTOM = "print_margins_bottom"
        private const val PREF_HORIZONTAL_SEPERATION = "print_horizontal_separation"
        private const val PREF_VERTICAL_SEPARATION = "print_vertical_separation"
        private const val PREF_SEP_LINE_WIDTH = "print_separator_line_width"
        private const val PREF_NUMBER_OF_COLUMNS = "print_number_of_columns"
        private const val PREF_ORPHANS = "print_orphans"
        private const val PREF_PAGE_SIZE = "print_page_size"
        private const val PREF_PRINT_PAGE_SIZE_LABEL = "print_page_size_label"
        private const val PREF_PORTRAIT = "print_portrait"
        const val PREF_TEXT_SIZE = "print_text_size"
        const val PREF_INCLUDED_FIELDS = "print_included_fields"
    }
}
