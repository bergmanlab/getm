package uk.ac.man.getm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import uk.ac.man.documentparser.DocumentParser;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.entitytagger.EntityTagger;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.doc.TaggedDocument;
import uk.ac.man.entitytagger.matching.MatchOperations;
import uk.ac.man.entitytagger.matching.Matcher;
import uk.ac.man.entitytagger.matching.matchers.ConcurrentMatcher;

import martin.common.ArgParser;
import martin.common.ComparableTuple;
import martin.common.Loggers;
import martin.common.compthreads.IteratorBasedMaster;

public class ExpressionMiner {
	/**
	 * Loads NER matchers for species, gene, anatomy and trigger detection
	 * @param ap object containing user-supplied matcher information
	 * @param logger
	 * @return an ExpressionMatcher that can be used to extract gene expression mentions
	 */
	public static ExpressionMatcher getMatcher(ArgParser ap, Logger logger){
		Matcher triggerMatcher = EntityTagger.getMatcher(ap, logger, "-triggers");
		Matcher geneMatcher = EntityTagger.getMatcher(ap, logger, "-genes");
		Matcher anatomyMatcher = EntityTagger.getMatcher(ap, logger, "-anatomy");
		Matcher speciesMatcher = EntityTagger.getMatcher(ap, logger, "-species");
		return new ExpressionMatcher(speciesMatcher, geneMatcher, anatomyMatcher, triggerMatcher); 
	}

	public static void main(String[] args){
		ArgParser ap = new ArgParser(args);
		Logger logger = Loggers.getDefaultLogger(ap);

		int numThreads = ap.getInt("threads", 1);
		
		//report specifies how often progress reports should be printed (e.g. every 1000 documents). Default is -1, which is no progress reports at all.
		int report = ap.getInt("report", -1);

		DocumentIterator documents = DocumentParser.getDocuments(ap,logger);
		
		//create the object that will do the mining for us
		Matcher expressionMatcher = getMatcher(ap,logger);

		logger.info("%t: Processing documents...");

		//processes the documents and stores the results to a file
		if (ap.containsKey("out"))
			MatchOperations.runToFile(expressionMatcher, documents, numThreads, report, ap.getFile("out"), logger);

		//processes the documents and stores the results to a file, in HTML format for displaying
		if (ap.containsKey("outHTML"))
			runHTML(documents, expressionMatcher, ap.getFile("outHTML"), report, logger, ap.getInt("pre",50), ap.getInt("post",50));

		//process the documents and stores the results to a file, with the addition of text surrounding the mention
		if (ap.containsKey("outContext"))
			runContext(expressionMatcher, documents, numThreads, report, ap.getFile("outContext"), logger);
	}

	/**
	 * Will process documents and write the results to a file, including the text context surrounding each mentino
	 * @param expressionMatcher the miner
	 * @param documents
	 * @param numThreads
	 * @param report how often to print a progress report (every 1000 documents, 10000, 100000...). Don't print at all if report=-1
	 * @param outFile 
	 * @param logger
	 */
	private static void runContext(Matcher expressionMatcher,
			DocumentIterator documents, int numThreads, int report, File outFile,
			Logger logger) {

		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(outFile));
			
			//set up concurrent processing, start thread
			ConcurrentMatcher cm = new ConcurrentMatcher(expressionMatcher,documents);
			IteratorBasedMaster<TaggedDocument> master = new IteratorBasedMaster<TaggedDocument>(cm,numThreads);
			master.startThread();
			
			int c = 0; 
			
			for (TaggedDocument td : master){
				List<Mention> mentions = td.getRawMatches();
				
				for (Mention m : mentions)
					outStream.write(m.toString() + "\t" + ((ExpressionMention) m).getContext() + "\n");
				
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

	/**
	 * converts an array of mentions to a string representation
	 * @param mentions
	 * @return
	 */
	private static String toString(Mention[] mentions) {
		if (mentions == null)
			return null;
		if (mentions.length == 0)
			return "\t";

		String ids = mentions[0].getIdsToString();
		String text = mentions[0].getText();

		for (int i = 1; i < mentions.length; i++){
			ids += ";" + mentions[i].getIdsToString();
			text += ";" + mentions[i].getText();
		}

		return ids + "\t" + text;
	}

	/**
	 * Process the documents and send to a file in  HTML format (useful for visualization during development)
	 * @param documents
	 * @param expressionMatcher
	 * @param outFile
	 * @param report how often to print a progress report (every 1000 documents, 10000, 100000...). Don't print at all if report=-1
	 * @param logger
	 * @param pre number of characters prior to the mention to include
	 * @param post number of characters after the mention to include
	 */
	private static void runHTML(DocumentIterator documents,
			Matcher expressionMatcher, File outFile, int report, Logger logger, int pre, int post) {

		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(outFile));
			int c = 0;
			for (Document d : documents){
				if (d != null){
					String text = d.toString();
					List<Mention> matches = expressionMatcher.match(text, d);

					for (Mention m : matches){
						ExpressionMention em = (ExpressionMention) m;

						if (em.getSpecies() != null && em.getSpecies().length > 0)
							outStream.write("<b>" + d.getID() + "</b> [" + toString(em.getSpecies()).split("\\t")[1] + "]: " + toHTML(text, em, pre, post) + "<p>\n");
						else
							outStream.write("<b>" + d.getID() + "</b>: " + toHTML(text, em, pre, post) + "<p>\n");
					}
				}

				if (report != -1 && ++c % report == 0)
					logger.info("%t: Processed " + c + " documents.\n");
			}

			outStream.close();
			logger.info("%t: Completed.\n");
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Converts an ExpressionMention to HTML format
	 * @param text
	 * @param em
	 * @param pre
	 * @param post
	 * @return the string HTML representation
	 */
	public static String toHTML(String text, ExpressionMention em, int pre, int post) {
		ComparableTuple[] matches = init(em);

		//Arrays.sort(matches);

		int added = 0;

		for (int i = matches.length - 1; i >= 0; i--){
			Mention m = (Mention) matches[i].getA();
			String color = (String) matches[i].getB();
			int s = m.getStart();
			int e = m.getEnd();

			String start = "<font style=\"background-color: " + color + "\">";
			String end = "</font>";

			text = text.substring(0, e) + end + text.substring(e);
			text = text.substring(0, s) + start + text.substring(s);

			added += end.length() + start.length();
		}

		text = text.substring(
				Math.max(0,((Mention)matches[0].getA()).getStart() - pre),
				Math.min(text.length(),((Mention)matches[matches.length - 1].getA()).getEnd() + added + post)
		);

		return text;
	}

	/**
	 * used by toHTML
	 * @param em
	 * @return
	 */
	private static ComparableTuple[] init(ExpressionMention em) {
		List<ComparableTuple> list = new ArrayList<ComparableTuple>();

		for (Mention m : em.getActors())
			if (m != null)
				list.add(new ComparableTuple<Mention,String>(m, "#FF5555"));
		for (Mention m : em.getLocations())
			if (m != null)
				list.add(new ComparableTuple<Mention,String>(m, "#55FF55"));

		if (em.getTrigger() != null)
			list.add(new ComparableTuple<Mention,String>(em.getTrigger(), "#7777FF"));

		ComparableTuple[] arr = list.toArray(new ComparableTuple[0]);
		Arrays.sort(arr);
		return arr;
	}
}
