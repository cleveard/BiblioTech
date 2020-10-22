package com.example.cleve.bibliotech.utils

import android.content.Context
import android.graphics.PorterDuff
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import androidx.paging.map
import com.example.cleve.bibliotech.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

abstract class BaseViewModel : ViewModel() {
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

        private var _lastSelection: MutableLiveData<Long> = MutableLiveData(null)
        val lastSelection: LiveData<Long>
            get() { return _lastSelection }
        fun clearLastSelection() {
            _lastSelection.value = null
        }

        private fun hasSelectChanged() {
            val value = _inverted || _selection.size > 0
            if (value != _hasSelection.value)
                _hasSelection.value = value
            invalidateUI()
        }

        fun selectAll(select: Boolean) {
            _selection.clear()
            _inverted = select
            _lastSelection.value = null
            hasSelectChanged()
        }

        fun select(bookId: Long, select: Boolean) {
            if (select != _inverted) {
                _lastSelection.value = bookId
                _selection.add(bookId)
            } else {
                _lastSelection.value = null
                _selection.remove(bookId)
            }
            hasSelectChanged()
        }

        fun toggle(bookId: Long) {
            if (_selection.contains(bookId)) {
                _lastSelection.value = null
                _selection.remove(bookId)
            } else {
                _lastSelection.value = bookId
                _selection.add(bookId)
            }
            hasSelectChanged()
        }

        fun invert() {
            _lastSelection.value = null
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

    companion object {
        fun setupIcon(context: Context?, menu: Menu, id: Int): MenuItem {
            val item = menu.findItem(id)
            item?.iconTintList = context?.resources?.getColorStateList(R.color.enable_icon_tint, null)
            item?.iconTintMode = PorterDuff.Mode.MULTIPLY
            return item
        }
    }
}