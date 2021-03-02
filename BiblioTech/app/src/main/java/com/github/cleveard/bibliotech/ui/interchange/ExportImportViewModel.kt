package com.github.cleveard.bibliotech.ui.interchange

import android.widget.ListAdapter
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.github.cleveard.bibliotech.db.BookRepository
import com.github.cleveard.bibliotech.db.ViewEntity

class ExportImportViewModel : ViewModel() {
    /** The book repository **/
    val repo: BookRepository = BookRepository.repo
}
