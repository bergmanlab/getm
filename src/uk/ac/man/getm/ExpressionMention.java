package uk.ac.man.getm;


import java.io.File;
import java.util.List;
import java.util.Map;

import uk.ac.man.entitytagger.Mention;

/**
 * Represents a mention of gene expression.
 * @author Martin
 *
 */
	public class ExpressionMention extends Mention{
	private static final long serialVersionUID = -3034733464952588498L;
	private Mention[] actors;
	private Mention trigger;
	private Mention[] locations;
	private Mention[] species;
	private String context = "test";

	/**
	 * @return the context
	 */
	public String getContext() {
		return context;
	}

	/**
	 * @param context the context to set
	 */
	public void setContext(String context) {
		this.context = context;
	}

	public ExpressionMention(Mention[] actors, Mention trigger, Mention[] locations, Mention[] species){
		super(trigger.getMostProbableID(), trigger.getStart(), trigger.getEnd(), trigger.getText());
		
		this.setDocid(trigger.getDocid());
		
		this.actors = actors;
		this.trigger = trigger;
		this.locations = locations;
		this.species = species;
	}
	
	public String toString(){
		//return (actors != null ? actors.toString() : null) + "\t" + (trigger != null ? trigger.toString()  : null)+ "\t" + (locations != null ? locations.toString()  : null)+ "\t" + (species != null ? species.toString() : null);

		StringBuffer toString = new StringBuffer(trigger.getDocid() != null ? trigger.getDocid() : "");

		String[] actorsStrs = new String[this.actors.length];
		String[] locationsStrs = new String[this.locations.length];
		
		String triggerStr = trigger.getIdsToString() + "%" + trigger.getStart() + "%" + trigger.getEnd() + "%" + trigger.getText(); 
		
		for (int i = 0; i < actors.length; i++)
			actorsStrs[i] = actors[i].getIdsToString() + "%" + actors[i].getStart() + "%" + actors[i].getEnd() + "%" + actors[i].getText();

		for (int i = 0; i < locations.length; i++)
			locationsStrs[i] = locations[i].getIdsToString() + "%" + locations[i].getStart() + "%" + locations[i].getEnd() + "%" + locations[i].getText();
		
		toString.append("\t" + triggerStr);
		toString.append("\t" + martin.common.Misc.implode(actorsStrs, "$"));
		toString.append("\t" + martin.common.Misc.implode(locationsStrs, "$"));
		
		return toString.toString();
	}

	/**
	 * @return the serialVersionUID
	 */
	public static long getSerialVersionUID() {
		return serialVersionUID;
	}

	/**
	 * @return the actor
	 */
	public Mention[] getActors() {
		return actors;
	}

	/**
	 * @return the trigger
	 */
	public Mention getTrigger() {
		return trigger;
	}

	/**
	 * @return the location
	 */
	public Mention[] getLocations() {
		return locations;
	}

	/**
	 * @return the species
	 */
	public Mention[] getSpecies() {
		return species;
	}
}
