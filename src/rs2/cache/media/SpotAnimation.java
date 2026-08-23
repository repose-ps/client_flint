package rs2.cache.media;

import rs2.cache.Archive;
import rs2.collection.LruCache;
import rs2.media.renderable.Model;
import rs2.net.Buffer;

/**
 * Definition of a temporary graphical effect, such as a spell impact.
 *
 * <p>
 * The definition combines a base model with an optional animation, recolouring
 * rules, scale, rotation, and lighting adjustments.
 * </p>
 */
public class SpotAnimation {

	private static final int RECOLOR_COUNT = 6;

	public static int count;
	public static SpotAnimation[] definitions;

	public int id;
	public int modelId;
	public int animationId = -1;
	public AnimationSequence sequence;
	public int[] originalColors = new int[RECOLOR_COUNT];
	public int[] replacementColors = new int[RECOLOR_COUNT];
	public int resizeXY = 128;
	public int resizeZ = 128;
	public int rotation;
	public int ambient;
	public int contrast;

	/** Shared cache of unanimated, recoloured base models. */
	public static LruCache modelCache = new LruCache(30);

	/** Loads all spot-animation definitions from {@code spotanim.dat}. */
	public static void load(Archive archive) {
		Buffer buffer = new Buffer(archive.read("spotanim.dat"));
		count = buffer.readUnsignedShort();

		if (definitions == null) {
			definitions = new SpotAnimation[count];
		}

		for (int id = 0; id < count; id++) {
			if (definitions[id] == null) {
				definitions[id] = new SpotAnimation();
			}
			definitions[id].id = id;
			definitions[id].decode(buffer);
		}
	}

	/** Decodes one opcode-delimited spot-animation definition. */
	public void decode(Buffer buffer) {
		while (true) {
			int opcode = buffer.readUnsignedByte();
			if (opcode == 0) {
				return;
			} else if (opcode == 1) {
				modelId = buffer.readUnsignedShort();
			} else if (opcode == 2) {
				animationId = buffer.readUnsignedShort();
				if (AnimationSequence.sequences != null) {
					sequence = AnimationSequence.sequences[animationId];
				}
			} else if (opcode == 4) {
				resizeXY = buffer.readUnsignedShort();
			} else if (opcode == 5) {
				resizeZ = buffer.readUnsignedShort();
			} else if (opcode == 6) {
				rotation = buffer.readUnsignedShort();
			} else if (opcode == 7) {
				ambient = buffer.readUnsignedByte();
			} else if (opcode == 8) {
				contrast = buffer.readUnsignedByte();
			} else if (opcode >= 40 && opcode < 50) {
				originalColors[opcode - 40] = buffer.readUnsignedShort();
			} else if (opcode >= 50 && opcode < 60) {
				replacementColors[opcode - 50] = buffer.readUnsignedShort();
			} else {
				System.out.println("Error unrecognised spotanim config code: " + opcode);
			}
		}
	}

	/**
	 * Returns the cached base model, loading and recolouring it when necessary.
	 */
	public Model getModel() {
		Model model = (Model) modelCache.get(id);
		if (model != null) {
			return model;
		}

		model = Model.getModel(modelId);
		if (model == null) {
			return null;
		}

		// Revision 377 treats a zero first source colour as an empty recolour table.
		if (originalColors[0] != 0) {
			for (int index = 0; index < RECOLOR_COUNT; index++) {
				model.recolor(originalColors[index], replacementColors[index]);
			}
		}

		modelCache.put(id, model);
		return model;
	}
}
