package com.github.cleveard.bibliotech.print

import android.graphics.Canvas
import android.graphics.RectF
import android.text.*
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.Column
import kotlin.math.ceil

/** Class to hold absolute layout for a book */
data class BookLayout(
    /** The printer */
    val printer: PDFPrinter,
    /** The description of the book layout */
    val description: LayoutDescription,
    /** The column and their bounds after layout */
    val columns: List<DrawLayout>,
    /** The width of the print column */
    val columnWidth: Float,
    /** Bounding box of the book. This does not includes */
    val bounds: RectF,
    /** Bounding box of the book. This includes all margins in the layout */
    val marginBounds: RectF,
    /** Clip rectangle for the book */
    val clip: RectF,
    /** Right-to-left text */
    val rtl: Boolean
) {
    companion object {
        const val MAX_HEIGHT = Float.MAX_VALUE / 4.0f
    }

    /** Interface for fields and the parent to calculate a layout */
    sealed interface LayoutBounds {
        /** The bounds of the field/parent */
        val bounds: RectF
        /** The offset of the top baseline from the bounds top */
        val baseline: Float
        /** True to layout right to left */
        val rtl: Boolean
        /** alignment criteria for the field */
        val alignment: MutableMap<LayoutDescription.LayoutAlignmentType, List<LayoutDimension>>
        /** The field/parent margins */
        val margins: RectF
        /** The description of the field. Null means the BookLayout */
        val description: LayoutDescription.FieldLayoutDescription?
        /** The maximum width of the field */
        var width: Float
        /** The maximum height of the field */
        var height: Float
    }

    /**
     * The layout bounds interface for the BookLayout
     * This is a separate object to keep the LayoutBounds interface nested
     */
    val layoutBounds: LayoutBounds = BookLayoutBounds()

    /**
     * Clip the book layout to a column
     * @param y The vertical position of the layout in the column
     * @param columnHeight The height of the column
     */
    fun verticalClip(y: Float, columnHeight: Float): BookLayout {
        // Initialize the clip rectangles
        columns.forEach { it.clip.set(it.bounds) }

        // Calculate the layout clip rectangle for the layout
        val bookTop = y + bounds.top
        val bookBottom = y + bounds.bottom
        val clipTop = bookTop.coerceAtLeast(0.0f) - bookTop
        val clipBottom = bookBottom.coerceAtMost(columnHeight) -  bookTop
        clip.set(bounds.left, clipTop, bounds.right, clipBottom)

        // If the book is completely visible, then return the layout
        if (bookTop >= 0 && bookBottom <= columnHeight)
            return this

        // Not completely visible. We need to make sure fields are clipped
        // on proper internal boundaries - e.g. lines for text
        // The top clip is set to include lines on the boundary
        clip.top = columns.minOf {
            if (it.verticalClip(clipTop, clipBottom, false))
                it.clip.top
            else
                bounds.bottom
        }

        // The bottom clip is set to exclude lines on the boundary
        clip.bottom = columns.maxOf {
            if (it.verticalClip(clip.top, clipBottom - clipTop + clip.top, true))
                it.clip.bottom
            else
                bounds.top
        }

        return this
    }

    /**
     * Orphan handling
     * @param y The vertical position of the layout in the column
     * @param columnStart We are the top of a column
     * In printing an orphan line(s) are a small number of lines that
     * are separated from the body of a paragraph. This code stops
     * us from breaking a book layout so a small number of lines
     * end up in a separate column. PDFPrinter.orphans is where the
     * number of lines is specified.
     */
    fun handleOrphans(y: Float, columnStart: Boolean) {
        // Don't bother with orphans if orphans aren't important
        // Or the entire layout isn't visible
        // Or the bottom of the layout is visible. We don't need
        // about the case where the bottom is visible because, either
        // the whole layout is visible, or we handled it when we clipped
        // the layout previously
        if (printer.orphans < 1 || clip.isEmpty || clip.bottom + EPSILON >= bounds.bottom)
            return

        // Create a list of the top and bottom of all lines in the layout
        val lines: List<Pair<Float,Float>> = columns.asSequence()            // All of the fields
            .map {l ->
                // Map each field to a sequence of line vertical bounds
                // or null if the textLayout is null or doesn't have any lines
                if (l.bounds.isEmpty)
                    null
                else {
                    l.textLayout?.let { t ->
                        (0 until t.lineCount).asSequence()
                            .filter { l.bounds.top + printer.verticalPixelsToPoints(t.getLineTop(it)) >= l.clip.top }
                            .map {
                                Pair(
                                    l.bounds.top + printer.verticalPixelsToPoints(t.getLineTop(it)),
                                    l.bounds.top + printer.verticalPixelsToPoints(t.getLineTop(it + 1))
                                )
                            }
                    }
                }
            }
            .filterNotNull()                        // Filter out fields without a text layout
            .flatten()                              // Flatten the sequences to a list of vertical bounds
            .toMutableList().apply {
                sortWith(compareBy({ it.first }, { it.second }))    // Sort by the top of the bounds, then the bottom
            }

        // Keep track of lines we move from the current column to the next
        var end = lines.size
        while (!clip.isEmpty) {
            // Check for orphans at the start of the layout
            // If we are at the start of a column, don't worry orphans at the start
            // Also don't worry if we aren't displaying the top of the layout
            // in this draw, because it should have been handled before
            if (!columnStart && clip.top <= bounds.top && clip.bottom > bounds.top) {
                // Count the visible lines. We count fields, and discard
                // fields that share the same line
                var visibleCount = 0
                var bottom = clip.top
                for (line in lines) {
                    if (line.first >= clip.bottom)
                        break                           // At the bottom, stop
                    // Skip lines that aren't fully visible
                    if (line.second <= clip.bottom) {
                        // Is this the first field on a different line
                        if (line.first >= bottom) {
                            // Yes, count it and mark the bottom of the line
                            ++visibleCount
                            bottom = line.second
                        }
                    }
                }
                if (visibleCount < printer.orphans) {
                    // Not enough visible lines force next column, by making the clip rectangle empty
                    clip.top = bounds.top
                    clip.bottom = bounds.top
                    return
                }
            }

            // We return above when the bottom of the layout is being displayed
            // So when we get here we need to check the number of lines at the
            // bottom going to the next column
            // Count the lines after the visible lines. We count fields, and discard
            // fields that share the same line
            var nextCount = 0
            var bottom = clip.top
            for (line in lines) {
                // Skip visible lines
                if (line.second > clip.bottom) {
                    // Is this the first field on a different line
                    if (line.first >= bottom) {
                        // Yes, count it and mark the bottom of the line
                        ++nextCount
                        bottom = line.second
                    }
                }
            }
            // If there are no lines for the next column, or there are enough lines,
            // then it is OK
            if (nextCount <= 0 || nextCount >= printer.orphans)
                return

            // Need to move some lines to the next column
            while (--end >= 0) {
                // Find the last line above the current clip bottom
                if (lines[end].first < clip.bottom) {
                    // Clip to the top of that line
                    verticalClip(y, y + lines[end].first)
                    if (clip.isEmpty)
                        return
                    // Recheck the line counts
                    break
                }
            }
        }
    }

    /**
     * Set the content of the layout from a book
     * @param book The book to set the content from
     */
    fun setContent(book: BookAndAuthors) {
        // Set the content of each field
        for (d in columns)
            d.setContent(book)
    }

    /**
     * Draw the book on a canvas
     * @param canvas The canvas
     */
    fun draw(canvas: Canvas) {
        // Draw each field
        for (dl in columns)
            dl.draw(canvas)
    }

    /** Object to handle the bounds of the entire layout */
    private inner class BookLayoutBounds: LayoutBounds {
        override val bounds: RectF
            get() = this@BookLayout.bounds
        override val baseline: Float
            get() = 0.0f
        override val rtl: Boolean
            get() = this@BookLayout.rtl
        override val alignment = HashMap<LayoutDescription.LayoutAlignmentType, List<LayoutDimension>>()
        override val margins: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        override val description: LayoutDescription.FieldLayoutDescription?
            get() = null
        override var width: Float
            get() = columnWidth
            set(@Suppress("UNUSED_PARAMETER")v) {}
        override var height: Float
            get() = MAX_HEIGHT
            set(@Suppress("UNUSED_PARAMETER")v) {}
    }

    /** Interface for getting a dimension from a field */
    sealed interface LayoutDimension {
        /** The bounds of the field */
        val bounds: LayoutBounds
        /** The value of the dimension */
        val dimension: Float
    }

    /** Get the start of the field */
    class StartDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.margins.left - if (bounds.rtl) bounds.bounds.right else bounds.bounds.left
    }

    /** Get the end of the field */
    class EndDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.margins.right + if (bounds.rtl) bounds.bounds.left else bounds.bounds.right
    }

    /** Get the horizontal center of the field */
    class HCenterDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = (bounds.bounds.left - bounds.margins.left
                + bounds.bounds.right + bounds.margins.right) / 2.0f
    }

    /** Get the top of the field */
    class TopDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.bounds.top - bounds.margins.top
    }

    /** Get the bottom of the field */
    class BottomDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.bounds.bottom + bounds.margins.bottom
    }

    /** Get the baseline of the field */
    class BaselineDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.bounds.top + bounds.baseline
    }

    /** Get the vertical center of the field */
    class VCenterDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = (bounds.bounds.top - bounds.margins.top
                + bounds.bounds.bottom + bounds.margins.bottom) / 2.0f
    }

    /**
     * The base class for drawable fields
     * @param printer The PDFPrinter we are using. Several attributes are kept there
     * @param description The description of the field layout
     */
    abstract class DrawLayout(
        /** The printer */
        val printer: PDFPrinter,
        /** The field description */
        override val description: LayoutDescription.FieldLayoutDescription,
        /** The maximum width of the field */
        override var width: Float
    ): LayoutBounds {
        /** The bounds on the page */
        override var bounds: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        /** Text layout for text. Null for non-text fields */
        open val textLayout: Layout? = null
        override var height: Float = MAX_HEIGHT
        /** Current clip rectangle */
        var clip: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        /** Baseline location of the first line */
        override var baseline: Float = 0.0f
        /** True means text is right to left */
        override var rtl: Boolean = false
        /** alignment criteria for the field */
        override val alignment: MutableMap<LayoutDescription.LayoutAlignmentType, List<LayoutDimension>> = LinkedHashMap()
        /** The field margins */
        override val margins: RectF
            get() = description.margin

        /**
         * Draw the layout
         * @param canvas The canvas to use
         */
        abstract fun draw(canvas: Canvas)

        /**
         * Clip the top of the field to the print area
         * @param y The position of the book layout
         * @param columnHeight The height of the column
         * @param exclusive True to exclude parts of the field that
         *                  straddle the print area boundary. False
         *                  to include those parts
         * @return True if the field is visible
         */
        abstract fun verticalClip(y: Float, columnHeight: Float, exclusive: Boolean): Boolean

        /**
         * Set the content of the layout from a book
         * @param book The book to set the content from
         */
        abstract fun setContent(book: BookAndAuthors)

        /**
         * Clip vertical bounds by y and columnHeight
         * @param y Top of the clip region
         * @param columnHeight Bottom of the clip region
         * @return True if the clipped field is not empty
         */
        protected fun clip(y: Float, columnHeight: Float): Boolean {
            // Calculate the clip to the column height
            clip.set(bounds)
            clip.top = clip.top.coerceAtLeast(y)
            clip.bottom = clip.bottom.coerceAtMost(columnHeight)
            return !clip.isEmpty
        }
    }

    /**
     * Class for static text
     * @param printer The PDFPrinter we are using. Several attributes are kept there
     * @param width Maximum width of the field
     * @param description The description of the field layout
     * @param text The contents of the field
     * @param paint The paint used to draw the contents
     */
    open class TextLayout(
        /** The printer */
        printer: PDFPrinter,
        /** Maximum width of the field */
        width: Float,
        /** The field description */
        description: LayoutDescription.FieldLayoutDescription,
        /** The column in the database */
        protected var text: String,
        /** The paint used to draw the text */
        private var paint: TextPaint = printer.basePaint
    ): DrawLayout(printer, description, width) {
        /** Create a StaticLayout to format the text */
        override var textLayout = createLayout()

        /** @inheritDoc */
        override fun draw(canvas: Canvas) {
            if (!clip.isEmpty) {
                canvas.save()
                canvas.clipRect(clip)
                canvas.translate(bounds.left, bounds.top)
                textLayout.draw(canvas)
                canvas.restore()
            }
        }

        /**
         * Create the StaticLayout from the current values
         */
        protected fun createLayout(): StaticLayout {
            val layout = StaticLayout.Builder.obtain(
                text, 0, text.length, paint,
                printer.pointsToHorizontalPixels(width)
            ).build()
            if (layout.height < height)
                return layout
            var lines = layout.getLineForVertical(printer.pointsToVerticalPixels(height))
            if (printer.verticalPixelsToPoints(layout.getLineTop(lines)) > height)
                --lines
            return (
                if (lines > 0)
                    StaticLayout.Builder.obtain(
                        text, 0, text.length, paint,
                        printer.pointsToHorizontalPixels(width)
                    ).setMaxLines(lines)
                else
                    StaticLayout.Builder.obtain(
                        "", 0, 0, paint,
                        printer.pointsToHorizontalPixels(width)
                    )
            ).build()
        }

        /** @inheritDoc */
        override var width: Float
            get() = super.width
            set(v) {
                super.width.let {
                    super.width = v
                    // Create a new StaticLayout
                    if (it != v)
                        textLayout = createLayout()
                }
            }

        /** @inheritDoc */
        override var height: Float
            get() = super.height
            set(v) {
                super.height.let {
                    super.height = v
                    // Create a new StaticLayout
                    if (it != v)
                        textLayout = createLayout()
                }
            }

        /**
         * Find the top or bottom of the line on a clip boundary
         * @param boundary The vertical clip boundary
         * @bottom bottom True to find the bottom. False to find the top
         * If top or bottom of a line is exactly on the boundary, then the boundary is returned
         * If the top line is below the boundary, or the bottom line is above the boundary,
         * then the top of the top line or bottom of the bottom line is returned
         */
        private fun findBoundary(boundary: Float, bottom: Boolean): Float {
            // Find the line at the boundary or before it
            val line = textLayout.getLineForVertical(printer.pointsToVerticalPixels(boundary))
            // Get the top and bottom of the line
            val t = printer.verticalPixelsToPoints(textLayout.getLineTop(line))
            val b = printer.verticalPixelsToPoints(textLayout.getLineTop(line + 1))
            return when {
                // Either the top of the line is on the boundary, or the top line
                // is below the boundary, so return the top of the line
                t >= boundary -> t
                // Either the bottom of the line is on the boundary, or the bottom line
                // is above the boundary, so return the bottom of the line
                b <= boundary -> b
                // Otherwise return the bottom, if that is what we want
                bottom -> b
                // Or the top
                else -> t
            }
        }

        /** @inheritDoc */
        override fun verticalClip(y: Float, columnHeight: Float, exclusive: Boolean): Boolean {
            // Clip the rectangle and return if the field is not visible
            if (!clip(y, columnHeight))
                return false

            // If there are not lines in the field, then remove it
            val lineCount = textLayout.lineCount
            if (lineCount <= 0) {
                clip.top = bounds.top
                clip.bottom = bounds.top
                return false
            }

            // Set the top of the clip to the top of the top line that is
            // fully or partially visible
            clip.top = findBoundary(clip.top - bounds.top, exclusive) + bounds.top
            // Set the bottom of the clip to the bottom of the bottom line that
            // is fully visible
            clip.bottom = findBoundary(clip.bottom - bounds.top, !exclusive) + bounds.top

            // Return true if the clip isn't empty
            return !clip.isEmpty
        }

        /** The length of the longest line in the field */
        private val maxLineEnd: Float
            get() = printer.horizontalPixelsToPoints(ceil((0 until textLayout.lineCount).maxOf { textLayout.getLineMax(it) }).toInt())

        /** @inheritDoc */
        override fun setContent(book: BookAndAuthors) {
            setContent()
        }

        /**
         * Set the bounds of the field from the content
         */
        fun setContent() {
            // The text is static, but reset the bounding rectangle of the field
            bounds.set(0.0f, 0.0f, maxLineEnd, printer.verticalPixelsToPoints(textLayout.height))
            // Set the baseline, and set the bounds empty if there are no lines
            baseline = if (!bounds.isEmpty && textLayout.lineCount > 0)
                printer.verticalPixelsToPoints(textLayout.getLineBaseline(0))
            else {
                bounds.set(0.0f, 0.0f, 0.0f, 0.0f)
                0.0f
            }
        }
    }

    /**
     * Class for a field with a book database value
     * @param printer The PDFPrinter we are using. Several attributes are kept there
     * @param description The description of the field layout
     * @param column The database column with the field value
     */
    open class ColumnLayout(
        /** The printer */
        printer: PDFPrinter,
        /** Maximum width of the field */
        width: Float,
        /** The field description */
        description: LayoutDescription.FieldLayoutDescription,
        val column: Column,
    ): TextLayout(printer, width, description, "") {
        /** @inheritDoc */
        override fun setContent(book: BookAndAuthors) {
            // Get the column value
            text = column.desc.getDisplayValue(book)
            // Create the StaticLayout
            textLayout = createLayout()
            // Fill in the bounds and baseline of the field
            super.setContent()
        }
    }
}
