package com.camlinard.mangatracker;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.*;
import android.os.CancellationSignal;

import java.lang.reflect.Constructor;

public class BookDatabase implements DatabaseErrorHandler {

	private static final String DATABASE_FILENAME = "books_database";
	private static final String ID_COLUMN = "_id";
	private static final String BOOK_TABLE = "books";
	private static final String VOLUME_ID_COLUMN = "volume_id";
	private static final String ISBN_COLUMN = "isbn";
	private static final String TITLE_COLUMN = "title";
	private static final String SUBTITLE_COLUMN = "subtitle";
	private static final String DESCRIPTION_COLUMN = "description";
	private static final String SMALL_THUMB_COLUMN = "small_thumb";
	private static final String LARGE_THUMB_COLUMN = "large_thumb";
	private static final String AUTHORS_TABLE = "authors";
	private static final String LAST_NAME_COLUMN = "last_name";
	private static final String REMAINING_COLUMN = "remaining";
	private static final String BOOK_AUTHORS_TABLE = "book_authors";
	private static final String BOOK_ID_COLUMN = "book_id";
	private static final String AUTHOR_ID_COLUMN = "author_id";
	private static final String AUTHOR_ORDER_COLUMN = "author_order";
	private static final String VIEWS_TABLE = "views";
	private static final String VIEW_NAME_COLUMN = "name";
	private static final String VIEW_ORDER_COLUMN = "order";
	private static final String BOOK_VIEWS_TABLE = "book_views";
	private static final String VIEW_ID_COLUMN = "view_id";
	private static final String VIEW_SORT_COLUMN = "view_sort";
	
