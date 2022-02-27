package com.github.cleveard.bibliotech.gb

import com.github.cleveard.bibliotech.db.*
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.cleveard.bibliotech.annotations.EnvironmentValues
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.services.books.v1.Books as BooksService
import com.google.api.client.extensions.android.json.AndroidJsonFactory
import com.google.api.services.books.v1.BooksRequestInitializer
import com.google.api.services.books.v1.model.Volume
import com.google.api.services.books.v1.model.Volumes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.MalformedURLException
import java.util.*
import kotlin.collections.ArrayList

private const val apiKey = "GOOGLE_BOOKS_API_KEY"

/**
 * Class used to query books on books.google.com
 * Constructor is private because all methods are in the companion object
 */
@EnvironmentValues(apiKey)
internal class GoogleBookLookup {

    /**
     * Exception thrown when a book query fails
     */
    class LookupException(message: String?, cause: Throwable? = null) : java.lang.Exception(message, cause)

    /**
     * PagingSource for queries from books.google.com
     * @param query The base query to books.google.com
     * @param itemCount The total number of items the query returns. We get the first page of
     *                  the query before we build the paging source and get the itemCount from there.
     * @param list The list of item in the first page of the query. We immediately return these
     *             the first time we ask for the first page.
     */
    private inner class BookQueryPagingSource(private val query: String, private var itemCount: Int = 0, private val list: List<BookAndAuthors>? = null):
        GoogleQueryPagingSource<BookAndAuthors, Any>(itemCount)
    {
        /**
         * Run a query for volumes
         * @param index The index of the first result returned
         * @param loadSize The number of items to return
         * @return The result of the load
         */
        override suspend fun runQuery(index: Long, loadSize: Long): LookupResult<BookAndAuthors>? {
            val result = if (index == 0L && list != null && itemCount != 0) {
                // The first time we asl for page 0, return the list we already loaded
                LookupResult(list, itemCount)
            } else {
                generalLookup(query, index, loadSize)
            }

            // Set a temporary id for the books to be their position in the stream
            result?.list?.forEachIndexed { i, b ->
                b.book.id = (index + i)
            }

            return result
        }

        /** @inheritDoc */
        override fun getRefreshKey(state: PagingState<Long, Any>): Long? {
            return state.anchorPosition?.toLong()
        }

        override val jumpingSupported: Boolean
            get() = true
    }

    companion object {
        @Suppress("SpellCheckingInspection")
        private const val kISBNParameter = "isbn:%s"
        private const val kTitleParameter = "title:\"%s\""
        private const val kAuthorParameter = "author:\"%s\""
        private const val kBooksVolumes = "books#volumes"
        private const val kBooksVolume = "books#volume"
        private const val kISBN_13 = "ISBN_13"
        private const val kISBN_10 = "ISBN_10"
        private val kSmallThumb = arrayOf<(Volume.VolumeInfo.ImageLinks) -> String?>(
            { it.thumbnail },
            { it.smallThumbnail }
        )
        private val kThumb = arrayOf<(Volume.VolumeInfo.ImageLinks) -> String?>(
            { it.large },
            { it.medium },
            { it.small },
            { it.thumbnail },
            { it.extraLarge },
            { it.smallThumbnail }
        )

        /** The book service object */
        val service: BooksService = BooksService.Builder(NetHttpTransport(), AndroidJsonFactory(), null)
            .setBooksRequestInitializer(BooksRequestInitializer(GoogleBookLookup_Environment[apiKey]))
            .build()
    }

    /**
     * Look up a book by ISBN
     * @param isbn The isbn to lookup
     * @return The LookupResult for the query, or null
     */
    @Throws(LookupException::class)
    suspend fun lookupISBN(isbn: String): GoogleQueryPagingSource.LookupResult<BookAndAuthors>? {
        return queryBooks(String.format(kISBNParameter, isbn))
    }

    /**
     * Get search terms for title and author lookup
     * @param title The title to search for
     * @param author The author to search for
     * @return The query search terms
     */
    fun getTitleAuthorQuery(title: String, author: String): String {
        return if (title.isNotEmpty()) {
            if (author.isNotEmpty())
                "${String.format(kTitleParameter, title)} ${String.format(kAuthorParameter, author)}"
            else
                String.format(kTitleParameter, title)
        } else if (author.isNotEmpty())
            String.format(kAuthorParameter, author)
        else
            ""
    }

    /**
     * Lookup books using a general query
     * @param query The query
     * @param index The index of the result page to return
     * @param pageCount The number of results to return
     * @return The LookupResult for the query, or null
     */
    @Throws(LookupException::class)
    suspend fun generalLookup(query: String, index: Long = 0, pageCount: Long = 10): GoogleQueryPagingSource.LookupResult<BookAndAuthors>? {
        return queryBooks(query, index, pageCount.coerceAtMost(40L))
    }

    /**
     * Lookup books using a general query
     * @param search The query
     * @param itemCount The total number of items the query will return, if available
     * @param list The books in the first page of the query, if available
     * @return The PagingSource for the book in the query
     */
    fun generalLookupPaging(search: String, itemCount: Int = 0, list: List<BookAndAuthors>? = null): PagingSource<Long, Any> {
        return BookQueryPagingSource(search, itemCount, list)
    }

