package com.camlinard.mangatracker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONArray;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

public class Book {
	static public final String kTitle = "title";
	static public final String kSubTitle = "subtitle";
	static public final String kKind = "kind";
	static public final String kBooksVolume = "books#volume";
	static public final String kType = "type";
	static public final String kIdentifier = "identifier";
	static public final String kIndustryIdentifiers = "industryIdentifiers";
	static public final String kVolumeInfo = "volumeInfo";
	static public final String kISBN_13 = "ISBN_13";
	static public final String kISBN_10 = "ISBN_10";
	static public final String kVolumeID = "id";
	static public final String kPageCount = "pageCount";
	static public final String kDescription = "description";
	static public final String kImageLinks = "imageLinks";
	static public final String kSmallThumb = "smallThumbnail";
	static public final String kThumb = "thumbnail";
	static public final String kAuthors = "authors";
	static private final String mSuffix[] = {".small.png", ".png"};

	static public final int kSmallThumbnail = 0;
	static public final int kThumbnail = 1;
	static public final int kThumbnailCount = 2;
	
	public String mVolumeID = new String();
	public String mISBN = new String();
	public String mTitle = new String();
	public String mSubTitle = new String();
	public String[] mAuthors = new String[0];
	public String mDescription = new String();
	public String[] mThumbnails = new String[kThumbnailCount];
	public String[] mThumbnailFiles = new String[kThumbnailCount];
	public int mPageCount = 0;
	
	private static LinkedBlockingQueue<QueueEntry> mThumbQueue = new LinkedBlockingQueue<QueueEntry>(50);

	private class QueueEntry extends AsyncTask<Void, Void, Bitmap> {
		int mThumbnail;
		ImageView mView;
		File mThumbFile = null;
		
		
		QueueEntry(int thumbnail, ImageView view) {
			mThumbnail = thumbnail;
			mView = view;
		}

		@Override
		protected Bitmap doInBackground(Void... arg0) {
			try {
				if (mThumbnailFiles[mThumbnail] != null) {
					mThumbFile = new File(mThumbnailFiles[mThumbnail]);
					Bitmap image = openThumbFile();
					if (image != null)
						return image;
				} else {
					mThumbFile = File.createTempFile("MangaTracker." + mTitle, mSuffix[mThumbnail], ListActivity.getCache());
				}
				if (downloadThumbnail()) {
					Bitmap image = openThumbFile();
					if (image != null)
						return image;
				}
			} catch (Exception e) {
			}

			if (mThumbFile != null) {
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
				mThumbnailFiles[mThumbnail] = mThumbFile.getAbsolutePath();
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
				String spec = mThumbnails[mThumbnail];
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
				result = true;;
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
	
	public static Book load(ObjectInputStream s, int version)
			throws OptionalDataException, ClassNotFoundException, IOException {
		Book book = new Book();
		book.mTitle = s.readUTF();
		book.mSubTitle = s.readUTF();
		book.mISBN = s.readUTF();
		book.mDescription = s.readUTF();
		book.mThumbnails = (String[])s.readObject();
		book.mThumbnailFiles = (String[])s.readObject();
		book.mPageCount = s.readInt();
		book.mVolumeID = s.readUTF();
		book.mAuthors = (String[])s.readObject();
		return book;
	}
	
	public void store(ObjectOutputStream s, int version)
			throws IOException {
		s.writeUTF(mTitle);
		s.writeUTF(mSubTitle);
		s.writeUTF(mISBN);
		s.writeUTF(mDescription);
		s.writeObject(mThumbnails);
		s.writeObject(mThumbnailFiles);
		s.writeInt(mPageCount);
		s.writeUTF(mVolumeID);
		s.writeObject(mAuthors);
	}
	
	static public Book parseJSON(JSONObject json) throws Exception {
		Book book = new Book();
		JSONObject volume = json.getJSONObject(kVolumeInfo);
		String kind = json.getString(kKind);
		if (!kind.equals(kBooksVolume))
			throw new Exception("Invalid Response");
		book.mISBN = volume.has(kIndustryIdentifiers)
			? findISBN(volume.getJSONArray(kIndustryIdentifiers))
			: "";
		book.mTitle = hasMember(volume, kTitle);
		book.mSubTitle = hasMember(volume, kSubTitle);
		book.mDescription = hasMember(volume, kDescription);
		book.mVolumeID = hasMember(json, kVolumeID);
		book.mPageCount = volume.has(kPageCount) ? volume.getInt(kPageCount) : 0;
		if (volume.has(kImageLinks)) {
			JSONObject links = volume.getJSONObject(kImageLinks);
			book.mThumbnails[kSmallThumbnail] = hasMember(links, kSmallThumb);
			book.mThumbnails[kThumbnail] = hasMember(links, kThumb);
		}
		if (volume.has(kAuthors)) {
			JSONArray authors = volume.getJSONArray(kAuthors);
			int count = authors.length();
			book.mAuthors = new String[count];
			for (int i = 0; i < count; ++i) {
				book.mAuthors[i] = authors.getString(i);
			}
		}
		book.mSubTitle = hasMember(volume, kSubTitle);
		return book;
	}
	
	static String hasMember(JSONObject json, String name) throws Exception {
		return json.has(name) ? json.getString(name) : "";
	}
	
	static String findISBN(JSONArray identifiers) throws Exception {
		String result = "";
		for (int i = identifiers.length(); --i >= 0 ;) {
			JSONObject id = identifiers.getJSONObject(i);
			String type = id.getString(kType);
			if (type.equals(kISBN_13))
				return id.getString(kIdentifier);
			if (type.equals(kISBN_10))
				result = id.getString(kIdentifier);
		}
		return result;
	}

	public void getThumbnail(int thumbnail, ImageView view) {
		if (thumbnail >= 0 && thumbnail < kThumbnailCount) {
			if (mThumbnails[thumbnail] != null && mThumbnails[thumbnail].length() > 0) {
				mThumbQueue.add(new QueueEntry(thumbnail, view));
				if (mThumbQueue.size() == 1)
					startAsync();
			}
		}
	}

	public static void startAsync() {
		QueueEntry entry = mThumbQueue.peek();
		if (entry != null)
			entry.execute();
	}
}
