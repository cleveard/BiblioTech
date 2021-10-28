package com.github.cleveard.bibliotech.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.print.PageRange
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import android.text.*
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.Column
import com.github.cleveard.bibliotech.ui.print.PrintLayouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.lang.IllegalStateException
import kotlin.math.roundToInt

/** Class for printing to a PDF */
class PDFPrinter(private val layouts: PrintLayouts, private val getThumbnailCallback: suspend (bookId: Long, large: Boolean) -> Bitmap?): Closeable {
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
    private val margins: RectF = RectF(72.0f, 72.0f, 72.0f, 72.0f)
    /** The print attributes */
    private var attributes: PrintAttributes = defaultAttributes
        set(v) {
            // If the print attributes change, reprint the document
            if (v != field)
                pdf = null
            field = v
        }
    /** The list of books */
    var bookList: List<BookAndAuthors>? = null
        // If the book list changes, reprint the document
        set(v) { if (v != field) pdf = null; field = v }
    /** List of laid out pages */
    private var pages: List<PageLayoutHandler.Page>? = null
    /** Number of pages */
    val pageCount: Int
        get() = pages?.size?: 0
    /** Distance in points to separate the books horizontally */
    var horizontalSeparation: Float = 18.0f
        // If the horizontal separation changes, reprint the document
        set(v) { if (v != field) pdf = null; field = v }
    /** Distance in points to separate print columns vertically */
    var verticalSeparation: Float = 9.0f
        // If the vertical separation changes, reprint the document
        set(v) { if (v != field) pdf = null; field = v }
    /** The bounds of the drawing area on the page */
    val pageDrawBounds = RectF()
    /** The layout handler for the document */
    private var pageLayoutHandler: PageLayoutHandler = PageLayoutHandler(this, calculateDrawBounds())
    /**
     * The width of a separator line
     * Set to 0 to prevent the line from printing
     */
    var separatorLineWidth: Float = 0.5f
        // If the separator line changes, reprint the document
        set(v) { if (v != field) pdf = null; field = v }
    /** The number of print columns */
    var numberOfColumns: Int = 2
        // If the number of columns changes, reprint the document
        set(v) { if (v != field) pdf = null; field = v }
    /** The smallest number of lines that aren't orphans */
    var orphans: Int = 2
        // If the orphan count changes, reprint the document
        set(v) { if (v != field) pdf = null; field = v }
    /** Set of visible fields in layout */
    val visibleFields: MutableSet<String> = mutableSetOf(
        "SmallThumb",
        Column.TITLE.name,
        Column.SUBTITLE.name,
        Column.FIRST_NAME.name
    )
    /** The base paint for printing the document */
    val basePaint = TextPaint()

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
        attributes = defaultAttributes
    }

    /**
     * Get a layout based on the column width
     * @param columnWidth The width of a column
     */
    fun getLayout(columnWidth: Float): LayoutDescription = layouts.getLayout(columnWidth)

    /**
     * Change the print attributes and margins
     * @param newAttributes The new print attributes
     * @param newMargins The new margins
     * @return True if the document needs to be reprinted
     */
    fun changeLayout(newAttributes: PrintAttributes? = null, newMargins: RectF? = null): Boolean {
        attributes = newAttributes?: defaultAttributes
        if (newMargins != null && newMargins != margins) {
            margins.set(newMargins)
            pdf = null
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
     */
    private suspend fun layoutPages(): List<PageLayoutHandler.Page> {
        // Only layout if there are some books
        return bookList?.let {books ->
            // If pages is not null, the previous layout is valid, return it
            pages?: run {
                // Get a new layout handler
                pageLayoutHandler = PageLayoutHandler(this, calculateDrawBounds())
                // Use it to layout the pages
                pageLayoutHandler.layoutPages(books).let {pages ->
                    // Not pages, through an exception
                    if (pages.isEmpty())
                        throw IllegalStateException("No pages were found to print")
                    // Remember the layout
                    this.pages = pages
                    // Return the pages
                    pages
                }
            }
        }?: throw IllegalStateException("A list of books must be selected to print")
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

                // For each page
                for (pageNumber in pages.indices) {
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

                    // Get the canvas for printing
                    val canvas = page.canvas
                    canvas.translate(pageDrawBounds.left, pageDrawBounds.top)

                    // Debugging - Draw border of margin area
                    //canvas.drawRect(-0.5f, -0.5f, pageDrawBounds.width() + 0.5f, 0.0f, basePaint)
                    //canvas.drawRect(-0.5f, 0.0f, 0.0f, pageDrawBounds.height(), basePaint)
                    //canvas.drawRect(pageDrawBounds.width(), 0.0f, pageDrawBounds.width() + 0.5f, pageDrawBounds.height(), basePaint)
                    //canvas.drawRect(-0.5f, pageDrawBounds.height(), pageDrawBounds.width() + 0.5f, pageDrawBounds.height() + 0.5f, basePaint)

                    // Print each book on the page
                    for (bp in pages[pageNumber].books) {
                        // Print the book
                        bp.draw(canvas, books)
                    }

                    // Finish the page
                    doc.finishPage(page)
                }

                // save the last range we printed
                if (start >= 0)
                    writtenRanges.add(PageRange(start, end))
            }
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

    companion object {
        /** Default attributes for printing */
        private val defaultAttributes: PrintAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.NA_LEDGER)
            .setResolution(PrintAttributes.Resolution("1", "300DPI", 300, 300))
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins(500, 500, 500, 500))
            .build()

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
    }
}
