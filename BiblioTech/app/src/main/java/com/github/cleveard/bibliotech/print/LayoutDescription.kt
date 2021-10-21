package com.github.cleveard.bibliotech.print

import android.graphics.PointF
import android.graphics.RectF
import android.text.DynamicLayout
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import com.github.cleveard.bibliotech.db.Column
import java.util.*
import kotlin.collections.HashMap

/**
 * Class to hold the description of a book layout
 * @param inColumns The list of field descriptions for the layout
 * @param inHeaders The list of header fields for the layout. Null means no headers
 * @param horizontalSeparation The distance to separate columns
 * @param verticalSeparation The distance to separate books in the same column
 */
data class LayoutDescription(
    /** Layouts for the columns of the book to print */
    val inColumns: List<FieldLayoutDescription>,
    /** Columns to print as book header. Set to null to leave out the header */
    val inHeaders: List<Column>?,
    /** Distance in points to separate the books horizontally */
    val horizontalSeparation: Float,
    /** Distance in points to separate print columns vertically */
    val verticalSeparation: Float
) {
    /**
     *  Create the book layout from this description
     *  @param printer The printer being used
     *  @param columnWidth The width of a column
     *  @param rtl True to create a layout for right-to-left
     */
    fun createLayout(printer: PDFPrinter, columnWidth: Float, rtl: Boolean): BookLayout {
        // Create the book layout
        val bookLayout = BookLayout(
            // The printer
            printer,
            // The layout description
            this,
            // The fields in the layout
            inColumns.map { it.createLayout(printer, columnWidth) },
            // The column width
            columnWidth,
            // The bounds rectangle excluding margins
            RectF(0.0f, 0.0f, 0.0f, 0.0f),
            // The bounds rectangle including margins
            RectF(0.0f, 0.0f, 0.0f, 0.0f),
            // The clip rectangle
            RectF(0.0f, 0.0f, 0.0f, 0.0f),
            // Right-to-left flag
            rtl
        )

        // Map to map field descriptor to a field
        val map = HashMap<FieldLayoutDescription?, BookLayout.LayoutBounds>()
        // Null maps to the layout bounds for the entire layout
        map[null] = bookLayout.layoutBounds
        // Fill in the map for the fields in the layout
        for (dl in bookLayout.columns)
            map[dl.description] = dl

        // Create the layout alignment dependencies in the layout
        for (dl in bookLayout.columns) {
            // Get the alignment for the field in a local variable
            val align = dl.alignment
            // For each horizontal layout description
            for (entry in dl.description.horizontalLayout) {
                // Take the alignment dependencies
                entry.value.asSequence().map {
                    // A map them to a layout dimension on the field
                    map[it.alignTo]?.let {b -> it.align.createAlignment(b) }
                }.filterNotNull().toList().also {list ->
                    // If the list isn't empty add it to the field alignment
                    if (list.isNotEmpty())
                        align[entry.key] = list
                }
            }

            // For each vertical layout description
            for (entry in dl.description.verticalLayout) {
                // Take the alignment dependencies
                entry.value.asSequence().map {
                    // A map them to a layout dimension on the field
                    map[it.alignTo]?.let {b -> it.align.createAlignment(b) }
                }.filterNotNull().toList().also {list ->
                    // If the list isn't empty add it to the field alignment
                    if (list.isNotEmpty())
                        align[entry.key] = list
                }
            }
        }

        // return the layout
        return bookLayout
    }

    /** Interface for alignment types */
    interface LayoutAlignmentType {
        /**
         * Create an object to return a fields current dimension
         * @param bounds The layout bounds for the field
         */
        fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension

        /**
         * Calculate the alignment for a dimensions type
         * @param target The object where the result is stored
         * @param sources The dimensions used to calculate the alignment
         */
        fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>)
    }

    /** Interface to an alignment description */
    interface LayoutAlignmentDescription {
        /**
         * Create an object to return a fields current dimension for this description
         * @param bounds The layout bounds for the field
         */
        fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension
    }

    /**
     * Object to collect alignment information for a field
     * @param baseline The offset of the baseline from the top of the field
     * @param rtl True for right-to-left layouts
     * @param top The resolved top alignment. Null means it wasn't specified
     * @param left The resolved left alignment. Null means it wasn't specified
     * @param right The resolved right alignment. Null means it wasn't specified
     * @param bottom The resolved bottom alignment. Null means it wasn't specified
     */
    data class AlignmentTarget(
        /** The offset of the baseline from the top of the field */
        var baseline: Float = 0.0f,
        /** True for right-to-left layouts */
        var rtl: Boolean = false,
        /** The resolved top alignment. Null means it wasn't specified */
        var top: Float? = null,
        /** The resolved left alignment. Null means it wasn't specified */
        var left: Float? = null,
        /** The resolved right alignment. Null means it wasn't specified */
        var right: Float? = null,
        /** The resolved bottom alignment. Null means it wasn't specified */
        var bottom: Float? = null
    ) {
        /**
         * Clear the collected result
         * @param baseline New baseline offset
         * @param rtl New right-to-left flag
         */
        fun clear(baseline: Float = 0.0f, rtl: Boolean = false) {
            this.baseline = baseline
            this.rtl = rtl
            top = null
            left = null
            right = null
            bottom = null
        }
    }

    /**
     * Vertical alignment description for layout
     * @param align The dimension we want to align to
     * @param alignTo The field we want to align to
     */
    data class VerticalLayoutAlignment(
        /** The alignmentType */
        val align: Type,
        /** Field aligned to */
        val alignTo: FieldLayoutDescription?
    ): LayoutAlignmentDescription {
        /** The different vertical dimensions we can align to */
        enum class Type: LayoutAlignmentType {
            /** Align to top */
            Top {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a top dimension
                    return BookLayout.TopDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // Align the top to the max of the source dimensions
                    target.top = sources.maxOf { it.dimension }
                }
            },
            /** Align to bottom */
            Bottom {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a bottom dimension
                    return BookLayout.BottomDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // Align the bottom to the min of the source dimensions
                    target.bottom = sources.minOf { it.dimension }
                }
            },
            /** Align to baseline */
            BaseLine {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a baseline dimension
                    return BookLayout.BaselineDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // Align the top to the max of the source dimensions offset by the baseline
                    target.top = sources.maxOf { it.dimension } - target.baseline
                }
            },
            /** Align to center */
            Center {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a center dimension
                    return BookLayout.VCenterDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // Center aligns the top to the min of the dimensions and the bottom to the max
                    // This will align the field to the center of the vertical bounds of the fields
                    data class Center(var min: Float = Float.POSITIVE_INFINITY, var max: Float = Float.NEGATIVE_INFINITY)
                    return sources.fold(Center()) { acc, layoutDimension ->
                        acc.min = acc.min.coerceAtMost(layoutDimension.dimension)
                        acc.max = acc.max.coerceAtLeast(layoutDimension.dimension)
                        acc
                    }.let {
                        target.top = it.min
                        target.bottom = it.max
                    }
                }
            };
        }

        /** @inheritDoc */
        override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
            // Create the dimension object for the alignment in this description
            return align.createAlignment(bounds)
        }
    }

    /**
     * Horizontal alignment description for layout
     * @param align The dimension we want to align to
     * @param alignTo The field we want to align to
     */
    data class HorizontalLayoutAlignment(
        /** The alignmentType */
        val align: Type,
        /** Field aligned to */
        val alignTo: FieldLayoutDescription?
    ): LayoutAlignmentDescription {
        /** The different horizontal dimensions we can align to */
        enum class Type: LayoutAlignmentType {
            /** Align to start */
            Start {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a start dimension
                    return BookLayout.StartDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // If right-to-left, align right to min of dimensions
                    // Otherwise align left to max of dimensions
                    return if (target.rtl)
                        target.right = sources.minOf { it.dimension }
                    else
                        target.left = sources.maxOf { it.dimension }
                }
            },
            /** Align to end */
            End {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return an end dimension
                    return BookLayout.EndDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // If right-to-left, align left to max of dimensions
                    // Otherwise align right to min of dimensions
                    return if (target.rtl)
                        target.left = sources.maxOf { it.dimension }
                    else
                        target.right = sources.minOf { it.dimension }
                }
            },
            /** Align to center */
            Center {
                /** @inheritDoc */
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    // Return a center dimension
                    return BookLayout.HCenterDimension(bounds)
                }

                /** @inheritDoc */
                override fun calculateAlignment(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    // Center aligns the left to the min of the dimensions and the right to the max
                    // This will align the field to the center of the horizontal bounds of the fields
                    data class Center(var min: Float = Float.POSITIVE_INFINITY, var max: Float = Float.NEGATIVE_INFINITY)
                    return sources.fold(Center()) { acc, layoutDimension ->
                        acc.min = acc.min.coerceAtMost(layoutDimension.dimension)
                        acc.max = acc.max.coerceAtLeast(layoutDimension.dimension)
                        acc
                    }.let {
                        target.left = it.min
                        target.right = it.max
                    }
                }
            }
        }

        /** @inheritDoc */
        override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
            // Create the dimension object for the alignment in this description
            return align.createAlignment(bounds)
        }
    }

    /**
     * Layout description for a column from the database
     * @param margin Additional padding in points
     * @param minSize The minimum size in points used by the column
     * @param maxSize The maximum size in points used by the column
     * @param horizontalLayout The horizontal layout for the column
     * @param verticalLayout The vertical layout for the column
     */
    abstract class FieldLayoutDescription(
        /** Additional padding in points */
        val margin: RectF = RectF(0.0f, 0.0f, 0.0f, 0.0f),
        /** The minimum size in points used by the column */
        val minSize: PointF = PointF(0.0f, 0.0f),
        /** The maximum size in points used by the column */
        val maxSize: PointF = PointF(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        /**
         * The horizontal layout for the column
         * The map key is the dimension of column we are aligning
         * The map value contains the dimensions of other columns it is aligned to
         * The value we align to depends on the key:
         *    Top - align to maximum
         *    Bottom - align to minimum
         *    Baseline - align to maximum
         *    Center - align to midpoint of minimum and maximum
         */
        val horizontalLayout: MutableMap<HorizontalLayoutAlignment.Type, List<HorizontalLayoutAlignment>> = EnumMap(HorizontalLayoutAlignment.Type::class.java),
        /**
         * The vertical layout for the column
         * The map key is the dimension of column we are aligning
         * The map value contains the dimensions of other columns it is aligned to
         * The value we align to depends on the key and left-to-right setting:
         *    Start, LTR - align to maximum
         *    Start, RTL - align to minimum
         *    End, LTR - align to minimum
         *    End, RTL - align to maximum
         *    Center - align to midpoint of minimum and maximum
         */
        val verticalLayout: MutableMap<VerticalLayoutAlignment.Type, List<VerticalLayoutAlignment>> = EnumMap(VerticalLayoutAlignment.Type::class.java)
    ) {
        /**
         * Get/Fill the layout for a field
         * @param printer The printer we are using
         * @param columnWidth The width of a column
         */
        abstract fun createLayout(printer: PDFPrinter, columnWidth: Float): BookLayout.DrawLayout
    }

    /**
     * Static text field
     * @param text The static text to display
     */
    class TextFieldLayoutDescription(private val text: String): FieldLayoutDescription() {
        /** @inheritDoc */
        override fun createLayout(printer: PDFPrinter, columnWidth: Float): BookLayout.DrawLayout {
            // Create a StaticLayout to format the text
            val textLayout = StaticLayout.Builder.obtain(
                text, 0, text.length, printer.basePaint,
                printer.pointsToHorizontalPixels(columnWidth)
            )
                .build()
            // Return a text field with the StaticLayout
            return BookLayout.TextLayout(printer, this, textLayout)
        }
    }

    /**
     * Text field from a book database column
     * @param column The database column description
     */
    class ColumnFieldLayoutDescription(private val column: Column): FieldLayoutDescription() {
        /** @inheritDoc */
        override fun createLayout(printer: PDFPrinter, columnWidth: Float): BookLayout.DrawLayout {
            // Create a DynamicLayout with a spannable string builder we can use to update the text
            val content = SpannableStringBuilder()
            // DynamicLayout.Builder was introduced in API 28
            val textLayout = if (android.os.Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                (DynamicLayout(
                    content, printer.basePaint, printer.pointsToHorizontalPixels(columnWidth),
                    Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true
                ))
            } else {
                DynamicLayout.Builder.obtain(content, printer.basePaint, printer.pointsToHorizontalPixels(columnWidth))
                    .build()
            }
            // Create the field with the column description, the content holder and the DynamicLayout
            return BookLayout.ColumnLayout(printer, this, column, content, textLayout)
        }
    }
}