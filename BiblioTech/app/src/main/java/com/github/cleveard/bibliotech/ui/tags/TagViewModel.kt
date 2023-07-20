package com.github.cleveard.bibliotech.ui.tags

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.github.cleveard.bibliotech.db.BookRepository
import com.github.cleveard.bibliotech.db.TagEntity
import com.github.cleveard.bibliotech.utils.GenericViewModel

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
    override val selection: DataBaseSelectionSet = DataBaseSelectionSet(repo.FilteredBookCount(repo.tagFlags, TagEntity.SELECTED, viewModelScope))

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