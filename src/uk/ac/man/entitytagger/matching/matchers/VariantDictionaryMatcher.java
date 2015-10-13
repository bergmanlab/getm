package uk.ac.man.entitytagger.matching.matchers;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.matching.Matcher;

import martin.common.CacheMap;
import martin.common.Function;
import martin.common.Misc;
import martin.common.Pair;
import martin.common.Sizeable;
import martin.common.StreamIterator;

public class VariantDictionaryMatcher extends Matcher implements Sizeable {
	private String[][] termToIdsMap;
	private String[] terms;
	private boolean ignoreCase;
	private Pattern splitPattern;

	private String[] tableNames;
	private String tag;
	private long size=-1;
	private Connection conn;

	public VariantDictionaryMatcher(String[][] termToIdsMap, String[] terms, boolean ignoreCase) {

		this.termToIdsMap = termToIdsMap;
		this.terms = terms;
		this.ignoreCase = ignoreCase;
		this.splitPattern = Pattern.compile("\\b");
	}

	public int size(){
		return terms.length;
	}
	
	public VariantDictionaryMatcher(Connection conn, String[] tableNames, String tag, boolean ignoreCase) {

		this.termToIdsMap = null;
		this.terms = null;

		this.ignoreCase = ignoreCase;
		this.splitPattern = Pattern.compile("\\b");

		this.conn = conn;
		this.tableNames =  tableNames;
		this.tag = tag;
	}

	public static VariantDictionaryMatcher load(File inFile, boolean ignoreCase){
		Map<String,Set<String>> termToIdsMap = loadFile(inFile, ignoreCase);

		String[] terms = new String[termToIdsMap.size()];
		int i = 0;

		for (String term : termToIdsMap.keySet()){
			terms[i++] = term;
		}

		Arrays.sort(terms);

		String[][] termToIdsMapArray = new String[terms.length][];

		for (int j = 0; j < terms.length; j++)
			termToIdsMapArray[j] = termToIdsMap.get(terms[j]).toArray(new String[0]);

		return new VariantDictionaryMatcher(termToIdsMapArray, terms, ignoreCase);
	}

	private void init(){
		Map<String,Set<String>> termToIdsMap = loadFromDB();

		String[] terms = new String[termToIdsMap.size()];
		int i = 0;

		for (String term : termToIdsMap.keySet()){
			terms[i++] = term;
		}

		Arrays.sort(terms);

		String[][] termToIdsMapArray = new String[terms.length][];

		for (int j = 0; j < terms.length; j++)
			termToIdsMapArray[j] = termToIdsMap.get(terms[j]).toArray(new String[0]);

		this.termToIdsMap = termToIdsMapArray;
		this.terms = terms;	
	}
	
