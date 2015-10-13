package uk.ac.man.getm;

import java.util.ArrayList;
import java.util.List;

import martin.common.Function;
import martin.common.Pair;
import martin.common.SentenceSplitter;

import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.doc.TaggedDocument;
import uk.ac.man.entitytagger.doc.TaggedDocument.Format;
import uk.ac.man.entitytagger.matching.Matcher;

/**
 * Class which, when instantiated, allows the extraction of gene expression mentions from text documents.
 * @author Martin
 *
 */
public class ExpressionMatcher extends Matcher {
	private Matcher speciesMatcher;
	private Matcher geneMatcher;
	private Matcher anatomyMatcher;
	private Matcher triggerMatcher;

	public ExpressionMatcher(Matcher speciesMatcher, Matcher geneMatcher, Matcher anatomyMatcher, Matcher triggerMatcher){
		assert(speciesMatcher != null);
		assert(geneMatcher != null);
		assert(anatomyMatcher != null);
		assert(triggerMatcher != null);
		this.speciesMatcher = speciesMatcher;
		this.geneMatcher = geneMatcher;
		this.anatomyMatcher = anatomyMatcher;
		this.triggerMatcher = triggerMatcher;
	}

	/**
	 * Identify gene expression mentions in text and associate them to gene and anatomical mentions
	 * @param text The text
	 * @param d The document (only important thing is the document id)
	 * @return a list of all gene expression mentions occuring in the text
	 */
	public List<Mention> match(String text, Document d){
		List<Mention> genes = geneMatcher.match(text,d);
		List<Mention> species = speciesMatcher.match(text,d);
		List<Mention> anatomy = anatomyMatcher.match(text,d);
		List<Mention> triggers = triggerMatcher.match(text, d);

		List<Mention> res = new ArrayList<Mention>();

		filterAnatomy(anatomy,text);

		SentenceSplitter sentences = new SentenceSplitter(text);

		for (Pair<Integer> sc : sentences){
			//sentence start/end coordinates
			int s = sc.getX();
			int e = sc.getY();
			
			//get the triggers occuring in the sentence
			List<Mention> sTriggers = Mention.getMentionsInRange(triggers,s,e);
			
			if (sTriggers.size() > 0){
				//get the gene, anatomical and species mentions occuring in the sentence
				List<Mention> sGenes = Mention.getMentionsInRange(genes,s,e);
				List<Mention> sAnatomy = Mention.getMentionsInRange(anatomy,s,e);
				List<Mention> sSpecies = Mention.getMentionsInRange(species,s,e);

				//remove anatomical locations overlapping with genes (some genes can mistakenly be identified as anatomical locations by the NER)
				for (Mention m : sGenes)
					for (int i = 0; i < sAnatomy.size(); i++)
						if (m.overlaps(sAnatomy.get(i)))
							sAnatomy.remove(i--);

				if (sGenes.size() > 0 && sAnatomy.size() > 0){
					for (Mention trigger : sTriggers){
						//for each trigger in the sentence, associate it with a gene and anatomical location
						ExpressionMention em = associate(trigger, sGenes, sAnatomy, sSpecies, text);

						//get a nice context string suitable for HTML display and associate it with the mention
						em.setContext(getContext(text,s,e,em.getTrigger(),em.getActors(),em.getLocations()));

						res.add(em);
					}
				}
			}
		}

		return res;		
	}

	/**
	 * Creates a HTML-format context string for a gene expression mention
	 * @param text
	 * @param s the start of the context substring
	 * @param e the end of the context substring
	 * @param trigger trigger mention
	 * @param mentions2 gene mentions
	 * @param mentions3 anatomical mentions
	 * @return a string such as e.g. "We have found that IL-2 is expressed in T-cells." with IL-2, expressed and T-cells italic and underlined.
	 */
	private String getContext(String text, int s, int e, Mention trigger,
			Mention[] mentions2, Mention[] mentions3) {

		String context = text.substring(s,e);
		context = context.replace('\t', ' ');
		context = context.replace('\r', ' ');
		context = context.replace('\n', ' ');

		List<Mention> mentions = new ArrayList<Mention>(1 + mentions2.length + mentions3.length);
		mentions.add(trigger.clone());
		for (Mention m : mentions2)
			mentions.add(m.clone());
		for (Mention m : mentions3)
			mentions.add(m.clone());
		for (Mention m : mentions){
			m.setStart(m.getStart()-s);
			m.setEnd(m.getEnd()-s);			
		}

		StringBuffer sb = TaggedDocument.toHTML(context, mentions, Format.HTML, false, new Function<Pair<String>>() {
			public Pair<String> function(Object[] args) {
				//Mention m = (Mention) args[0];
				//Format f = (Format) args[1];
				//boolean link = (Boolean) args[2];
				return new Pair<String>("<u><i>","</i></u>");
			}
		});

		return sb.toString();
	}

