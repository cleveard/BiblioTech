package com.example.cleve.bibliotech.ui.books

import android.app.Application
import android.content.Context
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

class BooksViewModel(val app: Application) : GenericViewModel<BookAndAuthors>(app) {

    val repo: BookRepository = BookRepository.repo
    internal lateinit var adapter: BooksAdapter
    internal lateinit var layoutManager: LinearLayoutManager
    private var flowJob: Job? = null
    private var filter: BookFilter? = null
    private var nameStyles: Array<TextAppearanceSpan>
    private var itemStyles: Array<TextAppearanceSpan>
    private var locale: Locale
    private val names = SparseArray<String>()


    init {
        val context = app.applicationContext
        val resources = context.resources
        locale = resources.configuration.locales[0]
        nameStyles = arrayOf(
            TextAppearanceSpan(context, R.style.HeaderName0),
            TextAppearanceSpan(context, R.style.HeaderName1),
            TextAppearanceSpan(context, R.style.HeaderName2)
        )
        itemStyles = arrayOf(
            TextAppearanceSpan(context, R.style.HeaderItem0),
            TextAppearanceSpan(context, R.style.HeaderItem1),
            TextAppearanceSpan(context, R.style.HeaderItem2)
        )
        for (c in BookFilter.Column.values()) {
            names.put(c.nameResourceId, if (c.nameResourceId == 0) null else resources.getString(c.nameResourceId))
        }
    }

    override fun invalidateUI() {
        adapter.refresh()
    }

    fun buildFlow(filter: BookFilter?) {
        flowJob?.let {
            it.cancel()
            flowJob = null
        }
        this.filter = filter
        val config = PagingConfig(pageSize = 10)
        val pager = Pager(
            config
        ) {
            repo.getBooks(filter)
        }
        val flow = addHeaders(applySelectionTransform(pager.flow))
            .cachedIn(viewModelScope)
        flowJob = viewModelScope.launch {
            flow.collectLatest { data ->
                adapter.submitData(data)
            }
        }
    }

    fun buildHeader(): Spannable? {
        val filter = this.filter ?: return null

        var pos = layoutManager.findFirstVisibleItemPosition()
        var book: BookAndAuthors?
        if (pos == RecyclerView.NO_POSITION)
            book = null
        else {
            do {
                book = adapter.peek(pos) as? BookAndAuthors
            } while (++pos < adapter.itemCount && book == null)
        }

        return buildHeader(filter, book)
    }

    fun buildHeader(filter: BookFilter, book: BookAndAuthors?): Spannable? {
        val span = SpannableStringBuilder()

        fun appendSpan(text: String, vararg effects: Any?) {
            val start = span.length
            span.append(text)
            for (eff in effects) {
                eff?.let {
                    span.setSpan(it, start, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        fun textAppearance(base: Array<TextAppearanceSpan>, i: Int): TextAppearanceSpan {
            val style = base[if (i < base.size) i else base.size - 1]
            return TextAppearanceSpan(style.family, style.textStyle, style.textSize, style.textColor, style.linkTextColor)
        }

        val added = HashSet<BookFilter.Column>()
        for ((i, field) in filter.orderList.withIndex()) {
            if (book == null) {
                span.appendLine()
            } else if (field.headers && !added.contains(field.column)) {
                added.add(field.column)
                if (span.isNotEmpty())
                    span.appendLine()
                appendSpan("${names[field.column.nameResourceId]}: ",
                    textAppearance(nameStyles, i))
                appendSpan(field.column.getValue(book, locale),
                    textAppearance(itemStyles, i)
                )
            }
        }

        return if (span.isNotEmpty()) span else null
    }

    fun compareAndBuildeHeader(filter: BookFilter, before: BookAndAuthors?, after: BookAndAuthors?): Spannable? {
        if (before == null) {
            if (after == null)
                return null
            return buildHeader(filter, after)
        }

        if (after != null) {
            for (field in filter.orderList) {
                if (!field.column.isSame(before, after))
                    return buildHeader(filter, after)
            }
        }
        return null
    }

    fun addHeaders(flow: Flow<PagingData<BookAndAuthors>>): Flow<PagingData<Any>> {
        return flow.map {data ->
            data.insertSeparators { before, after ->
                filter?.let {
                    compareAndBuildeHeader(it, before, after)
                }

            }
        }
    }
}
