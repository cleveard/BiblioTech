package com.github.cleveard.BiblioTech.utils

import android.app.Application
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Generic view model base class
 * @param <T> The data type that is being handled by the view model
 * @param app The application
 */
abstract class GenericViewModel<T : Any>(app: Application) : BaseViewModel(app) {
}