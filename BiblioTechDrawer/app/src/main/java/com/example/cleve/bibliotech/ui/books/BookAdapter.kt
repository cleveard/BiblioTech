package com.example.cleve.bibliotech.ui.books

import com.example.cleve.bibliotech.db.*
import com.example.cleve.bibliotech.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.*
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue


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

    private fun List<AuthorEntity>?.setField(parent: View, id: Int) {
        val text = parent.findViewById<TextView>(id)
        if (this == null)
            text.text = ""
        else {
            val authors = StringBuilder()
            for (a in this) {
                if (authors.isNotEmpty())
                    authors.append("\n")
                if (a.lastName != "") {
                    if (a.remainingName != "")
                        authors.append(a.remainingName).append(" ").append(a.lastName)
                    else
                        authors.append(a.lastName)
                } else if (a.remainingName != "")
                    authors.append(a.remainingName)
                else
                    continue
            }
            text.text = authors
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
        contactView.setOnClickListener {
            toggleViewVisibility(it)
        }
        contactView.findViewById<View>(R.id.selected).setOnClickListener {
        }

        // Return a new holder instance
        return ViewHolder(contactView)
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
        book.authors.setField(holder.itemView, R.id.book_list_authors)
        book.book.description.setField(holder.itemView, R.id.book_desc)
        book.book.volumeId.setField(holder.itemView, R.id.book_volid)
        book.book.ISBN.setField(holder.itemView, R.id.book_isbn)
        val box = holder.itemView.findViewById<View>(R.id.selected) as CheckBox
        box.setChecked(false)
        changeViewVisibility(false, holder.itemView)

        thumbSmall.setImageDrawable(getNoThumb(context))
        thumbLarge.setImageResource(0)
        QueueEntry.getThumbnail(
            book.book.id,
            book.book.smallThumb,
            kSmallThumb
        ) {
            if (it != null) {
                val item = getItem(holder.adapterPosition)
                if (item.book.id == id)
                    thumbSmall.setImageBitmap(it)
            }
        }
        QueueEntry.getThumbnail(
            book.book.id,
            book.book.largeThumb,
            kThumb
        ) {
            if (it != null) {
                val item = getItem(holder.adapterPosition)
                if (item.book.id == id)
                    thumbLarge.setImageBitmap(it)
            }
        }
    }
}