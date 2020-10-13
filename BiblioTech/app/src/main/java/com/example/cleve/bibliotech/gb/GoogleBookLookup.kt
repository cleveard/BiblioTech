package com.example.cleve.bibliotech.gb

import com.example.cleve.bibliotech.db.*
import android.os.AsyncTask
import androidx.paging.PagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

internal class GoogleBookLookup {
    internal interface LookupDelegate {
        fun bookLookupResult(result: List<BookAndAuthors>?, itemCount: Int)
        fun bookLookupError(error: String?)
    }

    class LookupException(message: String?) : java.lang.Exception(message)

    class BookQueryPagingSource(private val query: String, private val itemCount: Int, private var list: List<BookAndAuthors>? = null) : PagingSource<Int, BookAndAuthors>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BookAndAuthors> {
            val loadSize = params.loadSize.coerceAtMost(40)
            val index = params.key?:0
            val spec = buildUrl(
                kVolumesCollection,
                "${query}&startIndex=${index}&maxResults=${loadSize}"
            )
            val result: List<BookAndAuthors>?
            if (index == 0 && list != null) {
                result = list
                list = null
            } else {
                var errorResult: String? = null
                result = withContext(Dispatchers.Default) {
                    queryBooks(spec, object : LookupDelegate {
                        override fun bookLookupError(error: String?) {
                            errorResult = error ?: ""
                        }

                        override fun bookLookupResult(
                            result: List<BookAndAuthors>?,
                            itemCount: Int
                        ) {
                            // Not called by queryBooks
                        }
                    })?.list
                }
                if (errorResult != null)
                    return LoadResult.Error(LookupException(errorResult))
            }

