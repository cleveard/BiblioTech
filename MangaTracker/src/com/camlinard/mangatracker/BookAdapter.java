package com.camlinard.mangatracker;

import java.util.List;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class BookAdapter extends ArrayAdapter<Book> {
	static Drawable m_nothumb = null;
	static final String kNoThumbFile = "res/drawable/nothumb.png";
	
	public BookAdapter(Context context, List<Book> objects) {
		super(context, R.layout.book_layout, objects);
	}
	
	private Drawable getNoThumb() {
		if (m_nothumb == null) {
			int resID = getContext().getResources().getIdentifier("nothumb" , "drawable", getContext().getPackageName());
			m_nothumb = getContext().getResources().getDrawable(resID);
		}
		return m_nothumb;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater)getContext().getSystemService
				  (Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.book_layout,null);
		Book book = getItem(position);
		TextView text = (TextView)view.findViewById(R.id.book_list_title);
		text.setText(book.mTitle);
		text = (TextView)view.findViewById(R.id.book_list_subtitle);
		text.setText(book.mSubTitle);
		String authors = "";
		if (book.mAuthors != null && book.mAuthors.length > 0) {
			authors = book.mAuthors[0];
			if (book.mAuthors.length > 1) {
				authors += ",\n" + book.mAuthors[1];
			}
		}
		text = (TextView)view.findViewById(R.id.book_list_authors);
		text.setText(authors);
		ImageView thumb = (ImageView)view.findViewById(R.id.book_list_thumb);
		thumb.setImageDrawable(getNoThumb());
		book.getThumbnail(Book.kSmallThumbnail, thumb);
		return view;
	}

}
