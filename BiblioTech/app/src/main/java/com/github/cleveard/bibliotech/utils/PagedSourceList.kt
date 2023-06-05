package com.github.cleveard.bibliotech.utils

import androidx.paging.PagingSource
import androidx.paging.PagingState

class PagingSourceList<T: Any>(private val list: List<T>): PagingSource<Int, T>() {
    override val jumpingSupported: Boolean
        get() = true

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        if (list.isEmpty())
            return LoadResult.Page(emptyList(), null, null)

        val key = params.key?.coerceAtLeast(0)?: 0
        if (key >= list.size)
            return LoadResult.Error(IllegalArgumentException("Key $key is past the end of the data"))
        var end = key + params.loadSize

        val nextKey = if (end < list.size) end else {
            end = list.size
            null
        }
        val prevKey = if (key == 0) null else (key - params.loadSize).coerceAtLeast(0)

        return LoadResult.Page(list.subList(key, end), prevKey, nextKey)
    }
}