	/**
	 * Helps scan for patterns such as [gene] was expressed in [tissue].
	 * @param firstMentions A list of the mentions that should go in the first position (genes in the above example)
	 * @param firstStartPosition the position at which the first mentions (genes above) need to start (-1 if it doesn't matter)
	 * @param firstEndPosition the position at which the first mentions (genes above) need to end (-1 if it doesn't matter)
	 * @param lastMentions A list of the mentions that should go in the last position (tissues in the above example)
	 * @param lastStartPosition the position at which the last mentions (tissues above) need to start  (-1 if it doesn't matter)
	 * @param lastEndPosition the position at which the last mention (tissues above) need to end (-1 if it doesn't matter)
	 * @return
	 */
	private Pair<Mention> checkForPattern(List<Mention> firstMentions, int firstStartPosition, int firstEndPosition, List<Mention> lastMentions, int lastStartPosition, int lastEndPosition){
		
		Mention firstMention = null;
		Mention lastMention = null;
		
		for (Mention m : firstMentions)
			if (m.getStart() == firstStartPosition || firstStartPosition == -1)
				if (m.getEnd() == firstEndPosition || firstEndPosition == -1)
					firstMention = m;

		for (Mention m : lastMentions)
			if (m.getStart() == lastStartPosition || lastStartPosition == -1)
				if (m.getEnd() == lastEndPosition || lastEndPosition == -1)
					lastMention = m;

		if (firstMention != null && lastMention != null)
			return new Pair<Mention>(firstMention,lastMention);

		return null;
	}

	/**
	 * Associates gene and anatomical mentions with trigger mentions, forming gene expression mentions
	 * @param trigger trigger keyword, e.g. "expression", "transcribed"
	 * @param genes
	 * @param locations the anatomical location mentions
	 * @param species
	 * @param text
	 * @return
	 */
	private ExpressionMention associate(Mention trigger, List<Mention> genes, List<Mention> locations, List<Mention> species, String text) {
		Mention gene=null,location=null,sp=null;

		//while species information is extracted here, it is not actually used in output
		//there are plans to use it later though

		//if there is only a single gene/tissue, pick that one
		if (genes.size() == 1)
			gene = genes.get(0);
		if (locations.size() == 1)
			location = locations.get(0);
		if (species.size() == 1)
			sp = species.get(0);

		//if gene/tissue mentions conform to specific patterns
		if (gene == null && location == null){
			//exp1: variations of "<gene> is expressed in <tissue>"
			//exp4: variations of "<gene> is transcribed in <tissue>"
			if (trigger.getText().endsWith("exp1") || trigger.getText().endsWith("exp4")){
				int tstart = trigger.getStart();
				int tend = trigger.getEnd();

				//check that there's enough space around the trigger on either side
				if (text.length() > tend + 5 && tstart > 4){
					if (text.substring(tstart-4, tend+4).equals(" is " + trigger.getText() + " in ")){
						Pair<Mention> mentions = checkForPattern(genes, -1, tstart-4, locations, tend+4, -1);
						if (mentions != null){
							gene = mentions.getX();
							location = mentions.getY();
						}
					}
				}				
			}
			//exp2, exp3: variations of "production/transcription of <gene> in <tissue>"
			if (trigger.getText().endsWith("exp2") || trigger.getText().endsWith("exp3")){
				int tstart = trigger.getStart();
				int tend = trigger.getEnd();
				int inIndex = text.indexOf(" in ", tend);

				//check that there's enough space around the trigger on either side
				if (inIndex != -1 && text.length() > inIndex + 5){
					if (text.substring(tstart, tend+4).equals(trigger.getText() + " of ")){
						Pair<Mention> mentions = checkForPattern(genes, tend + 4, inIndex, locations, inIndex+4, -1);
						if (mentions != null){
							gene = mentions.getX();
							location = mentions.getY();
						}
					}
				}				
			}
		}

		//if none of the above apply, pick closest gene and tissue		
		if (gene == null)
			gene = Mention.findClosestMention(genes, trigger.getStart());
		if (location == null)
			location = Mention.findClosestMention(locations, trigger.getStart());
		if (sp == null)
			sp = Mention.findClosestMention(species, trigger.getStart());

		Mention[] geneGroup = getGroup(genes, gene);
		Mention[] locationGroup = getGroup(locations, location);
		Mention[] speciesGroup = sp != null ? new Mention[]{sp} : null;

		return new ExpressionMention(geneGroup,trigger,locationGroup,speciesGroup);
	}

	private void filterAnatomy(List<Mention> anatomy, String text) {
		for (int i = 0; i < anatomy.size(); i++){
			if (text.length() > anatomy.get(i).getEnd() && text.charAt(anatomy.get(i).getEnd()) == '+')
				anatomy.remove(i--);
			else if (anatomy.get(i).getText().toLowerCase().equals(anatomy.get(i).getText().toUpperCase()))
				anatomy.remove(i--);
		}
	}

	private Mention[] getGroup(List<Mention> genes, Mention g) {
		if (g == null)
			return null;

		if (g.getComment() == null || !g.getComment().contains(", valid: "))
			return new Mention[]{g};

		List<Mention> res = new ArrayList<Mention>();

		for (Mention m : genes)
			if (m.getComment() != null && m.getComment().equals(g.getComment()))
				res.add(m);

		return res.toArray(new Mention[0]);
	}
}
