package com.example.cleve.bibliotech.ui.books

import com.example.cleve.bibliotech.db.BookRepository
import com.example.cleve.bibliotech.utils.BaseBooksViewModel

class BooksViewModel : BaseBooksViewModel() {

    val repo: BookRepository = BookRepository.repo
    internal lateinit var adapter: BooksAdapter

    override fun invalidateUI() {
        adapter.refresh()
    }
}
