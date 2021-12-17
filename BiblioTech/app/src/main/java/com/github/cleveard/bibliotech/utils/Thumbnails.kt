package com.github.cleveard.bibliotech.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.cleveard.bibliotech.MainActivity
import com.github.cleveard.bibliotech.db.kSmallThumb
import com.github.cleveard.bibliotech.db.kThumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.*
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext

class Thumbnails(dir: String = "db") {

    private val thumbDir: File?

    init {
        // Make sure the directory exists. Create it if it doesn't
        thumbDir = MainActivity.cache?.let {
            var path = it
            // Internal function to add a directory
            fun addDir(d: String) {
                if (d.isNotEmpty()) {
                    path = File(path, d)
                    // If it is already a directory, it is OK
                    if (!path.isDirectory) {
                        // If it already exists, then remove it
                        if (path.exists())
                            path.delete()
                        // Add the directory
                        path.mkdir()
                    }
                }
            }

            // Add thumbnails directory
            addDir("thumbnails")
            // Add directories from the constructor argument
            for (d in dir.split('/'))
                addDir(d)
            path
        }
    }

    /**
     * Get the thumbnail cache file for a book
     * @param bookId The book id
     * @param large True to get the large thumbnail file. False for the small thumbnail file.
     */
    private fun getThumbFile(bookId: Long, large: Boolean): File? {
        return thumbDir?.let {
            File(it, "BiblioTech.Thumb.$bookId${if (large) kThumb else kSmallThumb}")
        }
    }

    /**
     * Delete a thumbnail file for a book from the cache
     * @param bookId The book id
     * @param large True to get the large thumbnail file. False for the small thumbnail file.
     */
    suspend fun deleteThumbFile(bookId: Long, large: Boolean) {
        try {
            withContext(Dispatchers.IO) { getThumbFile(bookId, large)?.delete() }
        } catch (e: Exception) {}
    }

    /**
     * Delete all thumbnails in this directory
     */
    suspend fun deleteAllThumbFiles() {
        thumbDir?.let {
            /**
             * Recursively delete thumbnails
             */
            fun recurse(dir: File) {
                // List the files. Returns null, if dir is not a directory
                dir.listFiles()?.let { list ->
                    // Recurse for all of the files in dir
                    for (file in list) {
                        try {
                            recurse(file)
                        } catch (e: IOException) {
                        }
                    }
                }

                try {
                    // Delete the file, if it isn't the top level cache directory
                    if (dir != MainActivity.cache)
                        dir.delete()
                } catch (e: IOException) {
                }
            }

            withContext(Dispatchers.IO) {
                // Delete the thumbnails on an IO thread
                recurse(it)
            }
        }
    }

    /**
     * Get a thumbnail for a book
     * @param bookId The book id
     * @param large True to get the large thumbnail file. False for the small thumbnail file.
     */
    suspend fun getThumbnail(bookId: Long, large: Boolean, getUrl: suspend (bookId: Long, large: Boolean) -> String?): Bitmap? {
        return try {
            run {
                // Get the file path
                val file = getThumbFile(bookId, large)?: return@run null
                // Load the bitmap return null, if the load succeeds return the bitmap
                val result = loadBitmap(file)
                if (result != null)
                    return@run result
                // If the file already exists, then don't try to download it again
                if (file.exists())
                    return@run null

                var tmpFile: File
                do {
                    // If coroutine is canceled, return
                    if (!coroutineContext.isActive)
                        return@run null

                    // Get the URL to the image, return null if it fails
                    val url = getThumbUrl(getUrl(bookId, large), file) ?: return null
                    // If coroutine is canceled, return
                    if (!coroutineContext.isActive)
                        return@run null

                    // Download the bitmap, return null if files
                    tmpFile = downloadBitmap(url, file) ?: return null
                    // If coroutine is canceled, return
                    if (!coroutineContext.isActive)
                        return@run null

                    // Move the downloaded bitmap to the proper file, retry if it fails
                } while (!moveFile(tmpFile, file))

                loadBitmap(file)
            }
        } catch (e: Exception) {
            null
        } finally {
            // If coroutine is canceled throw exception
            coroutineContext.ensureActive()
        }
    }

    /**
     * Load bitmap from a file
     * @param file The file holding the bitmap
     * @return The bitmap of null if the file doesn't exist
     */
    private suspend fun loadBitmap(file: File): Bitmap? {
        return try {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
        } catch (e: Exception) {
            null
        } finally {
            coroutineContext.ensureActive()
        }
    }

    /**
     * Get the thumbnail URL for a book
     * @param urlString The URL to the thumbnail
     * @param file The file where the thumbnail is cached
     */
    private suspend fun getThumbUrl(urlString: String?, file: File): URL? {
        // Return null if there isn't a thumbnail url
        urlString?: return null
        @Suppress("BlockingMethodInNonBlockingContext")
        return withContext(Dispatchers.IO)  {
            var url: URL? = null
            try {
                // Make sure the url is valid
                val tmpUrl = URL(urlString)
                // Create a new file. If it is deleted while downloading the bitmap
                // Then, the data base was updated and we try to get the bitmap again
                file.createNewFile()
                url = tmpUrl
            } catch (e: Exception) {}
            url
        }
    }

    /**
     * Move one file to another
     * @param from The source file
     * @param to The destination file
     */
    private suspend fun moveFile(from: File, to: File): Boolean {
        // If the file, which we created before was deleted, then
        // the book was updated, and we need to try getting the thumbnail again
        if (!to.exists())
            return false

        try {
            // Move the tmp file to the real file
            @Suppress("BlockingMethodInNonBlockingContext")
            (withContext(Dispatchers.IO) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    })
        } catch (e: Exception) {}
        return true
    }

    /**
     * Download the bitmap for a URL and cache
     * @param url The url to download
     * @param file The cache file
     */
    private suspend fun downloadBitmap(inUrl: URL, file: File): File? {
        @Suppress("BlockingMethodInNonBlockingContext")
        return withContext(Dispatchers.IO) {
            var result = false
            var connection: HttpURLConnection? = null
            var output: BufferedOutputStream? = null
            var stream: InputStream? = null
            var buffered: BufferedInputStream? = null
            val tmpFile = File.createTempFile("tmp_bitmap", null, file.parentFile) ?: return@withContext null
            try {
                val url = if (inUrl.protocol == "http")
                    URL("https", inUrl.host, inUrl.port, inUrl.file)
                else
                    inUrl
                connection = url.openConnection() as HttpURLConnection
                output = BufferedOutputStream(FileOutputStream(tmpFile))
                stream = connection.inputStream
                buffered = BufferedInputStream(stream!!)
                val kBufSize = 4096
                val buf = ByteArray(kBufSize)
                var size: Int
                while (buffered.read(buf).also { size = it } >= 0) {
                    if (size > 0) output.write(buf, 0, size)
                }
                result = true
            } catch (e: MalformedURLException) {
                tmpFile.delete()
            } catch (e: IOException) {
                tmpFile.delete()
            }
            if (output != null) {
                try {
                    output.close()
                } catch (e: IOException) {
                    result = false
                }
            }
            if (buffered != null) {
                try {
                    buffered.close()
                } catch (e: IOException) {
                }
            }
            if (stream != null) {
                try {
                    stream.close()
                } catch (e: IOException) {
                }
            }
            connection?.disconnect()
            if (result)
                return@withContext tmpFile

            null
        }
    }
}