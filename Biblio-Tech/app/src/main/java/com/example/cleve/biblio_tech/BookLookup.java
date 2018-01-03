package com.example.cleve.biblio_tech;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;

import android.os.AsyncTask;

class BookLookup {
	private static final String kKey = "&key=AIzaSyDeQMfnPyhQ23-ndhb9xs9IY_EaSiTxgms";
    private static final String kURL = "https://www.googleapis.com/books/v1";
    private static final String kVolumesCollection = "volumes";
    private static final String kISBNParameter = "isbn:%s";
    private static final String kKind = "kind";
    private static final String kBooksVolumes = "books#volumes";
    private static final String kItemCount = "totalItems";
    private static final String kItems = "items";
	
	interface LookupDelegate {
		void BookLookupResult(Book[] result, boolean more);
		void BookLookupError(String error);
	}
	
	private LookupTask m_lookup = null;
	
	private boolean Lookup(LookupDelegate results, String collection, String parameters) {
		if (m_lookup != null)
			return false;
		m_lookup = new LookupTask(results);
		m_lookup.execute(String.format("%s/%s?q=%s%s", kURL, collection, parameters, kKey));
		return true;
	}
	
	boolean LookupISBN(LookupDelegate results, String isbn) {
		return Lookup(results, kVolumesCollection, String.format(kISBNParameter, isbn));
	}
	
	private class LookupTask extends AsyncTask<String, Void, Book[]> {
		LookupDelegate m_results;
		
		LookupTask(LookupDelegate results) {
			super();
			m_results = results;
		}

		@Override
		protected Book[] doInBackground(String... params) {
			String spec = params[0];
			HttpURLConnection bookClient = null;
			try {
                URL url = new URL(spec);
                bookClient = (HttpURLConnection)url.openConnection();
                InputStream content = new BufferedInputStream(bookClient.getInputStream());
                int status = bookClient.getResponseCode();
				if (status != 200)
					throw new Exception(String.format("HTTP Error %d", status));

				InputStreamReader input = new InputStreamReader(content);
				BufferedReader reader = new BufferedReader(input);
				StringBuilder responseBuilder = new StringBuilder();
				String lineIn;
				while ((lineIn=reader.readLine())!=null) {
					responseBuilder.append(lineIn);
				}

				String responseString = responseBuilder.toString();
				JSONObject json = new JSONObject(responseString);
				return parseResponse(json);
			} catch (MalformedURLException e) {
				m_results.BookLookupError(String.format("Error: %s: %s", e.toString(), params[0]));
			} catch (Exception e) {
				m_results.BookLookupError(String.format("Error: %s", e.toString()));
			} finally {
                if (bookClient != null) {
                    bookClient.disconnect();
                }
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
