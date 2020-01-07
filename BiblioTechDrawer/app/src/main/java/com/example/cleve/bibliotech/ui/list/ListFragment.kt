package com.example.cleve.bibliotech.ui.list

import com.example.cleve.bibliotech.db.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleve.bibliotech.*

class ListFragment : Fragment() {

    private lateinit var listViewModel: ListViewModel
    private lateinit var adapter: ListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        listViewModel =
            ViewModelProviders.of(this).get(ListViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_list, container, false)

        val recycler = root.findViewById<RecyclerView>(R.id.book_list)

        recycler.layoutManager = LinearLayoutManager(activity)
        adapter = ListAdapter(container!!.context)
        BookRepository.repo.books.observe(this,
            Observer<List<BookInView>> { list -> adapter.submitList(list) })
        recycler.adapter = adapter


        return root
    }
}