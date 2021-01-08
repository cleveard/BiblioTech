package com.github.cleveard.BiblioTech.ui.tags

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.github.cleveard.BiblioTech.db.BookEntity
import com.github.cleveard.BiblioTech.db.BookRepository
import com.github.cleveard.BiblioTech.db.TagEntity
import com.github.cleveard.BiblioTech.utils.GenericViewModel

/**
 * View model for the tags fragment
 * @param app The application
 */
class TagViewModel(app: Application): GenericViewModel<TagEntity>(app) {
    /**
     * Repository with the tag data
     */
    val repo: BookRepository = BookRepository.repo

    /**
     * Selection set for tags
     */
    override val selection: DataBaseSelectionSet = DataBaseSelectionSet(repo.tagFlags, TagEntity.SELECTED, viewModelScope).also {
        it.filter = null
    }

    /**
     * Tag recycler adapter
     */
    internal val adapter = TagsAdapter(this)

    /**
     * Selection change listener
     */
    private val selectChange = {
        adapter.refresh()
    }.also { selection.onSelectionChanged.add(it) }

    /**
     * @inheritDoc
     */
    override fun onCleared() {
        selection.onSelectionChanged.remove(selectChange)
        super.onCleared()
    }
}