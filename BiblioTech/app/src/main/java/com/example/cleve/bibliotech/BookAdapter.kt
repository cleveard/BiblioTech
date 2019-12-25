package com.example.cleve.bibliotech

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
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.BookDatabase.BookCursor
import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue


internal class BookAdapter(private val context: Context) :
    RecyclerView.Adapter<BookAdapter.ViewHolder>() {
    private var mBookCursor: BookCursor? = null
    public var cursor: BookCursor?
        get() = mBookCursor
        set(bookCursor) {
            val c = mBookCursor
            if (c != null)
                notifyItemRangeRemoved(0, c.count)
            mBookCursor = bookCursor
            if (bookCursor != null)
                notifyItemRangeInserted(0, bookCursor.count)
        }

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
        val view = parent.findViewById<View>(id)
        val text = view as TextView
        text.text = this ?: ""
    }

    internal class QueueEntry private constructor(
        bookId: Long,
        private val mUrl: String,
        suffix: String,
        private val mView: ImageView
    ) : AsyncTask<Void?, Void?, Bitmap?>() {
        private var mCache: String = "MangaTracker.Thumb.$bookId$suffix"
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
                mView.setImageBitmap(result)
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
                url: String,
                suffix: String,
                view: ImageView
            ) {
                if (url.isNotEmpty()) {
                    mThumbQueue.add(QueueEntry(bookId, url, suffix, view))
                    if (mThumbQueue.size == 1) startAsync()
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
    }

    internal class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        // Inflate the custom layout
        val contactView: View = inflater.inflate(R.layout.book_layout, parent, false)

        // Return a new holder instance
        return ViewHolder(contactView)
    }

    override fun getItemCount(): Int {
        val bc = mBookCursor;
        return if (bc == null) 0 else bc.count
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book: BookCursor = mBookCursor!!
        book.moveToPosition(position)
        book.title.setField(holder.itemView, R.id.book_list_title)
        book.subTitle.setField(holder.itemView, R.id.book_list_subtitle)
        book.allAuthors.setField(holder.itemView, R.id.book_list_authors)
        val thumb =
            holder.itemView.findViewById<View>(R.id.book_list_thumb) as ImageView
        thumb.setImageDrawable(getNoThumb(context))
        QueueEntry.getThumbnail(
            book.id,
            book.smallThumb,
            kSmallThumb,
            thumb
        )
        QueueEntry.getThumbnail(
            book.id,
            book.largeThumb,
            kThumb,
            holder.itemView.findViewById<View>(R.id.book_thumb) as ImageView
        )
        book.description.setField(holder.itemView, R.id.book_desc)
        book.volumeId.setField(holder.itemView, R.id.book_volid)
        book.ISBN.setField(holder.itemView, R.id.book_isbn)
        val box = holder.itemView.findViewById<View>(R.id.selected) as CheckBox
        box.tag = book.position
        box.isChecked = book.isSelected
        changeViewVisibility(book.isOpen, holder.itemView)
    }
}