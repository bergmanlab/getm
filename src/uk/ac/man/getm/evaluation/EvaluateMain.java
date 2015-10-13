package uk.ac.man.getm.evaluation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.StreamIterator;
import uk.ac.man.documentparser.DocumentParser;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.entitytagger.EntityTagger;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.matching.Matcher;
import uk.ac.man.getm.ExpressionMention;
import uk.ac.man.getm.ExpressionMatcher;
import uk.ac.man.getm.ExpressionMiner;

public class EvaluateMain {
	public static void main(String[] args){
		ArgParser ap = new ArgParser(args);
		Logger logger = Loggers.getDefaultLogger(ap);

		DocumentIterator documents = DocumentParser.getDocuments(ap, logger);
		ExpressionMatcher matcher = ExpressionMiner.getMatcher(ap, logger);
		Map<String,List<ExpressionMention>> goldStandard = loadGold(ap.getFile("gold"));

		Matcher geneMatcher = EntityTagger.getMatcher(ap, logger, "-genes");
		Matcher anatomyMatcher = EntityTagger.getMatcher(ap, logger, "-anatomy");

		run(documents,matcher,goldStandard, geneMatcher, anatomyMatcher, ap.getFile("outHTML"), ap.getInt("pre",50), ap.getInt("post",50));
	}