	// If you change the databse definition, make sure you change
	// the version
	private static final int VERSION = 1;
	// Define the databse tables.
	private static final Table[] mPersistantTables = new Table[] {
		// The main book table.
		new Table(BOOK_TABLE,new Column[] {
				new Column(ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
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
				new Column(ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(LAST_NAME_COLUMN, "TEXT NOT NULL", 1),
				new Column(REMAINING_COLUMN, "TEXT NOT NULL DEFAULT ''", 1),
			},
			"UNIQUE (" + LAST_NAME_COLUMN + ", " + REMAINING_COLUMN + ")", 1),
		// This table contains an entry for each author for each book. This is how multiple
		// authors are handled. To find all the authors of a book, look in this table for
		// all entries with the book id. The Author id's identify the authors
		new Table(BOOK_AUTHORS_TABLE, new Column[] {
				new Column(ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(BOOK_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + BOOK_TABLE + "(" + ID_COLUMN + ")", 1),
				new Column(AUTHOR_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + AUTHORS_TABLE + "(" + ID_COLUMN + ")", 1),
				new Column(AUTHOR_ORDER_COLUMN, "INTEGER NOT NULL", 1),
			},
			"UNIQUE (" + BOOK_ID_COLUMN + ", " + AUTHOR_ID_COLUMN + ")", 1),
		// The table of all of the views in the database. A view is a list of books. A book may
		// appear in multiple views. The order column is the order the views should appear.
		new Table(VIEWS_TABLE, new Column[] {
				new Column(ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(VIEW_NAME_COLUMN, "TEXT NOT NULL", 1),
				new Column(VIEW_ORDER_COLUMN, "INTEGER NOT NULL", 1),
				new Column(VIEW_SORT_COLUMN, "STRING NOT NULL", 1),
			},
			"", 1),
		// The table that identifies which books are in which views.
		new Table(BOOK_VIEWS_TABLE, new Column[] {
				new Column(ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(BOOK_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + BOOK_TABLE + "(" + ID_COLUMN + ")", 1),
				new Column(VIEW_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + VIEWS_TABLE + "(" + ID_COLUMN + ")", 1),
			},
			"UNIQUE (" + BOOK_ID_COLUMN + ", " + VIEW_ID_COLUMN + ")", 1)
	};

	private BookOpenHelper mHelper;
	private SQLiteDatabase mDb;
	private Context mContext;

	// Open the database if it exists, create it if it doesn't, upgrade it if
	// it is an earlier version.
	public void open(Context context) {
		mContext = context;
		mHelper = new BookOpenHelper(context);
		mDb = mHelper.getWritableDatabase();
	}

	// Close the database
	public void close() {
		mHelper.close();
		mHelper = null;
	}

	public ViewCursor getViewList(CancellationSignal cancellationSignal) {
		return (ViewCursor)mDb.queryWithFactory(new ViewCursor.Factory(), false, VIEWS_TABLE, null,
				null, null, null, null, VIEW_ORDER_COLUMN, null, cancellationSignal);
	}

	public BookCursor getBookList(int viewid, CancellationSignal cancellationSignal) {
		return (BookCursor)mDb.rawQueryWithFactory(new BookCursor.Factory(), "", new String[] {
			Integer.toString((viewid))
		}, BOOK_VIEWS_TABLE, cancellationSignal);
	}

	public BookCursor getBook(int bookId, CancellationSignal cancellationSignal) {
		return (BookCursor)mDb.rawQueryWithFactory(new BookCursor.Factory(), "", new String[] {
				Integer.toString((bookId))
		}, BOOK_VIEWS_TABLE, cancellationSignal);
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
	public static class ViewCursor extends SQLiteCursor {
		int idIndex, nameIndex, orderIndex;
		ViewCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
				   String editTable, SQLiteQuery query) {
			super(db, masterQuery, editTable, query);
			idIndex = this.getColumnIndex(VIEW_ID_COLUMN);
			nameIndex = this.getColumnIndex(VIEW_NAME_COLUMN);
			orderIndex = this.getColumnIndex(VIEW_ORDER_COLUMN);
		}

		public int getId() {
			return this.getInt(idIndex);
		}

		public String getName() {
			return this.getString(nameIndex);
		}

		public int getOrder() {
			return this.getInt(orderIndex);
		}

		public static class Factory implements SQLiteDatabase.CursorFactory {
			@Override
			public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
									String editTable, SQLiteQuery query) {
				return new ViewCursor(db, masterQuery, editTable, query);
			}
		}
	}

	public static class BookCursor extends SQLiteCursor {
		BookCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
				   String editTable, SQLiteQuery query) {
			super(db, masterQuery, editTable, query);
		}

		public static class Factory implements SQLiteDatabase.CursorFactory {
			@Override
			public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
									String editTable, SQLiteQuery query) {
				return new BookCursor(db, masterQuery, editTable, query);
			}
		}
	}

	class BookOpenHelper extends SQLiteOpenHelper {
		public BookOpenHelper(Context context) {
			super(context, DATABASE_FILENAME, null, VERSION, BookDatabase.this);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			for (Table t : mPersistantTables) {
				t.onCreate(db, false);
			}
			
			// Add the default book lists
			ContentValues values = new ContentValues();
			values.put(VIEW_NAME_COLUMN, mContext.getString(R.string.title_section1));
			values.put(VIEW_ORDER_COLUMN, 0);
			db.insert(VIEWS_TABLE, null, values);
			values.clear();
			values.put(VIEW_NAME_COLUMN, mContext.getString(R.string.title_section2));
			values.put(VIEW_ORDER_COLUMN, 1);
			db.insert(VIEWS_TABLE, null, values);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			for (Table t : mPersistantTables) {
				t.onUpgrade(db, oldVersion, newVersion);
			}
		}
		
	}

	static class Column {
		public String mName;
		public String mDefinition;
		public int mVersion;
		
		Column(String name, String definition, int version)
		{
			mName = name;
			mDefinition = definition;
			mVersion = version;
		}
	}

	static class Table {
		public String mName;
		public Column[] mColumns;
		public String mConstraints;
		public int mVersion;
		
		Table(String name, Column[] fields, String constraints, int version)
		{
			mName = name;
			mColumns = fields;
			mConstraints = constraints;
			mVersion = version;
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
			createString.append(" );");
			
			return createString.toString();
		}
		
		String buildAlterString(Column c) {
			StringBuilder alterString = new StringBuilder(1024);
			alterString.append("ALTER TABLE ");
			alterString.append(mName);
			alterString.append(" ADD COLUMN ");
			
			alterString.append(c.mName);
			alterString.append(" ");
			alterString.append(c.mDefinition);
			alterString.append(";");
			
			return alterString.toString();
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
