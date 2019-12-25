package com.example.cleve.biblio_tech;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.*;
import android.os.CancellationSignal;
import android.util.Log;

import java.util.HashMap;

class BookDatabase implements DatabaseErrorHandler {

	private static final String DATABASE_FILENAME = "books_database";
	private static final String BOOK_TABLE = "books";
	private static final String BOOK_ID_COLUMN = "books_id";
	private static final String VOLUME_ID_COLUMN = "books_volume_id";
	private static final String ISBN_COLUMN = "books_isbn";
	private static final String TITLE_COLUMN = "books_title";
	private static final String SUBTITLE_COLUMN = "books_subtitle";
	private static final String DESCRIPTION_COLUMN = "books_description";
	private static final String SMALL_THUMB_COLUMN = "books_small_thumb";
	private static final String LARGE_THUMB_COLUMN = "books_large_thumb";
	private static final String AUTHORS_TABLE = "authors";
	private static final String AUTHORS_ID_COLUMN = "authors_id";
	private static final String LAST_NAME_COLUMN = "authors_last_name";
	private static final String REMAINING_COLUMN = "authors_remaining";
	private static final String BOOK_AUTHORS_TABLE = "book_authors";
	private static final String BOOK_AUTHORS_ID_COLUMN = "book_authods_id";
	private static final String BOOK_AUTHORS_BOOK_ID_COLUMN = "book_authors_book_id";
	private static final String BOOK_AUTHORS_AUTHOR_ID_COLUMN = "book_authors_author_id";
	private static final String VIEWS_TABLE = "views";
    private static final String VIEWS_ID_COLUMN = "views_id";
	private static final String VIEWS_NAME_COLUMN = "views_name";
	private static final String VIEWS_ORDER_COLUMN = "views_order";
	private static final String VIEWS_SORT_COLUMN = "views_sort";
	private static final String BOOK_VIEWS_TABLE = "book_views";
	private static final String BOOK_VIEWS_ID_COLUMN = "book_views_id";
	private static final String BOOK_VIEWS_VIEW_ID_COLUMN = "book_views_view_id";
	private static final String BOOK_VIEWS_BOOK_ID_COLUMN = "book_views_book_id";
	private static final String SELECTED_COLUMN = "book_views_selected";
	private static final String OPEN_COLUMN = "book_views_open";
	private static final String BOOK_AUTHORS_VIEW = "book_authors_view";
	private static final String ALL_AUTHORS_COLUMN = "book_authors_view_all_authors";

