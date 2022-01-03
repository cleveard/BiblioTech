package com.github.cleveard.bibliotech.gb

import android.net.Uri
import com.github.cleveard.bibliotech.db.*
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.cleveard.bibliotech.annotations.EnvironmentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

private const val apiKey = "GOOGLE_BOOKS_API_KEY"
private const val oauthKey = "GOOGLE_BOOK_OAUTH_ID"

/**
 * Class used to query books on books.google.com
 * Constructor is private because all methods are in the companion object
 */
@EnvironmentValues(apiKey, oauthKey)
internal class GoogleBookLookup private constructor() {
    /**
     * Exception thrown when a book query fails
     */
    class LookupException(message: String?) : java.lang.Exception(message)

    /**
     * Result of a query
     * @param list List of books in the first page returned by the query
     * @param itemCount Total number of items returned by the query
     */
    data class LookupResult(val list: List<BookAndAuthors>, val itemCount: Int)

    /**
     * PagingSource for queries from books.google.com
     * @param query The base query to books.google.com
     * @param itemCount The total number of items the query returns. We get the first page of
     *                  the query before we build the paging source and get the itemCount from there.
     * @param list The list of item in the first page of the query. We immediately return these
     *             the first time we ask for the first page.
     */
    private class BookQueryPagingSource(private val query: String, private var itemCount: Int = 0, private val list: List<BookAndAuthors>? = null) : PagingSource<Int, Any>() {
        /**
         * Load a page from the query
         * @param params The params for the load
         * @return The result of the load
         */
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Any> {
            // Get the page size and index we want to load
            val loadSize = params.loadSize
            val index = params.key?:0

            val result: List<BookAndAuthors>
            if (index == 0 && list != null && itemCount != 0) {
                // The first time we asl for page 0, return the list we already loaded
                result = list
            } else {
                try {
                    // query Google books to get the list
                    val query = generalLookup(query, index, loadSize)
                    result = query?.list?: emptyList()
                    itemCount = query?.itemCount?: itemCount
                } catch (e: Exception) {
                    // Return an error if we got one
                    return LoadResult.Error(e)
                }
            }

            // Set a temporary id for the books to be their position in the stream
            for ((i, b) in result.withIndex())
                b.book.id = (index + i).toLong()

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
        override fun getRefreshKey(state: PagingState<Int, Any>): Int? {
            return state.anchorPosition
        }

        override val jumpingSupported: Boolean
            get() = true
    }

