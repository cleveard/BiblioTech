package com.github.cleveard.bibliotech.print

import android.graphics.*
import android.text.*
import com.github.cleveard.bibliotech.db.BookAndAuthors
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.ceil
import androidx.core.graphics.withTranslation

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
        // Maximum height for a field
        const val MAX_HEIGHT = Float.MAX_VALUE / 4.0f
        // Minimum width for overlapping areas
        const val MIN_WIDTH = 36.0f
    }

    /** Interface for fields and the parent to calculate a layout */
    sealed interface LayoutBounds {
        /** The field position in book coordinates */
        val position: PointF
        /** The bounds of the field/parent in the fields coordinates */
        val bounds: RectF
        /** The bounds of the field/parent including margins */
        val marginBounds: RectF
        /** The offset of the top baseline from the bounds top */
        val baseline: Float
        /** True to layout right to left */
        val rtl: Boolean
        /** alignment criteria for the field */
        val alignment: Map<LayoutDescription.LayoutAlignmentType, LayoutAlignmentParameters>
        /** Layouts we need to check that may overlap this one */
        val overlapping: Set<LayoutBounds>
        /** Last set of overlapping rectangles */
        val overlaps: List<RectF>
        /** The field/parent margins. Left is the start margin and right is the end. */
        val margins: RectF
        /** The description of the field. Null means the BookLayout */
        val description: LayoutDescription.FieldLayoutDescription<*>?
        /** The maximum width of the field */
        var width: Float
        /** The maximum height of the field */
        var height: Float

        /**
         * Wrap the contents of this field around overlapping fields
         * @return True if the layout changed
         */
        fun wrapOverlapping(): Boolean = false
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
        val bookBottom = y + bounds.bottom
        val clipTop = y.coerceAtLeast(-bounds.top) - y + bounds.top

        var clipBottom: Float
        var pos = bookBottom.coerceAtMost(columnHeight) -  y
        do {
            clipBottom = pos
            for (dl in columns) {
                pos = dl.getBreakPosition(clipTop, clipBottom)
                if (pos < clipBottom)
                    break
            }
        } while (pos < clipBottom)


        clip.set(bounds.left, clipTop, bounds.right, clipBottom)

        // If the book is completely visible, then return the layout
        if (y >= 0 && bounds.bottom <= clipBottom + EPSILON)
            return this

        // Not completely visible. We need to make sure fields are clipped
        // on proper internal boundaries - e.g. lines for text
        // The top clip is set to include lines on the boundary
        clip.top = columns.minOf {
            if (it.verticalClip(clipTop, clipBottom, false))
                it.clip.top + it.position.y
            else
                bounds.bottom
        }

        // The bottom clip is set to exclude lines on the boundary
        clip.bottom = columns.maxOf {
            if (it.verticalClip(clip.top, clipBottom, true))
                it.clip.bottom + it.position.y
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
                else
                    l.getVerticalLineBounds()
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
                if (visibleCount <= printer.orphans) {
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
            if (nextCount <= 0 || nextCount > printer.orphans)
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
    suspend fun setContent(book: BookAndAuthors) {
        // Set the content of each field
        for (d in columns)
            d.setContent(book)
    }

    /**
     * Draw the book on a canvas
     * @param canvas The canvas
     */
    suspend fun draw(canvas: Canvas) {
        // Draw each field
        for (dl in columns)
            dl.draw(canvas)
    }

    /** Object to handle the bounds of the entire layout */
    private inner class BookLayoutBounds: LayoutBounds {
        /** @inheritDoc */
        override val position: PointF = PointF(0.0f, 0.0f)
        /** @inheritDoc */
        override val bounds: RectF = this@BookLayout.bounds
        /** @inheritDoc */
        override val marginBounds: RectF = this@BookLayout.marginBounds
        /** @inheritDoc */
        override val baseline: Float = 0.0f
        /** @inheritDoc */
        override val rtl: Boolean
            get() = this@BookLayout.rtl
        /** @inheritDoc */
        override val alignment = HashMap<LayoutDescription.LayoutAlignmentType, LayoutAlignmentParameters>()
        /** @inheritDoc */
        override val overlapping: Set<LayoutBounds> = emptySet()
        /** @inheritDoc */
        override val overlaps: List<RectF> = emptyList()
        /** @inheritDoc */
        override val margins: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        /** @inheritDoc */
        override val description: LayoutDescription.FieldLayoutDescription<*>? = null
        /** @inheritDoc */
        override var width: Float
            get() = columnWidth
            set(@Suppress("UNUSED_PARAMETER")v) {}
        /** @inheritDoc */
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
            get() = bounds.position.x + if (bounds.rtl)
                bounds.bounds.right + bounds.margins.left
            else
                bounds.bounds.left - bounds.margins.left
    }

    /** Get the end of the field */
    class EndDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.position.x + if (bounds.rtl)
                bounds.bounds.left - bounds.margins.right
            else
                bounds.bounds.right + bounds.margins.right
    }

    /** Get the horizontal center of the field */
    class HCenterDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.position.x + (bounds.bounds.left + bounds.bounds.right +
                        if (bounds.rtl)
                            bounds.margins.left - bounds.margins.right
                        else
                            bounds.margins.right - bounds.margins.left
                    ) / 2.0f
    }

    /** Get the top of the field */
    class TopDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.position.y + bounds.bounds.top - bounds.margins.top
    }

    /** Get the bottom of the field */
    class BottomDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.position.y + bounds.bounds.bottom + bounds.margins.bottom
    }

    /** Get the baseline of the field */
    class BaselineDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.position.y + bounds.bounds.top + bounds.baseline
    }

    /** Get the vertical center of the field */
    class VCenterDimension(override val bounds: LayoutBounds): LayoutDimension {
        override val dimension: Float
            get() = bounds.position.y + (bounds.bounds.top - bounds.margins.top
                + bounds.bounds.bottom + bounds.margins.bottom) / 2.0f
    }

    class LayoutAlignmentParameters(
        /** Dimensions used to calculate the alignment */
        val dimensions: Sequence<LayoutDimension>,
        /** Alignment description */
        private val description: LayoutDescription.LayoutAlignment
    ) {
        /**
         * Calculate the alignment
         * @param reverse True to reverse the alignment direction
         * @param baseline Offset from top to baseline
         * @return The alignment value
         */
        fun calculateAlignment(reverse: Boolean, baseline: Float): Float {
            val min = dimensions.minOf { it.dimension }
            val max = dimensions.maxOf { it.dimension }
            var value = if (reverse)
                max + description.interpolate * (min - max) - description.offset
            else
                min + description.interpolate * (max - min) + description.offset
            if (description.baselineOffset)
                value -= baseline
            return value
        }
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
        override val description: LayoutDescription.FieldLayoutDescription<*>,
        /** The maximum width of the field */
        override var width: Float
    ): LayoutBounds {
        /** The position of the field in the book layout */
        override val position: PointF = PointF(0.0f, 0.0f)
        /** The bounds of the field relative to the position */
        override val bounds: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        /** The bounds of the field/parent including margins */
        override val marginBounds: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        /** Height of the field */
        override var height: Float = MAX_HEIGHT
        /** Current clip rectangle, relative to the position */
        var clip: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        /** Baseline location of the first line */
        override var baseline: Float = 0.0f
        /** True means text is right to left */
        override var rtl: Boolean = false
        /** alignment criteria for the field */
        override lateinit var alignment: Map<LayoutDescription.LayoutAlignmentType, LayoutAlignmentParameters>
        /** Layouts we need to check that may overlap this one */
        override lateinit var overlapping: Set<LayoutBounds>
        /** List of overlapping rectangles */
        override var overlaps: List<RectF> = emptyList()
        /** The field margins */
        override val margins: RectF
            get() = description.margin

        /**
         * Draw the layout
         * @param canvas The canvas to use
         */
        abstract suspend fun draw(canvas: Canvas)

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
         * Get the position where this field wants to break the layout
         * @param y The position of the book layout
         * @param columnHeight The height of the column
         * @return The position
         * Allows a field that cannot be moved to the next column to break above itself
         */
        open fun getBreakPosition(y: Float, columnHeight: Float): Float {
            return columnHeight
        }

        /**
         * Return a sequence of vertical line bounds for a field
         * @return the sequence or null if the field doesn't contain lines
         */
        open fun getVerticalLineBounds(): Sequence<Pair<Float, Float>>? {
            return null
        }

        /**
         * Set the content of the layout from a book
         * @param book The book to set the content from
         */
        abstract suspend fun setContent(book: BookAndAuthors)

        /**
         * Clip vertical bounds by y and columnHeight
         * @param y Top of the clip region
         * @param columnHeight Bottom of the clip region
         * @return True if the clipped field is not empty
         */
        protected fun clip(y: Float, columnHeight: Float): Boolean {
            // Calculate the clip to the column height
            clip.set(bounds)
            clip.top = clip.top.coerceAtLeast(y - position.y)
            clip.bottom = clip.bottom.coerceAtMost(columnHeight - position.y)
            return !clip.isEmpty
        }
    }

    /**
     * Class for static text
     * @param printer The PDFPrinter we are using. Several attributes are kept there
     * @param width Maximum width of the field
     * @param textDescription The description of the field layout
     * @param paint The paint used to draw the contents
     */
    open class TextLayout(
        /** The printer */
        printer: PDFPrinter,
        /** Maximum width of the field */
        width: Float,
        /** The field description */
        protected val textDescription: LayoutDescription.FieldLayoutDescription<String>,
        /** The paint used to draw the text */
        private var paint: TextPaint = printer.basePaint
    ): DrawLayout(printer, textDescription, width) {
        protected var text: String = ""
        protected inner class LayoutSpan(
            val position: PointF,
            val layout: StaticLayout
        ) {
            val bounds: RectF = RectF().apply {
                // Get the length of the longest line
                val width = printer.horizontalPixelsToPoints(ceil((0 until layout.lineCount).maxOf { layout.getLineMax(it) }).toInt())
                // Calculate the left and right positions from the rtl flag
                val left: Float
                val right: Float
                if (rtl) {
                    // Right to left, right is the layout width
                    right = printer.horizontalPixelsToPoints(layout.width) + position.x
                    // left is the right position minus the length of longest line
                    left = right - width
                } else {
                    // Left to right, left is the layout position
                    left = position.x
                    // Right is the left plus the length of longest line
                    right = left + width
                }
                set(left, position.y, right, printer.verticalPixelsToPoints(layout.height) + position.y)
            }

            @Suppress("unused")
            fun move(x: Float, y: Float) {
                bounds.offset(x - position.x, y - position.y)
                position.set(x, y)
            }
        }
        /** Create list to hold the static layouts for the field */
        private val textSpans: ArrayList<LayoutSpan> = ArrayList()

        /** @inheritDoc */
        override suspend fun draw(canvas: Canvas) {
            var lastX = 0.0f
            var lastY = 0.0f
            if (!clip.isEmpty) {
                canvas.withTranslation(position.x, position.y) {
                    clipRect(clip)
                    //canvas.drawColor(0x400000ff)  // Color clip region for debugging
                    for (span in textSpans) {
                        translate(span.position.x - lastX, span.position.y - lastY)
                        lastX = span.position.x
                        lastY = span.position.y
                        span.layout.draw(this)
                    }
                }
            }
        }

        /**
         * Create the StaticLayout from the current values
         */
        protected fun createInitialSpans() {
            // Clear the spans
            textSpans.clear()
            // Add the initial span to the list of spans
            createInitialLayout()?.let { textSpans.add(LayoutSpan(PointF(0.0f, 0.0f), it)) }
            // Clear the overlaps because the current spans are for no overlaps
            if (overlaps.isNotEmpty())
                overlaps = emptyList()
        }

        /**
         * Create the StaticLayout from the current values
         */
        private fun createInitialLayout(): StaticLayout? {
            // Don't create anything if the width is too small
            if (width <= MIN_WIDTH)
                return null

            // Build the static layout
            var layout = StaticLayout.Builder.obtain(
                text, 0, text.length, paint,
                printer.pointsToHorizontalPixels(width)
            ).build()
            // If the field is vertically constrained, then constrain the layout
            if (layout.height > height) {
                // Calculate how many lines we need to be smaller than height
                var lines = layout.getLineForVertical(printer.pointsToVerticalPixels(height))
                if (printer.verticalPixelsToPoints(layout.getLineTop(lines)) > height)
                    --lines
                layout = (
                    // If we can have some lines, then limit the layout. Otherwise
                    // make the layout an empty string.
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
            // Return the layout
            return layout
        }

        /** @inheritDoc */
        override var width: Float
            get() = super.width
            set(v) {
                super.width.let {
                    super.width = v
                    // Create a new StaticLayout
                    if (it != v)
                        createInitialSpans()
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
                        createInitialSpans()
                }
            }

        /** @inheritDoc */
        override fun getVerticalLineBounds(): Sequence<Pair<Float, Float>>? {
            return textSpans.asSequence().map { span ->
                val spanLayout = span.layout
                (0 until spanLayout.lineCount).asSequence()
                    .map {
                        Pair(
                            position.y + span.position.y + printer.verticalPixelsToPoints(spanLayout.getLineTop(it)),
                            position.y + span.position.y + printer.verticalPixelsToPoints(spanLayout.getLineTop(it + 1))
                        )
                    }
            }.flatten()
        }

        /**
         * Calculate the overlapping rectangles
         * @return List of rectangles in the field covered by another fields
         */
        private fun getOverlaps(): Boolean {
            // Set the margins for the overlaps
            val leftMargin = if (rtl) margins.right else margins.left
            val topMargin = margins.top
            val rightMargin = if (rtl) margins.left else margins.right
            val bottomMargin = margins.bottom

            // Get a list of overlapping fields and sort it vertically
            val overlapped = overlapping.fold(ArrayList<RectF>()) {list, item ->
                // If the overlapping field is empty ignore it
                if (!item.marginBounds.isEmpty) {
                    val dx = item.position.x - position.x
                    val dy = item.position.y - position.y
                    val left = if (item.rtl) item.marginBounds.left else 0.0f
                    val right = if (item.rtl) item.width else item.marginBounds.right
                    // translate the other field bounds relative to this fields position
                    val intersection = RectF(
                        left + dx - rightMargin, item.marginBounds.top + dy - bottomMargin,
                        right + leftMargin + dx, item.marginBounds.bottom + dy + topMargin
                    )
                    // If they intersect, add the intersection
                    if (intersection.intersect(0.0f, 0.0f, width, height)) {
                        // Ignore very small overlaps
                        if (intersection.width() >= EPSILON && intersection.height() >= EPSILON)
                            list.add(intersection)
                    }
                }
                list
            }

            if (overlapped == overlaps)
                return false
            overlaps = overlapped
            return true
        }

        /**
         * Subtract the overlapping fields from this field
         * @return Horizontal stripes that are not overlapping sorted from top to bottom and right to left
         */
        private fun createStripes(): Sequence<RectF> {
            // Sort from top to bottom then start to end
            val compare = compareBy<RectF>({ it.top }, { it.left }, { it.right })
            val stripes = TreeSet(compare)
            // Break all of the overlapping rectangles base on where they are cut by tops and bottoms
            for (rect in overlaps) {
                // First use rect to split any rectangles already in the set
                // along its top and bottom
                for (other in stripes) {
                    // We only need to split other if ot overlaps rect vertically
                    // and other isn't contained in rect
                    if ((rect.top > other.top && rect.top < other.bottom) ||
                        (rect.bottom > other.top && rect.bottom < other.bottom)) {
                        // Remove the rectangle we are splitting
                        stripes.remove(other)
                        // Start splitting at the top
                        var top = other.top
                        // If rect.top > top, then split at rect.top
                        if (rect.top > top) {
                            stripes.add(RectF(other.left, top, other.right, rect.top))
                            top = rect.top
                        }
                        // If rect.bottom < other.bottom, then split at rect.bottom
                        if (rect.bottom < other.bottom) {
                            stripes.add(RectF(other.left, top, other.right, rect.bottom))
                            top = rect.bottom
                        }
                        // Add in the portion at the bottom of other
                        stripes.add(RectF(other.left, top, other.right, other.bottom))
                    }
                }

                // Now split rect along tops and bottoms of rectangles in the set
                var top = rect.top
                for (other in stripes) {
                    // Skip rectangles until we get to one that might overlap
                    if (other.bottom >= top) {
                        // We can stop once we get past the bottom of rect.
                        if (other.top >= rect.bottom)
                            break
                        // Split at other top, if it is inside rect
                        if (other.top > top) {
                            stripes.add(RectF(rect.left, top, rect.right, other.top))
                            top = other.top
                        }
                        // Split at other bottom, if it is inside rect
                        if (other.bottom < rect.bottom) {
                            stripes.add(RectF(rect.left, top, rect.right, other.bottom))
                            top = other.bottom
                        }
                    }
                }
                // Add bottom of rect
                stripes.add(RectF(rect.left, top, rect.right, rect.bottom))
            }

            return sequence {
                // The stripe we are working on
                var top = 0.0f
                var left = 0.0f
                var right = width
                var bottom = 0.0f
                var gapLeft = width
                for (rect in stripes) {
                    // Break if we are done
                    if (rect.top >= height)
                        break
                    // If we are done with this stripe
                    if (rect.bottom > bottom) {
                        // If top >= bottom or right <= left, then the stripe is empty
                        // don't output anything. If gap < width, then the stripe
                        // has multiple segments and we don't want to output anything, then
                        if (top < bottom && right > left && gapLeft >= width)
                            yield(RectF(left, top, right, bottom))

                        // Start the next stripe
                        top = bottom
                        bottom = rect.bottom
                        // if left edge of rect is not at the left edge of the field
                        // Then the stripe starts at the left edge and goes to the
                        // left edge of rect.
                        if (rect.left > EPSILON) {
                            left = 0.0f
                            right = rect.left
                            gapLeft = rect.right
                        } else {
                            // Otherwise it starts at rect right and goes
                            // to the right edge of the fields
                            left = rect.right
                            right = width
                            gapLeft = width
                        }
                    }

                    // If there is a vertical gap, output the stripe in the gap
                    if (rect.top > top) {
                        yield(RectF(0.0f, top, width, rect.top))
                        top = rect.top
                    }

                    // If there is a horizontal gap, then skip the stripe completely
                    when {
                        rect.left > gapLeft -> top = bottom
                        rect.left <= left -> left = rect.right
                        else -> {
                            right = rect.right
                            gapLeft = rect.right
                        }
                    }
                }
                // If we have a stripe ready then output it
                if (top < bottom && right > left && gapLeft >= width)
                    yield(RectF(left, top, right, bottom))
                // If we run out of stripes, then output the bottom
                if (bottom < height)
                    yield(RectF(0.0f, bottom, width, height))
            }
        }

        /** @inheritDoc */
        override fun wrapOverlapping(): Boolean {
            // If this field doesn't test for overlaps, or the overlaps haven't changed,
            // or the width is smaller than minimum, then return false
            if (overlapping.isEmpty() || !getOverlaps() || width < MIN_WIDTH)
                return false

            // Clear existing spans
            textSpans.clear()
            // Get the overlapping fields
            val stripes = createStripes()

            var left = 0.0f         // Left side of current stripe
            var top = 0.0f          // Top of current stripe
            var right = width       // Right side of current stripe
            var bottom = 0.0f       // Bottom of current stripe
            var start = 0           // Start in text string of current layout
            var layout: StaticLayout? = null    // The current layout

            /**
             * Create a layout based on the current variables
             * @return The layout or null if we are at the end of the text string
             */
            fun buildLayout(): StaticLayout? {
                // If we are at the end of the text string, return null
                if (start >= text.length)
                    return null
                // Return the layout
                return StaticLayout.Builder.obtain(text, start, text.length, paint,
                    printer.pointsToHorizontalPixels(right - left)
                ).build().also {
                    // Set the new layout in the layout variable
                    layout = it
                }
            }

            /**
             * Add a span to textSpans
             */
            fun addSpan(bottomLine: Int): LayoutSpan? {
                // Don't add a span if layout is null
                return layout?.let { l ->
                    // Get the end character offset of the bottom line
                    var end = l.getLineEnd(bottomLine)
                    // Create a layout with from start to end
                    val spanLayout = StaticLayout.Builder.obtain(text, start, end, paint, l.width).build()
                    val span = LayoutSpan(PointF(left, top), spanLayout)
                    // Add a span with the layout
                    textSpans.add(span)
                    // Skip whitespace, if any
                    while (end < text.length && text[end].isWhitespace())
                        ++end
                    // Set the start character of the next span
                    start = end
                    // Clear the layout
                    layout = null
                    span
                }
            }

            // Loop over the stripes, accumulate them and add spans to fill them
            outer@ for (stripe in stripes) {
                // If there is a gap in the stripes, then start at this stripe
                if (stripe.top > bottom + EPSILON) {
                    left = stripe.left
                    right = stripe.right
                    top = stripe.top
                } else {
                    // Accumulate stripe.
                    left = left.coerceAtLeast(stripe.left)
                    right = right.coerceAtMost(stripe.right)
                }
                bottom = stripe.bottom

                // If the width get less than the minimum, then skip the accumulated
                // stripes and start over
                if (right - left < MIN_WIDTH) {
                    // Start over at the bottom of this stripe
                    left = 0.0f
                    right = width
                    top = stripe.bottom
                    continue
                }

                while (true) {
                    // Create a layout for the rest of the text, break when we are at the end
                    val l = if (printer.pointsToHorizontalPixels(right - left) != layout?.width)
                        buildLayout() ?: break@outer
                    else
                        layout ?: buildLayout() ?: break@outer

                    // Continue collecting stripes until we have enough space for at least one line
                    if (printer.verticalPixelsToPoints(l.getLineBottom(0)) > bottom - top)
                        break

                    // We have one or more lines, add some spans
                    val span = if (left > stripe.left || right < stripe.right) {
                        // This stripe is bigger than the accumulated stripes
                        // Output the single line at the top and try to
                        // output more spans using the larger stripe
                        addSpan(0)
                    } else {
                        // Output as many spans as possible
                        // Find the line at the bottom of the accumulated stripes
                        var bottomLine = l.getLineForVertical(printer.pointsToVerticalPixels(bottom - top))
                        if (printer.verticalPixelsToPoints(l.getLineBottom(bottomLine)) > bottom - top + EPSILON)
                            --bottomLine
                        addSpan(bottomLine)
                    }
                    val lineBottom = span?.bounds?.bottom?: 0.0f
                    // If the line is at the bottom of the stripe, Then we are done with this stripe
                    if (lineBottom >= bottom - EPSILON) {
                        // Expand to full column for next stripe
                        left = 0.0f
                        right = width
                        top = bottom
                        break
                    }
                    // Try to add another span in the stripe
                    left = stripe.left
                    right = stripe.right
                    top = lineBottom - printer.verticalPixelsToPoints(
                        span?.let { it.layout.bottomPadding - it.layout.topPadding }?: 0
                    )
                }
            }

            // Return true if the bounds have changed
            calculateBounds()
            marginBounds.set(
                bounds.left - if (rtl) margins.right else margins.left,
                bounds.top - margins.top,
                bounds.right + if (rtl) margins.left else margins.right,
                bounds.bottom + margins.bottom
            )
            return true
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
            // If there are no spans, then return 0
            if (textSpans.isEmpty())
                return 0.0f
            // Get the span with the boundary line
            val span = textSpans.indexOfFirst { boundary <= it.position.y }.let {
                if (it == -1)
                    textSpans.last()
                else
                    textSpans[it]
            }
            // Find the line at the boundary or before it
            val localBoundary = boundary - span.position.y
            val line = span.layout.getLineForVertical(printer.pointsToVerticalPixels(localBoundary))
            // Get the top and bottom of the line
            val t = printer.verticalPixelsToPoints(span.layout.getLineTop(line))
            val b = printer.verticalPixelsToPoints(span.layout.getLineTop(line + 1))
            return span.position.y + when {
                // Either the top of the line is on the boundary, or the top line
                // is below the boundary, so return the top of the line
                t >= localBoundary -> t
                // Either the bottom of the line is on the boundary, or the bottom line
                // is above the boundary, so return the bottom of the line
                b <= localBoundary -> b
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
            val lineCount = textSpans.sumOf { it.layout.lineCount }
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

        /** @inheritDoc */
        override suspend fun setContent(book: BookAndAuthors) {
            val value = textDescription.getContent(book)
            if (value != text || textSpans.isEmpty()) {
                text = value
                createInitialSpans()
            }
            setContent()
        }

        /**
         * Recalculate the bounding box from the current spans
         */
        private fun calculateBounds() {
            // Start with empty box
            bounds.setEmpty()
            // Union in the bounds for each span
            textSpans.fold(bounds) {bounds, span -> bounds.union(span.bounds); bounds }
        }

        /**
         * Set the bounds of the field from the content
         */
        fun setContent() {
            // The text is static, but reset the bounding rectangle of the field
            calculateBounds()
            // Set the baseline, and set the bounds empty if there are no lines
            baseline = if (!bounds.isEmpty && textSpans.isNotEmpty())
                printer.verticalPixelsToPoints(textSpans.first().layout.getLineBaseline(0))
            else {
                bounds.set(0.0f, 0.0f, 0.0f, 0.0f)
                0.0f
            }
        }
    }

    /**
     * Class for a field with a book database value
     * @param printer The PDFPrinter we are using. Several attributes are kept there
     * @param textDescription The description of the field layout
     */
    open class ColumnTextLayout(
        /** The printer */
        printer: PDFPrinter,
        /** Maximum width of the field */
        width: Float,
        /** The field description */
        textDescription: LayoutDescription.FieldLayoutDescription<String>,
        /** The paint used to draw the text */
        paint: TextPaint = printer.basePaint
    ): TextLayout(printer, width, textDescription, paint) {
        /** @inheritDoc */
        override suspend fun setContent(book: BookAndAuthors) {
            // Get the column value
            text = textDescription.getContent(book)
            // Create the StaticLayout
            createInitialSpans()
            // Fill in the bounds and baseline of the field
            super.setContent()
        }
    }

    open class EmptyLayout(
        /** The printer */
        printer: PDFPrinter,
        /** The field description */
        description: LayoutDescription.FieldLayoutDescription<*>
    ): DrawLayout(printer, description, 0.0f) {
        override suspend fun draw(canvas: Canvas) {
        }

        override fun verticalClip(y: Float, columnHeight: Float, exclusive: Boolean): Boolean {
            return false
        }

        override suspend fun setContent(book: BookAndAuthors) {
            bounds.set(0.0f, 0.0f, 0.0f, 0.0f)
            baseline = 0.0f
        }
    }

    /**
     * Class for a field with a book database value
     * @param printer The PDFPrinter we are using. Several attributes are kept there
     * @param bitmapDescription The description of the field layout
     * @param columnWidth The width of the print column
     * @param large True to display the large thumbnail
     * @param paint The paint used to draw the bitmap
     */
    open class ColumnBitmapLayout(
        /** The printer */
        printer: PDFPrinter,
        /** The field description */
        private val bitmapDescription: LayoutDescription.FieldLayoutDescription<Long>,
        /** The width of the print column */
        private val columnWidth: Float,
        /** True to show the large bitmap */
        val large: Boolean,
        /** The paint used to draw the bitmap */
        private val paint: TextPaint = printer.basePaint
    ): DrawLayout(printer, bitmapDescription, bitmapDescription.maxSize.x) {
        /** @inheritDoc */
        override var height: Float = bitmapDescription.maxSize.y
            set(v) {
                // Set the bounds bottom when the height changes
                if (v != field)
                    bounds.bottom = bounds.top + v
                field = v
            }
        /** @inheritDoc */
        override var width: Float
            get() = super.width
            set(v) {
                super.width.let {
                    super.width = v
                    // Set the bounds right when the width changes
                    if (v != it)
                        bounds.right = bounds.left + v
                }
            }
        /** The book id used to load the bitmap when drawing */
        var bookId: Long = 0
        /** The bitmap */
        var bitmap: Bitmap? = null

        /** @inheritDoc */
        override suspend fun draw(canvas: Canvas) {
            // If the bitmap us null, then load it.
            (bitmap?: printer.getThumbnail(bookId, large))?.let {bitmap ->
                this.bitmap = bitmap
                // Scale the bitmap to fit the bounds.
                val dst = RectF(0.0f, 0.0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                if (dst.right * bounds.height() > dst.bottom * bounds.width()) {
                    dst.bottom = dst.bottom * bounds.width() / dst.right
                    dst.right = bounds.width()
                } else {
                    dst.right = dst.right * bounds.height() / dst.bottom
                    dst.bottom = bounds.height()
                }
                // Center the bitmap in the bounds
                dst.offset(bounds.left + (bounds.width() - dst.right) / 2.0f,
                    bounds.top + (bounds.height() - dst.bottom) / 2.0f)
                // Draw the bitmap
                canvas.withTranslation(position.x, position.y) {
                    clipRect(clip)
                    drawBitmap(bitmap, null, dst, paint)
                }
            }
        }

        /** @inheritDoc */
        override suspend fun setContent(book: BookAndAuthors) {
            // Set the bookId and clear the bitmap
            bookId = bitmapDescription.getContent(book)
            bitmap = null
            // Set the bounds to the width and height.
            bounds.set(0.0f, 0.0f,
                width.coerceAtMost(columnWidth), height.coerceAtMost(printer.pageDrawBounds.height()))
            baseline = 0.0f
        }

        /** @inheritDoc */
        override fun verticalClip(y: Float, columnHeight: Float, exclusive: Boolean): Boolean {
            // If the bitmap isn't fully visible, then make the clip rectangle empty
            if (y > bounds.top + position.y || columnHeight < bounds.bottom + position.y) {
                clip.bottom = clip.top
                return false
            }
            return true
        }

        /** @inheritDoc */
        override fun getBreakPosition(y: Float, columnHeight: Float): Float {
            // If the bottom of the column overlaps the bitmap,
            // then force it to the top of the next column
            val top = bounds.top + position.y
            val bottom = bounds.bottom + position.y
            return if (y <= top && columnHeight < bottom)
                top
            else
                columnHeight
        }
    }
}
