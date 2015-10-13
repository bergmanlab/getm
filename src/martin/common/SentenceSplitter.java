package martin.common;

import java.text.BreakIterator;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class SentenceSplitter implements Iterator<Pair<Integer>>, Iterable<Pair<Integer>>{
	private BreakIterator bi;
	private Integer nextStart;
	private Integer nextEnd;

	public SentenceSplitter(String text){
		this.bi = BreakIterator.getSentenceInstance();
		bi.setText(text);
		this.nextStart = 0;
		this.nextEnd = bi.next();
	}

	public boolean hasNext() {
		return (nextEnd != BreakIterator.DONE);
	}

	public Pair<Integer> next() {
		if (!hasNext())
			throw new NoSuchElementException();
		
		Pair<Integer> aux = new Pair<Integer>(nextStart,nextEnd);

		nextStart = nextEnd;
		nextEnd = bi.next();
		
		return aux;
	}

	public void remove() {
		throw new IllegalStateException();
	}

	public Iterator<Pair<Integer>> iterator() {
		return this;
	}
}
