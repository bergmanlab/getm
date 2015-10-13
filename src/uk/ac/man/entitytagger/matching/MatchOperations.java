package uk.ac.man.entitytagger.matching;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import martin.common.Function;
import martin.common.Misc;
import martin.common.Pair;
import martin.common.compthreads.IteratorBasedMaster;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.doc.TaggedDocument;
import uk.ac.man.entitytagger.doc.TaggedDocument.Format;
import uk.ac.man.entitytagger.matching.matchers.ConcurrentMatcher;

public class MatchOperations {

	public static void run(Matcher matcher, DocumentIterator documents, int numThreads, int report, File outDir, Logger logger){
		ConcurrentMatcher tm = new ConcurrentMatcher(matcher,documents);
		IteratorBasedMaster<TaggedDocument> master = new IteratorBasedMaster<TaggedDocument>(tm,numThreads);
		new Thread(master).start();

		int numNullDocuments = 0;

		try{

			int counter = 0; 

			while (master.hasNext()){
				TaggedDocument td = master.next();
				if (td != null){

					String id = td.getOriginal().getID();

					ArrayList<Mention> matches = td.getAllMatches();
					Mention.saveToFile(matches, new File(outDir,id+".tags"));
					/*BufferedWriter outStream = new BufferedWriter(new FileWriter(new File(outDir,id+".tags")));
					outStream.write("#species,document,start,end,text,extra\n");

					ArrayList<Match> matches = td.getAllMatches();
					for (int i = 0; i < matches.size(); i++){
						outStream.write(matches.get(i).toString() + "\n");
					}

					outStream.close();*/
				} else {
					numNullDocuments++;
				}

				if (report != -1 && ++counter % report == 0)
					logger.info("%t: Tagged " + counter + " documents.\n");
			}
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		if (numNullDocuments > 0)
			logger.warning("Number of null documents: " + numNullDocuments + "\n");
		logger.info("%t: Completed.");
	}

	public static void runDB(Matcher matcher, DocumentIterator documents, int numThreads, String table, int report, Logger logger, Connection dbConn){
		ConcurrentMatcher tm = new ConcurrentMatcher(matcher,documents);
		IteratorBasedMaster<TaggedDocument> master = new IteratorBasedMaster<TaggedDocument>(tm,numThreads);
		new Thread(master).start();

		int numNullDocuments = 0;

		initDBTable(dbConn, table, logger);

		try{
			PreparedStatement pstmt_match = dbConn.prepareStatement("INSERT INTO " + table + " (id_document, start, end, text, comment) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
			PreparedStatement pstmt_id = dbConn.prepareStatement("INSERT INTO " + table + "ids (id_match, id_entity, probability) VALUES (?,?,?)", Statement.NO_GENERATED_KEYS);
			int counter = 0; 

			while (master.hasNext()){
				TaggedDocument td = master.next();
				ArrayList<Mention> matches = td.getAllMatches();
				if (matches != null){
					for (Mention m : matches){
						m.saveToDB(pstmt_match, pstmt_id);
					}
				} else {
					numNullDocuments++;
					logger.warning("null document," + td.getOriginal().getID() + "\n");
				}

				if (report != -1 && ++counter % report == 0)
					logger.info("%t: Tagged " + counter + " documents.\n");
			}

			pstmt_match.close();

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		if (numNullDocuments > 0)
			logger.warning("Number of null documents: " + numNullDocuments + "\n");
		logger.info("%t: Completed.");
	}

	public static void runHTML(Matcher matcher, DocumentIterator documents, int numThreads, File htmlFile, int report, Logger logger, Format format) {
		runHTML(matcher, documents, numThreads, htmlFile, report, logger, format, true);
	}

	public static void runHTML(Matcher matcher, DocumentIterator documents, int numThreads, File htmlFile, int report, Logger logger, Format format, boolean link) {
		runHTML(matcher, documents, numThreads, htmlFile, report, logger, format, link, null);
	}

	public static void runHTML(Matcher matcher, DocumentIterator documents, int numThreads, File htmlFile, int report, Logger logger, Format format, boolean link, Function<Pair<String>> alternativeTagFunction) {

		ConcurrentMatcher tm = new ConcurrentMatcher(matcher,documents);
		IteratorBasedMaster<TaggedDocument> master = new IteratorBasedMaster<TaggedDocument>(tm,numThreads);
		new Thread(master).start();

		int numNullDocuments = 0;

		logger.info("%t: Starting HTML tagging.\n");

		try {
			BufferedWriter outStream = new BufferedWriter(new FileWriter(htmlFile));

			if (format == Format.HTML)
				outStream.write("<html><body link=\"black\" alink=\"black\" vlink=\"black\" hlink=\"black\">\n");

			int counter = 0;

			while (master.hasNext()){
				TaggedDocument td = master.next();

				if (td != null){
					if (format == Format.HTML){
						outStream.write("<b>" + td.getOriginal().getID() + "</b><br>\n");

						String str = td.toHTML(link, alternativeTagFunction).toString();
						str = str.replace("\n", "<br>");

						outStream.write(str);
						outStream.flush();

						outStream.write("<p><hr><p>");
					} else if (format == Format.XMLTags){
						outStream.write(TaggedDocument.toHTML(td.getContent(), td.getRawMatches(), Format.XMLTags, link, alternativeTagFunction).toString());
						outStream.flush();
					} else {
						throw new IllegalStateException("should not have reached this stage");
					}
				} else {
					numNullDocuments++;
				}

				if (report != -1 && ++counter % report == 0)
					logger.info("%t: Tagged " + counter + " documents.\n");
			}

			if (format == Format.HTML)
				outStream.write("</body></html>");
			outStream.close();

			logger.info("%t: HTML tagging completed.\n");

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		

		if (numNullDocuments > 0)
			logger.warning("Number of null documents: " + numNullDocuments + "\n");
		logger.info("%t: Completed.");
	}

	public static void runToFile(Matcher matcher, DocumentIterator documents, int numThreads, int report, File outFile, Logger logger){
		ConcurrentMatcher tm = new ConcurrentMatcher(matcher,documents);
		IteratorBasedMaster<TaggedDocument> master = new IteratorBasedMaster<TaggedDocument>(tm,numThreads);
		new Thread(master).start();

		int numNullDocuments = 0;

		logger.info("%t: Tagging...\n");
		
		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(outFile));

			outStream.write("#entity\tdocument\tstart\tend\ttext\tcomment\n");

			int counter = 0; 

			while (master.hasNext()){
				TaggedDocument td = master.next();
				ArrayList<Mention> matches = td.getAllMatches();
				if (matches != null){
					matches = Misc.sort(matches);
					for (int i = 0; i < matches.size(); i++){
						outStream.write(matches.get(i).toString()+ "\n");
					}
				} else {
					numNullDocuments++;
					logger.warning("null document," + td.getOriginal().getID() + "\n");
				}

				if (report != -1 && ++counter % report == 0)
					logger.info("%t: Tagged " + counter + " documents.\n");

				outStream.flush();
			}

			logger.info("%t: Tagging completed.\n");

			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		if (numNullDocuments > 0)
			logger.warning("Number of null documents: " + numNullDocuments + "\n");
		logger.info("%t: Completed.");
	}

	public static TaggedDocument matchDocument(Matcher matcher, Document doc){
		String rawText = doc.toString();

		List<Mention> matches = matcher.match(rawText, doc);

		if (matches == null)
			return new TaggedDocument(doc,null,null,matches,rawText);

		for (Mention m : matches){
			m.setDocid(doc.getID());
		}

		if (doc.isIgnoreCoordinates()){
			for (int i = 0; i < matches.size(); i++){
				Mention m = matches.get(i);
				m.setStart(-1);
				m.setEnd(-1);
			}
		}

		return new TaggedDocument(doc,null,null,matches,rawText);
	}

	private static void initDBTable(Connection dbConn, String table, Logger logger) {
		try{
			logger.info("%t: Creating tables...");

			Statement stmt = (Statement) dbConn.createStatement();

			stmt.addBatch("DROP TABLE IF EXISTS `" + table + "ids`;");
			stmt.addBatch("DROP TABLE IF EXISTS `" + table + "`;");
			stmt.addBatch("CREATE TABLE  `" + table + "` (" +
					"`id_match` BIGINT unsigned NOT NULL auto_increment," +
					"`id_document` varchar(45) NOT NULL," +
					"`start` int(10) unsigned default NULL," +
					"`end` int(10) unsigned default NULL," +
					"`text` text," +
					"`comment` text," +
					"PRIMARY KEY  (`id_match`)" +
			") ENGINE=InnoDB DEFAULT CHARSET=latin1;");
			stmt.addBatch("CREATE TABLE  `" + table + "ids` (" +
					"`id_ids` BIGINT unsigned NOT NULL auto_increment," +
					"`id_match` BIGINT unsigned NOT NULL," +
					"`id_entity` varchar(45) NOT NULL," +
					"`probability` double default NULL," +
					"PRIMARY KEY  (`id_ids`)," +
					"KEY `FK_" + table + "-ids_1` (`id_match`)," +
					"CONSTRAINT `FK_" + table + "ids_1` FOREIGN KEY (`id_match`) REFERENCES `" + table + "` (`id_match`)" +
			") ENGINE=InnoDB DEFAULT CHARSET=latin1;");
			stmt.executeBatch();

			logger.info(" done.\n");
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		
	}

	public static void runOutWithContext(Matcher matcher,
			DocumentIterator documents, int numThreads, int report,
			File file, Logger logger, int preLength, int postLength) {

		ConcurrentMatcher cMatcher = new ConcurrentMatcher(matcher, documents);
		IteratorBasedMaster<TaggedDocument> master = new IteratorBasedMaster<TaggedDocument>(cMatcher, numThreads);
		master.startThread();

		int c = 0;

		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(file));
			outStream.write("#entity\tdocument\tstart\tend\ttext\tcomment\tpre\tpost\n");
			logger.info("%t: Tagging...\n");
			for (TaggedDocument td : master){
				Document d = td.getOriginal();
				String text = d.toString();
				List<Mention> matches = td.getAllMatches();

				for (Mention m : matches){
					int s = m.getStart();
					int e = m.getEnd();

					String pre = text.substring(Math.max(0, s-preLength), Math.min(text.length(), s)).replace('\n', ' ').replace('\r',' ');
					//String term = text.substring(Math.max(0,s),Math.min(text.length(), e));
					String post = text.substring(Math.max(0,e), Math.min(text.length(), e+postLength)).replace('\n', ' ').replace('\r',' ');

					outStream.write(m.toString() + "\t" + pre + "\t" + post + "\n");
				}

				outStream.flush();

				if (report != -1 && ++c % report == 0)
					logger.info("%t: Processed " + c + " documents.\n");
			}	
			logger.info("%t: Completed.");
			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

}
