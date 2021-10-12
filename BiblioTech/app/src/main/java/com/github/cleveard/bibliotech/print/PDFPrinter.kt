package com.github.cleveard.bibliotech.print

import android.content.Context
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
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

private val title = LayoutDescription.ColumnFieldLayoutDescription(Column.TITLE)
private val subtitle = LayoutDescription.ColumnFieldLayoutDescription(Column.SUBTITLE).apply {
    margin.top = 4.5f
}
private val authors = LayoutDescription.ColumnFieldLayoutDescription(Column.FIRST_NAME).apply {
    margin.top = 4.5f
}
private val alignTitleTop = LayoutDescription.VerticalLayoutAlignment(LayoutDescription.VerticalLayoutAlignment.Type.Top, null).also {
    title.verticalLayout[LayoutDescription.VerticalLayoutAlignment.Type.Top] = listOf(it)
}
private val alignTitleStart = LayoutDescription.HorizontalLayoutAlignment(LayoutDescription.HorizontalLayoutAlignment.Type.Start, null).also {
    title.horizontalLayout[LayoutDescription.HorizontalLayoutAlignment.Type.Start] = listOf(it)
}
private val alignSubtitleTop = LayoutDescription.VerticalLayoutAlignment(LayoutDescription.VerticalLayoutAlignment.Type.Bottom, title).also {
    subtitle.verticalLayout[LayoutDescription.VerticalLayoutAlignment.Type.Top] = listOf(it)
}
private val alignSubtitleStart = LayoutDescription.HorizontalLayoutAlignment(LayoutDescription.HorizontalLayoutAlignment.Type.Start, title).also {
    subtitle.horizontalLayout[LayoutDescription.HorizontalLayoutAlignment.Type.Start] = listOf(it)
}
private val alignAuthorsTop = LayoutDescription.VerticalLayoutAlignment(LayoutDescription.VerticalLayoutAlignment.Type.Bottom, subtitle).also {
    authors.verticalLayout[LayoutDescription.VerticalLayoutAlignment.Type.Top] = listOf(it)
}
private val alignAuthorsStart = LayoutDescription.HorizontalLayoutAlignment(LayoutDescription.HorizontalLayoutAlignment.Type.Start, title).also {
    authors.horizontalLayout[LayoutDescription.HorizontalLayoutAlignment.Type.Start] = listOf(it)
}
private val simpleLayout = LayoutDescription(
    listOf(title, subtitle, authors),
    emptyList(),
    18.0f,
    9.0f
)

class PDFPrinter: Closeable {
    private var pdf: PrintedPdfDocument? = null
        private set(v) {
            field.let {
                field = v
                if (it != v) {
                    it?.close()
                    if (v == null) {
                        pages = null
                    }
                }
            }
        }

    private var pageDrawBounds: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
    private var attributes: PrintAttributes = defaultAttributes
        set(v) {
            if (v != field)
                pdf = null
            field = v
            val size = v.mediaSize?: defaultAttributes.mediaSize!!
            val margins = v.minMargins?: defaultAttributes.minMargins!!
            pageDrawBounds.top = margins.topMils.toFloat() * 72.0f / 1000.0f
            pageDrawBounds.left = margins.leftMils.toFloat() * 72.0f / 1000.0f
            pageDrawBounds.right = (size.widthMils - margins.rightMils).toFloat() * 72.0f / 1000.0f
            pageDrawBounds.bottom = (size.heightMils - margins.rightMils).toFloat() * 72.0f / 1000.0f
        }
    var bookList: List<BookAndAuthors>? = null
        set(v) { if (v != field) pdf = null; field = v }
    private var pages: List<PageLayoutHandler.Page>? = null
    val pageCount: Int
        get() = pages?.size?: 0
    var layoutDescription: LayoutDescription = simpleLayout
        set(v) { if (v != field) pdf = null; field = v }
    var separatorLineWidth: Float = 2.0f
        set(v) { if (v != field) pdf = null; field = v }
    var numberOfColumns: Int = 1
        set(v) { if (v != field) pdf = null; field = v }
    private var pageLayoutHandler: PageLayoutHandler = PageLayoutHandler(this, pageDrawBounds)
    val basePaint = TextPaint()

