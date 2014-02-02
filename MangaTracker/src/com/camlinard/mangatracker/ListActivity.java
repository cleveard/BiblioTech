package com.camlinard.mangatracker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Locale;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

public class ListActivity extends FragmentActivity implements ActionBar.TabListener {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
     * will keep every loaded fragment in memory. If this becomes too memory
     * intensive, it may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    private static final int kCurrentVersion = 0;
    private static int mLoadingVersion = 0;
    
    private static ArrayList<ArrayList<Book>> mLists = new ArrayList<ArrayList<Book>>();
    private static ArrayList<BookAdapter> mAdapters = new ArrayList<BookAdapter>();
    private static ArrayList<String> mPageNames = new ArrayList<String>();
    private int mTabPosition = 0;
    private final String kDocFilename = "mistuff";
    private BookLookup m_lookup = new BookLookup();
    private static File m_cache;
    
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

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

        // Load the book list. If it fails, clear the data
        loadBooks();
        
        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
        	if (i >= mLists.size()) {
	        	mLists.add(new ArrayList<Book>());
	        	mAdapters.add(new BookAdapter(this, mLists.get(i)));
	        	mPageNames.add(mSectionsPagerAdapter.getPageTitle(i).toString());
        	}

        	// Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mPageNames.get(i))
                            .setTabListener(this));
        }
    }
    
    static public File getCache() {
    	return m_cache;
    }
    
    static public Book getBook(int list, int book)
    {
    	if (list < 0 || list >= mAdapters.size())
    		return null;
    	BookAdapter adapter = mAdapters.get(list);
    	if (adapter == null)
    		return null;
    	if (book < 0 || book > adapter.getCount())
    		return null;
    	return adapter.getItem(book);
    }

    private boolean loadBooks() {
    	try {
        	mLists.clear();
        	mAdapters.clear();
			FileInputStream input = openFileInput(kDocFilename);
			ObjectInputStream stream = new ObjectInputStream(input);
			mLoadingVersion = stream.readInt();
			int listCount = stream.readInt();
			for (int i = 0; i < listCount; ++i) {
	        	mLists.add(new ArrayList<Book>());
	        	mAdapters.add(new BookAdapter(this, mLists.get(i)));
        		mPageNames.add(stream.readUTF());
				
				BookAdapter adapter = mAdapters.get(i);
				int itemCount = stream.readInt();
				for (int j = 0; j < itemCount; ++j) {
					try {
						adapter.add(Book.load(stream, mLoadingVersion));
					} catch (ClassNotFoundException ce) {
					}
				}
			}
			stream.close();
			input.close();
			return true;
    	} catch (IOException e) {
    	}
    	mLists.clear();
    	mAdapters.clear();
    	return false;
    }
    
    private boolean saveBooks(int version) {
    	try {
			FileOutputStream output = openFileOutput(kDocFilename, Context.MODE_PRIVATE);
			ObjectOutputStream stream = new ObjectOutputStream(output);
			stream.writeInt(version);
			int listCount = mLists.size();
			stream.writeInt(listCount);
			for (int i = 0; i < listCount; ++i) {
				stream.writeUTF(mPageNames.get(i));
				ArrayList<Book> list = mLists.get(i);
				int itemCount = list.size();
				stream.writeInt(itemCount);
				for (int j = 0; j < itemCount; ++j) {
					list.get(j).store(stream, version);
				}
			}
			stream.close();
			output.close();
			return true;
    	} catch (IOException e) {
    	}
    	return false;
    }
    
    @Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
		saveBooks(kCurrentVersion);
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
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a DummySectionFragment (defined as a static inner class
            // below) with the page number as its lone argument.
            ListFragment fragment = new ListFragment();
            fragment.adapter(mAdapters.get(position), position);
            //Bundle args = new Bundle();
            //args.putInt(DummySectionFragment.ARG_SECTION_NUMBER, position + 1);
            //fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_section1).toUpperCase(l);
                case 1:
                    return getString(R.string.title_section2).toUpperCase(l);
            }
            return null;
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

    class AddBookByISBN implements BookLookup.LookupDelegate {

		@Override
		public void BookLookupResult(Book[] result, boolean more) {
			if (result != null && result.length > 0)
				mAdapters.get(mTabPosition).addAll(result);
		}

		@Override
		public void BookLookupError(String error) {
			// TODO Auto-generated method stub
			
		}
    	
    }
    
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		IntentResult scanningResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
		if (scanningResult != null) {
			m_lookup.LookupISBN(new AddBookByISBN(), scanningResult.getContents());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_scan:
			{
				IntentIntegrator scanIntegrator = new IntentIntegrator(this);
				scanIntegrator.initiateScan();
			}
			break;
		case R.id.action_settings:
			return false;	// Use default behavior
		}

		return true;
	}

    
}
