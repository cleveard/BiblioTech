package com.example.cleve.bibliotech.ui.books

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.TextAppearanceSpan
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

val nameStyles = arrayOf(R.style.HeaderName0, R.style.HeaderName1, R.style.HeaderName2)
val itemStyles = arrayOf(R.style.HeaderItem0, R.style.HeaderItem1, R.style.HeaderItem2)

class BooksViewModel : GenericViewModel<BookAndAuthors>() {

    val repo: BookRepository = BookRepository.repo
    internal lateinit var adapter: BooksAdapter
    internal lateinit var layoutManager: LinearLayoutManager
    private var flowJob: Job? = null
    private var filter: BookFilter? = null

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
        val flow = applySelectionTransform(pager.flow)
            .cachedIn(viewModelScope)
        flowJob = viewModelScope.launch {
            flow.collectLatest { data ->
                adapter.submitData(data)
            }
        }
    }

    fun buildHeader(context: Context): Spannable? {
        val filter = this.filter?: return null

        val pos = layoutManager.findFirstVisibleItemPosition()
        val book = if (pos == RecyclerView.NO_POSITION)
            null
        else
            adapter.peek(pos)

        val span = SpannableStringBuilder()

        fun appendSpan(text: String, vararg effects: Any?) {
            val start = span.length
            span.append(text)
            for (eff in effects) {
                eff?.let { span.setSpan(it, start, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
        }

        val locale = context.resources.configuration.locales[0]
        val added = HashSet<BookFilter.Column>()
        for ((i, field) in filter.orderList.withIndex()) {
            if (book == null) {
                span.appendLine()
            } else if (field.headers && !added.contains(field.column)) {
                added.add(field.column)
                val nameStyle = nameStyles[if (i < nameStyles.size) i else nameStyles.size - 1]
                val itemStyle = itemStyles[if (i < itemStyles.size) i else itemStyles.size - 1]
                if (span.isNotEmpty())
                    span.appendLine()
                appendSpan("${context.resources.getString(field.column.nameResourceId)}: ", TextAppearanceSpan(context, nameStyle))
                appendSpan(
                    field.column.getValue(book, locale),
                    itemStyle?.let { TextAppearanceSpan(context, it) }
                )
            }
        }

        return if (span.isNotEmpty()) span else null
    }
}
