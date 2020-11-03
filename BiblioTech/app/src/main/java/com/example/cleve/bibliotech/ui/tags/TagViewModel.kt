package com.example.cleve.bibliotech.ui.tags

import android.app.Application
import com.example.cleve.bibliotech.db.BookRepository
import com.example.cleve.bibliotech.db.Tag
import com.example.cleve.bibliotech.utils.GenericViewModel

class TagViewModel(app: Application): GenericViewModel<Tag>(app) {

    val repo: BookRepository = BookRepository.repo
    internal lateinit var adapter: TagsAdapter

    override fun invalidateUI() {
        adapter.refresh()
    }
}