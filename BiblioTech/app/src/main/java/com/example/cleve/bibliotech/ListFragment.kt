package com.example.cleve.bibliotech

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [ListFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [ListFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ListFragment : Fragment() {
    private lateinit var mBookList: String
    private var mPosition = 0
    private lateinit var mViewModel: ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val a = arguments
        if (a != null) {
            mBookList = a.getString(ARG_BOOK_LIST) ?: ""
            mPosition = a.getInt(ARG_POSITION)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? { // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_list, container, false)
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
        private const val ARG_BOOK_LIST = MainActivity.ARG_BOOK_LIST
        private const val ARG_POSITION = MainActivity.ARG_POSITION
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param bookList name of the book list to view
         * @param position starting position in the book list
         * @return A new instance of fragment ListFragment.
         */
        fun newInstance(bookList: String?, position: Int): ListFragment {
            val fragment = ListFragment()
            val args = Bundle()
            args.putString(ARG_BOOK_LIST, bookList)
            args.putInt(ARG_POSITION, position)
            fragment.arguments = args
            return fragment
        }
    }
}