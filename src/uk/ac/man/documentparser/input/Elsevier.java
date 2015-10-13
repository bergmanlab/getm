package uk.ac.man.documentparser.input;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import martin.common.xml.EntityResolver;

import uk.ac.man.documentparser.dataholders.Author;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.dataholders.ExternalID;
import uk.ac.man.documentparser.dataholders.Journal;
import uk.ac.man.documentparser.dataholders.Document.Type;
import uk.ac.man.documentparser.dataholders.ExternalID.Source;

public class Elsevier implements DocumentIterator {
	private org.w3c.dom.Document root;
	private String doi;
	private boolean next;
	private String xml;

	public Elsevier(String doi, String xml, String[] dtdLocations){

		if (xml.toLowerCase().startsWith("<error>"))
			next = false;
		else {
			this.doi = doi;
			try{
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();

				if (dtdLocations != null)
					db.setEntityResolver(new EntityResolver(dtdLocations));

				this.root = db.parse(new InputSource(new StringReader(xml)));
				//this.root = db.parse(new FileInputStream(xml));
				this.next = true;
				this.xml=xml;
			}catch (Exception e){
				System.err.println(doi + ": " + e);
				next=false;
			}
		}
	}

	public void skip() {
		if (!next)
			throw new NoSuchElementException();
		next=false;
	}

	public boolean hasNext() {
		return next;
	}

	public Document next() {
		if (!next)
			throw new NoSuchElementException();

		Node dtdInfoNode = martin.common.xml.XPath.getNode("doc/lnv:DTD-INFO/lnsm:art", root);
		
		String docSubType = null;
		if (dtdInfoNode != null){
			if (docSubType == null && dtdInfoNode.getAttributes().getNamedItem("docsubtype") != null)
				docSubType = dtdInfoNode.getAttributes().getNamedItem("docsubtype").getTextContent(); 
			if (docSubType == null && dtdInfoNode.getAttributes().getNamedItem("DOCSUBTY") != null)
				docSubType = dtdInfoNode.getAttributes().getNamedItem("DOCSUBTY").getTextContent(); 
		}
		if (docSubType != null)
			docSubType = docSubType.toLowerCase();
		
		Node typeNode = martin.common.xml.XPath.getNode("doc/lnv:DOC-HEAD", root);
		

		/*String type = typeNode != null ? typeNode.getTextContent() : null;
		Type type_ = type != null ? Type.OTHER : null;
		if (type != null && type.toLowerCase().contains("research paper"))
			type_ = Type.RESEARCH;
		if (type != null && type.toLowerCase().contains("review"))
			type_ = Type.REVIEW;*/
		Type type = Type.OTHER;
		if (docSubType != null && (docSubType.contains("fla") || docSubType.contains("abs")))
			type = Type.RESEARCH;
		if (docSubType != null && docSubType.contains("rev"))
			type = Type.REVIEW;
		
		Node titleNode = martin.common.xml.XPath.getNode("doc/lnv:ENG-TITLE", root);
		String title = titleNode != null ? titleNode.getTextContent() : null;

		Node pagesNode = martin.common.xml.XPath.getNode("doc/lnv:PAGES", root);
		String pages_ = pagesNode != null ? pagesNode.getTextContent() : null;
		String pages = pages_ != null && pages_.toLowerCase().contains("page") && pages_.indexOf(' ') != -1 ? pages_.split(" ")[1] : pages_;		
		
		Node dateNode = martin.common.xml.XPath.getNode("doc/lnv:DATE", root);
		String date = dateNode != null ? dateNode.getTextContent() : null;
		String[] date_fs = date != null && date.contains(" ") ? date.split(" ") : null;
		String year = date_fs != null ? date_fs[date_fs.length-1] : date;		
		
		Node volNode = martin.common.xml.XPath.getNode("doc/lnv:VOL-ISSUE", root);
		String volIssue = volNode != null ? volNode.getTextContent() : null;
		String volume = volIssue; String issue=null;
		if (volIssue.toLowerCase().matches("volume .*, issues? .*")){
			String[] fs = volIssue.split(", ");
			volume = fs[0].split(" ")[1];
			issue = fs[1].split(" ")[1];
		}
		
		Node absNode = martin.common.xml.XPath.getNode("doc/lnv:PRIM-ABST/lnsm:abs", root);
		String abs = absNode != null ? absNode.getTextContent() : null;

		Node bdyNode = martin.common.xml.XPath.getNode("doc/lnv:TEXT-1", root);
		String bdy = bdyNode != null ? bdyNode.getTextContent() : null;

		Node ISSNNode = martin.common.xml.XPath.getNode("doc/lnv:ISSN", root);
		String ISSN = ISSNNode != null ? ISSNNode.getTextContent() : null;
		Node journalNameNode = martin.common.xml.XPath.getNode("doc/lnv:JOURNAL-NAME", root);
		String journalName = journalNameNode != null ? journalNameNode.getTextContent() : null;

		next=false;
		
		ExternalID eid = new ExternalID(doi,Source.ELSEVIER);
		Journal journal = new Journal(ISSN,journalName,null);
		Author[] authors = null;
		
		Document d = new Document(doi,title,abs,bdy,null,null,year,journal,type,authors,volume,issue,pages,xml,eid);
		
		return d;
	}

	public void remove() {
		throw new IllegalStateException("Not implemented");
	}

	public Iterator<Document> iterator() {
		return this;
	}
}
