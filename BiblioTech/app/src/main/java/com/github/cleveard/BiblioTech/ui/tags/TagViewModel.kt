package com.github.cleveard.BiblioTech.ui.tags

import android.app.Application
import com.github.cleveard.BiblioTech.db.BookRepository
import com.github.cleveard.BiblioTech.db.Tag
import com.github.cleveard.BiblioTech.utils.GenericViewModel

/**
 * View model for the tags fragment
 * @param app The application
 */
class TagViewModel(app: Application): GenericViewModel<Tag>(app) {
    init {
        // Setup the selection handler
        applyExtra = {
            it.selected = selection.isSelected(it.tag.id)
        }
    }

    /**
     * Repository with the tag data
     */
    val repo: BookRepository = BookRepository.repo

    /**
     * Tag recycler adapter
     */
    internal val adapter = TagsAdapter(this)

    init {
        selection.onSelectionChanged = {
            adapter.refresh()
        }
    }
}