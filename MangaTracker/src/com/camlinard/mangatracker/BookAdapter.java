package com.camlinard.mangatracker;

import java.util.List;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
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

	private void setField(View parent, int id, String value) {
		if (value == null)
			value = "";
		View view = parent.findViewById(id);
		TextView text = (TextView)view;
		text.setText(value);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null)
		{
			LayoutInflater inflater = (LayoutInflater)getContext().getSystemService
					  (Context.LAYOUT_INFLATER_SERVICE);
			convertView = inflater.inflate(R.layout.book_layout,null);
		}
		Book book = getItem(position);
		setField(convertView, R.id.book_list_title, book.mTitle);
		setField(convertView, R.id.book_list_subtitle, book.mSubTitle);
		String authors = "";
		if (book.mAuthors != null && book.mAuthors.length > 0) {
			authors = book.mAuthors[0];
			if (book.mAuthors.length > 1) {
				authors += ",\n" + book.mAuthors[1];
			}
		}
		setField(convertView, R.id.book_list_authors, authors);
		ImageView thumb = (ImageView)convertView.findViewById(R.id.book_list_thumb);
		thumb.setImageDrawable(getNoThumb());
		book.getThumbnail(Book.kSmallThumbnail, thumb);
		book.getThumbnail(Book.kThumbnail, (ImageView)convertView.findViewById(R.id.book_thumb));
		setField(convertView, R.id.book_desc, book.mDescription);
		setField(convertView, R.id.book_volid, book.mVolumeID);
		setField(convertView, R.id.book_isbn, book.mISBN);
		CheckBox box = (CheckBox)convertView.findViewById(R.id.selected);
		box.setTag(book);
		box.setChecked(book.mChecked);
		book.changeViewVisibility(convertView);
		return convertView;
	}

}
