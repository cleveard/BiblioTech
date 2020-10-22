package com.example.cleve.bibliotech.ui.tags

import com.example.cleve.bibliotech.db.BookRepository
import com.example.cleve.bibliotech.db.Tag
import com.example.cleve.bibliotech.utils.GenericViewModel

class TagViewModel: GenericViewModel<Tag>() {

    val repo: BookRepository = BookRepository.repo
    internal lateinit var adapter: TagsAdapter

    override fun invalidateUI() {
        adapter.refresh()
    }
}