    companion object {
        @Suppress("SpellCheckingInspection")
        private val kKey = "&key=${GoogleBookLookup_Environment[apiKey]}"
        private const val kURL = "https://www.googleapis.com/books/v1"
        private const val kVolumesCollection = "volumes"
        private const val kISBNParameter = "isbn:%s"
        private const val kTitleParameter = "title:\"%s\""
        private const val kAuthorParameter = "author:\"%s\""
        private const val kKind = "kind"
        private const val kBooksVolumes = "books#volumes"
        private const val kItemCount = "totalItems"
        private const val kItems = "items"
        private const val kTitle = "title"
        private const val kSubTitle = "subtitle"
        private const val kBooksVolume = "books#volume"
        private const val kType = "type"
        private const val kIdentifier = "identifier"
        private const val kIndustryIdentifiers = "industryIdentifiers"
        private const val kVolumeInfo = "volumeInfo"
        private const val kISBN_13 = "ISBN_13"
        private const val kISBN_10 = "ISBN_10"
        private const val kVolumeID = "id"
        private const val kPageCount = "pageCount"
        private const val kDescription = "description"
        private const val kImageLinks = "imageLinks"
        private val kSmallThumb = arrayOf(
            "thumbnail",
            "smallThumbnail"
        )
        private val kThumb = arrayOf(
            "large",
            "medium",
            "small",
            "thumbnail",
            "extraLarge",
            "smallThumbnail"
        )
        private const val kAuthors = "authors"
        private const val kCategories = "categories"
        private const val kMainCategory = "mainCategory"
        private const val kVolumeLink = "infoLink"
        private const val kRating = "averageRating"

        /**
         * Look up a book by ISBN
         * @param isbn The isbn to lookup
         * @return The LookupResult for the query, or null
         */
        @Throws(LookupException::class)
        suspend fun lookupISBN(isbn: String): LookupResult? {
            return queryBooks(buildUrl(kVolumesCollection, Uri.encode(String.format(kISBNParameter, isbn))))
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
        suspend fun generalLookup(query: String, index: Int = 0, pageCount: Int = 10): LookupResult? {
            val loadSize = pageCount.coerceAtMost(40)
            val spec = buildUrl(
                kVolumesCollection,
                "${Uri.encode(query)}&startIndex=${index}&maxResults=${loadSize}"
            )

            return queryBooks(spec)
        }

        /**
         * Lookup books using a general query
         * @param search The query
         * @param itemCount The total number of items the query will return, if available
         * @param list The books in the first page of the query, if available
         * @return The PagingSource for the book in the query
         */
        fun generalLookupPaging(search: String, itemCount: Int = 0, list: List<BookAndAuthors>? = null): PagingSource<Int, Any> {
            return BookQueryPagingSource(search, itemCount, list)
        }

        /**
         * Get a json value from json object
         * @param <T> The type of the object being returned
         * @param json The json object
         * @param name The name of the value
         * @param defaultValue Default value returned if the value isn't present
         */
        @Throws(Exception::class)
        private inline fun <reified T> getJsonValue(json: JSONObject, name: String, defaultValue: T): T {
            val value = json.opt(name)
            return if (value != null && value is T) value else defaultValue
        }

        /**
         * Get a list value from a json array object
         * @param <T> The type of the values in the list
         * @param <I> The type of the values in the json object
         * @param json The json object
         * @param name The name of the list
         * @param lambda A lambda that converts the json type to the list type
         * @return The list of values
         */
        private inline fun <T, reified I> getJsonValue(json: JSONObject, name: String, lambda: (I) -> (T)): MutableList<T> {
            // Get the json array and return an empty list if it doesn't exist
            val array = json.optJSONArray(name)
            array?: return ArrayList(0)

            // Loop through the array and add them to the result list
            val count = array.length()
            val list = ArrayList<T>(count)
            for (i in 0 until count) {
                list.add(lambda(array[i] as I))
            }
            return list
        }

        /**
         * Get a thumbnail link from a json object
         * @param json The json object
         * @param thumbs The list of possible thumbnail value names in proper order
         * @return The thumbnail URL or ""
         * We search for the URL using the names in thumbs and return the first one found
         */
        private fun getThumbnail(json: JSONObject, thumbs: Array<String>) : String {
            // Return "" if there aren't any image links
            if (!json.has(kImageLinks))
                return ""

            // Get the image links and search for a URL
            val links = json.getJSONObject(kImageLinks)
            for (thumb in thumbs) {
                val link = getJsonValue(links, thumb, "")
                // Return the link if we find it
                if (link != "")
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
        private fun findISBNs(identifiers: JSONArray): ArrayList<IsbnEntity> {
            val result = ArrayList<IsbnEntity>()
            var i = identifiers.length()
            // Loop through the identifiers
            while (--i >= 0) {
                // Get the next identifier
                val id = identifiers.getJSONObject(i)
                // Get the identifier type
                val type = id.getString(kType)
                // If we get an ISBN 13 identifier, then return it
                if (type == kISBN_13)
                    result.add(IsbnEntity(id = 0, isbn = id.getString(kIdentifier)))
                // If we get an ISBN 10 identifier, then remember it
                if (type == kISBN_10)
                    result.add(IsbnEntity(id = 0, isbn = id.getString(kIdentifier)))
            }
            // Return null or the ISBN 10 identifier, if no ISBN 13 id was found
            return result
        }

        /**
         * Break authors name and put in AuthorEntity
         * @param in_name The name of the author
         * @return An AuthorEntity with the name
         */
        private fun separateAuthor(in_name: String) : AuthorEntity {
            return AuthorEntity(0, in_name)
        }

        /**
         * Parse a book from a json object
         * @param json The json object
         * @return The data from the json object in a BookAndAuthors object
         */
        @Throws(Exception::class)
        fun parseJSON(json: JSONObject): BookAndAuthors {
            // Make sure the object is formatted properly
            val volume =
                json.getJSONObject(kVolumeInfo)
            val kind =
                json.getString(kKind)
            if (kind != kBooksVolume) throw Exception(
                "Invalid Response"
            )

            // Get the categories
            val categories = getJsonValue<CategoryEntity, String>(volume, kCategories) { CategoryEntity(0, it) }
            // Put the main category at the top of the list, if it isn't there
            val cat = getJsonValue(volume, kMainCategory, "")
            val catIndex = categories.indexOfFirst { it.category == cat }
            if (catIndex > 0) {
                val tmp = categories[catIndex]
                for (i in catIndex downTo 1) {
                    categories[i] = categories[i - 1]
                }
                categories[0] = tmp
            }

            // Return the book object
            return BookAndAuthors(
                book = BookEntity(
                    id = 0,
                    volumeId = getJsonValue(json, kVolumeID, ""),
                    sourceId = "books.google.com",
                    title = getJsonValue(volume, kTitle, ""),
                    subTitle = getJsonValue(volume, kSubTitle, ""),
                    description = getJsonValue(volume, kDescription, ""),
                    pageCount = getJsonValue(volume, kPageCount, 0),
                    bookCount = 1,
                    linkUrl = getJsonValue(volume, kVolumeLink, ""),
                    rating = getJsonValue(volume, kRating, 1.0),
                    added = Date(0),
                    modified = Date(0),
                    smallThumb = getThumbnail(volume, kSmallThumb),
                    largeThumb = getThumbnail(volume, kThumb),
                    flags = 0
                ),
                authors = getJsonValue<AuthorEntity, String>(volume, kAuthors) {
                    separateAuthor(it)
                },
                categories = categories,
                tags = ArrayList(),
                isbns = run {
                    if (volume.has(kIndustryIdentifiers))
                        findISBNs(volume.getJSONArray(kIndustryIdentifiers))
                    else
                        emptyList()
                }
            )
        }

        /**
         * Parse the response for a query
         * @param json The query converted to a json object
         */
        @Throws(Exception::class)
        private fun parseResponse(json: JSONObject): LookupResult? {
            val list: MutableList<BookAndAuthors> = ArrayList(1)
            val kind = json.getString(kKind)
            if (kind == kBooksVolumes) {
                // We have a list of books
                try {
                    // Get the array of books from the object
                    val items = json.getJSONArray(kItems)
                    val count = items.length()
                    // If count is 0, then nothing left to do
                    if (count == 0)
                        return null
                    // Loop through the json array and add each one to the result list
                    for (i in 0 until count) {
                        list.add(parseJSON(items.getJSONObject(i)))
                    }
                    // Get the total number of items returned by the query
                    val totalItems = json.getInt(kItemCount)
                    // Return the result
                    return LookupResult(list, totalItems)
                } catch (e: JSONException) {
                    // Stop on a JSON exception.
                    return null
                }
            }
            throw Exception("Invalid Response")
        }

        /**
         * For a query url
         * @param collection The collection we are querying
         * @param parameters The query parameters
         * @return The URL
         */
        @Suppress("SameParameterValue")
        private fun buildUrl(collection: String, parameters: String): String {
            return String.format(
                "%s/%s?q=%s%s",
                kURL,
                collection,
                parameters,
                kKey
            )
        }

        /**
         * Run a query and return a result
         */
        @Suppress("BlockingMethodInNonBlockingContext")
        @Throws(LookupException::class)
        private suspend fun queryBooks(spec: String?) : LookupResult? {
            // Run in an IO context to handle coroutines properly
            return withContext(Dispatchers.IO) {
                var bookClient: HttpURLConnection? = null
                try {
                    // Setup the URL to google books
                    val url = URL(spec)
                    bookClient = url.openConnection() as HttpURLConnection
                    // Did we get a valid return
                    val status = bookClient.responseCode
                    // If not, through an error
                    if (status != 200) throw Exception(
                        String.format(
                            "HTTP Error %d",
                            status
                        )
                    )
                    // Get the data stream for the response
                    val content: InputStream =
                        BufferedInputStream(bookClient.inputStream)
                    val input = InputStreamReader(content)
                    val reader = BufferedReader(input)
                    val responseBuilder = StringBuilder()
                    var lineIn: String?
                    // Read the response into a single string
                    while (reader.readLine().also { lineIn = it } != null) {
                        responseBuilder.append(lineIn)
                    }
                    val responseString = responseBuilder.toString()
                    // Create the json object
                    val json = JSONObject(responseString)
                    // Parse the json object
                    return@withContext  parseResponse(json)
                } catch (e: MalformedURLException) {
                    // Throw an exception if we formed a bad URL
                    throw LookupException("Bad URL: $spec: $e")
                } catch (e: Exception) {
                    // Pass on other exceptions
                    throw LookupException(e.toString())
                } finally {
                    // Disconnect from google books
                    bookClient?.disconnect()
                }
            }
        }
    }
}