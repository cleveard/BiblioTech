package com.example.cleve.bibliotech.ui.books

import android.content.ActivityNotFoundException
import com.example.cleve.bibliotech.db.*
import com.example.cleve.bibliotech.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.*
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.concurrent.LinkedBlockingQueue


private val format = SimpleDateFormat("MM/dd/yy")

internal class BooksAdapter(private val context: Context) :
    ListAdapter<BookAndAuthors, com.example.cleve.bibliotech.ui.books.BooksAdapter.ViewHolder>(DIFF_CALLBACK) {

    private fun getNoThumb(context: Context): Drawable? {
        if (m_nothumb == null) {
            val resID = context.resources.getIdentifier(
                kNoThumbResource,
                "drawable",
                context.packageName
            )
            m_nothumb = context.resources.getDrawable(resID, null)
        }
        return m_nothumb
    }

    private fun String?.setField(parent: View, id: Int) {
        val text = parent.findViewById<TextView>(id)
        text.text = this ?: ""
    }

    private fun <T> List<T>?.setField(parent: View, id: Int, separator: String,
                                      toString: (T) -> String) {
        val text = parent.findViewById<TextView>(id)
        if (this == null)
            text.text = ""
        else {
            val value = StringBuilder()
            for (entry in this) {
                val s = toString(entry)
                if (s.isNotEmpty()) {
                    if (value.isNotEmpty())
                        value.append(separator)
                    value.append(s)
                }
            }
            text.text = value
        }
    }

    internal class QueueEntry private constructor(
        bookId: Long,
        private val mUrl: String,
        suffix: String,
        private val mCallback: (Bitmap?) -> Unit
    ) : AsyncTask<Void?, Void?, Bitmap?>() {
        private var mCache: String = "BiblioTech.Thumb.$bookId$suffix"
        private var mThumbFile: File? = null
        override fun doInBackground(vararg arg0: Void?): Bitmap? {
            try {
                mThumbFile = File(MainActivity.cache, mCache)
                var fileInCache = mThumbFile!!.exists()
                if (!fileInCache) {
                    fileInCache = downloadThumbnail()
                }
                if (fileInCache) {
                    return openThumbFile()
                }
            } catch (e: Exception) {
            }
            if (mThumbFile!!.exists()) {
                try {
                    mThumbFile!!.delete()
                } catch (e: Exception) {
                }
            }
            return null
        }

        override fun onPostExecute(result: Bitmap?) {
            super.onPostExecute(result)
            if (result != null) {
                mCallback(result)
            }
            mThumbQueue.remove()
            startAsync()
        }

        private fun downloadThumbnail(): Boolean {
            var result = false
            var connection: HttpURLConnection? = null
            var output: BufferedOutputStream? = null
            var stream: InputStream? = null
            var buffered: BufferedInputStream? = null
            try {
                val url = URL(mUrl)
                connection = url.openConnection() as HttpURLConnection
                output = BufferedOutputStream(FileOutputStream(mThumbFile!!))
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
            } catch (e: IOException) {
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
            return result
        }

        private fun openThumbFile(): Bitmap {
            return BitmapFactory.decodeFile(mThumbFile!!.absolutePath)
        }

        companion object {
            fun getThumbnail(
                bookId: Long,
                url: String?,
                suffix: String,
                callback: (Bitmap?) -> Unit
            ) {
                if (url != null && url.isNotEmpty()) {
                    mThumbQueue.add(QueueEntry(bookId, url, suffix, callback))
                    if (mThumbQueue.size == 1) startAsync()
                } else {
                    callback(null)
                }
            }

            fun startAsync() {
                val entry = mThumbQueue.peek()
                entry?.execute()
            }
        }

    }

    companion object {
        private var m_nothumb: Drawable? = null
        private const val kNoThumbResource = "nothumb"
        private const val kSmallThumb = ".small.png"
        const val kThumb = ".png"
        private fun changeViewVisibility(
            visible: Boolean,
            arg1: View
        ) {
            val view = arg1.findViewById<View>(R.id.book_list_open)
            view.visibility = if (visible) View.VISIBLE else View.GONE
        }

        fun toggleViewVisibility(arg1: View): Boolean {
            val view = arg1.findViewById<View>(R.id.book_list_open)
            val visible = view.visibility != View.VISIBLE
            changeViewVisibility(visible, arg1)
            return visible
        }

        private val mThumbQueue =
            LinkedBlockingQueue<QueueEntry>(50)

        val DIFF_CALLBACK =
            object: DiffUtil.ItemCallback<BookAndAuthors>() {
                override fun areItemsTheSame(
                    oldBook: BookAndAuthors, newBook: BookAndAuthors): Boolean {
                    // User properties may have changed if reloaded from the DB, but ID is fixed
                    return oldBook.book.id == newBook.book.id
                }
                override fun areContentsTheSame(
                    oldBook: BookAndAuthors, newBook: BookAndAuthors): Boolean {
                    // NOTE: if you use equals, your object must properly override Object#equals()
                    // Incorrectly returning false here will result in too many animations.
                    return oldBook == newBook
                }
            }
    }

    internal class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        // Inflate the custom layout
        val contactView: View = inflater.inflate(R.layout.book_layout, parent, false)
        val holder = ViewHolder(contactView)
        contactView.setOnClickListener {
            toggleViewVisibility(it)
        }
        contactView.findViewById<ViewFlipper>(R.id.book_list_flipper).setOnClickListener {
            if (it is ViewFlipper) {
                val book = getItem(holder.layoutPosition)
                BookRepository.repo.select(holder.layoutPosition, !book.selected)
            }
        }
        contactView.findViewById<TextView>(R.id.book_list_link).setOnClickListener {
            if (it is TextView) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.getText().toString()))
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Log.e("BiblioTech", "Failed to launch browser")
                }
            }
        }

        // Return a new holder instance
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book: BookAndAuthors = getItem(position)
        val id = book.book.id
        val thumbSmall =
            holder.itemView.findViewById<ImageView>(R.id.book_list_thumb)
        val thumbLarge =
            holder.itemView.findViewById<ImageView>(R.id.book_thumb)
        book.book.title.setField(holder.itemView, R.id.book_list_title)
        book.book.subTitle.setField(holder.itemView, R.id.book_list_subtitle)
        book.authors.setField(holder.itemView, R.id.book_list_authors, ", ") {
            if (it.lastName != "") {
                if (it.remainingName != "")
                    "${it.remainingName} ${it.lastName}"
                else
                    it.lastName
            } else if (it.remainingName != "")
                it.remainingName
            else
                ""
        }
        book.categories.setField(holder.itemView, R.id.book_catagories, ", ") {
            it.category
        }
        book.tags.setField(holder.itemView, R.id.book_tags, ", ") {
            it.name
        }
        book.book.description.setField(holder.itemView, R.id.book_desc)
        book.book.volumeId.setField(holder.itemView, R.id.book_volid)
        book.book.ISBN.setField(holder.itemView, R.id.book_isbn)
        book.book.linkUrl.setField(holder.itemView, R.id.book_list_link)
        book.book.pageCount.toString().setField(holder.itemView, R.id.book_list_pages)
        holder.itemView.findViewById<RatingBar>(R.id.book_list_rating).rating = book.book.rating.toFloat()
        format.format(book.book.added).setField(holder.itemView, R.id.book_list_added)
        format.format(book.book.modified).setField(holder.itemView, R.id.book_list_modified)
        val box = holder.itemView.findViewById<ViewFlipper>(R.id.book_list_flipper)
        box.displayedChild = if (book.selected) 1 else 0
        changeViewVisibility(false, holder.itemView)

        /* fun ImageView.setImageBitmapAndResize(bitmap: Bitmap) {
            this.setImageBitmap(bitmap)
            this.layoutParams.height = bitmap.height;
            this.layoutParams.width = bitmap.width;
            this.invalidate()
            this.requestLayout();
        } */

        thumbSmall.setImageDrawable(getNoThumb(context))
        thumbLarge.setImageResource(0)
        QueueEntry.getThumbnail(
            book.book.id,
            book.book.smallThumb,
            kSmallThumb
        ) {
            val pos = holder.layoutPosition;
            if (it != null && pos >= 0) {
                val item = getItem(pos)
                if (item.book.id == id)
                    thumbSmall.setImageBitmap(it)
            }
        }
        QueueEntry.getThumbnail(
            book.book.id,
            book.book.largeThumb,
            kThumb
        ) {
            val pos = holder.layoutPosition;
            if (it != null && pos >= 0) {
                val item = getItem(pos)
                if (item.book.id == id)
                    thumbLarge.setImageBitmap(it)
            }
        }
    }
}