package com.example.cleve.bibliotech.ui.books

import android.content.ActivityNotFoundException
import com.example.cleve.bibliotech.db.*
import com.example.cleve.bibliotech.*
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.lang.StringBuilder
import java.text.SimpleDateFormat


private val format = SimpleDateFormat("MM/dd/yy")

internal class BooksAdapter(private val context: Context) :
    ListAdapter<BookAndAuthors, BooksAdapter.ViewHolder>(DIFF_CALLBACK) {

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

    companion object {
        private var m_nothumb: Drawable? = null
        private const val kNoThumbResource = "nothumb"
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
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.text.toString()))
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Log.e("BiblioTech", "Failed to launch browser")
                }
            }
        }

        // Return a new holder instance
        return holder
    }

    fun bindThumb(bookId: Long, large: Boolean, holder: ViewHolder, viewId: Int) {
        BookRepository.repo.scope.launch {
            BookRepository.repo.getThumbnail(
                bookId,
                large
            )?.also {
                val pos = holder.layoutPosition
                if (it != null && pos >= 0) {
                    val item = getItem(pos)
                    if (item.book.id == bookId)
                        holder.itemView.findViewById<ImageView>(viewId).setImageBitmap(it)
                }
            }
        }
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
        bindThumb(book.book.id, false, holder, R.id.book_list_thumb)
        bindThumb(book.book.id, true, holder, R.id.book_thumb)
    }
}