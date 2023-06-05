package com.github.cleveard.bibliotech.gb

import androidx.paging.PagingSource
import androidx.paging.PagingState


/**
 * PagingSource for queries from books.google.com
 * @param itemCount The total number of items the query returns.
 */
abstract class GoogleQueryPagingSource<T: S, S: Any>(private var itemCount: Int = 0) : PagingSource<Long, S>() {
    /**
     * Result of a query
     * @param list List of books in the first page returned by the query
     * @param itemCount Total number of items returned by the query
     */
    data class LookupResult<T>(val list: List<T>, val itemCount: Int)

    abstract suspend fun runQuery(index: Long, loadSize: Long): LookupResult<T>?

    /**
     * Load a page from the query
     * @param params The params for the load
     * @return The result of the load
     */
    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, S> {
        // Get the page size and index we want to load
        val loadSize = params.loadSize.toLong()
        val index = params.key?:0

        val result = try {
            // query Google books to get the list
            val query = runQuery(index, loadSize)
            itemCount = query?.itemCount?: itemCount
            query?.list?: emptyList()
        } catch (e: Exception) {
            // Return an error if we got one
            return LoadResult.Error(e)
        }

        // Calculate the next page key. Add the number of books we loaded to the current page index
        // If the result is null, or empty or we get to the number of books, set the key to null
        // which means that we are at the end of the pages
        val nextKey = if (result.isEmpty() || index + result.size >= itemCount) null else index + result.size
        // Return the loaded books
        return LoadResult.Page(
            data = result,
            prevKey = null,
            nextKey = nextKey
        )
    }

    /** @inheritDoc */
    override fun getRefreshKey(state: PagingState<Long, S>): Long? {
        return state.anchorPosition?.toLong()
    }

    override val jumpingSupported: Boolean
        get() = true
}
