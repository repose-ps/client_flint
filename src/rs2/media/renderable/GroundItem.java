package rs2.media.renderable;

import rs2.cache.def.ItemDefinition;

/** A ground item whose model may depend on its stack amount. */
public class GroundItem extends Renderable {

	/** Creates a new ground item with its default client state. */
	public GroundItem() {
	}

	/** Stores the current ID. */
	public int id;

	/** Stores the current amount. */
	public int amount;

	/**
	 * Returns model.
	 *
	 * @return the resulting model
	 */
	public Model getModel() {
		ItemDefinition itemDefinition = ItemDefinition.lookup(id);
		return itemDefinition.getModel(amount);
	}

}