            val nextKey = if (result == null || index + result.size >= itemCount) null else index + result.size
            return LoadResult.Page(
                data = result?: emptyList(),
                prevKey = null,
                nextKey = nextKey
            )
        }
    }

    private var mLookup: LookupTask? = null
    private fun lookup(
        results: LookupDelegate,
        collection: String,
        parameters: String
    ): Boolean {
        if (mLookup != null) return false
        mLookup = LookupTask(this, results)
        mLookup!!.execute(
            buildUrl(collection, parameters)
        )
        return true
    }

    fun lookupISBN(results: LookupDelegate, isbn: String): Boolean {
        return lookup(
            results,
            kVolumesCollection,
            String.format(kISBNParameter, isbn)
        )
    }

    fun generalLookup(results: LookupDelegate, search: String, index: Int = 0, pageCount: Int = 10): Boolean {
        return lookup(
            results,
            kVolumesCollection,
            "${search}&startIndex=${index}&maxResults=${pageCount}"
        )
    }

    private data class LookupResult(val list: List<BookAndAuthors>, val itemCount: Int)

    private class LookupTask constructor(parent: GoogleBookLookup, val mResults: LookupDelegate) :
        AsyncTask<String?, Void?, LookupResult?>() {
        val mParent = WeakReference(parent)
        override fun doInBackground(vararg params: String?): LookupResult? {
            return queryBooks(params[0], mResults)
        }

        override fun onPostExecute(result: LookupResult?) {
            super.onPostExecute(result)
            val parent = mParent.get()
            if (parent != null)
                parent.mLookup = null
            if (result == null)
                mResults.bookLookupResult(null, 0)
            else
                mResults.bookLookupResult(result.list, result.itemCount)
        }

    }

    companion object {
        private const val kKey = "&key=AIzaSyDeQMfnPyhQ23-ndhb9xs9IY_EaSiTxgms"
        private const val kURL = "https://www.googleapis.com/books/v1"
        private const val kVolumesCollection = "volumes"
        private const val kISBNParameter = "isbn:%s"
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

        @Throws(Exception::class)
        private inline fun <reified T> getJsonValue(json: JSONObject, name: String, defaultValue: T): T {
            val value = json.opt(name)
            return if (value != null && value is T) value else defaultValue
        }

        private inline fun <T, reified I> getJsonValue(json: JSONObject, name: String, lambda: (I) -> (T)): MutableList<T> {
            val array = json.optJSONArray(name)
            array?: return ArrayList(0)

            val count = array.length()
            val list = ArrayList<T>(count)
            for (i in 0 until count) {
                list.add(lambda(array[i] as I))
            }
            return list
        }

        private fun getThumbnail(json: JSONObject, thumbs: Array<String>) : String {
            if (!json.has(kImageLinks))
                return ""

            val links = json.getJSONObject(kImageLinks)
            for (thumb in thumbs) {
                val link = getJsonValue(links, thumb, "")
                if (link != "")
                    return link
            }
            return ""
        }

        @Throws(Exception::class)
        private fun findISBN(identifiers: JSONArray): String? {
            var result: String? = null
            var i = identifiers.length()
            while (--i >= 0) {
                val id = identifiers.getJSONObject(i)
                val type = id.getString(kType)
                if (type == kISBN_13)
                    return id.getString(kIdentifier)
                if (type == kISBN_10)
                    result = id.getString(kIdentifier)
            }
            return result
        }

        // Break authors name and put in AuthorEntity
        private fun separateAuthor(
            in_name: String) : AuthorEntity {
            val name = in_name.trim { it <= ' ' }
            // Look for a , assume last, remaining if found
            var lastIndex = name.lastIndexOf(',')
            var lastName = name
            var remainingName = ""
            if (lastIndex > 0) {
                lastName = name.substring(0, lastIndex).trim { it <= ' ' }
                remainingName = name.substring(lastIndex + 1).trim { it <= ' ' }
            } else {
                // look for a space, assume remaining last if found
                lastIndex = name.lastIndexOf(' ')
                if (lastIndex > 0) {
                    lastName = name.substring(lastIndex)
                    remainingName = name.substring(0, lastIndex).trim { it <= ' ' }
                }
            }

            return AuthorEntity(
                id = 0,
                lastName = lastName,
                remainingName = remainingName
            )
        }

        @Throws(Exception::class)
        fun parseJSON(json: JSONObject): BookAndAuthors {
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

            return BookAndAuthors(
                book = BookEntity(
                    id = 0,
                    volumeId = getJsonValue(json, kVolumeID, ""),
                    sourceId = "books.google.com",
                    ISBN = run {
                        if (volume.has(kIndustryIdentifiers))
                            findISBN(volume.getJSONArray(kIndustryIdentifiers))
                        else
                            null
                    },
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
                    largeThumb = getThumbnail(volume, kThumb)
                ),
                authors = getJsonValue<AuthorEntity, String>(volume, kAuthors) {
                    separateAuthor(it)
                },
                categories = categories,
                tags = ArrayList()
            )
        }

        @Throws(Exception::class)
        private fun parseResponse(json: JSONObject): LookupResult? {
            val list: MutableList<BookAndAuthors> = ArrayList(1)
            val kind = json.getString(kKind)
            if (kind == kBooksVolumes) {
                try {
                    val items = json.getJSONArray(kItems)
                    val count = items.length()
                    // If count is 0, then nothing left to do
                    if (count == 0)
                        return null
                    for (i in 0 until count) {
                        list.add(parseJSON(items.getJSONObject(i)))
                    }
                    val totalItems = json.getInt(kItemCount)
                    return LookupResult(list, totalItems)
                } catch (e: JSONException) {
                    // Stop on a JSON exception.
                    return null
                }
            }
            throw Exception("Invalid Response")
        }

        private fun buildUrl(collection: String, parameters: String): String {
            return String.format(
                "%s/%s?q=%s%s",
                kURL,
                collection,
                parameters,
                kKey
            )
        }

        private fun queryBooks(spec: String?, reportError: LookupDelegate) : LookupResult? {
            var bookClient: HttpURLConnection? = null
            try {
                val url = URL(spec)
                bookClient = url.openConnection() as HttpURLConnection
                val status = bookClient.responseCode
                if (status != 200) throw Exception(
                    String.format(
                        "HTTP Error %d",
                        status
                    )
                )
                val content: InputStream =
                    BufferedInputStream(bookClient.inputStream)
                val input = InputStreamReader(content)
                val reader = BufferedReader(input)
                val responseBuilder = StringBuilder()
                var lineIn: String?
                while (reader.readLine().also { lineIn = it } != null) {
                    responseBuilder.append(lineIn)
                }
                val responseString = responseBuilder.toString()
                val json = JSONObject(responseString)
                return parseResponse(json)
            } catch (e: MalformedURLException) {
                reportError.bookLookupError(
                    String.format(
                        "Error: %s: %s",
                        e.toString(),
                        spec
                    )
                )
            } catch (e: Exception) {
                reportError.bookLookupError(String.format("Error: %s", e.toString()))
            } finally {
                bookClient?.disconnect()
            }
            return null
       }

    }
}