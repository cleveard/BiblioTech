package com.github.cleveard.bibliotech.ui.interchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.cleveard.bibliotech.db.BookEntity
import com.github.cleveard.bibliotech.db.BookRepository

class ExportImportViewModel : ViewModel() {
    /** The book repository **/
    val repo: BookRepository = BookRepository.repo

    val exportCount: BookRepository.FilteredBookCount = repo.FilteredBookCount(repo.bookFlags, BookEntity.SELECTED, viewModelScope)
}
