package com.example.cleve.bibliotech.ui.books

import androidx.lifecycle.viewModelScope
import androidx.paging.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cleve.bibliotech.db.BookAndAuthors
import com.example.cleve.bibliotech.db.BookFilter
import com.example.cleve.bibliotech.db.BookRepository
import com.example.cleve.bibliotech.utils.GenericViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

}
