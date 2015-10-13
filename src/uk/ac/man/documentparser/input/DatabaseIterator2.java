package uk.ac.man.documentparser.input;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;

import uk.ac.man.documentparser.dataholders.Author;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.dataholders.ExternalID;
import uk.ac.man.documentparser.dataholders.Journal;
import uk.ac.man.documentparser.dataholders.Document.Text_raw_type;
import uk.ac.man.documentparser.dataholders.Document.Type;
import uk.ac.man.documentparser.dataholders.ExternalID.Source;

public class DatabaseIterator2 implements DocumentIterator {

	private ResultSet rs;
	private boolean hasNext;
	private boolean full;

	public DatabaseIterator2(Connection conn, String SELECT, boolean full){
		try {
			System.out.println(SELECT);
			
			Statement stmt = conn.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
			stmt.setFetchSize(Integer.MIN_VALUE);
			
			this.rs = stmt.executeQuery(SELECT);

			System.out.println("done");
			hasNext = rs.next();
			this.full = full;
		} catch (SQLException e) {
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void skip() {
		try {
			hasNext = rs.next();
			if (!hasNext)
				rs.close();
		} catch (SQLException e) {
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public boolean hasNext() {
		return hasNext;
	}

	public Document next() {
		assert(hasNext);
		try{
			Document d = null;
			
			if (!full){
				d = new Document(
						rs.getString("id_ext"),
						rs.getString("text_title"),
						rs.getString("text_abstract"),
						rs.getString("text_body"),
						rs.getString("text_raw"),
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						null,
						null);
			} else {
			d = new Document(
					rs.getString("id_ext"),
					rs.getString("text_title"),
					rs.getString("text_abstract"),
					rs.getString("text_body"),
					rs.getString("text_raw"),
					convTextRawType(rs.getString("text_raw_type")),
					rs.getString("year"),
					new Journal(rs.getString("id_issn"),null,null),
					convType(rs.getString("article_type")),
					convAuthors(rs.getString("authors")),
					rs.getString("volume"),
					rs.getString("issue"),
					rs.getString("pages"),
					rs.getString("xml"),
					new ExternalID(rs.getString("id_ext"), convSource(rs.getString("source"))));
			}

			hasNext = rs.next();
			if (!hasNext)
				rs.close();
			return d;
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return null;
	}

	private Source convSource(String source) {
		if (source == null)
			return null;

		if (source.equals(ExternalID.Source.ELSEVIER.toString().toLowerCase()))
			return ExternalID.Source.ELSEVIER;
		if (source.equals(ExternalID.Source.MEDLINE.toString().toLowerCase()))
			return ExternalID.Source.MEDLINE;
		if (source.equals(ExternalID.Source.OTHER.toString().toLowerCase()))
			return ExternalID.Source.OTHER;
		if (source.equals(ExternalID.Source.PMC.toString().toLowerCase()))
			return ExternalID.Source.PMC;
		if (source.equals(ExternalID.Source.TEXT.toString().toLowerCase()))
			return ExternalID.Source.TEXT;
		return null;
	}

	private Author[] convAuthors(String string) {
		if (string == null)
			return null;
		if (string.length() == 0)
			return new Author[0];
		
		String[] fs = string.split("\\|");

		Author[] as = new Author[fs.length];
		for (int i = 0; i < fs.length; i++){
			String[] fss = fs[i].split(", ");
			if (fss.length == 2)
				as[i] = new Author(fss[0],fss[1],null);
			else
				as[i] = new Author(fs[i],"",null);
		}
		return as;
	}

	private Type convType(String type) {
		if (type == null)
			return null;

		if (type.equals(Document.Type.OTHER.toString().toLowerCase()))
			return Document.Type.OTHER;
		if (type.equals(Document.Type.RESEARCH.toString().toLowerCase()))
			return Document.Type.RESEARCH;
		if (type.equals(Document.Type.REVIEW.toString().toLowerCase()))
			return Document.Type.REVIEW;
		return null;
	}

	private Text_raw_type convTextRawType(String type) {
		if (type == null)
			return null;

		if (type.equals(Document.Text_raw_type.OCR.toString().toLowerCase()))
			return Text_raw_type.OCR;
		if (type.equals(Document.Text_raw_type.PDF2TEXT.toString().toLowerCase()))
			return Text_raw_type.PDF2TEXT;
		if (type.equals(Document.Text_raw_type.TEXT.toString().toLowerCase()))
			return Text_raw_type.TEXT;
		if (type.equals(Document.Text_raw_type.XML.toString().toLowerCase()))
			return Text_raw_type.XML;
		return null;
	}

	public void remove() {
		throw new IllegalStateException("not implemented");
	}

	public Iterator<Document> iterator() {
		return this;
	}
}
