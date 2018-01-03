package com.example.cleve.biblio_tech;

import android.os.CancellationSignal;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.os.Bundle;

/**
 * @author Camlin
 *
 */
public class ListFragment extends Fragment {

	private BookDatabase mDb;
	private long mViewId;
	private String mSortOrder;
	private BookAdapter mAdapter;
	private CancellationSignal m_cancel = new CancellationSignal();
	/**
	 * 
	 */
	public ListFragment() {
		super();
	}

	public void constructCursor(BookDatabase db, long viewId, String sortOrder) {
		mDb = db;
		mViewId = viewId;
		mSortOrder = sortOrder;
	}

	public void closeCursor() {
		if (mAdapter != null) {
			mAdapter.getCursor().close();
			mAdapter = null;
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		ListView view = (ListView)inflater.inflate(R.layout.list_layout, container, false);
		BookDatabase.BookCursor cursor = mDb.getBookList(mViewId, mSortOrder, m_cancel);
		mAdapter = new BookAdapter(inflater.getContext(), cursor);
		view.setAdapter(mAdapter);

        view.setClickable(true);
        view.setOnItemClickListener(new AdapterView.OnItemClickListener() {

          @Override
          public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
			  BookDatabase.BookCursor c = (BookDatabase.BookCursor)mAdapter.getCursor();
			  c.moveToPosition(position);
			  c.setOpen(BookAdapter.toggleViewVisibility(arg1));
          }
        });
        
        return view;
	}

	public void onClickCheckBox(View view) {
		Long position = (Long)view.getTag();
		boolean checked = ((CheckBox)view).isChecked();
		BookDatabase.BookCursor c = (BookDatabase.BookCursor)mAdapter.getCursor();
		c.moveToPosition(position.intValue());
		c.setSelected(checked);

	}
}
