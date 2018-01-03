package com.example.cleve.biblio_tech;

import org.json.JSONArray;
import org.json.JSONObject;

class Book {
	static private final String kTitle = "title";
	static private final String kSubTitle = "subtitle";
	static private final String kKind = "kind";
	static private final String kBooksVolume = "books#volume";
	static private final String kType = "type";
	static private final String kIdentifier = "identifier";
	static private final String kIndustryIdentifiers = "industryIdentifiers";
	static private final String kVolumeInfo = "volumeInfo";
	static private final String kISBN_13 = "ISBN_13";
	static private final String kISBN_10 = "ISBN_10";
	static private final String kVolumeID = "id";
	static private final String kPageCount = "pageCount";
	static private final String kDescription = "description";
	static private final String kImageLinks = "imageLinks";
	static private final String kSmallThumb = "smallThumbnail";
	static private final String kThumb = "thumbnail";
	static private final String kAuthors = "authors";

	static final int kSmallThumbnail = 0;
	static final int kThumbnail = 1;
	static final int kThumbnailCount = 2;
	
	String mVolumeID = "";
	String mISBN = "";
	String mTitle = "";
	String mSubTitle = "";
	String[] mAuthors = new String[0];
	String mDescription = "";
	String[] mThumbnails = new String[kThumbnailCount];
	int mPageCount = 0;

	static Book parseJSON(JSONObject json) throws Exception {
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
	
	private static String hasMember(JSONObject json, String name) throws Exception {
		return json.has(name) ? json.getString(name) : "";
	}

	private static String findISBN(JSONArray identifiers) throws Exception {
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
}