	// If you change the databse definition, make sure you change
	// the version
	private static final int VERSION = 1;
	// Define the databse tables.
	private static final Table[] mPersistantTables = new Table[] {
		// The main book table.
		new Table(BOOK_TABLE,new Column[] {
				new Column(BOOK_ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(VOLUME_ID_COLUMN, "TEXT DEFAULT NULL UNIQUE", 1),
				new Column(ISBN_COLUMN, "TEXT DEFAULT NULL UNIQUE", 1),
				new Column(TITLE_COLUMN, "TEXT NOT NULL DEFAULT ''", 1),
				new Column(SUBTITLE_COLUMN, "TEXT NOT NULL DEFAULT ''", 1),
				new Column(DESCRIPTION_COLUMN, "TEXT NOT NULL DEFAULT ''", 1),
				new Column(SMALL_THUMB_COLUMN, "TEXT NOT NULL DEFAULT ''", 1),
				new Column(LARGE_THUMB_COLUMN, "TEXT NOT NULL DEFAULT ''", 1),
			},
			"", 1),
		// The author's table. We only keep author's names. Lastname and the remaining
		// We force the author names to be unique. In our case two different authots with the
		// same name isn't important, since all we track is the name.
		new Table(AUTHORS_TABLE, new Column[] {
				new Column(AUTHORS_ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(LAST_NAME_COLUMN, "TEXT NOT NULL", 1),
				new Column(REMAINING_COLUMN, "TEXT NOT NULL DEFAULT ''", 1),
			},
			"UNIQUE (" + LAST_NAME_COLUMN + ", " + REMAINING_COLUMN + ")", 1),
		// This table contains an entry for each author for each book. This is how multiple
		// authors are handled. To find all the authors of a book, look in this table for
		// all entries with the book id. The Author id's identify the authors
		new Table(BOOK_AUTHORS_TABLE, new Column[] {
				new Column(BOOK_AUTHORS_ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(BOOK_AUTHORS_BOOK_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + BOOK_TABLE + "(" + BOOK_ID_COLUMN + ")", 1),
				new Column(BOOK_AUTHORS_AUTHOR_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + AUTHORS_TABLE + "(" + AUTHORS_ID_COLUMN + ")", 1),
			},
			"UNIQUE (" + BOOK_AUTHORS_BOOK_ID_COLUMN + ", " + BOOK_AUTHORS_AUTHOR_ID_COLUMN + ")", 1),
		// The table of all of the views in the database. A view is a list of books. A book may
		// appear in multiple views. The order column is the order the views should appear.
		new Table(VIEWS_TABLE, new Column[] {
				new Column(VIEWS_ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(VIEWS_NAME_COLUMN, "TEXT NOT NULL", 1),
				new Column(VIEWS_ORDER_COLUMN, "INTEGER NOT NULL", 1),
				new Column(VIEWS_SORT_COLUMN, "STRING NOT NULL", 1),
			},
			"", 1),
		// The table that identifies which books are in which views.
		new Table(BOOK_VIEWS_TABLE, new Column[] {
				new Column(BOOK_VIEWS_ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(BOOK_VIEWS_BOOK_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + BOOK_TABLE + "(" + BOOK_ID_COLUMN + ")", 1),
				new Column(BOOK_VIEWS_VIEW_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + VIEWS_TABLE + "(" + VIEWS_ID_COLUMN + ")", 1),
				new Column(SELECTED_COLUMN, "INTEGER NOT NULL", 1),
				new Column(OPEN_COLUMN, "INTEGER NOT NULL", 1),
			},
			"UNIQUE (" + BOOK_VIEWS_BOOK_ID_COLUMN + ", " + BOOK_VIEWS_VIEW_ID_COLUMN + ")", 1)
	};

	private BookOpenHelper mHelper;
	private SQLiteDatabase mDb;
	private Context mContext;

	// Open the database if it exists, create it if it doesn't, upgrade it if
	// it is an earlier version.
	void open(Context context) {
		mContext = context;
		mHelper = new BookOpenHelper(context);
		mDb = mHelper.getWritableDatabase();
	}

	// Close the database
	void close() {
		mHelper.close();
		mHelper = null;
	}

	private static void separateAuthor(String name, StringBuilder last, StringBuilder remaining) {
		name = name.trim();
		// Look for a , assume last, remaining if found
		int lastIndex = name.lastIndexOf(',');
		if (lastIndex > 0) {
			last.append(name.substring(0, lastIndex).trim());
			remaining.append(name.substring(lastIndex + 1).trim());
		} else {
			// look for a space, assume remaining last if foud
			lastIndex = name.lastIndexOf(' ');
			if (lastIndex > 0) {
				last.append(name.substring(lastIndex));
				remaining.append(name.substring(0, lastIndex).trim());
			} else {
				// No space or commas, only last name
				last.append(name);
			}
		}
	}

	private long addAuthor(long bookId, String author) {
		StringBuilder last = new StringBuilder(), remaining = new StringBuilder();
		// Separate the author into last name and remaining
		BookDatabase.separateAuthor(author, last, remaining);
		// look up the author in the authors table
		Cursor result = mDb.query(AUTHORS_TABLE, new String[] { AUTHORS_ID_COLUMN },
			LAST_NAME_COLUMN + " = '?' AND " + REMAINING_COLUMN + " = '?'",
			new String[] { last.toString(), remaining.toString() }, null, null, null);
		long authorId;
		if (result.getCount() > 0) {
			// Author is already there, get the id
			result.moveToFirst();
			authorId = result.getLong(result.getColumnIndex(AUTHORS_ID_COLUMN));
		} else {
			// Add the author to the table
			ContentValues values = new ContentValues();
			values.put(LAST_NAME_COLUMN, last.toString());
			values.put(REMAINING_COLUMN, remaining.toString());
			authorId = mDb.insertOrThrow(AUTHORS_TABLE, null, values);
		}
		result.close();
		// Link the book and author
		ContentValues values = new ContentValues();
		values.put(BOOK_ID_COLUMN, bookId);
		values.put(AUTHORS_ID_COLUMN, authorId);
		return mDb.insertOrThrow(BOOK_AUTHORS_TABLE, null, values);
	}

	long addBook(Book book) {
		// Add the book to the book table
		ContentValues values = new ContentValues();
		values.put(VOLUME_ID_COLUMN, book.mVolumeID);
		values.put(ISBN_COLUMN, book.mISBN);
		values.put(TITLE_COLUMN, book.mTitle);
		values.put(SUBTITLE_COLUMN, book.mSubTitle);
		values.put(DESCRIPTION_COLUMN, book.mDescription);
		values.put(SMALL_THUMB_COLUMN, book.mThumbnails[Book.kSmallThumbnail]);
		values.put(LARGE_THUMB_COLUMN, book.mThumbnails[Book.kThumbnail]);
		long bookId = mDb.insertOrThrow(BOOK_TABLE, null, values);
		// Add the authors of the book
		for (int i = 0; i < book.mAuthors.length; ++i) {
			addAuthor(bookId, book.mAuthors[i]);
		}
		return bookId;
	}

	long addBookToView(long viewId, long bookId) {
		// Look to see if the book is already in the view
		Cursor result = mDb.query(BOOK_AUTHORS_TABLE, new String[] { BOOK_AUTHORS_ID_COLUMN },
				VIEWS_ID_COLUMN + " = ? AND " + BOOK_ID_COLUMN + " = ?",
				new String[] { Long.toString(viewId), Long.toString(bookId) }, null, null, null);
		if (result.getCount() > 0) {
			// Book is alread in the view, return its id
			result.moveToFirst();
			long id = result.getLong(result.getColumnIndex(BOOK_AUTHORS_ID_COLUMN));
			result.close();
			return id;
		}
		result.close();
		// Add the book to the view
		ContentValues values = new ContentValues();
		values.put(BOOK_VIEWS_VIEW_ID_COLUMN, viewId);
		values.put(BOOK_VIEWS_BOOK_ID_COLUMN, bookId);
		values.put(SELECTED_COLUMN, false);
		values.put(OPEN_COLUMN, false);
		return mDb.insertOrThrow(BOOK_VIEWS_TABLE, null, values);
	}

	void removeBook(long bookId) {
		// Save the author ids, so we can check to delete unreferenced authors later
		String[] selectArg = new String[] { Long.toString(bookId) };
		String[] idColumn = new String[] { BOOK_AUTHORS_ID_COLUMN };
		Cursor result = mDb.query(BOOK_AUTHORS_TABLE, idColumn,
				BOOK_AUTHORS_BOOK_ID_COLUMN + " = ?", selectArg, null, null, null);
		long[] authors = new long[result.getCount()];
		int index = result.getColumnIndex(BOOK_AUTHORS_AUTHOR_ID_COLUMN);
		for (int i = 0; i < result.getCount(); ++i) {
			result.moveToPosition(i);
			authors[i] = result.getLong(index);
		}
		result.close();
		// Delete the book author links
		mDb.delete(BOOK_AUTHORS_TABLE, BOOK_AUTHORS_BOOK_ID_COLUMN + " = ?", selectArg);
		// Loop through the authors and delete the ones that aren't referenced
		for (int i = 0; i < authors.length; ++i) {
			selectArg[0] = Long.toString(authors[i]);
			if (mDb.query(BOOK_AUTHORS_TABLE, idColumn, BOOK_AUTHORS_AUTHOR_ID_COLUMN + " = ?", selectArg,
				null, null, null).getCount() == 0) {
				// Not referenced, delete it
				mDb.delete(AUTHORS_TABLE, AUTHORS_ID_COLUMN + " = ?", selectArg);
			}
		}

		// Now delete the book from any views that it is in.
		selectArg[0] = Long.toString(bookId);
		mDb.delete(BOOK_VIEWS_TABLE, BOOK_VIEWS_BOOK_ID_COLUMN + " = ?", selectArg);

		// Finally delete the book
		mDb.delete(BOOK_TABLE, BOOK_ID_COLUMN + " = ?", selectArg);
	}

	void removeBookFromView(long viewId, long bookId) {
		// Save the author ids, so we can check to delete unreferenced authors later
		String[] selectArg = new String[] { Long.toString(viewId), Long.toString(bookId) };
		mDb.delete(BOOK_VIEWS_TABLE, BOOK_VIEWS_VIEW_ID_COLUMN + " = ? AND " + BOOK_VIEWS_BOOK_ID_COLUMN + " = ?", selectArg);
	}

	boolean isBookInAnyView(long bookId) {
		// Save the author ids, so we can check to delete unreferenced authors later
		String[] selectArg = new String[] { Long.toString(bookId) };
		String[] idColumn = new String[] { BOOK_VIEWS_ID_COLUMN };
		Cursor result = mDb.query(BOOK_VIEWS_TABLE, idColumn,
				BOOK_VIEWS_BOOK_ID_COLUMN + " = ?", selectArg, null, null, null);
		boolean inView = result.getCount() > 0;
		result.close();
		return inView;
	}

	ViewCursor getViewList(CancellationSignal cancellationSignal) {
        return ViewCursor.query(mDb, cancellationSignal);
	}

	BookCursor getBookList(long viewid, String sortOrder, CancellationSignal cancellationSignal) {
        return BookCursor.query(viewid, sortOrder, mDb, cancellationSignal);
	}

	BookCursor getBook(long bookId, CancellationSignal cancellationSignal) {
		return BookCursor.getBook(bookId, mDb, cancellationSignal);
	}

	@Override
	public void onCorruption(SQLiteDatabase dbObj) {
		// 1. Instantiate an AlertDialog.Builder with its constructor
		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		// 2. Chain together various setter methods to set the dialog characteristics
		builder.setMessage(R.string.db_error)
		       .setTitle(R.string.db_error_title);

		// 3. Get the AlertDialog from create()
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	// View cursor supplies information about the view
	static class ViewCursor extends SQLiteCursor {
		private static HashMap<String, String> mProjMap;
		private static final String[] mSelect = new String[] {
            VIEWS_NAME_COLUMN, VIEWS_ID_COLUMN, VIEWS_ORDER_COLUMN, VIEWS_SORT_COLUMN
		};
		static {
			mProjMap = new HashMap<>();
			mProjMap.put(VIEWS_NAME_COLUMN, VIEWS_NAME_COLUMN);
			mProjMap.put(VIEWS_ID_COLUMN, VIEWS_ID_COLUMN);
			mProjMap.put(VIEWS_ORDER_COLUMN, VIEWS_ORDER_COLUMN);
			mProjMap.put(VIEWS_SORT_COLUMN, VIEWS_SORT_COLUMN);
		}
		int idIndex, nameIndex, orderIndex, sortIndex;
		private ViewCursor(SQLiteCursorDriver masterQuery,
				   String editTable, SQLiteQuery query) {
			super(masterQuery, editTable, query);
			idIndex = this.getColumnIndex(VIEWS_ID_COLUMN);
			nameIndex = this.getColumnIndex(VIEWS_NAME_COLUMN);
			orderIndex = this.getColumnIndex(VIEWS_ORDER_COLUMN);
            sortIndex = this.getColumnIndex(VIEWS_SORT_COLUMN);
		}

		static ViewCursor query(SQLiteDatabase db, CancellationSignal cancellationSignal) {
			SQLiteQueryBuilder query = new SQLiteQueryBuilder();
			query.setCursorFactory(new ViewCursor.Factory());
			query.setDistinct(false);
			query.setTables(VIEWS_TABLE);
			query.setProjectionMap(mProjMap);
			query.setStrict(true);
			return (ViewCursor)query.query(db, mSelect, null, null, null, null, VIEWS_ORDER_COLUMN, null, cancellationSignal);
		}

		int getId() {
			return this.getInt(idIndex);
		}

		String getName() {
			return this.getString(nameIndex);
		}

		int getOrder() {
			return this.getInt(orderIndex);
		}

        String getSort() {
            return this.getString(sortIndex);
        }

		static class Factory implements SQLiteDatabase.CursorFactory {
			@Override
			public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
									String editTable, SQLiteQuery query) {
				return new ViewCursor(masterQuery, editTable, query);
			}
		}
	}

	static class BookCursor extends SQLiteCursor {
		private static HashMap<String, String>  mProjMap;
        private static final String[] mSelect = new String[] {
			BOOK_ID_COLUMN + " AS _id", TITLE_COLUMN, SUBTITLE_COLUMN, SMALL_THUMB_COLUMN,
			LARGE_THUMB_COLUMN, DESCRIPTION_COLUMN, VOLUME_ID_COLUMN, ISBN_COLUMN,
			ALL_AUTHORS_COLUMN, BOOK_VIEWS_VIEW_ID_COLUMN
        };
        private static String[] mSelectOpn = new String[] {
			BOOK_ID_COLUMN, VOLUME_ID_COLUMN, SELECTED_COLUMN, OPEN_COLUMN
		};
        static {
            mProjMap = new HashMap<>();
			mProjMap.put("_id", "_id");
			mProjMap.put(BOOK_ID_COLUMN, BOOK_ID_COLUMN);
			mProjMap.put(BOOK_ID_COLUMN + " AS _id", BOOK_ID_COLUMN + " AS _id");
			mProjMap.put(TITLE_COLUMN, TITLE_COLUMN);
			mProjMap.put(SUBTITLE_COLUMN, SUBTITLE_COLUMN);
			mProjMap.put(SMALL_THUMB_COLUMN, SMALL_THUMB_COLUMN);
			mProjMap.put(LARGE_THUMB_COLUMN, LARGE_THUMB_COLUMN);
			mProjMap.put(DESCRIPTION_COLUMN, DESCRIPTION_COLUMN);
			mProjMap.put(VOLUME_ID_COLUMN, VOLUME_ID_COLUMN);
			mProjMap.put(ISBN_COLUMN, ISBN_COLUMN);
			mProjMap.put(ALL_AUTHORS_COLUMN, ALL_AUTHORS_COLUMN);
			mProjMap.put(BOOK_VIEWS_VIEW_ID_COLUMN, BOOK_VIEWS_VIEW_ID_COLUMN);
        }
        private int idIndex;
		private int titleIndex;
		private int subTitleIndex;
		private int smallThumbIndex;
		private int largeThumbIndex;
		private int descriptionIndex;
		private int volumeIdIndex;
		private int isbnIndex;
		private int allAuthorsIndex;
		private int viewIdIndex;

		private BookCursor(SQLiteCursorDriver masterQuery,
				   String editTable, SQLiteQuery query) {
			super(masterQuery, editTable, query);
			idIndex = getColumnIndex("_id");
			titleIndex = getColumnIndex(TITLE_COLUMN);
			subTitleIndex = getColumnIndex(SUBTITLE_COLUMN);
			smallThumbIndex = getColumnIndex(SMALL_THUMB_COLUMN);
			largeThumbIndex = getColumnIndex(LARGE_THUMB_COLUMN);
			descriptionIndex = getColumnIndex(DESCRIPTION_COLUMN);
			volumeIdIndex = getColumnIndex(VOLUME_ID_COLUMN);
			isbnIndex = getColumnIndex(ISBN_COLUMN);
			allAuthorsIndex = getColumnIndex(ALL_AUTHORS_COLUMN);
			viewIdIndex = getColumnIndex(BOOK_VIEWS_VIEW_ID_COLUMN);
		}

        static BookCursor query(long viewId, String sortOrder, SQLiteDatabase db, CancellationSignal cancellationSignal) {
            SQLiteQueryBuilder query = new SQLiteQueryBuilder();
            query.setCursorFactory(new BookCursor.Factory());
            query.setDistinct(false);
            query.setTables("( " + BOOK_VIEWS_TABLE
                + " LEFT JOIN " + BOOK_AUTHORS_VIEW
                    + " ON (" + BOOK_ID_COLUMN + " = " + BOOK_VIEWS_BOOK_ID_COLUMN + ") )");
            query.setProjectionMap(mProjMap);
            query.setStrict(true);
            return (BookCursor)query.query(db, mSelect,
				BOOK_VIEWS_VIEW_ID_COLUMN + " = ?", new String[] { Long.toString(viewId) },
				null, null, sortOrder, null, cancellationSignal);
        }

        static BookCursor getBook(long bookId, SQLiteDatabase db, CancellationSignal cancellationSignal) {
			SQLiteQueryBuilder query = new SQLiteQueryBuilder();
			query.setCursorFactory(new BookCursor.Factory());
			query.setDistinct(true);
			query.setTables(BOOK_AUTHORS_VIEW);
			query.setProjectionMap(mProjMap);
			query.setStrict(true);
			return (BookCursor)query.query(db, mSelect,
					"_id = ?", new String[] { Long.toString(bookId) },
					null, null, null, null, cancellationSignal);
		}

		public long getId() {
			return getLong(idIndex);
		}

		long getViewId() {
			return getLong(viewIdIndex);
		}

		String getTitle() {
			return getString(titleIndex);
		}

		String getSubTitle() {
			return getString(subTitleIndex);
		}

		String getSmallThumb() {
			return getString(smallThumbIndex);
		}

		String getLargeThumb() {
			return getString(largeThumbIndex);
		}

		String getDescription() {
			return getString(descriptionIndex);
		}

		String getVolumeId() {
			return getString(volumeIdIndex);
		}

		String getISBN() {
			return getString(isbnIndex);
		}

		String getAllAuthors() {
			return getString(allAuthorsIndex);
		}

		boolean isSelected() {
			Cursor c = getDatabase().query(true, BOOK_VIEWS_TABLE, mSelectOpn,
					BOOK_VIEWS_BOOK_ID_COLUMN + " = ?" + " AND " + BOOK_VIEWS_VIEW_ID_COLUMN + " = ?",
				new String[] { Long.toString(getId()), Long.toString(getViewId()) }, null, null, null, null);
			boolean selected = c.getInt(c.getColumnIndex(SELECTED_COLUMN)) != 0;
			c.close();
			return selected;
		}

		void setSelected(boolean selected) {
			ContentValues values = new ContentValues();
			values.put(SELECTED_COLUMN, selected ? 1 : 0);
			getDatabase().update(BOOK_VIEWS_TABLE, values, BOOK_VIEWS_BOOK_ID_COLUMN + " = ?" + " AND " + BOOK_VIEWS_VIEW_ID_COLUMN + " = ?",
					new String[] { Long.toString(getId()), Long.toString(getViewId()) });
		}

		boolean isOpen() {
			Cursor c = getDatabase().query(true, BOOK_VIEWS_TABLE, mSelectOpn,
					BOOK_VIEWS_BOOK_ID_COLUMN + " = ?" + " AND " + BOOK_VIEWS_VIEW_ID_COLUMN + " = ?",
					new String[] { Long.toString(getId()), Long.toString(getViewId()) }, null, null, null, null);
			boolean open = c.getInt(c.getColumnIndex(OPEN_COLUMN)) != 0;
			c.close();
			return open;
		}

		void setOpen(boolean open) {
			ContentValues values = new ContentValues();
			values.put(OPEN_COLUMN, open ? 1 : 0);
			getDatabase().update(BOOK_VIEWS_TABLE, values, BOOK_VIEWS_BOOK_ID_COLUMN + " = ?" + " AND " + BOOK_VIEWS_VIEW_ID_COLUMN + " = ?",
					new String[] { Long.toString(getId()), Long.toString(getViewId()) });
		}

		static class Factory implements SQLiteDatabase.CursorFactory {
			@Override
			public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
									String editTable, SQLiteQuery query) {
				return new BookCursor(masterQuery, editTable, query);
			}
		}
	}

	private class BookOpenHelper extends SQLiteOpenHelper {
		BookOpenHelper(Context context) {
			super(context, DATABASE_FILENAME, null, VERSION, BookDatabase.this);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			for (Table t : mPersistantTables) {
				t.onCreate(db, false);
			}

			String book_concat_view = "CREATE VIEW " + BOOK_AUTHORS_VIEW + " AS"
				+ " SELECT *, GROUP_CONCAT((" + LAST_NAME_COLUMN + " || ', ' || " + REMAINING_COLUMN + "), ',\n') AS " + ALL_AUTHORS_COLUMN
				+ " FROM ( SELECT * FROM " + BOOK_TABLE
					+ " LEFT JOIN " + BOOK_AUTHORS_TABLE
						+ " ON (" + BOOK_AUTHORS_BOOK_ID_COLUMN + " = " + BOOK_ID_COLUMN + ")"
					+ " LEFT JOIN " + AUTHORS_TABLE
						+ " ON (" + AUTHORS_ID_COLUMN + " = " + BOOK_AUTHORS_AUTHOR_ID_COLUMN + ")"
				+ ") GROUP BY " + BOOK_ID_COLUMN + ";";
			db.execSQL(book_concat_view);

			// Add the default book lists
			ContentValues values = new ContentValues();
			values.put(VIEWS_NAME_COLUMN, mContext.getString(R.string.title_section1));
			values.put(VIEWS_ORDER_COLUMN, 0);
			values.put(VIEWS_SORT_COLUMN, BOOK_VIEWS_ID_COLUMN);
			db.insert(VIEWS_TABLE, null, values);
			values.clear();
			values.put(VIEWS_NAME_COLUMN, mContext.getString(R.string.title_section2));
			values.put(VIEWS_ORDER_COLUMN, 1);
			values.put(VIEWS_SORT_COLUMN, BOOK_VIEWS_ID_COLUMN);
			db.insert(VIEWS_TABLE, null, values);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			for (Table t : mPersistantTables) {
				t.onUpgrade(db, oldVersion, newVersion);
			}
		}
		
	}

	private static class Column {
		String mName;
		String mDefinition;
		int mVersion;
		
		Column(String name, String definition, int version)
		{
			mName = name;
			mDefinition = definition;
			mVersion = version;
		}
	}

	private static class Table {
		String mName;
		Column[] mColumns;
		String mConstraints;
		boolean mNoRowId;
		int mVersion;

		Table(String name, Column[] fields, String constraints, int version, boolean noRowId)
		{
			mName = name;
			mColumns = fields;
			mConstraints = constraints;
			mVersion = version;
			mNoRowId = noRowId;
		}

		Table(String name, Column[] fields, String constraints, int version)
		{
			this(name, fields,  constraints, version, false);
		}
		
		String buildCreateString(boolean temp) {
			StringBuilder createString = new StringBuilder(1024);
			createString.append("CREATE ");
			if (temp)
				createString.append("TEMP ");
			createString.append("TABLE ");
			createString.append(mName);
			createString.append(" ( ");
			
			for (Column c : mColumns) {
				createString.append(c.mName);
				createString.append(" ");
				createString.append(c.mDefinition);
				createString.append(", ");
			}

			if (mConstraints.isEmpty())
				createString.delete(createString.length() - 2, createString.length());
			else
				createString.append(mConstraints);
			createString.append(" )");
			if (mNoRowId) {
				createString.append(" WITHOUT ROWID");
			}
			createString.append(";");

			return createString.toString();
		}
		
		String buildAlterString(Column c) {
			return "ALTER TABLE "+ mName + " ADD COLUMN " + c.mName + " " + c.mDefinition + ";";
		}
		
		// Create the table
		void onCreate(SQLiteDatabase db, boolean temp) {
			String create = buildCreateString(temp);
			db.execSQL(create);
		}
		
		// Create the table
		void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (mVersion <= oldVersion) {
				try {
					for (Column c : mColumns) {
						if (c.mVersion > oldVersion && c.mVersion <= newVersion)
						{
							String alter = buildAlterString(c);
							db.execSQL(alter);
						}
					}
					return;
				} catch (SQLException e) {
					// If we can't alter the table, then delete it and create it
				}
				db.execSQL("DROP TABLE IF EXISTS " + mName);
			}
		    onCreate(db, false);
		}
	}

}