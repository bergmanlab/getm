package uk.ac.man.documentparser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.BMCFactory;
import uk.ac.man.documentparser.input.BMCXMLFactory;
import uk.ac.man.documentparser.input.DatabaseIterator;
import uk.ac.man.documentparser.input.DatabaseIterator2;
import uk.ac.man.documentparser.input.Directory;
import uk.ac.man.documentparser.input.DocumentBuffer;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.documentparser.input.InputFactory;
import uk.ac.man.documentparser.input.MedlineIndexFactory;
import uk.ac.man.documentparser.input.MedlinePMCIndexFactory;
import uk.ac.man.documentparser.input.OTMI;
import uk.ac.man.documentparser.input.OTMIFactory;
import uk.ac.man.documentparser.input.PMCAbstract;
import uk.ac.man.documentparser.input.PMCFactory;
import uk.ac.man.documentparser.input.PMCIndexFactory;
import uk.ac.man.documentparser.input.TextFile;
import uk.ac.man.documentparser.input.TextFileFactory;

import martin.common.ArgParser;
import martin.common.Loggers;

public class DocumentParser {

	private static void runSeparated(DocumentIterator documents, File outputDir, int report, Logger logger){
		if (outputDir == null)
			throw new IllegalStateException("Need to specify an output base directory after the runSeparated command");

		int c = 0;

		while (documents.hasNext()){
			Document doc = documents.next();

			if (doc != null){
				if (doc.getID() == null)
					throw new IllegalStateException("ID not set");

				String id = doc.getID();

				boolean pmc = id.startsWith("PMC");

				String first = ("0000" + id).substring(id.length()+4-2,id.length()+4);
				String second = ("0000" + id).substring(id.length()+4-4,id.length()+4-2);

				File dir = new File(outputDir, first);

				if (!dir.exists())
					dir.mkdir();

				if (!pmc){
					dir = new File(dir, second);

					if (!dir.exists())
						dir.mkdir();
				}

				File outFile = new File(dir, id.replace(File.separatorChar, '_') + ".txt");

				doc.saveToTextFile(outFile, false);

				if (report != -1 && ++c % report == 0)
					logger.info("%t: Saved " + c + " documents.\n");
			}
		}		
	}

	private static void run(DocumentIterator documents, File outputDir, boolean simplify){
		while (documents.hasNext()){
			Document doc = documents.next();

			if (doc.getID() == null)
				throw new IllegalStateException("ID not set");

			if (outputDir != null){
				System.out.print("Saving document with ID " + doc.getID() + ", year "  + doc.getYear() + "... ");

				File outFile = new File(outputDir,doc.getID().replace(File.separatorChar, '_') + ".txt");

				doc.saveToTextFile(outFile, simplify);
				//doc.saveToTextFile(filename + ".txt.tokens",true);	
				System.out.println("done.");
			} else {
				System.out.println(doc.getID());
				doc.print();
			}
		}		
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArgParser ap = new ArgParser(args);

		Logger logger = Loggers.getDefaultLogger(ap);
		int report = ap.getInt("report", -1);

		if (ap.containsKey("help") || args.length == 0){
			System.out.println("documentparser.jar [--properties <conf file>]");
			System.out.println(getDocumentHelpMessage());
			System.out.println("[--outDir <export directory> [--simplify]]");
			System.out.println("[--getPubYears <output file> [--report <report interval>]]");
			System.exit(0);
		}

		if (ap.containsKey("outDir")){
			//save documents in text format to a directory, one file per document
			DocumentIterator documents = getDocuments(ap, logger);
			File outDir = ap.getFile("outDir");
			run(documents, outDir, ap.containsKey("simplify"));
		}

		if (ap.containsKey("outSeparated")){
			//save documents in text format to a directory, one file per document
			//the files will be saved in a hierarchical directory structure to avoid having too many
			//files in the same directory which would slow the filesystem down.
			DocumentIterator documents = getDocuments(ap, logger);
			File outBaseDir = ap.getFile("outSeparated");

			runSeparated(documents,outBaseDir, report, logger);
		}

		if (ap.containsKey("print")){
			//will print the documents to STDOUT. Mostly meant for debugging document parsing methods.
			DocumentIterator documents = getDocuments(ap, logger);
			for (Document d : documents)
				d.print();
		}

		if (ap.containsKey("saveToDB")){
			//saves the text of parsed documents to a database (can later be loaded with --databaseDocs)
			DocumentIterator documents = getDocuments(ap, logger);
			Connection conn = martin.common.SQL.connectMySQL(ap, logger, "articles");
			logger.info("%t: Processing...\n");
			saveToDB(documents, conn, logger, report);
			logger.info("%t: Completed.\n");
		}
		
		if (ap.containsKey("buildDescriptions")){
			DocumentIterator documents = getDocuments(ap, logger);
			buildDescriptions(documents, logger, ap.getFile("buildDescriptions"), ap.getInt("report", -1));
		}
	}

