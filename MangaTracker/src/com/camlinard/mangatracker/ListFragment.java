package com.camlinard.mangatracker;

import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.content.Intent;
import android.os.Bundle;

/**
 * @author Camlin
 *
 */
public class ListFragment extends Fragment {

	private ArrayAdapter<Book> mAdapter;
	private int mPosition;
	/**
	 * 
	 */
	public ListFragment() {
		super();
	}

	public void adapter(ArrayAdapter<Book> adapter, int position) {
		mAdapter = adapter;
		mPosition = position;
	}
	
	public ArrayAdapter<Book> adapter() {
		return mAdapter;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		ListView view = (ListView)inflater.inflate(R.layout.list_layout, container, false);
		view.setAdapter(mAdapter);

        view.setClickable(true);
        view.setOnItemClickListener(new AdapterView.OnItemClickListener() {

          @Override
          public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
        	  ListActivity.getBook(mPosition, position).toggleViewVisibility(arg1);
        	  //Intent intent = new Intent(getActivity(), BookActivity.class);
        	  //intent.putExtra(BookActivity.kBookIndex, position);
        	  //intent.putExtra(BookActivity.kListIndex, mPosition);
        	  //startActivity(intent);
          }
        });
        
        return view;
	}
}
