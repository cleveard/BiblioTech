package com.github.cleveard.bibliotech.ui.print

import android.content.Context
import android.content.res.Resources
import android.graphics.PointF
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.Column
import com.github.cleveard.bibliotech.print.LayoutDescription

class PrintLayouts(context: Context) {
    /**
     * Get a layout based on the column width
     * @param columnWidth The width of a print column
     */
    fun getLayout(columnWidth: Float): LayoutDescription {
        return (layouts.firstOrNull { columnWidth <= it.first }?: layouts.last()).second
    }

    companion object {
        private fun <T> MutableSet<T>.addAll(vararg elements: T) = addAll(elements)
        
        private fun MutableSet<LayoutDescription.LayoutAlignment>.addTopAlignment(
            vararg dimensions: LayoutDescription.VerticalLayoutDimension,
            offset: Float = 0.0f,
            interpolate: Float = 1.0f,
            baselineOffset: Boolean = false
            
        ) {
            // Add alignment from dimensions 
            this.add(LayoutDescription.VerticalLayoutAlignment(
                LayoutDescription.VerticalLayoutDimension.Type.Top, dimensions.toList(),
                interpolate, offset, baselineOffset)
            )
        }

        private fun MutableSet<LayoutDescription.LayoutAlignment>.addBaselineAlignment(
            vararg dimensions: LayoutDescription.VerticalLayoutDimension,
            offset: Float = 0.0f,
            interpolate: Float = 1.0f,
            baselineOffset: Boolean = true

        ) {
            // Add alignment from dimensions
            this.add(LayoutDescription.VerticalLayoutAlignment(
                LayoutDescription.VerticalLayoutDimension.Type.BaseLine, dimensions.toList(),
                interpolate, offset, baselineOffset)
            )
        }

        private fun MutableSet<LayoutDescription.LayoutAlignment>.addStartAlignment(
            vararg dimensions: LayoutDescription.HorizontalLayoutDimension,
            offset: Float = 0.0f,
            interpolate: Float = 1.0f

        ) {
            // Add alignment from dimensions 
            this.add(LayoutDescription.HorizontalLayoutAlignment(
                LayoutDescription.HorizontalLayoutDimension.Type.Start, dimensions.toList(),
                interpolate, offset)
            )
        }

        private fun MutableSet<LayoutDescription.LayoutAlignment>.addEndAlignment(
            vararg dimensions: LayoutDescription.HorizontalLayoutDimension,
            offset: Float = 0.0f,
            interpolate: Float = 0.0f

        ) {
            // Add alignment from dimensions 
            this.add(LayoutDescription.HorizontalLayoutAlignment(
                LayoutDescription.HorizontalLayoutDimension.Type.End, dimensions.toList(),
                interpolate, offset)
            )
        }
    }

