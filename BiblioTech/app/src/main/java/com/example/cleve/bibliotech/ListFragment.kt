package com.example.cleve.bibliotech

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [ListFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [ListFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ListFragment : Fragment() {
    private lateinit var mViewModel: ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? { // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        val recycler = view.findViewById<RecyclerView>(R.id.book_list)

        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.adapter = mViewModel.adaptor
        recycler.scrollToPosition(mViewModel.position)

        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context !is ShareViewModel) {
            throw RuntimeException(
                context.toString()
                        + " must implement ShareViewModel"
            )
        }

        mViewModel = context.provider[ViewModel::class.java]
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment ListFragment.
         */
        fun newInstance(): ListFragment {
            return ListFragment()
        }
    }
}