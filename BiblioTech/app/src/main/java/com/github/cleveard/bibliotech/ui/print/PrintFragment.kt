package com.github.cleveard.bibliotech.ui.print

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.ListAdapter
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookAndAuthors
import com.github.cleveard.bibliotech.db.Column
import com.github.cleveard.bibliotech.print.PDFPrinter
import com.github.cleveard.bibliotech.print.PageLayoutHandler
import com.github.cleveard.bibliotech.ui.books.BooksViewModel
import com.github.cleveard.bibliotech.utils.getLive
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class PrintFragment : Fragment() {

    companion object {
        @Suppress("unused")
        fun newInstance() = PrintFragment()

        /**
         * Create an entry for the visibleFieldNames array from a database Column
         * @param column The database column
         * @return The field names entry
         */
        private fun columnName(column: Column): Pair<Int, String> = Pair(column.desc.nameResourceId, column.name)

        /**
         * List of names for database fields we can print.
         * The list is a pair of the localized resource id and the
         * name of the database Column enum. The list is used to
         * populate the checkboxes that select the fields to be printed
         */
        val visibleFieldNames: List<Pair<Int,String>> = listOf(
            Pair(R.string.small_thumb, "SmallThumb"),
            Pair(R.string.large_thumb, "LargeThumb"),
            columnName(Column.TITLE),
            columnName(Column.SUBTITLE),
            Pair(R.string.author, Column.FIRST_NAME.name),
            columnName(Column.SERIES),
            columnName(Column.TAGS),
            columnName(Column.RATING),
            columnName(Column.PAGE_COUNT),
            columnName(Column.CATEGORIES),
            columnName(Column.ISBN),
            columnName(Column.DATE_ADDED),
            columnName(Column.DATE_MODIFIED),
            columnName(Column.SOURCE),
            columnName(Column.SOURCE_ID),
            columnName(Column.DESCRIPTION)
        )

        /**
         * Conversion factor from points to dips
         * Used to display the font size selection characters
         */
        private const val pointsToDips = 160.0f / 72.0f
    }

    /** Fragment navigation arguments */
    private val args: PrintFragmentArgs by navArgs()

    /**
     * Class used to display names for objects in the UI
     * @param obj The object to assign a name
     * @param displayName The display name for the object
     */
    private open class ObjectName<T>(val obj: T, val displayName: String) {
        /**
         * @inheritDoc
         * Only use the name for the comparison
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ViewName
            return obj == other.obj
        }

        /**
         * @inheritDoc
         * Only use the name for comparison
         */
        override fun hashCode(): Int {
            return obj.hashCode()
        }

        /**
         * @inheritDoc
         * Use the displayName for the string representation
         */
        override fun toString(): String {
            return displayName
        }
    }

    /**
     * Class used to display view names in the UI
     * @param obj The view name
     * @param displayName The display name for the view name
     */
    private class ViewName(obj: String?, displayName: String): ObjectName<String?>(obj, displayName) {
        companion object {
            /**
             * Make a ViewName object for a string
             * @param name The string
             * @param resources Resources to get the string for empty and null names
             * @return The ViewName
             */
            fun makeDisplay(name: String?, resources: Resources): ViewName {
                return ViewName(name,
                    when {
                        name == null -> resources.getString(R.string.all_books)
                        name.isEmpty() -> resources.getString(R.string.menu_books)
                        else -> name
                    }
                )
            }
        }
    }

    /**
     * Class used to display paper size names in the UI
     * @param obj The paper size
     */
    private inner class PaperSizeName(obj: PrintAttributes.MediaSize): ObjectName<PrintAttributes.MediaSize>(obj, obj.getLabel(requireContext().packageManager))

    /** Job used to get the list of books */
    private var filterJob: Job? = null
    /** The filter used for the export */
    private var filter: ViewName = ViewName(null, "")
     set(v) {
         if (v != field) {
             field = v
             getBookList(v, viewModel.printCount.selectedOnly)
         }
     }

    /** View model for the print fragment */
    private val viewModel: PrintViewModel by viewModels()

    /**
     * View model for the books fragment
     * Used to access the database and to get thumbnails
     */
    private val booksViewModel: BooksViewModel by activityViewModels()

    /** Adapter for the print preview recycler view */
    private lateinit var previewAdapter: ListAdapter<PageLayoutHandler.Page, PreviewViewHolder>
    /** Job used to layout the pages for previewing */
    private var pageJob: Job? = null

    /**
     * ViewHolder for the included fields list
     * @param checkBox Field checkbox
     * @param field The resource id and filter name for a field
     */
    private class IncludedViewHolder(
        /** Field checkbox */
        checkBox: CheckBox,
        /** The resource id and filter name for a field */
        var field: Pair<Int, String> = Pair(0, "")
    ): RecyclerView.ViewHolder(checkBox)

    /** Manager for PageLayoutHandlers */
    private val layoutHandlers = object: Any() {
        // Pool of handler
        private val handlers = LinkedHashSet<Pair<PageLayoutHandler, UInt>>()
        // Current handler generation
        private var generation = 0u

        /**
         * Acquire a PageLayoutHandler along with the current generation
         * @return The handler and generation
         */
        fun acquire(): Pair<PageLayoutHandler, UInt> {
            return (handlers.firstOrNull()?: Pair(
                PageLayoutHandler(viewModel.pdfPrinter, viewModel.pdfPrinter.pageDrawBounds),
                generation
            )).also {
                // Remove the handler, so it won't be acquired again
                handlers.remove(it)
            }
        }

        /**
         * Release a PageLayoutHandler acquired by acquire()
         * @param handler The handler and generation
         */
        fun release(handler: Pair<PageLayoutHandler, UInt>) {
            // Add the handler back into the pool, unless the generation has changed
            if (handler.second == generation)
                handlers.add(handler)
        }

        /**
         * Invalidate all handlers
         */
        fun invalidate() {
            // Bump the generation
            ++generation
            // And clear the pool
            handlers.clear()
        }
    }

    private fun getBookList(v: ViewName, selectedOnly: Boolean) {
        filterJob?.cancel()
        viewModel.viewModelScope.launch {
            filterJob?.join()
            filterJob = coroutineContext[Job]
            try {
                // Get the book filter for the export
                val bookFilter = v.obj?.let { name -> booksViewModel.repo.findViewByName(name) }?.filter
                ensureActive()
                // Get the PageSource for the books
                val source = if (bookFilter != null)
                    booksViewModel.repo.getBookList(bookFilter, selectedOnly, requireContext())
                else
                    booksViewModel.repo.getBookList(selectedOnly)
                ensureActive()
                viewModel.pdfPrinter.bookList = source.getLive()
                calculatePages()
            } finally {
                filterJob = null
            }
        }
    }

    /**
     * ViewHolder for the included fields list
     * @param view The view the holder holds
     * @param width The width of the page
     */
    private inner class PreviewViewHolder(
        /** Field checkbox */
        view: View,
        /** The width of the page */
        private val width: Int
    ): RecyclerView.ViewHolder(view) {
        /** The page image */
        var image: Bitmap? = createBitmap(null)
        /** The job drawing the page */
        var drawJob: Job? = null

        /**
         * Draw a page for this view holder
         * @param page The page to draw
         * @param books The list of books
         * @param scope A coroutine scope used for drawing
         */
        fun drawPage(page: PageLayoutHandler.Page?, books: List<BookAndAuthors>?, scope: CoroutineScope) {
            // If we are currently drawing, cancel it
            cancelDraw()
            // If page or books is null, the nothing to do
            if (page == null || books == null)
                return

            // Get the item view
            val imageView = itemView.findViewById<ImageView>(R.id.preview_page)
            // Draw into a bitmap
            image = createBitmap(image)
            image?.let { image ->
                // Erase the bitmap to white
                image.eraseColor(0xFFFFFFFF.toInt())
                // Start the job to draw the page
                scope.launch {
                    // If a draw was in progress wait for it to finish
                    drawJob?.join()
                    // Keep track of the current job
                    drawJob = coroutineContext[Job]
                    // Get a handler for the page
                    val handler = layoutHandlers.acquire()
                    try {
                        // Make the canvas for the page and save its state
                        val canvas = Canvas(image)
                        canvas.save()
                        // Scale the canvas to use points for coordinates
                        val scaleX = (viewModel.pdfPrinter.attributes.mediaSize?.widthMils ?: 8500).toFloat() / 1000.0f
                        val scaleY = (viewModel.pdfPrinter.attributes.mediaSize?.heightMils ?: 11000).toFloat() / 1000.0f
                        canvas.scale(image.width.toFloat() / (scaleX * 72.0f), image.height.toFloat() / (scaleY * 72.0f))
                        // Draw the page
                        viewModel.pdfPrinter.drawPage(canvas, page, books, handler.first)
                        // Restore the canvas state
                        canvas.restore()
                        // Invalidate the view
                        imageView.invalidate()
                    } finally {
                        // The job is complete, clean up
                        drawJob = null
                        layoutHandlers.release(handler)
                    }
                }
            }
        }

        /**
         * Cancel the current draw job, if any
         */
        fun cancelDraw() {
            drawJob?.cancel()
        }

        /**
         * Create a bitmap for this page
         * @param oldBitmap The current bitmap in the holder
         * @return The bitmap or null if the dimensions as 0
         */
        private fun createBitmap(oldBitmap: Bitmap?): Bitmap? {
            // Get the page size
            val pW = viewModel.pdfPrinter.attributes.mediaSize?.widthMils?: 8500
            val pH = viewModel.pdfPrinter.attributes.mediaSize?.heightMils?: 11000
            val h = (width * pH) / pW
            if (width == oldBitmap?.width && h == oldBitmap.height)
                return oldBitmap
            // create the bitmap
            val bitmap = if (width == 0 || h == 0)
                null
            else
                Bitmap.createBitmap(width * 2, h * 2, Bitmap.Config.ARGB_8888, true)
            // Set the bitmap on the image view
            val imageView = itemView.findViewById<ImageView>(R.id.preview_page)
            imageView.setImageBitmap(bitmap)
            // return the bitmap
            return bitmap
        }
    }

    /** @inheritDoc */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.print_fragment, container, false)
    }

    /**
     * Setup a spinner
     * @param picker The spinner to be setup
     * @param selected The initial selected item
     * @param values The values associated with the spinner items
     * @param setter A callback to set a selected value
     * The spinner items are set from a resource array in the layout xml
     */
    private fun setupSpinner(
        picker: Spinner,
        selected: Int,
        values: Array<String>,
        setter: (String?) -> Unit,
    ) {
        // Set the selected item listener
        picker.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // let the setter know something was selected
                setter(
                    if (position >= 0 && position < values.size)
                        values[position]
                    else
                        null
                )
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Let the setter know nothing was selected
                setter(null)
            }
        }
        // Set the initial selection
        if (selected >= 0)
            picker.setSelection(selected)
    }

    private inline fun <reified T> setupSpinner(
        picker: Spinner,
        selected: Int,
        items: List<T>,
        crossinline setter: (T?) -> Unit
    ) {
        picker.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items.toTypedArray())
        if (selected >= 0)
            picker.setSelection(selected)
        picker.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                setter(items[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                setter(null)
            }
        }
    }

    /**
     * Set up a text view that acts as a set of radio buttons
     * @param text The text view
     * @param startValue The initial value. Used to set the initial selection
     * @param content The array of text buttons in the text view
     * @param values The array of values for the text buttons
     * @param highlightColor The highlight color for the selected button
     * @param format Callback used to format the text button
     * @param setter Callback used to set selected value
     */
    private fun <T> setupTextviewRadio(
        text: TextView,
        startValue: T,
        content: Array<String>,
        values: List<T?>,
        highlightColor: Int,
        format: (SpannableString, start: Int, end: Int, value: T) -> Unit = {_, _, _, _ -> },
        setter: (TextView, T) -> Unit)
    {
        // Get the count of buttons
        val count = values.size.coerceAtMost(content.size)
        // Build the string for the buttons
        val spans = SpannableString(content.joinToString("") { it })
        // The highlight span for the selected button
        val highlight = ForegroundColorSpan(highlightColor)

        // Setup the spans
        run {
            var startOffset = 0
            for (i in 0 until count) {
                val endOffset = startOffset + content[i].length
                // If values is not null, then setup the button
                values[i]?.let { value ->
                    // Format the button based on the value
                    format(spans, startOffset, endOffset, value)
                    // Highlight the initial value
                    if (startValue == value) {
                        spans.setSpan(highlight, startOffset, endOffset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                startOffset = endOffset
            }
            // Set the string to the text view
            text.text = spans
        }

        // Set an on touch listener to handle button selection
        @Suppress("ClickableViewAccessibility")
        text.setOnTouchListener { _, event ->
            // Only select on first down action
            if (event.action == MotionEvent.ACTION_DOWN && event.pointerCount == 1) {
                // Get the event x coordinate
                val x = event.x
                // Make sure the text layout has been initialized
                text.layout?.let { layout ->
                    // Find the button that was touched
                    var startPos = 0
                    var startOffset = layout.getPrimaryHorizontal(startPos)
                    for (i in 0 until count) {
                        val endPos = startPos + content[i].length
                        val endOffset = layout.getPrimaryHorizontal(endPos)
                        // Direction independent test for x between startOffset and endOffset
                        // If x is between then (x - startOffset) and (x - endOffset) have different signs
                        if ((x - startOffset) * (x - endOffset) < 0) {
                            // Make sure the value is valid
                            values[i]?.let {
                                // Set the value
                                setter(text, it)
                                // move the highlight
                                spans.removeSpan(highlight)
                                spans.setSpan(highlight, startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                // Set the new highlight to the text view
                                text.text = spans
                            }
                        }
                        startPos = endPos
                        startOffset = endOffset
                    }
                }
            }
            true
        }
    }

    /** @inheritDoc */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Get the view model
        viewModel.initialize(booksViewModel.repo, requireContext()) {id, large ->
            booksViewModel.getThumbnail(id, large)
        }

        // Create the ViewName for the filter from the arguments
        filter = ViewName.makeDisplay(args.filterName, requireContext().resources)
        // Get the Spinner for the filters
        val viewSpinner = view.findViewById<Spinner>(R.id.select_filter)
        // Listen for selections
        viewSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Set the filter to the selected filter
                filter = (parent?.getItemAtPosition(position) as? ViewName)?: ViewName(null, "")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Set the filter to the first filter
                filter = (parent?.getItemAtPosition(0) as? ViewName)?: ViewName(null, "")
            }
        }
        // Create the adaptor for the spinner and set it
        val filterAdapter = ArrayAdapter<ViewName>(view.context, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        viewSpinner.adapter = filterAdapter
        // Add the view names to the spinner
        booksViewModel.repo.getViewNames().also {live ->
            // Observe the view names for changes
            live.observe(viewLifecycleOwner) {list ->
                // Set the current set of view names
                filterAdapter.clear()
                list?.let {
                    val resources = requireContext().resources
                    filterAdapter.add(ViewName.makeDisplay(null, resources))
                    filterAdapter.add(ViewName.makeDisplay("", resources))
                    for (s in it) {
                        // Don't add the empty name again
                        if (s.isNotEmpty())
                            filterAdapter.add(ViewName.makeDisplay(s, resources))
                    }
                    filterAdapter.notifyDataSetChanged()
                    val pos = filterAdapter.getPosition(filter).coerceAtLeast(0)
                    viewSpinner.setSelection(pos)
                }
            }
        }

        val resources = requireContext().resources
        // setup the number of columns view
        view.findViewById<TextView>(R.id.columns).let {text ->
            setupTextviewRadio(text, viewModel.pdfPrinter.numberOfColumns,
                resources.getStringArray(R.array.column), resources.getStringArray(R.array.column_values).map { it.toIntOrNull() },
                resources.getColor(R.color.colorAccent, text.context.theme)
            ) { _, size ->
                if (viewModel.pdfPrinter.numberOfColumns != size ) {
                    viewModel.pdfPrinter.numberOfColumns = size
                    calculatePages()
                }
            }
        }

        // Setup the separator line width spinner
        requireContext().resources.getStringArray(R.array.separator_values).let {values ->
            setupSpinner(
                view.findViewById(R.id.separator),
                values.indexOfFirst { viewModel.pdfPrinter.separatorLineWidth == it.toFloatOrNull() },
                values
            ) {value ->
                value?.toFloatOrNull()?.let {
                    if (viewModel.pdfPrinter.separatorLineWidth != it) {
                        viewModel.pdfPrinter.separatorLineWidth = it
                        calculatePages()
                    }
                }
            }
        }

        // Setup the paper size spinner
        setupSpinner(
            view.findViewById(R.id.paper_size),
            PDFPrinter.paperSizes.indexOfFirst { viewModel.pdfPrinter.attributes.mediaSize?.id == it.id },
            PDFPrinter.paperSizes.map { PaperSizeName(it) }
        ) {
            if (it != null)
                changeMediaSize(it.obj, view.findViewById<Spinner>(R.id.orientation).selectedItemPosition == 0)
        }

        // Setup the paper orientation width spinner
        requireContext().resources.getStringArray(R.array.paper_orientation_values).let {values ->
            setupSpinner(
                view.findViewById(R.id.orientation),
                if(viewModel.pdfPrinter.attributes.mediaSize?.isPortrait != false) 0 else 1,
                values
            ) {value ->
                value?.toIntOrNull()?.let {
                    viewModel.pdfPrinter.attributes.mediaSize?.let square@ {size ->
                        // If the paper size is square, or the orientation isn't changing
                        // then return because there is nothing to do
                        if (size.widthMils == size.heightMils || (it == 0) == (size.heightMils > size.widthMils))
                            return@square
                        // Change the media size
                        changeMediaSize(PrintAttributes.MediaSize(size.id, size.getLabel(requireContext().packageManager), size.heightMils, size.widthMils), it == 0)
                    }
                }
            }
        }

        // setup the orphan lines view
        view.findViewById<TextView>(R.id.orphans).let {text ->
            setupTextviewRadio(text, viewModel.pdfPrinter.orphans,
                resources.getStringArray(R.array.orphans), resources.getStringArray(R.array.orphans_values).map { it.toIntOrNull() },
                resources.getColor(R.color.colorAccent, text.context.theme)
            ) {_, size ->
                if (viewModel.pdfPrinter.orphans != size) {
                    viewModel.pdfPrinter.orphans = size
                    calculatePages()
                }
            }
        }

        // Set up the text size view
        view.findViewById<TextView>(R.id.size).let {text ->
            setupTextviewRadio(text, viewModel.pdfPrinter.basePaint.textSize,
                resources.getStringArray(R.array.size), resources.getStringArray(R.array.size_values).map { it.toFloatOrNull() },
                resources.getColor(R.color.colorAccent, text.context.theme), {spans, start, end, size ->
                    spans.setSpan(AbsoluteSizeSpan((size * pointsToDips).roundToInt(), true),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            ) {_, size ->
                if (viewModel.pdfPrinter.basePaint.textSize != size) {
                    viewModel.pdfPrinter.basePaint.textSize = size
                    viewModel.pdfPrinter.preferenceEditor.putFloat(PDFPrinter.PREF_TEXT_SIZE, size)
                    viewModel.pdfPrinter.preferenceEditor.commit()
                    viewModel.pdfPrinter.invalidateLayout()
                    calculatePages()
                }
            }
        }

        // Setup the check boxes to set the included fields
        val visible = view.findViewById<RecyclerView>(R.id.visible_fields)
        visible.adapter = object: ListAdapter<Pair<Int, String>, IncludedViewHolder>(object: DiffUtil.ItemCallback<Pair<Int, String>>() {
            override fun areItemsTheSame(oldItem: Pair<Int, String>, newItem: Pair<Int, String>): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: Pair<Int, String>, newItem: Pair<Int, String>): Boolean {
                return oldItem == newItem
            }
        }) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncludedViewHolder {
                return IncludedViewHolder(CheckBox(requireContext()))
            }

            private fun getHolder(name: String): IncludedViewHolder? {
                val recycler = view.findViewById<RecyclerView>(R.id.visible_fields)
                for (i in 0 until recycler.childCount) {
                    val holder = recycler.getChildViewHolder(recycler.getChildAt(i)) as IncludedViewHolder
                    if (holder.field.second == name)
                        return holder
                }
                return null
            }

            override fun onBindViewHolder(holder: IncludedViewHolder, position: Int) {
                holder.field = getItem(position)
                (holder.itemView as CheckBox).let {
                    val visibleFields = viewModel.pdfPrinter.visibleFields
                    it.isChecked = visibleFields.contains(holder.field.second)
                    it.text = requireContext().resources.getString(holder.field.first)
                    it.setOnClickListener {
                        if (visibleFields.contains(holder.field.second))
                            visibleFields.remove(holder.field.second)
                        else {
                            visibleFields.add(holder.field.second)
                            when (holder.field.second) {
                                "SmallThumb" -> getHolder("LargeThumb")
                                "LargeThumb" -> getHolder("SmallThumb")
                                else -> null
                            }?.let {other ->
                                (other.itemView as CheckBox).isChecked = false
                                visibleFields.remove(other.field.second)
                            }
                        }
                        viewModel.pdfPrinter.preferenceEditor.putStringSet(PDFPrinter.PREF_INCLUDED_FIELDS, visibleFields)
                        viewModel.pdfPrinter.preferenceEditor.commit()
                        viewModel.pdfPrinter.invalidateLayout()
                        calculatePages()
                        // notifyItemChanged(position)
                    }
                }
            }
        }.apply {
            submitList(visibleFieldNames)
        }

        // Handle the expand/collapse button for the print parameters
        view.findViewById<MaterialButton>(R.id.action_expand).let {button ->
            val parameters = view.findViewById<View>(R.id.print_parameters)
            button.isChecked = false
            parameters.visibility = View.GONE
            button.setOnClickListener {
                parameters.visibility = if (button.isChecked) View.VISIBLE else View.GONE
            }
        }

        // Setup the print preview recycler view
        val preview = view.findViewById<RecyclerView>(R.id.preview)
        previewAdapter = object: ListAdapter<PageLayoutHandler.Page, PreviewViewHolder>(object: DiffUtil.ItemCallback<PageLayoutHandler.Page>() {
            override fun areItemsTheSame(oldItem: PageLayoutHandler.Page, newItem: PageLayoutHandler.Page): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: PageLayoutHandler.Page, newItem: PageLayoutHandler.Page): Boolean {
                return oldItem == newItem
            }
        }) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
                @SuppressLint("InflateParams")
                val itemView = LayoutInflater.from(parent.context).inflate(R.layout.print_preview_item, null)
                return PreviewViewHolder(itemView, parent.width)
            }

            override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
                holder.itemView.findViewById<TextView>(R.id.preview_page_number).text =
                    requireContext().resources.getString(R.string.preview_page, position + 1, viewModel.pdfPrinter.pageCount)
                holder.drawPage(getItem(position), viewModel.pdfPrinter.bookList, viewModel.viewModelScope)
            }

            override fun onViewRecycled(holder: PreviewViewHolder) {
                // When a holder is recycled, kill any draw job that might be running
                holder.cancelDraw()
            }
        }
        preview.adapter = previewAdapter
        // Calculate the pages for the first preview
        calculatePages()

        // setup the print button
        view.findViewById<Button>(R.id.action_print).apply {
            setOnClickListener {
                print()
            }
            isEnabled = (viewModel.printCount.value?: 0) > 0
            viewModel.printCount.observe(viewLifecycleOwner) {
                isEnabled = (viewModel.printCount.value?: 0) > 0
            }
        }

        view.findViewById<CheckBox>(R.id.print_selected_only).apply {
            setOnClickListener {
                if (isEnabled) {
                    viewModel.printCount.setSelectedOnly(isChecked, requireContext())
                    getBookList(filter, isChecked)
                }
            }
        }
    }

    /**
     * Change the media size for the print preview
     * @param size The new media size
     * @param portrait The new portrait setting
     */
    private fun changeMediaSize(size: PrintAttributes.MediaSize, portrait: Boolean) {
        val oldAttributes = viewModel.pdfPrinter.attributes
        // If the paper size is square, or the orientation isn't changing
        // then the size is OK, otherwise swap the width and height to match the portrait setting
        val newSize = if (size.widthMils == size.heightMils || portrait == (size.heightMils > size.widthMils))
            size
        else {
            PrintAttributes.MediaSize(size.id, size.getLabel(requireContext().packageManager), size.heightMils, size.widthMils)
        }

        // Duplicate the current attributes, but change the media size
        val newAttributes = PrintAttributes.Builder()
            .setColorMode(oldAttributes.colorMode)
            .setDuplexMode(oldAttributes.duplexMode)
            .setMediaSize(newSize)
            .setMinMargins(oldAttributes.minMargins!!)
            .setResolution(oldAttributes.resolution!!)
            .build()

        // set the new attributes
        viewModel.pdfPrinter.changeLayout(requireContext(), newAttributes)
        calculatePages()
    }

    /**
     * Calculate the pages using the current parameters
     */
    private fun calculatePages() {
        // Cancel any calculation jobs currently running
        cancelPages()
        // Invalidate the page layout handlers
        layoutHandlers.invalidate()
        previewAdapter.submitList(null)
        viewModel.viewModelScope.launch {
            // Wait for the last job to complete
            pageJob?.join()
            pageJob = coroutineContext[Job]
            try {
                // Calculate the pages
                val pages = withContext(Dispatchers.IO) {
                    try {
                        if (viewModel.pdfPrinter.bookList != null)
                            return@withContext viewModel.pdfPrinter.layoutPages()
                    } catch (_: PDFPrinter.NoPagesException) {
                    } catch (_: PDFPrinter.NoBooksException) {
                    }
                    null
                }
                // Set the pages in the preview view and notify changes
                previewAdapter.submitList(pages)
                @Suppress("NotifyDataSetChanged")
                previewAdapter.notifyDataSetChanged()
            } finally {
                // Done - clean up
                pageJob = null
            }
        }
    }

    /**
     * Cancel any running page calculations
     */
    private fun cancelPages() {
        pageJob?.cancel()
    }

    /**
     * Print the document
     */
    private fun print() {
        viewModel.viewModelScope.launch {
            // Don't print if there aren't any books
            if (!viewModel.pdfPrinter.bookList.isNullOrEmpty()) {
                val context = requireContext()
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "${context.getString(R.string.app_name)} Document"
                val mediaSize = viewModel.pdfPrinter.attributes.mediaSize

                // Start the print job
                printManager.print(jobName, object: BookPrintAdapter(viewModel.pdfPrinter,
                    context, viewModel.viewModelScope) {
                    override fun onFinish() {
                        super.onFinish()
                        viewModel.pdfPrinter.attributes.mediaSize?.let {
                            if (mediaSize != it) {
                                val orientationSpinner = requireView().findViewById<Spinner>(R.id.orientation)
                                val portrait = it.isPortrait
                                if (portrait != (orientationSpinner.selectedItemPosition == 0))
                                    orientationSpinner.setSelection(if (portrait) 0 else 1)
                                val paperSizeSpinner = requireView().findViewById<Spinner>(R.id.paper_size)
                                val id = it.id
                                val position = PDFPrinter.paperSizes.indexOfFirst {size -> id == size.id }
                                paperSizeSpinner.setSelection(position)
                                changeMediaSize(it, portrait)
                            }
                        }
                    }
                }, viewModel.pdfPrinter.attributes)
            }
        }
    }
}