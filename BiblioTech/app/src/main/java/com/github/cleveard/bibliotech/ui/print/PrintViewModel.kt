package com.github.cleveard.bibliotech.ui.print

import androidx.lifecycle.ViewModel
import com.github.cleveard.bibliotech.db.BookRepository

class PrintViewModel : ViewModel() {
    /** The book repository **/
    val repo: BookRepository = BookRepository.repo
}