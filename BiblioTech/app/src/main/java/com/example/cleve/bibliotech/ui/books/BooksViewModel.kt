package com.example.cleve.bibliotech.ui.books

import android.app.Application
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.TextAppearanceSpan
import android.util.SparseArray
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.db.BookAndAuthors
import com.example.cleve.bibliotech.db.BookFilter
import com.example.cleve.bibliotech.db.BookRepository
import com.example.cleve.bibliotech.utils.GenericViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashSet

/**
 * The view model for the book list
 */
class BooksViewModel(val app: Application) : GenericViewModel<BookAndAuthors>(app) {

    /**
     * The book databse repository
     */
    val repo: BookRepository = BookRepository.repo

    /**
     * The adapter for the book recycler view
     */
    internal val adapter: BooksAdapter = BooksAdapter(app.applicationContext, this)

    /**
     * The layout manager for the book recycler view
     */
    internal lateinit var layoutManager: LinearLayoutManager

    /**
     * Job used to create the coroutine flow for thw PagingSource adaptr
     */
    private var flowJob: Job? = null

    /**
     * Filter for the book list
     */
    private var filter: BookFilter? = null

    /**
     * The styles to used for the names and items in headers and separators
     */
    private var nameStyles: Array<TextAppearanceSpan>
    private var itemStyles: Array<TextAppearanceSpan>

    /**
     * Locale to use for headers and separators and dates
     */
    private var locale: Locale

    /**
     * Column names for the filter columns om headers and separators
     */
    private val names = SparseArray<String>()


    init {
        // Get the locale
        // TODO: change the local when changed on the phone
        val context = app.applicationContext
        val resources = context.resources
        locale = resources.configuration.locales[0]
        // Get the text appearances for column names in separators
        nameStyles = arrayOf(
            TextAppearanceSpan(context, R.style.HeaderName0),
            TextAppearanceSpan(context, R.style.HeaderName1),
            TextAppearanceSpan(context, R.style.HeaderName2)
        )
        // Get the text appearances for item values in separators
        itemStyles = arrayOf(
            TextAppearanceSpan(context, R.style.HeaderItem0),
            TextAppearanceSpan(context, R.style.HeaderItem1),
            TextAppearanceSpan(context, R.style.HeaderItem2)
        )
        // Get the localized names of the columns
        for (c in BookFilter.Column.values()) {
            names.put(c.nameResourceId, if (c.nameResourceId == 0) null else resources.getString(c.nameResourceId))
        }
    }

    /**
     * @inheritDoc
     */
    override fun invalidateUI() {
        adapter.refresh()
    }

    fun buildFilter(orderFields: Array<BookFilter.OrderField>?, filterFields: Array<BookFilter.FilterField>?): BookFilter? {
        if (orderFields == null && filterFields == null)
            return null
        return BookFilter(orderFields?: emptyArray(), filterFields?: emptyArray())
    }

    /**
     * Build a new flow for book stream
     * @param filter The filter for the new stream
     * This is called when the filter is changed to update the display
     */
    fun buildFlow(orderFields: Array<BookFilter.OrderField>?, filterFields: Array<BookFilter.FilterField>?) {
        // Build the filter
        val filter = buildFilter(orderFields, filterFields)

        // Cancel previous job if there was one
        flowJob?.let {
            it.cancel()
            flowJob = null
        }
        // Set the filter
        this.filter = filter
        // Create the pager
        val config = PagingConfig(pageSize = 10)
        val pager = Pager(
            config
        ) {
            repo.getBooks(filter)
        }
        // Add haders and cache
        val flow = addHeaders(applySelectionTransform(pager.flow))
            .cachedIn(viewModelScope)
        // Start the flow
        flowJob = viewModelScope.launch {
            flow.collectLatest { data ->
                adapter.submitData(data)
            }
        }
    }