	private static void buildDescriptions(DocumentIterator documents, Logger logger, File outFile, int report) {
		try{
			BufferedWriter outStream = new  BufferedWriter(new FileWriter(outFile));
			int c = 0;
			outStream.write("#ID\tdescription\tyear\n");
			for (Document d : documents){
				String year = d.getYear() != null && d.getYear().length() == 4 ? d.getYear() : "0";
				outStream.write(d.getID() + "\t" + d.getDescription() + "\t" + year + "\n");
				
				if (report != -1 && ++c % report == 0)
					logger.info("%t: Processed " + c + " documents.\n");
			}
			logger.info("%t: Completed.\n");
			outStream.close();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static void saveToDB(DocumentIterator documents, Connection conn, Logger logger, int report) {
		try{
			PreparedStatement pstmt = Document.prepareInsertStatements(conn);

			int c = 0;
			for (Document d : documents){

				if (d != null)
					d.saveToDB(pstmt);

				if (report != -1 && ++c % report == 0)
					logger.info("%t: Saved " + c + " documents to DB.\n");
			}

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}		
	}

	/**
	 * @param ap an ArgParser object, containing user-supplied arguments used to determine how to load the documents
	 * @param validIDs if specified (i.e. not null), will restrict the iterator to only return documents contained in validIDs
	 * @return an iterator used for iterating over a set of documents
	 */
	public static DocumentIterator getDocuments(ArgParser ap){
		return getDocuments(ap, null);
	}

	/**
	 * @param ap an ArgParser object, containing user-supplied arguments used to determine how to load the documents
	 * @param validIDs if specified (i.e. not null), will restrict the iterator to only return documents contained in validIDs
	 * @param logger logging object used for performing logging (may be null). 
	 * @return an iterator used for iterating over a set of documents
	 */
	public static DocumentIterator getDocuments(ArgParser ap, Logger logger){
		String[] dtds = ap.gets("dtd");
		DocumentIterator documents = null;
		String restrictPostfix = ap.get("restrictPostfix");

		if (ap.containsKey("pmcAbs")){
			//not really used
			File medlineBaseDir = ap.getFile("medlineBaseDir");
			File medlineIndexFile = ap.getFile("medlineIndex");
			DocumentIterator medlineDocs = medlineIndexFile !=null ? new MedlineIndexFactory(medlineBaseDir,null).parse(medlineIndexFile) : null;

			File pmcBaseDir = ap.getFile("pmcBaseDir");
			File pmcIndexFile = ap.getFile("pmcIndex");
			DocumentIterator pmcDocs = pmcIndexFile != null ? new PMCIndexFactory(pmcBaseDir,dtds).parse(pmcIndexFile) : null;

			documents = new PMCAbstract(pmcDocs, medlineDocs);
		} else if (ap.containsKey("medlineIndex")){
			//MEDLINE XML, specified by a special index file (see documentation for format)
			File medlineBaseDir = ap.getFile("medlineBaseDir");
			File indexFile = ap.getFile("medlineIndex");

			documents = new MedlineIndexFactory(medlineBaseDir,null).parse(indexFile);
		} else if (ap.containsKey("medlinePMCIndex")){
			//combines a PMC and MEDLINE repository, returning documents
			//with data from both

			File medlineBaseDir = ap.getFile("medlineBaseDir");
			File pmcBaseDir = ap.getFile("pmcBaseDir");
			File indexFile = ap.getFile("medlinePMCIndex");

			documents = new MedlinePMCIndexFactory(medlineBaseDir,pmcBaseDir,dtds,null).parse(indexFile);
		} else if (ap.containsKey("pmcIndex")){
			//PMC XML, specified by a special index file (see documentation for format)
			File pmcBaseDir = ap.getFile("pmcBaseDir");
			File indexFile = ap.getFile("pmcIndex");

			documents = new PMCIndexFactory(pmcBaseDir,dtds).parse(indexFile);
		} else if (ap.containsKey("pmcDir")){
			//Directory containing PMC .xml files
			InputFactory pmcFactory = new PMCFactory(dtds);
			documents = new Directory(ap.getFile("pmcDir"),pmcFactory,".xml", ap.containsKey("recursive"));
		} else if (ap.containsKey("OTMI")){
			//OTMI XML file
			documents = new OTMI(ap.getFile("OTMI"));
		} else if (ap.containsKey("OTMIDir")){
			//Directory containing OTMI XML files
			documents = new Directory(ap.getFile("OTMIDir"),new OTMIFactory(),".otmi", ap.containsKey("recursive"));
		} else if (ap.containsKey("text")){
			//plain text-file
			return new TextFile(ap.getFiles("text"));
		} else if (ap.containsKey("textDir")){
			//directory containing plain text files
			documents = new Directory(ap.getFile("textDir"),new TextFileFactory(),".txt",ap.containsKey("recursive"));
		} else if (ap.containsKey("bmcxml")){
			//BMC XML file
			documents = new BMCXMLFactory().parse(ap.getFile("bmcxml"));
		} else if (ap.containsKey("bmcxmlDir")){
			//Directory containing BMC XML files
			documents = new Directory(ap.getFile("bmcxmlDir"),new BMCXMLFactory(),".xml",ap.containsKey("recursive"));
		} else if (ap.containsKey("bmcDir")){
			//Directory containing BMC XML files, alternative parsing
			documents = new Directory(ap.getFile("bmcDir"),new BMCFactory(dtds),".xml",ap.containsKey("recursive"));
		} else if (ap.containsKey("databaseDocs")){
			//Reads documents from a MySQL database
			Connection conn = martin.common.SQL.connectMySQL(ap, logger, "articles");
			documents = new DatabaseIterator(conn, ap.get("databaseDocs"), restrictPostfix);
		} else if (ap.containsKey("databaseDocs2")){
			//Reads documents from a MySQL database
			Connection conn = martin.common.SQL.connectMySQL(ap, logger, "articles");
			documents = new DatabaseIterator2(conn, ap.get("databaseDocs2"), ap.containsKey("full"));
		}

		if (ap.containsKey("buffer"))
			documents = new DocumentBuffer(documents, ap.getInt("buffer", 250), logger);
		
		if (documents != null && ap.containsKey("skip")){
			if (logger != null)
				logger.info("%t: Skipping " + ap.getInt("skip") + " documents...\n");
			for (int i = 0; i < ap.getInt("skip"); i++)
				documents.skip();		
			if (logger != null)
				logger.info("%t: Skip complete.\n");
		}

		return documents;		
	}

	public static String getDocumentHelpMessage() {
		return "[--medlineIndex <file> --medlineBaseDir <dir>]\n" +
		"[--medlinePMCIndex <file> --medlineBaseDir <dir> --pmcBaseDir <dir> --dtd <files>]\n" +
		"[--pmcIndex <file> --pmcBaseDir<dir> --dtd <files>]\n" +
		"[--textDir <dir> [--recursive]]\n"+
		"[--OTMIDir <dir> [--recursive]]\n";
	}

	public static Map<String, Document> getDocumentsToHash(ArgParser ap) {
		Map<String,Document> aux = new HashMap<String,Document>();
		DocumentIterator documents = getDocuments(ap);
		for (Document d : documents)
			aux.put(d.getID(),d);
		return aux;
	}
}
