package com.example.cleve.biblio_tech;

import java.io.File;
import java.util.ArrayList;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ListActivity extends FragmentActivity implements ActionBar.TabListener {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link android.support.v4.app.FragmentStatePagerAdapter} derivative.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    private int mTabPosition = 0;
    private static BookLookup m_lookup = new BookLookup();
    private static File m_cache;
    private static BookDatabase m_db;
    private static CancellationSignal m_cancel = new CancellationSignal();
    
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    public static BookDatabase.BookCursor getBook(long viewId, long bookId) {
        return m_db.getBook(bookId, m_cancel);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        
        m_cache = getCacheDir();
        
        // Set up the action bar.
        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the app.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // Open the book list. If it fails, clear the data
        if (m_db == null) {
        	m_db = new BookDatabase();
        	m_db.open(this);
        }
        
        mSectionsPagerAdapter.constructCursor(m_db);
        
        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
        	// Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }
    }
    
    static public File getCache() {
    	return m_cache;
    }
    
    @Override
	protected void onDestroy() {
		super.onDestroy();
        if (m_db != null) {
            m_db.close();
        }
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.list, menu);
        return true;
    }
    
    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
        mTabPosition = tab.getPosition();
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    /**
     * A {@link FragmentStatePagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    private class SectionsPagerAdapter extends FragmentStatePagerAdapter {
    	private BookDatabase.ViewCursor m_cursor;
        private CancellationSignal m_cancel_list_cursor = new CancellationSignal();
        private ArrayList<ListFragment> m_fragments = new ArrayList<ListFragment>();

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        void constructCursor(BookDatabase db) {
        	m_cursor = db.getViewList(m_cancel_list_cursor);
        }
        
        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Make sure fragments list includes the requested position
            for (int i = m_fragments.size(); i <= position; ++i) {
                m_fragments.add(null);
            }
            ListFragment fragment = m_fragments.get(position);
            if (fragment == null) {
                // Create list fragment if it doesn't exist.
                fragment = new ListFragment();
                m_cursor.moveToPosition(position);
                long viewId = m_cursor.getId();
                String sortOrder = m_cursor.getSort();
                fragment.constructCursor(m_db, viewId, sortOrder);
                m_fragments.set(position, fragment);
            }
            //Bundle args = new Bundle();
            //args.putInt(DummySectionFragment.ARG_SECTION_NUMBER, position + 1);
            //fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            super.destroyItem(container, position, object);
            if (m_fragments.get(position) != null) {
                m_fragments.get(position).closeCursor();
                m_fragments.set(position, null);
            }
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return m_cursor == null ? 0 : m_cursor.getCount();
        }

        @Override
        public CharSequence getPageTitle(int position) {
        	if (m_cursor == null || position < 0 || position >= m_cursor.getCount())
        		return "";
        	
        	m_cursor.moveToPosition(position);
        	return m_cursor.getName();
        }
    }

    /**
     * A dummy fragment representing a section of the app, but that simply
     * displays dummy text.
     */
    public static class DummySectionFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        public static final String ARG_SECTION_NUMBER = "section_number";

        public DummySectionFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_list_dummy, container, false);
            TextView dummyTextView = (TextView) rootView.findViewById(R.id.section_label);
            dummyTextView.setText(Integer.toString(getArguments().getInt(ARG_SECTION_NUMBER)));
            return rootView;
        }
    }

    static class AddBookByISBN implements BookLookup.LookupDelegate {
    	private int mList;
    	AddBookByISBN(int list) {
    		mList = list;
    	}
		@Override
		public void BookLookupResult(Book[] result, boolean more) {
			if (result != null && result.length > 0) {
				for (int i = 0; i < result.length; ++i) {
					Book book = result[i];
					long book_id = m_db.addBook(book);
					if (book_id >= 0) {
						m_db.addBookToView(mList, book_id);
					}
				}
			}
		}

		@Override
		public void BookLookupError(String error) {
			// TODO Auto-generated method stub
			
		}
    	
    }
    
	public static void addBookByISBN(String isbn, int list) {
		addBookByISBN(isbn, new AddBookByISBN(list));
	}
	
	public static void addBookByISBN(String isbn, AddBookByISBN callback) {
		m_lookup.LookupISBN(callback, isbn);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_scan:
			{
				Intent intent = new Intent(this, ScanActivity.class);
				intent.putExtra(ScanActivity.kScanList, mTabPosition);
				startActivity(intent);
			}
			break;
		case R.id.action_settings:
			return false;	// Use default behavior
		}

		return true;
	}
}
