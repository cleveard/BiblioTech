package com.example.cleve.bibliotech.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import androidx.paging.map
import com.example.cleve.bibliotech.db.BookAndAuthors
import com.example.cleve.bibliotech.db.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

abstract class BaseBooksViewModel : ViewModel() {
    // Handler for selection
    inner class Selection {
        private var _inverted: Boolean = false
        val inverted: Boolean
            get() { return _inverted }
        private var _selection: HashSet<Long> = HashSet()
        val selection: Array<Any>
            get() { return _selection.toArray() }
        private val _hasSelection = MutableLiveData(false)

        val hasSelection: LiveData<Boolean>
            get() { return _hasSelection }

        private fun hasSelectChanged() {
            val value = _inverted || _selection.size > 0
            if (value != _hasSelection.value)
                _hasSelection.value = value
            invalidateUI()
        }

        fun selectAll(select: Boolean) {
            _selection.clear()
            _inverted = select
            hasSelectChanged()
        }

        fun select(bookId: Long, select: Boolean) {
            if (select != _inverted)
                _selection.add(bookId)
            else
                _selection.remove(bookId)
            hasSelectChanged()
        }

        fun toggle(bookId: Long) {
            if (_selection.contains(bookId))
                _selection.remove(bookId)
            else
                _selection.add(bookId)
            hasSelectChanged()
        }

        fun invert() {
            _inverted = !_inverted
            hasSelectChanged()
        }

        fun isSelected(bookId: Long): Boolean {
            return _selection.contains(bookId) != _inverted
        }
    }

    // Pager and flow
    val selection = Selection()

    abstract fun invalidateUI()

    fun applySelectionTransform(flow: Flow<PagingData<BookAndAuthors>>): Flow<PagingData<BookAndAuthors>> {
        return flow.map {
            it.map { b ->
                b.apply { selected = selection.isSelected(book.id) }
            }
        }
    }
}