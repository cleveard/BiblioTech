package com.example.cleve.biblio_tech;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

class BookAdapter extends CursorAdapter {
	private static Drawable m_nothumb = null;
	private static final String kNoThumbResource = "nothumb";
    private static final String kSmallThumb = ".small.png";
    static final String kThumb = ".png";
	
	BookAdapter(Context context, BookDatabase.BookCursor c) {
		super(context, c, 0);
	}
	
	private Drawable getNoThumb(Context context) {
		if (m_nothumb == null) {
			int resID = context.getResources().getIdentifier(kNoThumbResource , "drawable", context.getPackageName());
			m_nothumb = context.getResources().getDrawable(resID, null);
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

	private static void changeViewVisibility(boolean visible, View arg1)
	{
		View view = arg1.findViewById(R.id.book_list_open);
		view.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	static boolean toggleViewVisibility(View arg1)
	{
		View view = arg1.findViewById(R.id.book_list_open);
		boolean visible = view.getVisibility() != View.VISIBLE;
		changeViewVisibility(visible, arg1);
		return visible;
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		BookDatabase.BookCursor book = (BookDatabase.BookCursor)cursor;
		setField(view, R.id.book_list_title, book.getTitle());
		setField(view, R.id.book_list_subtitle, book.getSubTitle());
		setField(view, R.id.book_list_authors, book.getAllAuthors());
		ImageView thumb = (ImageView)view.findViewById(R.id.book_list_thumb);
		thumb.setImageDrawable(getNoThumb(context));
		QueueEntry.getThumbnail(book.getId(), book.getSmallThumb(), kSmallThumb, thumb);
		QueueEntry.getThumbnail(book.getId(), book.getLargeThumb(), kThumb, (ImageView)view.findViewById(R.id.book_thumb));
		setField(view, R.id.book_desc, book.getDescription());
		setField(view, R.id.book_volid, book.getVolumeId());
		setField(view, R.id.book_isbn, book.getISBN());
		CheckBox box = (CheckBox)view.findViewById(R.id.selected);
		box.setTag(cursor.getPosition());
		box.setChecked(book.isSelected());
		changeViewVisibility(book.isOpen(), view);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater)context.getSystemService
				  (Context.LAYOUT_INFLATER_SERVICE);
		return inflater.inflate(R.layout.book_layout, parent);
	}

	private static LinkedBlockingQueue<BookAdapter.QueueEntry> mThumbQueue = new LinkedBlockingQueue<>(50);

	static class QueueEntry extends AsyncTask<Void, Void, Bitmap> {
		String mUrl;
		String mCache;
		ImageView mView;
		File mThumbFile = null;


		private QueueEntry(long bookId, String url, String suffix, ImageView view) {
			mUrl = url;
			mCache = "MangaTracker.Thumb." + Long.toString(bookId) + suffix;
			mView = view;
		}

		static void getThumbnail(long bookId, String url, String suffix, ImageView view) {
			if (url != null && url.length() > 0) {
				mThumbQueue.add(new QueueEntry(bookId, url, suffix, view));
				if (mThumbQueue.size() == 1)
					startAsync();
			}
		}

		static void startAsync() {
			QueueEntry entry = mThumbQueue.peek();
			if (entry != null)
				entry.execute();
		}

		@Override
		protected Bitmap doInBackground(Void... arg0) {
			try {
				mThumbFile = new File(ListActivity.getCache(), mCache);
				boolean fileInCache = mThumbFile.exists();
				if (!fileInCache) {
					fileInCache = downloadThumbnail();
				}
				if (fileInCache) {
					Bitmap image = openThumbFile();
					if (image != null)
						return image;
				}
			} catch (Exception e) {
			}

			if (mThumbFile.exists()) {
				try {
					mThumbFile.delete();
				} catch (Exception e) {
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);
			if (result != null) {
				if (mView != null)
					mView.setImageBitmap(result);
			}
			mThumbQueue.remove();
			startAsync();
		}

		private boolean downloadThumbnail() {
			boolean result = false;
			HttpURLConnection connection = null;
			BufferedOutputStream output = null;
			InputStream stream = null;
			BufferedInputStream buffered = null;

			try {
				String spec = mUrl;
				if (spec == null)
					return false;
				URL url = new URL(spec);
				connection = (HttpURLConnection)url.openConnection();
				output = new BufferedOutputStream(new FileOutputStream(mThumbFile));
				stream = connection.getInputStream();
				buffered = new BufferedInputStream(stream);

				final int kBufSize = 4096;
				byte[] buf = new byte[kBufSize];
				int size;
				while ((size = buffered.read(buf)) >= 0) {
					if (size > 0)
						output.write(buf, 0, size);
				}
				result = true;
			} catch (MalformedURLException e) {
			} catch (IOException e) {
			}

			if (output != null) {
				try {
					output.close();
				} catch (IOException e) {
					result = false;
				}
			}
			if (buffered != null) {
				try {
					buffered.close();
				} catch (IOException e) {
				}
			}
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
				}
			}
			if (connection != null) {
				connection.disconnect();
			}
			return result;
		}

		private Bitmap openThumbFile() {
			return BitmapFactory.decodeFile(mThumbFile.getAbsolutePath());
		}
	}
}
