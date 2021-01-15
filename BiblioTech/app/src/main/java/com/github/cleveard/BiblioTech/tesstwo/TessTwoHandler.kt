package com.github.cleveard.BiblioTech.tesstwo

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.github.cleveard.BiblioTech.ui.scan.ScanFragment
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.*
import java.io.*
import java.lang.Exception

private const val TESSDATA = "tessdata"

class TessTwoHandler(private val context: Context, assets: String, cache: File, language: String) {
    private var api: TessBaseAPI = TessBaseAPI()
    private var initJob: Job? = GlobalScope.launch {
        try {
            launch(Dispatchers.IO) {
                copyTessDataFiles(assets, File(cache, TESSDATA))
                api.init(cache.path, language)
                Log.d(TAG, "Tess-Two initialized")
            }.join()
        } finally {
            initJob = null
        }
    }

    suspend fun convertToText(bm: Bitmap): String? {
        // Wait for initialization to finish
        initJob?.join()

        return withContext(Dispatchers.IO) {
            try {
                api.setImage(bm)
                api.utF8Text
            } catch (e: Exception) {
                Log.d(TAG, "Image to text failed $e")
                null
            } finally {
                api.clear()
            }
        }
    }

    /**
     * Copy tess data files (located on assets/tessdata) to destination directory
     *
     * @param assets - name of directory with .traineddata files
     */
    private fun copyTessDataFiles(assets: String, cache: File) {
        try {
            val fileList = context.assets.list(assets)?: throw IOException("Assets at $assets not found")

            if (!cache.isDirectory) {
                if (cache.exists())
                    cache.delete()
                cache.mkdir()
            }

            for (fileName in fileList) {
                // open file within the assets folder
                // if it is not already there copy it to the cache
                val dataFile = File(cache, fileName)
                if (!dataFile.exists()) {
                    val inStream = context.assets.open("$assets/$fileName")
                    val outStream = FileOutputStream(dataFile)

                    // Transfer bytes from in to out
                    val buf = ByteArray(1024)
                    var len: Int
                    while (inStream.read(buf).also { len = it } > 0) {
                        outStream.write(buf, 0, len)
                    }
                    inStream.close()
                    outStream.close()
                    Log.d(TAG, "Copied " + fileName + "to tessdata")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to copy files to tessdata $e")
        }
    }

    companion object {
        const val TAG: String = "TessTwoHandler"
    }
}