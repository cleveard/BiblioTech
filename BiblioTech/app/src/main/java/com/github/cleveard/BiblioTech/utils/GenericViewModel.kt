package com.github.cleveard.BiblioTech.utils

import android.app.Application
import androidx.paging.PagingData
import androidx.paging.map
import com.github.cleveard.BiblioTech.db.Selectable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Generic view model base class
 * @param <T> The data type that is being handled by the view model
 * @param app The application
 */
abstract class GenericViewModel<T>(app: Application) : BaseViewModel(app) where T: Selectable {
    /**
     * Set a map in the data stream to set the selection state
     */
    fun applySelectionTransform(flow: Flow<PagingData<T>>): Flow<PagingData<T>> {
        return flow.map {
            it.map { b ->
                b.apply { selected = selection.isSelected(id) }
            }
        }
    }
}