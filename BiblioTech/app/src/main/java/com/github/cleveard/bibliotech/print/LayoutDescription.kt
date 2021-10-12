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

/** Class to hold the description of a book layout */
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
     */
    fun createLayout(printer: PDFPrinter, columnWidth: Float, rtl: Boolean): BookLayout {
        val bookLayout = BookLayout(
            printer,
            this,
            inColumns.map { it.createLayout(printer, columnWidth) },
            columnWidth,
            RectF(0.0f, 0.0f, 0.0f, 0.0f),
            RectF(0.0f, 0.0f, 0.0f, 0.0f),
            RectF(0.0f, 0.0f, 0.0f, 0.0f),
            rtl
        )
        
        val map = HashMap<FieldLayoutDescription?, BookLayout.LayoutBounds>()
        map[null] = bookLayout.layoutBounds
        for (dl in bookLayout.columns)
            map[dl.description] = dl

        for (dl in bookLayout.columns) {
            val align = dl.alignment
            for (entry in dl.description.horizontalLayout) {
                entry.value.asSequence().map {
                    map[it.alignTo]?.let {b -> it.align.createAlignment(b) }
                }.filterNotNull().toList().also {list ->
                    if (!list.isEmpty())
                        align[entry.key] = list
                }
            }
            for (entry in dl.description.verticalLayout) {
                entry.value.asSequence().map {
                    map[it.alignTo]?.let {b -> it.align.createAlignment(b) }
                }.filterNotNull().toList().also {list ->
                    if (!list.isEmpty())
                        align[entry.key] = list
                }
            }
        }

        return bookLayout
    }

    interface LayoutAlignmentType {
        fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension
        fun calculateAlignemt(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>)
    }

    interface LayoutAlignmentDescription {
        fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension
    }

    data class AlignmentTarget(
        var baseline: Float = 0.0f,
        var rtl: Boolean = false,
        var top: Float? = null,
        var left: Float? = null,
        var right: Float? = null,
        var bottom: Float? = null
    ) {
        fun clear(baseline: Float = 0.0f, rtl: Boolean = false) {
            this.baseline = baseline
            this.rtl = rtl
            top = null
            left = null
            right = null
            bottom = null
        }
    }

    /** Vertical alignment description for layout */
    data class VerticalLayoutAlignment(
        /** The alignmentType */
        val align: Type,
        /** Columns aligned to */
        val alignTo: FieldLayoutDescription?
    ): LayoutAlignmentDescription {
        enum class Type: LayoutAlignmentType {
            /** Align top */
            Top {
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    return BookLayout.TopDimension(bounds)
                }

                override fun calculateAlignemt(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    target.top = sources.maxOf { it.dimension }
                }
            },
            /** Align bottom */
            Bottom {
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    return BookLayout.BottomDimension(bounds)
                }

                override fun calculateAlignemt(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    target.bottom = sources.minOf { it.dimension }
                }
            },
            /** Align baseline */
            BaseLine {
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    return BookLayout.BaselineDimension(bounds)
                }

                override fun calculateAlignemt(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    target.baseline = sources.maxOf { it.dimension }
                }
            },
            /** Align center */
            Center {
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    return BookLayout.VCenterDimension(bounds)
                }

                override fun calculateAlignemt(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
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

        override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
            return align.createAlignment(bounds)
        }
    }

    /** Horizontal alignment description for layout */
    data class HorizontalLayoutAlignment(
        /** The alignmentType */
        val align: Type,
        /** Columns aligned to */
        val alignTo: FieldLayoutDescription?
    ): LayoutAlignmentDescription {
        enum class Type: LayoutAlignmentType {
            /** Align start */
            Start {
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    return BookLayout.StartDimension(bounds)
                }

                override fun calculateAlignemt(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    return if (target.rtl)
                        target.right = sources.minOf { it.dimension }
                    else
                        target.left = sources.maxOf { it.dimension }
                }
            },
            /** Align end */
            End {
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    return BookLayout.EndDimension(bounds)
                }

                override fun calculateAlignemt(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
                    return if (target.rtl)
                        target.left = sources.maxOf { it.dimension }
                    else
                        target.right = sources.minOf { it.dimension }
                }
            },
            /** Align center */
            Center {
                override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
                    return BookLayout.HCenterDimension(bounds)
                }

                override fun calculateAlignemt(target: AlignmentTarget, sources: Sequence<BookLayout.LayoutDimension>) {
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

        override fun createAlignment(bounds: BookLayout.LayoutBounds): BookLayout.LayoutDimension {
            return align.createAlignment(bounds)
        }
    }

    /** Layout description for a column from the database */
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
         */
        abstract fun createLayout(printer: PDFPrinter, columnWidth: Float): BookLayout.DrawLayout
    }

    class TextFieldLayoutDescription(private val text: String): FieldLayoutDescription() {
        override fun createLayout(printer: PDFPrinter, columnWidth: Float): BookLayout.DrawLayout {
            val textLayout = StaticLayout.Builder.obtain(
                text, 0, text.length, printer.basePaint,
                printer.horizontalPointsToPixels(columnWidth)
            )
                .build()
            return BookLayout.TextLayout(printer, this, textLayout)
        }
    }

    class ColumnFieldLayoutDescription(private val column: Column): FieldLayoutDescription() {
        override fun createLayout(printer: PDFPrinter, columnWidth: Float): BookLayout.DrawLayout {
            val content = SpannableStringBuilder()
            val textLayout = if (android.os.Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                (DynamicLayout(
                    content, printer.basePaint, printer.horizontalPointsToPixels(columnWidth),
                    Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true
                ))
            } else {
                DynamicLayout.Builder.obtain(content, printer.basePaint, printer.horizontalPointsToPixels(columnWidth))
                    .build()
            }
            return BookLayout.ColumnLayout(printer, this, column, content, textLayout)
        }
    }
}