    private val layouts = arrayOf(
        Pair(
            /** A narrow layout */
            288.0f, LayoutDescription(object: LayoutGenerator(context.resources, 4.5f, 1.0f) {
                override fun describeLayout() {
                    // Setup the title
                    title.overlapping.addAll(smallThumb, largeThumb)
                    // Setup the subtitle
                    subtitle.layoutAlignment.addTopAlignment(
                        // Subtitle is below the title
                        LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, title)
                    )
                    subtitle.overlapping.addAll(smallThumb, largeThumb)
                    // Setup the authors label and authors list
                    authors.simpleLayout(subtitle, smallThumb, largeThumb, fieldOverlapsLabel = true)
                    // Setup the tags label and tags list
                    tags.simpleLayout(authors.end, smallThumb, largeThumb, fieldOverlapsLabel = true)
                    // Setup the tags label and tags list
                    categories.simpleLayout(tags.end, smallThumb, largeThumb, fieldOverlapsLabel = true)
                    // Setup the tags label and tags list
                    isbns.simpleLayout(categories.end, smallThumb, largeThumb, fieldOverlapsLabel = true)
                    // Setup the page count label and page count
                    pageCount.simpleLayout(isbns.end, smallThumb, largeThumb)
                    // Setup the rating label and rating
                    rating.simpleLayout(pageCount.end, smallThumb, largeThumb)
                    // Setup the data added label and data added
                    added.simpleLayout(rating.end, smallThumb, largeThumb)
                    // Setup the date modified label and date modified
                    modified.simpleLayout(added.end, smallThumb, largeThumb)
                    // Setup the source label and source
                    source.simpleLayout(modified.end, smallThumb, largeThumb)
                    // Setup the id label and id
                    id.simpleLayout(source.end, smallThumb, largeThumb)
                    // Setup the description
                    description.layoutAlignment.addTopAlignment(
                        // Description is below the id
                        LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, id.end)
                    )
                    description.overlapping.addAll(smallThumb, largeThumb)
                }
            }.makeLayout(), emptyList()),
        ),
        Pair(
            /** A wider layout */
            Float.POSITIVE_INFINITY, LayoutDescription(object: LayoutGenerator(context.resources, 4.5f, 1.0f) {
                override fun describeLayout() {
                    // Setup the title
                    title.overlapping.addAll(smallThumb, largeThumb)
                    // Setup the subtitle
                    subtitle.layoutAlignment.addTopAlignment(
                        // Subtitle is below the title
                        LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, title)
                    )
                    subtitle.overlapping.addAll(smallThumb, largeThumb)
                    // The data is printed in two columns, the left(start) columns and the right(end) column
                    // which are reversed for right to left
                    // The start alignment for the start side
                    val startSideStart = LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.Start,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, null)
                        ), 1.0f
                    )
                    // The end alignment for the start side
                    val startSideEnd = LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.End,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, title),
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, null)
                        ),
                        0.5f,
                        -18.0f
                    )
                    // The start alignment for the end side
                    val endSideStart = LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.Start,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, title),
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, null)
                        ),
                        0.5f,
                        18.0f
                    )
                    // The end alignment for the end side
                    val endSideEnd = LayoutDescription.HorizontalLayoutAlignment(
                        LayoutDescription.HorizontalLayoutDimension.Type.End,
                        listOf(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, null)
                        ), 0.0f
                    )
                    // Setup the authors label and authors list
                    authors.splitLayout(startSideStart, startSideEnd, subtitle, smallThumb, largeThumb, fieldOverlapsLabel = true)
                    // Setup the tags label and tags list
                    tags.splitLayout(startSideStart, startSideEnd, authors.end, smallThumb, largeThumb, fieldOverlapsLabel = true)
                    // Setup the tags label and tags list
                    categories.splitLayout(endSideStart, endSideEnd, subtitle, smallThumb, largeThumb, fieldOverlapsLabel = true)
                    // Setup the tags label and tags list
                    isbns.splitLayout(endSideStart, endSideEnd, categories.end, smallThumb, largeThumb, fieldOverlapsLabel = true)
                    // Setup the page count label and page count
                    pageCount.splitLayout(startSideStart, startSideEnd, tags.end, smallThumb, largeThumb)
                    // Setup the rating label and rating
                    rating.splitLayout(endSideStart, endSideEnd, isbns.end, smallThumb, largeThumb)
                    // Setup the data added label and data added
                    added.splitLayout(startSideStart, startSideEnd, pageCount.end, smallThumb, largeThumb)
                    // Setup the date modified label and date modified
                    modified.splitLayout(startSideStart, startSideEnd, added.end, smallThumb, largeThumb)
                    // Setup the source label and source
                    source.splitLayout(endSideStart, endSideEnd, rating.end, smallThumb, largeThumb)
                    // Setup the id label and id
                    id.splitLayout(endSideStart, endSideEnd, source.end, smallThumb, largeThumb)
                    // Setup the description
                    description.layoutAlignment.addTopAlignment(
                        // Description is below the id and modified
                        LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, modified.end),
                        LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, id.end)
                    )
                    description.overlapping.addAll(smallThumb, largeThumb)
                }
            }.makeLayout(), emptyList()),
        )
    )

    private abstract class LayoutGenerator(resources: Resources, protected val labelMargin: Float, protected val verticalMargin: Float) {
        protected inner class LabeledField(
            resources: Resources,
            column: Column,
            labelId: Int,
            labelFollowsField: Boolean = false
        ) {
            val start: LayoutDescription.FieldLayoutDescription
            val end: LayoutDescription.FieldLayoutDescription

            init {
                val label = LayoutDescription.TextFieldLayoutDescription(column.name, resources.getString(labelId))
                val field = LayoutDescription.ColumnTextFieldLayoutDescription(column)
                start = if (labelFollowsField) field else label
                end = if (labelFollowsField) label else field
                start.margin.top = verticalMargin
                // Align field and label baselines
                end.layoutAlignment.addBaselineAlignment(
                    LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.BaseLine, start)
                )
                // put gap between label and field
                start.margin.right = labelMargin
            }

            fun simpleLayout(
                below: LayoutDescription.FieldLayoutDescription,
                vararg overlaps: LayoutDescription.FieldLayoutDescription,
                fieldOverlapsLabel: Boolean = false,
                endAlign: Boolean = false
            ) {
                start.layoutAlignment.addTopAlignment(
                    // Set label/field below the desired field
                    LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, below)
                )
                if (fieldOverlapsLabel) {
                    // Field overlaps label. It must be start aligned and follow the label
                    start.overlapping.addAll(overlaps)
                    end.overlapping.addAll(overlaps)
                    end.overlapping.add(start)
                } else {
                    start.overlapping.addAll(overlaps)
                    if (endAlign) {
                        // Position end at the end
                        end.layoutAlignment.addEndAlignment(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, null)
                        )
                        // Start precedes end
                        start.layoutAlignment.addEndAlignment(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, end)
                        )
                    } else {
                        // Position start at the start
                        end.layoutAlignment.addStartAlignment(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.Start, null)
                        )
                        // End follows start
                        end.layoutAlignment.addStartAlignment(
                            LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, start)
                        )
                    }
                }
            }

            fun splitLayout(
                sideStart: LayoutDescription.HorizontalLayoutAlignment,
                sideEnd: LayoutDescription.HorizontalLayoutAlignment,
                below: LayoutDescription.FieldLayoutDescription,
                vararg overlaps: LayoutDescription.FieldLayoutDescription,
                fieldOverlapsLabel: Boolean = false
            ) {
                start.layoutAlignment.addTopAlignment(
                    // Set label/field below the desired field
                    LayoutDescription.VerticalLayoutDimension(LayoutDescription.VerticalLayoutDimension.Type.Bottom, below)
                )

                // Set start field alignment to fill the side
                start.layoutAlignment.addAll(sideStart, sideEnd)
                // Set the end field alignment to end at the side end
                end.layoutAlignment.add(sideEnd)
                if (fieldOverlapsLabel) {
                    // Field overlaps label. It must be start aligned and follow the label
                    end.layoutAlignment.add(sideStart)
                    start.overlapping.addAll(overlaps)
                    end.overlapping.addAll(overlaps)
                    end.overlapping.add(start)
                } else {
                    start.overlapping.addAll(overlaps)
                    // End follows start
                    end.layoutAlignment.addStartAlignment(
                        LayoutDescription.HorizontalLayoutDimension(LayoutDescription.HorizontalLayoutDimension.Type.End, start)
                    )
                }
            }
        }

        protected val smallThumb = LayoutDescription.ColumnBitmapFieldLayoutDescription(false, PointF(16.0f, 25.0f)).apply {
            margin.right = labelMargin
        }
        protected val largeThumb = LayoutDescription.ColumnBitmapFieldLayoutDescription(true, PointF(72.0f, 112.0f)).apply {
            margin.right = labelMargin
        }
        protected val title = LayoutDescription.TitleTextFieldLayoutDescription()
        protected val subtitle = LayoutDescription.ColumnTextFieldLayoutDescription(Column.SUBTITLE).apply {
            margin.top = verticalMargin
        }
        protected val authors = LabeledField(resources, Column.FIRST_NAME, R.string.authors_by)
        protected val tags = LabeledField(resources, Column.TAGS, R.string.tags)
        protected val categories = LabeledField(resources, Column.CATEGORIES, R.string.categories)
        protected val isbns = LabeledField(resources, Column.ISBN, R.string.isbns)
        protected val pageCount = LabeledField(resources, Column.PAGE_COUNT, R.string.pages, true)
        protected val rating = LabeledField(resources, Column.RATING, R.string.rating_colon)
        protected val added = LabeledField(resources, Column.DATE_ADDED, R.string.added)
        protected val modified = LabeledField(resources, Column.DATE_MODIFIED, R.string.modified)
        protected val id = LabeledField(resources, Column.SOURCE_ID, R.string.id)
        protected val source = LabeledField(resources, Column.SOURCE, R.string.source_colon)
        protected val description = LayoutDescription.ColumnTextFieldLayoutDescription(Column.DESCRIPTION).apply {
            margin.top = verticalMargin
        }

        protected abstract fun describeLayout()

        fun makeLayout(): List<LayoutDescription.FieldLayoutDescription> {
            describeLayout()
            return listOf(
                smallThumb, largeThumb,
                title, subtitle,
                authors.start, authors.end,
                tags.start, tags.end,
                categories.start, categories.end,
                isbns.start, isbns.end,
                pageCount.start, pageCount.end,
                rating.start, rating.end,
                added.start, added.end,
                modified.start, modified.end,
                source.start, source.end,
                id.start, id.end,
                description
            )
        }
    }
}