    suspend fun getSeries(seriesId: String): SeriesEntity? {
        @Suppress("BlockingMethodInNonBlockingContext")
        return withContext(Dispatchers.IO) {
            service.series().get(mutableListOf(seriesId)).execute()?.let {
                val list = it.series
                if (list.isEmpty())
                    null
                else
                    SeriesEntity(0L, list[0].seriesId, list[0].title, 0)
            }
        }
    }

    /**
     * Get a thumbnail link from a json object
     * @param info The volume info object
     * @param thumbs The list of possible thumbnail value names in proper order
     * @return The thumbnail URL or ""
     * We search for the URL using the names in thumbs and return the first one found
     */
    private fun getThumbnail(info: Volume.VolumeInfo, thumbs: Array<(Volume.VolumeInfo.ImageLinks) -> String?>) : String {
        // Return "" if there aren't any image links
        val links = info.imageLinks?: return ""
        for (thumb in thumbs) {
            val link = thumb(links)
            // Return the link if we find it
            if (!link.isNullOrEmpty())
                return link
        }
        return ""
    }

    /**
     * Get the ISBN from the array of identifiers
     * @param identifiers The array of identifiers
     * @return The ISBN, or null if there isn't one
     */
    @Throws(Exception::class)
    private fun findISBNs(identifiers: List<Volume.VolumeInfo.IndustryIdentifiers>): ArrayList<IsbnEntity> {
        // Return array of ISBN-10 and ISBN-13 numbers
        return identifiers.asSequence()
            .filter { it.type == kISBN_13 || it.type == kISBN_10 }
            .mapTo(ArrayList()) {
                IsbnEntity(0, it.identifier)
            }
    }

    /**
     * Map a Volume to a BookAndAuthors object
     * @param volume The volume object
     * @return The data from the json object in a BookAndAuthors object
     */
    @Throws(LookupException::class)
    private fun mapVolume(volume: Volume): BookAndAuthors {
        // Make sure the object is formatted properly
        val info = volume.volumeInfo
        val kind = volume.kind
        if (kind != kBooksVolume) throw LookupException(
            "Invalid Response"
        )

        // Get the categories
        val categories = ArrayList<CategoryEntity>()
        info.categories?.filter { !it.isNullOrEmpty() }?.mapTo(categories) { CategoryEntity(0, it) }
        // Put the main category at the top of the list, if it isn't there
        val cat = info.mainCategory?: ""
        val catIndex = categories.indexOfFirst { it.category == cat }
        if (catIndex > 0) {
            val tmp = categories[catIndex]
            for (i in catIndex downTo 1) {
                categories[i] = categories[i - 1]
            }
            categories[0] = tmp
        }

        val seriesOrder: Int?
        val series = volume.volumeInfo.seriesInfo.volumeSeries.let {
            if (it.isEmpty()) {
                seriesOrder = null
                null
            } else {
                seriesOrder = it[0].orderNumber
                SeriesEntity(0L, it[0].seriesId, "", 0)
            }
        }

        // Return the book object
        return BookAndAuthors(
            book = BookEntity(
                id = 0,
                volumeId = volume.id,
                sourceId = "books.google.com",
                title = info.title?: "",
                subTitle = info.subtitle?: "",
                description = info.description?: "",
                pageCount = info.pageCount?: 0,
                bookCount = 1,
                linkUrl = info.infoLink?: "",
                rating = info.averageRating?: 0.0,
                seriesId = null,
                seriesOrder = seriesOrder,
                added = Date(0),
                modified = Date(0),
                smallThumb = getThumbnail(info, kSmallThumb),
                largeThumb = getThumbnail(info, kThumb),
                flags = 0
            ),
            series = series,
            authors = info.authors?.filter { !it.isNullOrEmpty() }?.map { AuthorEntity(0, it) }?: emptyList(),
            categories = categories,
            tags = ArrayList(),
            isbns = info.industryIdentifiers?.let { findISBNs(it) } ?: emptyList()
        )
    }

    /**
     * Map a volumes query response to a LookupResult
     * @param volumes The query response converted to a volumes object
     */
    @Throws(LookupException::class)
    private fun mapResponse(volumes: Volumes): GoogleQueryPagingSource.LookupResult<BookAndAuthors>? {
        // Do we have a list of books
        if (volumes.kind == kBooksVolumes) {
            // Get the array of books from the object
            val items = volumes.items?: return null
            val count = items.size
            // If count is 0, then nothing left to do
            if (count == 0)
                return null
            // Return the result
            return GoogleQueryPagingSource.LookupResult(
                // Map the Volume to BookAndAuthors
                items.map { mapVolume(it) },
                volumes.totalItems
            )
        }
        throw LookupException("Invalid Response")
    }

    /**
     * Run a query and return a result
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    @Throws(LookupException::class)
    private suspend fun queryBooks(query: String?, page: Long = 0, itemCount: Long = 10) : GoogleQueryPagingSource.LookupResult<BookAndAuthors>? {
        // Run in an IO context to handle coroutines properly
        return withContext(Dispatchers.IO) {
            try {
                val list = service.volumes().list(query?: "")
                list.prettyPrint = true
                list.startIndex = page
                list.maxResults = itemCount
                val volumes: Volumes = list.execute()
                // Parse the json object
                return@withContext  mapResponse(volumes)
            } catch (e: MalformedURLException) {
                // Throw an exception if we formed a bad URL
                throw LookupException("Bad URL: $query: $e", e)
            } catch (e: Exception) {
                // Pass on other exceptions
                throw LookupException("Unknown Error $e", e)
            }
        }
    }
}