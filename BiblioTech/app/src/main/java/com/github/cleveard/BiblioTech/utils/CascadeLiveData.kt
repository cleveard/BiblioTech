package com.github.cleveard.BiblioTech.utils

import androidx.lifecycle.*

/**
 * Build a LiveData from a LiveData source that can be changed
 */
class CascadeLiveData<T>: MediatorLiveData<T>() {
    /**
     * The source live data
     */
    var sourceValue: LiveData<T> = MutableLiveData<T>(null)
        set(l) {
            removeSource(field)
            field = l
            addSource(field) {
                value = it
            }
        }
}
