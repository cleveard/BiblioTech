package com.example.cleve.bibliotech.utils

import android.graphics.PorterDuff
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import androidx.paging.map
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.db.Selectable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

abstract class GenericViewModel<T> : BaseViewModel() where T: Selectable {
    fun applySelectionTransform(flow: Flow<PagingData<T>>): Flow<PagingData<T>> {
        return flow.map {
            it.map { b ->
                b.apply { selected = selection.isSelected(id) }
            }
        }
    }
}