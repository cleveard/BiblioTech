package com.github.cleveard.BiblioTech.utils

import android.app.Application
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun interface ApplyExtra<R> {
    fun apply()
}
/**
 * Generic view model base class
 * @param <T> The data type that is being handled by the view model
 * @param app The application
 * @param applyExtra Callback to apply extra processing to the books returned in the flow
 */
abstract class GenericViewModel<T : Any>(app: Application) : BaseViewModel(app) {
    protected var applyExtra: ((T) -> Unit)? = null

    /**
     * Set a map in the data stream to set the selection state
     */
    fun applySelectionTransform(flow: Flow<PagingData<T>>): Flow<PagingData<T>> {
        return applyExtra?.let { a ->
            flow.map {
                it.map { b -> a(b); b }
            }
        }?: flow
    }
}