	private static void run(DocumentIterator documents,
			ExpressionMatcher matcher,
			Map<String, List<ExpressionMention>> goldStandard, Matcher geneMatcher, Matcher anatomyMatcher, File outHTML, int pre, int post) {

		try{

			int TP=0,FP=0,FN=0;
			BufferedWriter outStream = outHTML != null ? new BufferedWriter(new FileWriter(outHTML)) : null;

			for (Document d : documents){
				String text = d.toString();

				//get GETM mentions
				List<Mention> predictedMentions = matcher.match(d);
				
				List<ExpressionMention> goldMentions = goldStandard.containsKey(d.getID()) ? goldStandard.get(d.getID()) : new ArrayList<ExpressionMention>();
				List<Mention> geneMatches = geneMatcher.match(d);
				List<Mention> anatomyMatches = anatomyMatcher.match(d);

				for (Mention predMention : predictedMentions){
					boolean isFP=true;
					//ExpressionMatch predMention2 = (ExpressionMatch) predMention;
					
					//if we have a mention 'IL2 is expressed in T cells and B cells', split it in two: 'IL2/T cells' and 'IL2/B cells'
					List<ExpressionMention> explodedMentions = explode((ExpressionMention) predMention);
					
					//for all extracted mentions
					for (ExpressionMention predMention2 : explodedMentions){
						
						//search for a match among the gold-standard corpus data
						for (ExpressionMention goldMention : goldMentions){
							//only check the gold-standard mentions that have an associated anatomical location
							if (goldMention.getLocations() != null && goldMention.getLocations().length > 0)
								if (matches(predMention2,goldMention, geneMatches, anatomyMatches))
									isFP=false;
						}
						
						if (isFP){
							//System.out.println("FP: " + predMention2.toString());
							//if (outStream != null)
							//	outStream.write("FP: " + d.getID() + ": " + ExpressionMiner.toHTML(text, predMention2, pre, post) + "<br><br>\n");
							FP++;
						} else {
							//System.out.println("TP: " + predMention2.toString());
							//if (outStream != null)
							//	outStream.write("TP: " + d.getID() + ": " + ExpressionMiner.toHTML(text, predMention2) + "<br><br>\n");
							TP++;
						}
					}
				}

				for (Mention goldMention : goldMentions){
					boolean isFN=true;
					ExpressionMention goldMention2 = (ExpressionMention) goldMention;

					//only process gold-standard expression mentions that have an associated anatomical location
					if (goldMention2.getLocations() != null && goldMention2.getLocations().length > 0){
						
						//search for a match among the extracted mentions 
						for (Mention predMention : predictedMentions){
							ExpressionMention predMention2 = (ExpressionMention) predMention;
							if (matches(predMention2, goldMention2, geneMatches, anatomyMatches))
								isFN=false;
						}
						
						//if none could be found, we have a FN. If one could be found, we have a TP (but this would ahve been covered previously already)
						if (isFN){
							//System.out.println("FN: " + goldMention2.toString());
							//if (outStream != null)
							//	outStream.write("FN: " + d.getID() + ": " + ExpressionMiner.toHTML(text, goldMention2) + "<br><br>\n");
							FN++;
						}
					} 
				}
			}

			System.out.println("TP: " + TP);
			System.out.println("FP: " + FP + ", p: " + (((double)TP)/((double)TP+(double)FP)));
			System.out.println("FN: " + FN + ", r: " + (((double)TP)/((double)TP+(double)FN)));

			outStream.close();

		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static List<ExpressionMention> explode(ExpressionMention predMention) {
		List<ExpressionMention> mentions = new ArrayList<ExpressionMention>();

		if (predMention.getActors() != null && predMention.getLocations() != null){

			for (Mention a : predMention.getActors()){
				for (Mention l : predMention.getLocations()){
					mentions.add(new ExpressionMention(new Mention[]{a},predMention.getTrigger(),new Mention[]{l},predMention.getSpecies()));
				}			
			}			

		} else {
			mentions.add(predMention);
		}
		return mentions;
	}

	/**
	 * Determines if a predicted gene expression mention matches one in the gold-standard document corpus 
	 * @param predMention
	 * @param goldMention
	 * @param geneMentions
	 * @param anatomyMentions
	 * @return
	 */
	private static boolean matches(ExpressionMention predMention, ExpressionMention goldMention, List<Mention> geneMentions, List<Mention> anatomyMentions) {
		if (predMention == null || goldMention == null)
			return false;

		if (predMention.getTrigger() == null || goldMention.getTrigger() == null)
			return false;

		if (predMention.getActors() == null || goldMention.getActors() == null)
			return false;

		if (!predMention.getTrigger().overlaps(goldMention.getTrigger()))
			return false;

		//check if the gene component matches
		boolean actorsMatches = false;
		for (Mention a : predMention.getActors())
			for (Mention a2 : goldMention.getActors())
				if (a != null && a2 != null && a.overlaps(a2))
					actorsMatches = true;

		if (!actorsMatches)
			return false;

		if (predMention.getLocations() == null || goldMention.getLocations() == null)
			return false;

		//check if the anatomical component matches
		boolean locationsMatches = false;
		for (Mention l : predMention.getLocations())
			for (Mention l2 : goldMention.getLocations())
				if (l != null && l2 != null && l.overlaps(l2))
					locationsMatches = true;

		if (!locationsMatches)
			return false;

		return true;
	}

	private static Map<String, List<ExpressionMention>> loadGold(File file) {
		Map<String,List<ExpressionMention>> aux = new HashMap<String, List<ExpressionMention>>();

		StreamIterator data = new StreamIterator(file,true);

		int numTotal = 0;
		int numWithLocation = 0;

		for (String l : data){
			String[] fs = l.split("\t",-1);

			Mention trigger = new Mention((String)null, Integer.parseInt(fs[1]), Integer.parseInt(fs[2]), fs[3]);
			Mention actor = new Mention((String)null, Integer.parseInt(fs[4]), Integer.parseInt(fs[5]), fs[6]);
			Mention location = fs[8].length() > 0 ? new Mention((String)null, Integer.parseInt(fs[8]), Integer.parseInt(fs[9]), fs[10]) : null;

			trigger.setDocid(fs[0]);
			actor.setDocid(fs[0]);
			if (location != null)
				location.setDocid(fs[0]);

			Mention[] locations = location != null ? new Mention[]{location} : null;

			ExpressionMention m = new ExpressionMention(new Mention[]{actor},trigger,locations,null);

			if (!aux.containsKey(fs[0]))
				aux.put(fs[0], new ArrayList<ExpressionMention>());
			aux.get(fs[0]).add(m);

			numTotal++;
			if (location != null)
				numWithLocation++;
		}

		System.out.println("Loaded " + numTotal + " annotations (of which " + numWithLocation + " have locations)");

		return aux;
	}
}
