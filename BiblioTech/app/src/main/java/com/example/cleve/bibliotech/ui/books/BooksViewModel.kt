package com.example.cleve.bibliotech.ui.books

import com.example.cleve.bibliotech.db.BookAndAuthors
import com.example.cleve.bibliotech.db.BookRepository
import com.example.cleve.bibliotech.utils.BaseViewModel

class BooksViewModel : BaseViewModel<BookAndAuthors>() {

    val repo: BookRepository = BookRepository.repo
    internal lateinit var adapter: BooksAdapter

    override fun invalidateUI() {
        adapter.refresh()
    }
}
