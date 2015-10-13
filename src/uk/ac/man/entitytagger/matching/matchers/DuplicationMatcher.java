package uk.ac.man.entitytagger.matching.matchers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.generate.GenerateMatchers;
import uk.ac.man.entitytagger.matching.Matcher;

public class DuplicationMatcher extends Matcher {
	private Matcher matcher;

	public DuplicationMatcher(Matcher matcher){
		this.matcher = matcher;
	}

	@Override
	public List<Mention> match(String text, Document doc) {
		List<Mention> mentions = matcher.match(text,doc);
		List<Mention> aux = new ArrayList<Mention>();
		
		Map<String,Mention> termToMention = new HashMap<String,Mention>();
		
		for (Mention m : mentions)
			termToMention.put(m.getText(), m);
		
		for (String term : termToMention.keySet()){
			
			Pattern p = Pattern.compile("\\b" + GenerateMatchers.escapeRegexp(term) + "\\b");
			java.util.regex.Matcher matcher = p.matcher(text);
			while (matcher.find()){
				Mention m = termToMention.get(term).clone();
				int s = matcher.start();
				m.setStart(s);
				m.setEnd(s + term.length());
				aux.add(m);
			}
		}
		
		return aux;
	}
}