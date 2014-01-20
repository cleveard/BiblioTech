package com.camlinard.mangatracker;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class BookAdapter extends ArrayAdapter<Book> {

	public BookAdapter(Context context, List<Book> objects) {
		super(context, R.layout.book_layout, objects);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater)getContext().getSystemService
				  (Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.book_layout,null);
		Book book = getItem(position);
		TextView text = (TextView)view.findViewById(R.id.book_title);
		text.setText(book.mTitle);
		text = (TextView)view.findViewById(R.id.book_isbn);
		text.setText(book.mISBN);
		return view;
	}

}
