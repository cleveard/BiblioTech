package com.example.cleve.bibliotech.ui.tags

import android.graphics.Color
import android.view.LayoutInflater
import com.example.cleve.bibliotech.db.*
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.R
import com.example.cleve.bibliotech.utils.GenericViewModel


internal open class TagsAdapter(private val viewModel: GenericViewModel<Tag>) :
    PagingDataAdapter<Tag, TagsAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var selectColor: Int = 0x00FFFF

    companion object {
        val DIFF_CALLBACK =
            object: DiffUtil.ItemCallback<Tag>() {
                override fun areItemsTheSame(
                    oldTag: Tag, newTag: Tag): Boolean {
                    // User properties may have changed if reloaded from the DB, but ID is fixed
                    return oldTag.tag.id == newTag.tag.id
                }
                override fun areContentsTheSame(
                    oldTag: Tag, newTag: Tag): Boolean {
                    // NOTE: if you use equals, your object must properly override Object#equals()
                    // Incorrectly returning false here will result in too many animations.
                    return oldTag == newTag
                }
            }
    }

    internal class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        selectColor = ResourcesCompat.getColor(context.resources, R.color.colorSelect, null)

        // Inflate the custom layout
        val contactView: View = inflater.inflate(R.layout.tags_layout, parent, false)
        val holder = ViewHolder(contactView)
        contactView.setOnClickListener {view ->
            (view.tag as? Long)?.let {id ->
                viewModel.selection.toggle(id)
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag = getItem(position)
        val name = holder.itemView.findViewById<TextView>(R.id.tag_name)

        name.text = tag?.tag?.name ?: ""
        val id = tag?.id ?: 0L
        holder.itemView.tag = id
        val selected = viewModel.selection.isSelected(id)
        holder.itemView.setBackgroundColor(
            if (selected)
                selectColor
            else
                Color.WHITE
        )
        val desc = tag?.tag?.desc
        val descView = holder.itemView.findViewById<TextView>(R.id.tags_desc)
        if (desc == null || desc == "") {
            descView.visibility = View.GONE
        } else {
            descView.visibility = View.VISIBLE
            descView.text = desc
        }
    }
}