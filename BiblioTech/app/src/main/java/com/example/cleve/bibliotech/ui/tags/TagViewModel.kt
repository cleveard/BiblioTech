package com.example.cleve.bibliotech.ui.tags

import android.app.Application
import com.example.cleve.bibliotech.db.BookRepository
import com.example.cleve.bibliotech.db.Tag
import com.example.cleve.bibliotech.utils.GenericViewModel

/**
 * View model for the tags fragment
 * @param app The application
 */
class TagViewModel(app: Application): GenericViewModel<Tag>(app) {

    /**
     * Repository with the tag data
     */
    val repo: BookRepository = BookRepository.repo

    /**
     * Tag recycler adapter
     */
    internal val adapter = TagsAdapter(this)

    /**
     * @inheritDoc
     */
    override fun invalidateUI() {
        adapter.refresh()
    }
}