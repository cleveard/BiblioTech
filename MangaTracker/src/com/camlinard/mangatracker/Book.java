package com.camlinard.mangatracker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;

public class Book {
	public String mTitle;
	public String mSubTitle;
	public String mISBN;
	
	Book() {
		mTitle = new String();
		mSubTitle = new String();
		mISBN = new String();
	}
	
	public static Book load(ObjectInputStream s, int version)
			throws OptionalDataException, ClassNotFoundException, IOException {
		Book book = new Book();
		book.mTitle = (String)s.readObject();
		book.mSubTitle = (String)s.readObject();
		book.mISBN = (String)s.readObject();
		return book;
	}
	
	public void store(ObjectOutputStream s, int version)
			throws IOException {
		s.writeObject(mTitle);
		s.writeObject(mSubTitle);
		s.writeObject(mISBN);
	}
}
