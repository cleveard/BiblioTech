package com.camlinard.mangatracker;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.*;

public class BookDatabase implements DatabaseErrorHandler, SQLiteDatabase.CursorFactory {

	public static final String DATABASE_FILENAME = "books_database";
	public static final String ID_COLUMN = "_id";
	public static final String BOOK_TABLE = "books";
	public static final String VOLUME_ID_COLUMN = "volume_id";
	public static final String ISBN_COLUMN = "isbn";
	public static final String TITLE_COLUMN = "title";
	public static final String SUBTITLE_COLUMN = "subtitle";
	public static final String DESCRIPTION_COLUMN = "description";
	public static final String SMALL_THUMB_COLUMN = "small_thumb";
	public static final String LARGE_THUMB_COLUMN = "large_thumb";
	public static final String AUTHORS_TABLE = "authors";
	public static final String LAST_NAME_COLUMN = "last_name";
	public static final String REMAINING_COLUMN = "remaining";
	public static final String BOOK_AUTHORS_TABLE = "book_authors";
	public static final String BOOK_ID_COLUMN = "book_id";
	public static final String AUTHOR_ID_COLUMN = "author_id";
	public static final String VIEWS_TABLE = "views";
	public static final String VIEW_NAME_COLUMN = "name";
	public static final String VIEW_ORDER_COLUMN = "order";
	public static final String BOOK_VIEWS_TABLE = "book_views";
	public static final String VIEW_ID_COLUMN = "view_id";
	
	// If you change the databse definition, make sure you change
	// the version
	static final int VERSION = 1;
	static final Table[] mPersistantTables = new Table[] {
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
		new Table(AUTHORS_TABLE, new Column[] {
				new Column(ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(LAST_NAME_COLUMN, "TEXT NOT NULL", 1),
				new Column(REMAINING_COLUMN, "TEXT NOT NULL DEFAULT ''", 1),
			},
			"UNIQUE (" + LAST_NAME_COLUMN + ", " + REMAINING_COLUMN + ")", 1),
		new Table(BOOK_AUTHORS_TABLE, new Column[] {
				new Column(ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(BOOK_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + BOOK_TABLE + "(" + ID_COLUMN + ")", 1),
				new Column(AUTHOR_ID_COLUMN, "INTEGER NOT NULL REFERNCE " + AUTHORS_TABLE + "(" + ID_COLUMN + ")", 1),
			},
			"UNIQUE (" + BOOK_ID_COLUMN + ", " + AUTHOR_ID_COLUMN + ")", 1),
		new Table(VIEWS_TABLE, new Column[] {
				new Column(ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(VIEW_NAME_COLUMN, "TEXT NOT NULL UNIQUE", 1),
				new Column(VIEW_ORDER_COLUMN, "INTEGER NOT NULL", 1),
			},
			"", 1),
		new Table(BOOK_VIEWS_TABLE, new Column[] {
				new Column(ID_COLUMN, "INTEGER PRIMARY KEY AUTOINCREMENT", 1),
				new Column(BOOK_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + BOOK_TABLE + "(" + ID_COLUMN + ")", 1),
				new Column(VIEW_ID_COLUMN, "INTEGER NOT NULL REFERENCES " + VIEWS_TABLE + "(" + ID_COLUMN + ")", 1),
			},
			"UNIQUE (" + BOOK_ID_COLUMN + ", " + VIEW_ID_COLUMN + ")", 1)
	};

	BookOpenHelper mHelper;
	SQLiteDatabase mDb;
	Context mContext;
	
	public void open(Context context) {
		mContext = context;
		mHelper = new BookOpenHelper(context);
		mDb = mHelper.getWritableDatabase();
	}

	public void close() {
		mHelper.close();
		mHelper = null;
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

	@Override
	public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
			String editTable, SQLiteQuery query) {
		// TODO Auto-generated method stub
		return null;
	}

	class BookOpenHelper extends SQLiteOpenHelper {
		public BookOpenHelper(Context context) {
			super(context, DATABASE_FILENAME, BookDatabase.this, VERSION);
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
