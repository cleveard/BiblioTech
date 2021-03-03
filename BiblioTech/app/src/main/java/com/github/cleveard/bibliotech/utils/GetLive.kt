package com.github.cleveard.bibliotech.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun <T> LiveData<T>.getLive(): T? {
    return withContext(MainScope().coroutineContext) {
        var observer: Observer<T>? = null
        try {
            suspendCoroutine {
                observer = Observer<T> { value ->
                    if (value != null)
                        it.resume(value)
                }.also { obs ->
                    observeForever(obs)
                }
            }
        } finally {
            observer?.let { removeObserver(it) }
        }
    }
}