    fun horizontalPointsToPixels(length: Float): Int {
        return pointsToPixels(length, attributes.resolution?.horizontalDpi)
    }

    fun verticalPointstoPixels(length: Float): Int {
        return pointsToPixels(length, attributes.resolution?.verticalDpi)
    }

    fun horizontalPixelsToPoints(length: Int): Float {
        return pixelsToPoints(length, attributes.resolution?.horizontalDpi)
    }

    fun verticalPixelsToPoints(length: Int): Float {
        return pixelsToPoints(length, attributes.resolution?.verticalDpi)
    }

    override fun close() {
        pdf = null
        bookList = null
        attributes = defaultAttributes
    }

    fun changeLayout(newAttributes: PrintAttributes? = null): Boolean {
        attributes = newAttributes?: defaultAttributes
        return pdf == null
    }

    suspend fun computePageCount(): Int {
        return withContext(Dispatchers.IO) {
            layoutPages().size
        }
    }

    suspend fun writePDF(
        context: Context,
        pageRanges: Array<PageRange>,
        writtenRanges: MutableList<PageRange>
    ): PrintedPdfDocument {
        pdf?.let { return@writePDF it }
        val doc = PrintedPdfDocument(context, attributes)
        pdf = doc
        bookList?.let { _ ->
            withContext(Dispatchers.IO) {
                pages = layoutPages().also {
                    processPages(it, pageRanges, writtenRanges)
                }
            }
        }
        return doc
    }

    private fun layoutPages(): List<PageLayoutHandler.Page> {
        return bookList?.let {books ->
            pages?: run {
                pageLayoutHandler = PageLayoutHandler(this, pageDrawBounds)
                pageLayoutHandler.layoutPages(books).let {pages ->
                    if (pages.isEmpty())
                        throw IllegalStateException("No pages were found to print")
                    this.pages = pages
                    pages
                }
            }
        }?: throw IllegalStateException("A list of books must be selected to print")
    }

    /**
     * Process the pages we are printing
     * @param pages The layouts of all of the pages
     */
    private fun processPages(pages: List<PageLayoutHandler.Page>, pageRanges: Array<PageRange>, writtenRanges: MutableList<PageRange>) {
        pdf?.let { doc ->
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

                // Print the books on it
                bookList?.printPage(pages[pageNumber], page)

                // Finish the page
                doc.finishPage(page)
            }

            // save the last range we printed
            if (start >= 0)
                writtenRanges.add(PageRange(start, end))
        }
    }

    /**
     * Print the books on a single page
     * @param layouts The layouts of the books on the page
     * @param page The pdf page we are printing
     */
    private fun List<BookAndAuthors>.printPage(layouts: PageLayoutHandler.Page, page: PdfDocument.Page) {
        // Get the canvas for printing
        val canvas = page.canvas
        // Print each book on the page
        for (bp in layouts.books) {
            // Save the current canvas state
            canvas.save()
            // Set the clip rectangle and location
            canvas.translate(bp.position.x, bp.position.y)
            // Print the book
            pageLayoutHandler.layoutBook(this[bp.bookIndex])
                .verticalClip(bp.position.y, pageLayoutHandler.pageHeight)
                .draw(canvas)
            // Restore the canvas
            canvas.restore()
        }
    }

    companion object {
        private val defaultAttributes: PrintAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.NA_LEDGER)
            .setResolution(PrintAttributes.Resolution("1", "300DPI", 300, 300))
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins(500, 500, 500, 500))
            .build()

        fun pixelsToPoints(length: Int, res: Int?): Float {
            return length.toFloat() * 72.0f / (res?.toFloat()?: 288.0f)
        }

        fun pointsToPixels(length: Float, res: Int?): Int {
            return ((length * (res?: 288)) / 72.0f).roundToInt()
        }
    }

}
