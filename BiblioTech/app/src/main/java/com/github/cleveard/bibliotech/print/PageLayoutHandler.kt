package com.github.cleveard.bibliotech.print

import android.graphics.PointF
import android.graphics.RectF
import com.github.cleveard.bibliotech.db.BookAndAuthors

/**
 * The class that calculates the layouts of books
 */
class PageLayoutHandler(
    private val printer: PDFPrinter,
    pageDrawBounds: RectF,
    private val rtl: Boolean = false
) {

    data class BookPosition(
        val bookIndex: Int,
        val position: PointF,
        val clip: RectF
    )

    data class Page(
        val books: List<BookPosition>
    )

    private val pages = ArrayList<Page>()
    private var page = ArrayList<BookPosition>()
    val pageHeight = pageDrawBounds.height()
    private val pageWidth = pageDrawBounds.width()
    private val columnStride = (pageWidth + printer.layoutDescription.horizontalSeparation) / printer.numberOfColumns
    private val columnWidth =  columnStride - printer.layoutDescription.horizontalSeparation
    private var location: PointF = PointF(0.0f, 0.0f)
    private var columnStart: Boolean = true
    private val dependencies: HashMap<BookLayout.LayoutBounds, MutableSet<BookLayout.LayoutBounds>> = HashMap()
    private val calculated: HashSet<BookLayout.LayoutBounds> = HashSet()
    private var laidOutBook: BookAndAuthors? = null
    private val layout: BookLayout by lazy {
        printer.layoutDescription.createLayout(printer, columnWidth, rtl).also {bookLayout ->
            // Build the layout map and dependency map
            dependencies[bookLayout.layoutBounds] = HashSet()
            // Map the FieldLayoutDescriptions to the  DrawLayouts
            // And initialize the dependency map
            for (dl in bookLayout.columns) {
                dependencies[dl] = HashSet<BookLayout.LayoutBounds>().also {
                    dl.alignment.values.asSequence().map { it.asSequence() }.flatten().forEach { dependencies[it.bounds]?.add(dl) }
                }
            }
        }
    }

    /**
     * Add page and start next one
     */
    private fun nextPage() {
        page.let {
            page = ArrayList()
            pages.add(Page(it))
        }
        columnStart = true
    }

    fun layoutPages(books: List<BookAndAuthors>): List<Page> {
        for (i in books.indices) {
            placeBook(i, layoutBook(books[i]))
        }

        if (page.size > 0)
            nextPage()
        return pages
    }

    /**
     * Layout a single book
     * @param book The book to layout
     * @return The layout for the book. All layouts share a single object
     */
    fun layoutBook(book: BookAndAuthors): BookLayout {
        if (book == laidOutBook)
            return layout
        laidOutBook = book
        layout.setContent(book)

        clear()
        do {
            calculateLayout()
        } while (resolveOverlaps())

        return layout
    }

    /**
     * Place the book layout on the page
     * @param index The index of the book
     * @param layout The layout of the  book
     */
    private fun placeBook(index: Int, layout: BookLayout) {
        // If nothing to print the return
        if (layout.bounds.isEmpty)
            return

        // Starting position
        var x = location.x
        var y = location.y
        // The space separating the books
        var separatorSize = if (columnStart)
            -layout.bounds.top
        else
            layout.description.verticalSeparation + printer.separatorLineWidth

        // Bump to the next column and next page if needed
        fun nextColumn(newY: Float) {
            x += columnWidth
            if (x >= pageWidth) {
                nextPage()
                x = 0.0f
            }
            columnStart = true
            y = newY
        }

        // Clip the book to the column height
        layout.verticalClip(y + separatorSize, pageHeight)
        // No space for anything, go to next column
        if (layout.clip.isEmpty) {
            // Go to next column
            nextColumn(0.0f)
            separatorSize = -layout.marginBounds.top
            // Clip the height for the new column
            layout.verticalClip(y + separatorSize, pageHeight)
        }
        y += separatorSize

        // Break the book layout over the columns and pages
        while (true) {
            // Get the current print position and clip rectangle
            y -= layout.clip.top
            val pos = PointF(x, y)
            val clip = RectF(layout.clip)
            clip.offset(0.0f, clip.top)
            // Add the printout to the page
            page.add(BookPosition(index, pos, clip))
            columnStart = false

            // Is this the end of the book
            val height = clip.height()
            if (height >= layout.bounds.bottom)
                break       // Yes

            // Go to the next column and set the position
            // where the layout will be printed
            nextColumn(y - height)
            // How much can we fit in this column
            layout.verticalClip(y, pageHeight)
        }

        // Set the location to the end of the book
        location.x = x
        location.y = y + layout.marginBounds.bottom
    }

    private fun clear() {
        calculated.clear()
    }

    /**
     * Find overlapping fields and adjust their width
     */
    private fun resolveOverlaps(): Boolean {
        return false
    }

    /**
     * Calculate the positions for the fields in the layout
     */
    private fun calculateLayout() {
        for (dl in layout.columns) {
            calculateLayout(dl)
        }
    }

    fun alignBounds(min: Float, max: Float, alignMin: Float?, alignMax: Float?): Float {
        return if (alignMin == null) {
            if (alignMax == null) {
                if (rtl)
                    columnWidth - max
                else
                    -min
            } else
                alignMax - max
        } else if (alignMax == null)
            alignMin - min
        else
            (alignMin + alignMax - min - max) / 2.0f
    }

    /**
     * Calculate the positions for the fields in the layout
     */
    private fun calculateLayout(
        dl: BookLayout.LayoutBounds,
        target: LayoutDescription.AlignmentTarget = LayoutDescription.AlignmentTarget(dl.baseline, dl.rtl)
    ): BookLayout.LayoutBounds {
        if (calculated.contains(dl))
            return dl

        calculated.add(dl)
        dl.alignment.asSequence().map { it.value.asSequence() }.flatten().forEach { calculateLayout(it.bounds, target) }
        dl.bounds.set(0.0f, 0.0f, dl.bounds.width(), dl.bounds.height())
        target.clear(dl.baseline, dl.rtl)
        dl.alignment.forEach { it.key.calculateAlignemt(target, it.value.asSequence()) }

        alignBounds(dl.bounds.top - dl.margins.top, dl.bounds.bottom + dl.margins.bottom,
            target.top?.minus(dl.margins.top), target.bottom?.plus(dl.margins.bottom)).let {
            dl.bounds.top += it
            dl.bounds.bottom += it
        }
        alignBounds(dl.bounds.left - dl.margins.left, dl.bounds.right + dl.margins.right,
            target.left?.minus(dl.margins.left), target.right?.plus(dl.margins.right)).let {
            dl.bounds.left += it
            dl.bounds.right += it
        }

        return dl
    }
}