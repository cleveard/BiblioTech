package com.github.cleveard.bibliotech.utils

import android.app.Application

/**
 * Generic view model base class
 * @param <T> The data type that is being handled by the view model
 * @param app The application
 */
abstract class GenericViewModel<T : Any>(app: Application) : BaseViewModel(app) {
}