	public static Map<String,Matcher> loadSeparatedFromDB(Connection conn, String[] tableNames, boolean ignoreCase){
		try{
			Statement stmt = conn.createStatement();
			Map<String,Matcher> res = new HashMap<String, Matcher>();

			for (String tableName : tableNames){
				ResultSet rs = stmt.executeQuery("SELECT DISTINCT(tag) FROM " + tableName);

				while (rs.next()){
					String tag = rs.getString(1);
					if (!res.containsKey(tag))
						res.put(tag, new VariantDictionaryMatcher(conn, tableNames, tag, ignoreCase));
				}
			}

			conn.close();

			return res;
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return null;
	}
	
	public static CacheMap<String,VariantDictionaryMatcher> loadSeparatedFromDBCached(final Connection conn, final String[] tableNames, final boolean ignoreCase, final long maxSize, final Logger logger){
		Function<VariantDictionaryMatcher> factory = new Function<VariantDictionaryMatcher>(){
			public VariantDictionaryMatcher function(Object[] args){
				String key = (String) args[0];
				
				return new VariantDictionaryMatcher(conn, tableNames, key, ignoreCase);
			}
		};

		return new CacheMap<String,VariantDictionaryMatcher>(maxSize, factory, logger);
	}

	public static Map<String,Matcher> loadSeparated(File[] inFiles, boolean ignoreCase){
		Map<String,Matcher> res = new HashMap<String, Matcher>();

		for (File inFile : inFiles){
			Map<String,Map<String,Set<String>>> termToIdsMapSeparated = loadFileSeparated(inFile, ignoreCase);

			for (String k : termToIdsMapSeparated.keySet()){
				Map<String,Set<String>> termToIdsMap = termToIdsMapSeparated.get(k);
				String[] terms = new String[termToIdsMap.size()];
				int i = 0;

				for (String term : termToIdsMap.keySet()){
					terms[i++] = term;
				}

				Arrays.sort(terms);

				String[][] termToIdsMapArray = new String[terms.length][];

				for (int j = 0; j < terms.length; j++)
					termToIdsMapArray[j] = termToIdsMap.get(terms[j]).toArray(new String[0]);

				res.put(k, new VariantDictionaryMatcher(termToIdsMapArray, terms, ignoreCase));
			}
		}

		return res;
	}

	private static Map<String, Map<String, Set<String>>> loadFileSeparated(
			File inFile, boolean ignoreCase) {

		Map<String,Map<String,Set<String>>> res = new HashMap<String, Map<String,Set<String>>>();

		StreamIterator fileData = new StreamIterator(inFile, true);
		for (String s  : fileData){
			String[] fields = s.split("\t");

			if (fields.length < 3)
				throw new IllegalStateException("The input file need three columns when calling loadFileSeparated");

			if (ignoreCase)
				fields[1] = fields[1].toLowerCase();

			if (!res.containsKey(fields[2]))
				res.put(fields[2], new HashMap<String,Set<String>>());

			Map<String,Set<String>> map = res.get(fields[2]);

			String[] names  = fields[1].split("\\|");

			for (String n : names){
				if (!res.containsKey(n))
					map.put(n, new HashSet<String>());

				map.get(n).add(fields[0]);
			}
		}

		return res;
	}

	private static Map<String, Set<String>> loadFile(File inFile,
			boolean ignoreCase) {

		Map<String,Set<String>> res = new HashMap<String,Set<String>>();

		StreamIterator fileData = new StreamIterator(inFile, true);
		int c = 0;
		
		Pattern tabPattern = Pattern.compile("\t");
		Pattern pipePattern = Pattern.compile("\\|");
		
		for (String s  : fileData){
			//System.out.println(c++);
			
			String[] fields = tabPattern.split(s);

			if (ignoreCase)
				fields[1] = fields[1].toLowerCase();

			String[] names  = pipePattern.split(fields[1]);
			for (String n : names){
				if (!res.containsKey(n))
					res.put(n, new HashSet<String>());

				res.get(n).add(fields[0]);
			}
		}

		return res;
	}

	private Map<String,Set<String>> loadFromDB(){
		try{
			if (tag != null)
				System.out.println("Loading variantMatcher from " + Misc.implode(this.tableNames,", ") + " (" + tag + ")... ");
			else
				System.out.println("Loading variantMatcher from " + Misc.implode(this.tableNames,", ") + "... ");

			Map<String,Set<String>> res = new HashMap<String,Set<String>>();

			for (String tableName : tableNames){
				ResultSet rs;
				if (tag != null)
					rs = conn.createStatement().executeQuery("SELECT id_entity, name FROM " + tableName + " WHERE tag = '" + tag + "'");
				else
					rs = conn.createStatement().executeQuery("SELECT id_entity, name FROM " + tableName);

				while (rs.next()){
					String id = rs.getString(1);
					String name = rs.getString(2);

					if (ignoreCase)
						name = name.toLowerCase();

					if (!res.containsKey(name)){
						res.put(name, new HashSet<String>());
					}

					res.get(name).add(id);
				}
			}

			return res;
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return null;
	}

	@Override
	public List<Mention> match(String text, Document doc) {
		if (terms == null || termToIdsMap == null)
			init();

		List<Mention> matches = new ArrayList<Mention>();

		String matchText = this.ignoreCase ? text.toLowerCase() : text;

		String docid = doc != null ? doc.getID() : null;
		java.util.regex.Matcher splitter = this.splitPattern.matcher(matchText);

		List<Pair<Integer>> tokenLocations = new ArrayList<Pair<Integer>>();

		int prev = -1;
		while (splitter.find()){
			if (prev != -1 && Character.isLetterOrDigit(matchText.charAt(prev))){
				tokenLocations.add(new Pair<Integer>(prev, splitter.start()));
			}

			prev = splitter.start();
		}

		for (int i = 0; i < tokenLocations.size(); i++){
			Pair<Integer> p = tokenLocations.get(i);
			List<Integer> foundMatches = getMatchIds(tokenLocations, i, matchText);

			for (int fm : foundMatches){
				String term = ignoreCase ? matchText.substring(p.getX(), p.getX() + terms[fm].length()) : terms[fm];
				Mention m = new Mention(termToIdsMap[fm].clone(), p.getX(), p.getX() + term.length(), term);
				m.setDocid(docid);
				matches.add(m);
			}
		}

		return matches;
	}

	private List<Integer> getMatchIds(List<Pair<Integer>> tokenLocations, int i, String matchText) {
		Pair<Integer> p = tokenLocations.get(i);
		List<Integer> res = new LinkedList<Integer>();

		int add = 0;

		do {
			String term = matchText.substring(p.getX(), tokenLocations.get(i+add).getY());

			int s = Arrays.binarySearch(terms, term);

			if (s >= 0){
				res.add(s);
			} else if (-s-1 < terms.length){
				if (!terms[-s-1].startsWith(term))
					break;
			} else {
				break;
			}

			add++;

		} while (i + add < tokenLocations.size());

		return res;
	}

	public long sizeof() {
		if (terms == null || termToIdsMap == null)
			init();
		
		if (this.size != -1)
			return this.size;

		long size = 0;
		
		for (String t : terms)
			size += t.length();
		
		for (int i = 0; i < termToIdsMap.length; i++)
			for (int j = 0; j < termToIdsMap[i].length; j++)
				size += termToIdsMap[i][j].length();
		
		this.size = size;
		return size;				
	}
}