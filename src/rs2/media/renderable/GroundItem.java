package rs2.media.renderable;

import rs2.cache.def.ItemDefinition;

/** A ground item whose model may depend on its stack amount. */
public /**
		 * Initializes this instance.
		 */
class GroundItem extends Renderable {

	/**
	 * Stores id.
	 */
	public int id;
	/**
	 * Stores amount.
	 */
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
