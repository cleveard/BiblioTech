package com.example.cleve.biblio_tech;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.app.NavUtils;

public class BookActivity extends Activity {

	public static final String kBookId = "BOOK_ID";
	public static final String kViewId = "LIST_ID";
	
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
		long bookId = intent.getIntExtra(kBookId, -1);
		long viewId = intent.getIntExtra(kViewId, -1);
		BookDatabase.BookCursor book = ListActivity.getBook(viewId, bookId);
		
		setField(R.id.book_title, book.getTitle());
		setField(R.id.book_subtitle, book.getSubTitle());
		setField(R.id.book_authors, book.getAllAuthors());
		BookAdapter.QueueEntry.getThumbnail(bookId, book.getLargeThumb(), BookAdapter.kThumb,
			(ImageView)findViewById(R.id.book_thumb));
		setField(R.id.book_desc, book.getDescription());
		setField(R.id.book_volid, book.getVolumeId());
		setField(R.id.book_isbn, book.getISBN());
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
