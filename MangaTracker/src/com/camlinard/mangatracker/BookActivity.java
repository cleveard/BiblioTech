package com.camlinard.mangatracker;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.support.v4.app.NavUtils;

public class BookActivity extends Activity {

	public static final String kBookIndex = "BOOK_INDEX";
	public static final String kListIndex = "LIST_INDEX";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_book);
		// Show the Up button in the action bar.
		setupActionBar();
	}

	/**
	 * Set up the {@link android.app.ActionBar}.
	 */
	private void setupActionBar() {

		getActionBar().setDisplayHomeAsUpEnabled(true);

	}
	
	private void setField(int id, String value) {
		if (value == null)
			value = "";
		View view = findViewById(id);
		TextView text = (TextView)view;
		text.setText(value);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.book, menu);
		
		Intent intent = getIntent();
		int bookIndex = intent.getIntExtra(kBookIndex, -1);
		int listIndex = intent.getIntExtra(kListIndex, -1);
		Book book = ListActivity.getBook(listIndex, bookIndex);
		
		setField(R.id.book_title, book.mTitle);
		setField(R.id.book_subtitle, book.mSubTitle);
		StringBuilder auth = new StringBuilder();
		if (book.mAuthors != null) {
			int count = book.mAuthors.length;
			if (count > 0) {
				auth.append(book.mAuthors[0]);
				for (int i = 1; i < count; ++i) {
					auth.append(", ");
					auth.append(book.mAuthors[i]);
				}
			}
		}
		setField(R.id.book_authors, auth.toString());
		book.getThumbnail(Book.kThumbnail, (ImageView)findViewById(R.id.book_thumb));
		setField(R.id.book_desc, book.mDescription);
		setField(R.id.book_volid, book.mVolumeID);
		setField(R.id.book_isbn, book.mISBN);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
