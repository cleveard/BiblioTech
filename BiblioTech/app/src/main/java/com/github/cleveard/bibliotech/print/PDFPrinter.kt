package com.github.cleveard.bibliotech.print

import android.content.Context
import android.graphics.RectF
import android.print.PageRange
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import android.text.*
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.Column
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.lang.IllegalStateException
import kotlin.math.roundToInt

/** A simple layout, Title, subtitle and authors */
private val simpleLayout = LayoutDescription(
    listOf(
        LayoutDescription.ColumnFieldLayoutDescription(Column.TITLE),
        LayoutDescription.ColumnFieldLayoutDescription(Column.SUBTITLE).apply {
            margin.top = 4.5f
        },
        LayoutDescription.ColumnFieldLayoutDescription(Column.FIRST_NAME).apply {
            margin.top = 4.5f
        }
    ),
    emptyList(),                // No headers
    18.0f,      // Separation between print columns
    9.0f            // Separation between books
).apply {
    // Title is at the top
    LayoutDescription.VerticalLayoutAlignment(LayoutDescription.VerticalLayoutAlignment.Type.Top, null).also {
        inColumns[0].verticalLayout[LayoutDescription.VerticalLayoutAlignment.Type.Top] = listOf(it)
    }
    // Title is at the start
    LayoutDescription.HorizontalLayoutAlignment(LayoutDescription.HorizontalLayoutAlignment.Type.Start, null).also {
        inColumns[0].horizontalLayout[LayoutDescription.HorizontalLayoutAlignment.Type.Start] = listOf(it)
    }
    // Subtitle is below the title
    LayoutDescription.VerticalLayoutAlignment(LayoutDescription.VerticalLayoutAlignment.Type.Bottom, inColumns[0]).also {
        inColumns[1].verticalLayout[LayoutDescription.VerticalLayoutAlignment.Type.Top] = listOf(it)
    }
    // Subtitle start is aligned with title start
    LayoutDescription.HorizontalLayoutAlignment(LayoutDescription.HorizontalLayoutAlignment.Type.Start, inColumns[0]).also {
        inColumns[1].horizontalLayout[LayoutDescription.HorizontalLayoutAlignment.Type.Start] = listOf(it)
    }
    // Authors is below the subtitle
    LayoutDescription.VerticalLayoutAlignment(LayoutDescription.VerticalLayoutAlignment.Type.Bottom, inColumns[1]).also {
        inColumns[2].verticalLayout[LayoutDescription.VerticalLayoutAlignment.Type.Top] = listOf(it)
    }
    // Authors is aligned with title start
    LayoutDescription.HorizontalLayoutAlignment(LayoutDescription.HorizontalLayoutAlignment.Type.Start, inColumns[0]).also {
        inColumns[2].horizontalLayout[LayoutDescription.HorizontalLayoutAlignment.Type.Start] = listOf(it)
    }
}

/** Class for printing to a PDF */
class PDFPrinter: Closeable {
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
    /** The description of the book layout */
    var layoutDescription: LayoutDescription = simpleLayout
        // If the book layout changes, reprint the document
        set(v) { if (v != field) pdf = null; field = v }
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
    /** The bounds of the drawing area on the page */
    private val pageDrawBounds = RectF()
    /** The layout handler for the document */
    private var pageLayoutHandler: PageLayoutHandler = PageLayoutHandler(this, calculateDrawBounds())
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
    private fun layoutPages(): List<PageLayoutHandler.Page> {
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
    private fun processPages(pages: List<PageLayoutHandler.Page>, pageRanges: Array<PageRange>, writtenRanges: MutableList<PageRange>) {
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
