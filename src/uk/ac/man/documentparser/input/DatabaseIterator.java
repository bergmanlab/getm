package uk.ac.man.documentparser.input;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.NoSuchElementException;

import java.sql.Connection;

import uk.ac.man.documentparser.dataholders.Document;

public class DatabaseIterator implements DocumentIterator {
	private ResultSet rs;

	public DatabaseIterator(Connection conn, String table, String restrictPostfix){
		try{
			Statement stmt = conn.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
			stmt.setFetchSize(Integer.MIN_VALUE);

			if (restrictPostfix == null)
				this.rs = stmt.executeQuery("SELECT id_document, text FROM " + table);
			else
				this.rs = stmt.executeQuery("SELECT id_document, text FROM " + table + " WHERE id_document LIKE '%" + restrictPostfix + "'");

			this.rs.next();
		} catch (Exception e){
			System.err.println(e.toString());
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void skip() {
		try{
			rs.next();
		} catch (SQLException e) {
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public boolean hasNext() {
		try {
			return !rs.isAfterLast();
		} catch (SQLException e) {
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return false; //dummy
	}

	public Document next() {
		try{
			if (rs.isAfterLast())
				throw new NoSuchElementException();


			String id = rs.getString(1);
			String text = rs.getString(2);

			Document d = new Document(id, null, null, text, null, null, null, null, null, null, null, null, null, null, null);

			rs.next();

			return d;
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return null; //dummy return
	}

	public void remove() {
		throw new IllegalStateException();		
	}

	public Iterator<Document> iterator() {
		return this;
	}
}
