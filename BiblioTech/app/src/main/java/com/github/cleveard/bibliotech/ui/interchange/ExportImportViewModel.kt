package com.github.cleveard.bibliotech.ui.interchange

import androidx.lifecycle.ViewModel
import com.github.cleveard.bibliotech.db.BookRepository

class ExportImportViewModel : ViewModel() {
    /** The book repository **/
    val repo: BookRepository = BookRepository.repo
}
