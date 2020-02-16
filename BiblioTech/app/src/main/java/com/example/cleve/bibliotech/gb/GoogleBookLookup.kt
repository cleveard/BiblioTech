package com.example.cleve.bibliotech.gb

import com.example.cleve.bibliotech.db.*
import android.os.AsyncTask
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

internal class GoogleBookLookup {
    internal interface LookupDelegate {
        fun bookLookupResult(result: List<BookAndAuthors>?, more: Boolean)
        fun bookLookupError(error: String?)
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
            String.format(
                "%s/%s?q=%s%s",
                kURL,
                collection,
                parameters,
                kKey
            )
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

    fun generalLookup(results: LookupDelegate, search: String): Boolean {
        return lookup(
            results,
            kVolumesCollection,
            search
        )
    }

    private class LookupTask internal constructor(parent: GoogleBookLookup, val mResults: LookupDelegate) :
        AsyncTask<String?, Void?, List<BookAndAuthors>?>() {
        val mParent = WeakReference(parent)
        override fun doInBackground(vararg params: String?): List<BookAndAuthors>? {
            val spec = params[0]
            var bookClient: HttpURLConnection? = null
            val list: MutableList<BookAndAuthors> = ArrayList(1)
            try {
                do {
                    val url = URL(String.format("%s&startIndex=%d", spec, list.size))
                    bookClient = url.openConnection() as HttpURLConnection
                    val content: InputStream =
                        BufferedInputStream(bookClient.inputStream)
                    val status = bookClient.responseCode
                    if (status != 200) throw Exception(
                        String.format(
                            "HTTP Error %d",
                            status
                        )
                    )
                    val input = InputStreamReader(content)
                    val reader = BufferedReader(input)
                    val responseBuilder = StringBuilder()
                    var lineIn: String?
                    while (reader.readLine().also { lineIn = it } != null) {
                        responseBuilder.append(lineIn)
                    }
                    val responseString = responseBuilder.toString()
                    val json = JSONObject(responseString)
                } while (parseResponse(json, list))
                return list
            } catch (e: MalformedURLException) {
                mResults.bookLookupError(
                    String.format(
                        "Error: %s: %s",
                        e.toString(),
                        params[0]
                    )
                )
            } catch (e: Exception) {
                mResults.bookLookupError(String.format("Error: %s", e.toString()))
            } finally {
                bookClient?.disconnect()
            }
            return null
        }

        override fun onPostExecute(result: List<BookAndAuthors>?) {
            super.onPostExecute(result)
            mResults.bookLookupResult(result, false)
            val parent = mParent.get()
            if (parent != null)
                parent.mLookup = null
        }

        @Throws(Exception::class)
        fun parseResponse(json: JSONObject, list: MutableList<BookAndAuthors>): Boolean {
            val kind = json.getString(kKind)
            if (kind == kBooksVolumes) {
                val items = json.getJSONArray(kItems)
                val count = items.length()
                // If count is 0, then nothing left to do
                if (count == 0) return false
                for (i in 0 .. count - 1) {
                    list.add(parseJSON(items.getJSONObject(i)))
                }
                val totalItems = json.getInt(kItemCount)
                return totalItems > list.size;
            }
            throw Exception("Invalid Response")
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
    }
}