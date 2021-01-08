package com.github.cleveard.BiblioTech.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

/**
 * Build a LiveData from a LiveData source that can be changed
 */
class CascadeLiveData<T>: MutableLiveData<T>(null) {
    /**
     * Listener for the source data
     */
    private val listener: Observer<T> = Observer<T> { count: T ->
        if (count != null && count != value)
            value = count
    }

    /**
     * The source live data
     */
    var sourceValue: LiveData<T> = MutableLiveData<T>(null)
        set(l) {
            field.removeObserver(listener)
            field = l
            l.observeForever(listener)
        }
}
