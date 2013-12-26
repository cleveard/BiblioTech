package com.camlinard.mangatracker;

import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.os.Bundle;

/**
 * @author Camlin
 *
 */
public class ListFragment extends Fragment {

	private ArrayAdapter<Book> mAdapter;
	/**
	 * 
	 */
	public ListFragment() {
		super();
	}

	public void adapter(ArrayAdapter<Book> adapter) {
		mAdapter = adapter;
	}
	
	public ArrayAdapter<Book> adapter() {
		return mAdapter;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		ListView view = (ListView)inflater.inflate(R.layout.list_layout, container, false);
		view.setAdapter(mAdapter);
		return view;
	}
}
