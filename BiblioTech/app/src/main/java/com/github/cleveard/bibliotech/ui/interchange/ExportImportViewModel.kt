package com.github.cleveard.bibliotech.ui.interchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.cleveard.bibliotech.db.BookEntity
import com.github.cleveard.bibliotech.db.BookRepository

class ExportImportViewModel : ViewModel() {
    /** The book repository **/
    val repo: BookRepository = BookRepository.repo

    /** The filters and counters for the export import fragment */
    val exportCount: BookRepository.FilteredBookCount = repo.FilteredBookCount(repo.bookFlags, BookEntity.SELECTED, viewModelScope)
}
