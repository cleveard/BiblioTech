package com.camlinard.mangatracker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;

import org.json.JSONArray;
import org.json.JSONObject;

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

	public String mVolumeID = new String();
	public String mISBN = new String();
	public String mTitle = new String();
	public String mSubTitle = new String();
	public String[] mAuthors = new String[0];
	public String mDescription = new String();
	public String mSmallThmbnail = new String();
	public String mSmallThumbnailFile = new String();
	public String mThumbnail = new String();
	public String mThumbnailFile = new String();
	public int mPageCount = 0;
	
	Book() {
	}
	
	public static Book load(ObjectInputStream s, int version)
			throws OptionalDataException, ClassNotFoundException, IOException {
		Book book = new Book();
		book.mTitle = s.readUTF();
		book.mSubTitle = s.readUTF();
		book.mISBN = s.readUTF();
		book.mDescription = s.readUTF();
		book.mSmallThmbnail = s.readUTF();
		book.mSmallThumbnailFile = s.readUTF();
		book.mThumbnail = s.readUTF();
		book.mThumbnailFile = s.readUTF();
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
		s.writeUTF(mSmallThmbnail);
		s.writeUTF(mSmallThumbnailFile);
		s.writeUTF(mThumbnail);
		s.writeUTF(mThumbnailFile);
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
			book.mSmallThmbnail = hasMember(links, kSmallThumb);
			book.mThumbnail = hasMember(links, kThumb);
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
}
