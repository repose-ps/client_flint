package rs2.media.renderable;

import rs2.cache.def.ItemDefinition;

/** A ground item whose model may depend on its stack amount. */
public class GroundItem extends Renderable {

	public int id;
	public int amount;

	public Model getModel() {
		ItemDefinition class16 = ItemDefinition.lookup(id);
		return class16.getModel(amount);
	}

}
