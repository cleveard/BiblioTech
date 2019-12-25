package com.example.cleve.bibliotech

import android.os.AsyncTask
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

internal class BookLookup {
    internal interface LookupDelegate {
        fun bookLookupResult(result: Array<Book?>?, more: Boolean)
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

    fun lookupISBN(results: LookupDelegate, isbn: String?): Boolean {
        return lookup(
            results,
            kVolumesCollection,
            String.format(kISBNParameter, isbn)
        )
    }

    private class LookupTask internal constructor(parent: BookLookup, val mResults: LookupDelegate) :
        AsyncTask<String?, Void?, Array<Book?>?>() {
        val mParent = WeakReference(parent)
        override fun doInBackground(vararg params: String?): Array<Book?>? {
            val spec = params[0]
            var bookClient: HttpURLConnection? = null
            try {
                val url = URL(spec)
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
                return parseResponse(json)
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

        override fun onPostExecute(result: Array<Book?>?) {
            super.onPostExecute(result)
            mResults.bookLookupResult(result, false)
            val parent = mParent.get()
            if (parent != null)
                parent.mLookup = null
        }

        @Throws(Exception::class)
        fun parseResponse(json: JSONObject): Array<Book?>? {
            val kind = json.getString(kKind)
            if (kind == kBooksVolumes) {
                val count = json.getInt(kItemCount)
                if (count == 0) return null
                val items = json.getJSONArray(kItems)
                val books: Array<Book?> = arrayOfNulls(count)
                for (i in 0 until count) {
                    books[i] = Book.parseJSON(items.getJSONObject(i))
                }
                return books
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
    }
}