package uk.ac.man.documentparser.input;

import java.io.File;

public class PMCFactory implements InputFactory {
	private String[] dtdLocations;
	
	public PMCFactory(String[] dtdLocations){
		this.dtdLocations = dtdLocations;
	}
	
	public DocumentIterator parse(String file) {
		return new PMC(new File(file),dtdLocations);
	}

	public DocumentIterator parse(File file) {
		return new PMC(file,dtdLocations);
	}

	public DocumentIterator parse(StringBuffer data) {
		return new PMC(data,dtdLocations);
	}
}