    /**
     * Build a header string for the filter
     * @return The Spananble string with the header content
     */
    fun buildHeader(): Spannable? {
        // No filter means no header
        val filter = this.filter ?: return null

        // Get the first visible item in the list
        var pos = layoutManager.findFirstCompletelyVisibleItemPosition()
        var book: BookAndAuthors?
        // Find the first completely visible book in the list
        if (pos == RecyclerView.NO_POSITION)
            book = null
        else {
            do {
                book = adapter.peek(pos) as? BookAndAuthors
            } while (++pos < adapter.itemCount && book == null)
        }

        // Build the header from the book using the filter
        return buildHeader(filter, book, false)
    }

    /**
     * Build header or separator string for a book
     * @param filter The book filter that defines the header
     * @param book The book
     * @param isSeparator True for separators and false the headers
     * @return The Spananble string with the content
     */
    fun buildHeader(filter: BookFilter, book: BookAndAuthors?, isSeparator: Boolean): Spannable? {
        val span = SpannableStringBuilder()

        /**
         * Append text with some effects
         * @param text The string containing the text
         * @param effects A variable list of spans to apply to the text.
         */
        fun appendSpan(text: String, vararg effects: Any?) {
            val start = span.length
            span.append(text)
            for (eff in effects) {
                eff?.let {
                    span.setSpan(it, start, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        /**
         * Construct a text appearance from the arrays
         * @param base The array to use for the appearance
         * @param i The depth in the filter
         */
        fun textAppearance(base: Array<TextAppearanceSpan>, i: Int): TextAppearanceSpan {
            // Find the style
            val style = base[if (i < base.size) i else base.size - 1]
            // Create a new appearance from the style
            return TextAppearanceSpan(style.family, style.textStyle, style.textSize, style.textColor, style.linkTextColor)
        }

        // Keep track of columns already added to the header
        val added = HashSet<BookFilter.Column>()
        // Loop over the fields in the filter order
        for ((i, field) in filter.orderList.withIndex()) {
            if (book == null) {
                // If the book is null, create a blank header
                span.appendLine()
            } else if (field.headers && !added.contains(field.column)) {
                // The field has a header and hasn't been added
                added.add(field.column)
                // Append a new line if data is already in the header
                if (span.isNotEmpty())
                    span.appendLine()
                // Add the name
                appendSpan("${names[field.column.nameResourceId]}: ",
                    textAppearance(nameStyles, i))
                // Add the item value, possibly different for headers and separators
                appendSpan(
                    if (isSeparator)
                        field.column.getSeparatorValue(book, locale)
                    else
                        field.column.getValue(book, locale),
                    textAppearance(itemStyles, i)
                )
            }
        }

        return if (span.isNotEmpty()) span else null
    }

    /**
     * Create a separator between two books
     * @param filter The filter that defines the separator
     * @param before The book before the separator
     * @param after the Book after the separator
     * @return The Spananble string with the separator content
     */
    fun compareAndBuildeHeader(filter: BookFilter, before: BookAndAuthors?, after: BookAndAuthors?): Spannable? {
        // If this is the first item create the separator, unless it is null, too
        if (before == null) {
            if (after == null)
                return null
            return buildHeader(filter, after, true)
        }

        // Loop through the header columns and see if any are changing.
        // Create the separator if they are
        if (after != null) {
            for (field in filter.orderList) {
                if (field.column.shouldAddSeparator(before, after))
                    return buildHeader(filter, after, true)
            }
        }
        return null
    }

    /**
     * Add a data mapping to insert separators in the book stream
     * @param flow The flow for the book stram
     * @return The flow for the book stream with headers
     */
    fun addHeaders(flow: Flow<PagingData<BookAndAuthors>>): Flow<PagingData<Any>> {
        return flow.map {data ->
            data.insertSeparators { before, after ->
                // Insert separators for the filter
                filter?.let {
                    compareAndBuildeHeader(it, before, after)
                }

            }
        }
    }
}
