package uk.ac.man.documentparser.misc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.logging.Logger;

import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.SQL;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.Elsevier;
import martin.common.compthreads.IteratorBasedMaster;
import martin.common.compthreads.Problem;

public class ElsevierParser {
	private class EProblemIterator implements Iterator<Problem<Document>> {
		private class EProblem implements Problem<Document>{
			private String doi;
			private String xml;

			public EProblem(String doi, String xml) {
				this.doi = doi;
				this.xml = xml;
			}

			public Document compute() {
				Elsevier e = new Elsevier(doi,xml,null);
				if (e.hasNext())
					return e.next();
				else
					return null;
			}
		}

		private ResultSet rs;
		private boolean hasNext;

		public EProblemIterator(ResultSet rs){
			this.rs = rs;
			try {
				this.hasNext = rs.next();
			} catch (SQLException e) {
				System.err.println(e);
				e.printStackTrace();
				System.exit(-1);
			}
		}

		public boolean hasNext() {
			return hasNext;
		}

		public Problem<Document> next() {
			try{
				String doi = rs.getString(1);
				String xml = rs.getString(2);
				hasNext = rs.next();
				return new EProblem(doi,xml);
			} catch (Exception e){
				System.err.println(e);
				e.printStackTrace();
				System.exit(-1);
			}
			return null;
		}

		public void remove() {
			throw new IllegalStateException("not implemented");
		}
	}

	public void run(ArgParser ap, Logger logger){
		String[] files = ap.gets("manual");
		if (files != null)
			for (String f : files){
				Document d = new Elsevier("test",f,null).next();
				if (d != null)
					System.out.println(d.toString());
				System.out.println();
			}

		String inTable = ap.get("inTable");
		String outTable = ap.get("outTable");
		
		try{
			Connection inputConn = SQL.connectMySQL(ap, logger, ap.get("inDB","articles"));
			Statement stmt = inputConn.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
			stmt.setFetchSize(Integer.MIN_VALUE);
			ResultSet rs = stmt.executeQuery("SELECT doi, xml from " + inTable);
			EProblemIterator epi = new EProblemIterator(rs);
			IteratorBasedMaster<Document> master = new IteratorBasedMaster<Document>(epi,ap.getInt("threads",1),1000);
			master.startThread();
			
			Connection outputConn = SQL.connectMySQL(ap, logger, ap.get("outDB","articles"));
			PreparedStatement pstmt = Document.prepareInsertStatements(outputConn, outTable);
			
			int report = ap.getInt("report",-1);
			int c=0;
			int c_null=0;
			
			for (Document d : master){
				if (d != null)
					d.saveToDB(pstmt);
				else
					c_null++;
				
				if (report != -1 && ++c % report == 0)
					logger.info("%t: Processed " + c + " documents (" +  c_null + " errors)\n");
			}
			
			
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArgParser ap = new ArgParser(args);
		Logger logger = Loggers.getDefaultLogger(ap);
		new ElsevierParser().run(ap,logger);
	}
}
