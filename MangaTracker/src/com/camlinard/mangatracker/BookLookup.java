package com.camlinard.mangatracker;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.os.AsyncTask;

public class BookLookup {
	static public final String kKey = "&key=AIzaSyDeQMfnPyhQ23-ndhb9xs9IY_EaSiTxgms";
	static public final String kURL = "https://www.googleapis.com/books/v1";
	static public final String kVolumesCollection = "volumes";
	static public final String kISBNParameter = "isbn:%s";
	static public final String kKind = "kind";
	static public final String kBooksVolumes = "books#volumes";
	static public final String kISBN = "isbn";
	static public final String kItemCount = "totalItems";
	static public final String kItems = "items";
	
	interface LookupDelegate {
		public void BookLookupResult(Book[] result, boolean more);
		public void BookLookupError(String error);
	}
	
	LookupTask m_lookup = null;
	
	public boolean Lookup(LookupDelegate results, String collection, String parameters) {
		if (m_lookup != null)
			return false;
		m_lookup = new LookupTask(results);
		m_lookup.execute(String.format("%s/%s?q=%s%s", kURL, collection, parameters, kKey));
		return true;
	}
	
	public boolean LookupISBN(LookupDelegate results, String isbn) {
		return Lookup(results, kVolumesCollection, String.format(kISBNParameter, isbn));
	}
	
	protected class LookupTask extends AsyncTask<String, Void, Book[]> {
		protected String m_url = null;
		protected LookupDelegate m_results;
		
		public LookupTask(LookupDelegate results) {
			super();
			m_results = results;
		}

		@Override
		protected Book[] doInBackground(String... params) {
			String spec = params[0];
			HttpClient bookClient = new DefaultHttpClient();
			try {
				HttpGet get = new HttpGet(spec);
				HttpResponse response = bookClient.execute(get);
				StatusLine status = response.getStatusLine();
				if (status.getStatusCode() != 200)
					throw new Exception(String.format("HTTP Error %d", status.getStatusCode()));

				HttpEntity entity = response.getEntity();
				InputStream content = entity.getContent();
				InputStreamReader input = new InputStreamReader(content);
				BufferedReader reader = new BufferedReader(input);
				StringBuilder responseBuilder = new StringBuilder();
				String lineIn;
				while ((lineIn=reader.readLine())!=null) {
					responseBuilder.append(lineIn);
				}

				String responseString = responseBuilder.toString();
				JSONObject json = new JSONObject(responseString);
				Book[] books = parseResponse(json);
				return books;
			} catch (MalformedURLException e) {
				m_results.BookLookupError(String.format("Error: %s: %s", e.toString(), params[0]));
			} catch (Exception e) {
				m_results.BookLookupError(String.format("Error: %s", e.toString()));
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(Book[] result) {
			super.onPostExecute(result);
			m_results.BookLookupResult(result, false);
			m_lookup = null;
		}

		Book[] parseResponse(JSONObject json) throws Exception {
			String kind = json.getString(kKind);
			if (kind.equals(kBooksVolumes)) {
				int count = json.getInt(kItemCount);
				if (count == 0)
					return null;
				JSONArray items = json.getJSONArray(kItems);
				Book[] books = new Book[count];
				for (int i = 0; i < count ; ++i) {
					books[i] = Book.parseJSON(items.getJSONObject(i));
				}
				return books;
			}
			throw new Exception("Invalid Response");
		}
	}
}
