package com.example.cleve.bibliotech

import org.json.JSONArray
import org.json.JSONObject

class Book {
    var mVolumeID = ""
    var mISBN = ""
    var mTitle = ""
    var mSubTitle = ""
    var mAuthors: ArrayList<String> = ArrayList(1)
    var mDescription = ""
    var mThumbnails =
        arrayOfNulls<String>(kThumbnailCount)
    var mPageCount = 0

    companion object {
        private const val kTitle = "title"
        private const val kSubTitle = "subtitle"
        private const val kKind = "kind"
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
        private const val kSmallThumb = "smallThumbnail"
        private const val kThumb = "thumbnail"
        private const val kAuthors = "authors"
        const val kSmallThumbnail = 0
        const val kThumbnail = 1
        const val kThumbnailCount = 2
        @Throws(Exception::class)
        fun parseJSON(json: JSONObject): Book {
            val book = Book()
            val volume =
                json.getJSONObject(kVolumeInfo)
            val kind =
                json.getString(kKind)
            if (kind != kBooksVolume) throw Exception(
                "Invalid Response"
            )
            book.mISBN =
                if (volume.has(kIndustryIdentifiers)) findISBN(
                    volume.getJSONArray(kIndustryIdentifiers)
                ) else ""
            book.mTitle = hasMember(
                volume,
                kTitle
            )
            book.mSubTitle = hasMember(
                volume,
                kSubTitle
            )
            book.mDescription = hasMember(
                volume,
                kDescription
            )
            book.mVolumeID = hasMember(
                json,
                kVolumeID
            )
            book.mPageCount =
                if (volume.has(kPageCount)) volume.getInt(
                    kPageCount
                ) else 0
            if (volume.has(kImageLinks)) {
                val links =
                    volume.getJSONObject(kImageLinks)
                book.mThumbnails[kSmallThumbnail] =
                    hasMember(
                        links,
                        kSmallThumb
                    )
                book.mThumbnails[kThumbnail] =
                    hasMember(
                        links,
                        kThumb
                    )
            }
            if (volume.has(kAuthors)) {
                val authors =
                    volume.getJSONArray(kAuthors)
                val count = authors.length()
                book.mAuthors.clear()
                for (i in 0 until count) {
                    book.mAuthors.add(authors.getString(i))
                }
            }
            book.mSubTitle = hasMember(
                volume,
                kSubTitle
            )
            return book
        }

        @Throws(Exception::class)
        private fun hasMember(json: JSONObject, name: String): String {
            return if (json.has(name)) json.getString(name) else ""
        }

        @Throws(Exception::class)
        private fun findISBN(identifiers: JSONArray): String {
            var result = ""
            var i = identifiers.length()
            while (--i >= 0) {
                val id = identifiers.getJSONObject(i)
                val type =
                    id.getString(kType)
                if (type == kISBN_13) return id.getString(
                    kIdentifier
                )
                if (type == kISBN_10) result =
                    id.getString(kIdentifier)
            }
            return result
        }
    }
}