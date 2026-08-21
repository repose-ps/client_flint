package rs2.media.renderable;

import rs2.Class16;

import rs2.Class50_Sub1_Sub4_Sub4;

/** A ground item whose model may depend on its stack amount. */
public class GroundItem extends Renderable {

	public int id;
	public int amount;

	public Class50_Sub1_Sub4_Sub4 getModel() {
		Class16 class16 = Class16.method212(id);
		return class16.method220(amount);
	}

}
