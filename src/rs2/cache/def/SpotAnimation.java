package rs2.cache.def;

import rs2.cache.Archive;
import rs2.collection.LruCache;
import rs2.media.model.Model;
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
	/* Cache-format opcode values. */
	/** Opcode for end. */
	private static final int OPCODE_END = 0;
	/** Opcode for model. */
	private static final int OPCODE_MODEL = 1;
	/** Opcode for animation. */
	private static final int OPCODE_ANIMATION = 2;
	/** Opcode for resize xy. */
	private static final int OPCODE_RESIZE_XY = 4;
	/** Opcode for resize z. */
	private static final int OPCODE_RESIZE_Z = 5;
	/** Opcode for rotation. */
	private static final int OPCODE_ROTATION = 6;
	/** Opcode for ambient. */
	private static final int OPCODE_AMBIENT = 7;
	/** Opcode for contrast. */
	private static final int OPCODE_CONTRAST = 8;
	/** First opcode in the recolor source range. */
	private static final int RECOLOR_SOURCE_FIRST = 40;
	/** Exclusive upper bound of the recolor source opcode range. */
	private static final int RECOLOR_SOURCE_LIMIT = 50;
	/** First opcode in the recolor target range. */
	private static final int RECOLOR_TARGET_FIRST = 50;
	/** Exclusive upper bound of the recolor target opcode range. */
	private static final int RECOLOR_TARGET_LIMIT = 60;

	/** Creates a new spot animation with its default client state. */
	public SpotAnimation() {
	}

	/** Constant value for recolor count. */
	private static final int RECOLOR_COUNT = 6;

	/** Stores the current count. */
	public static int count;
	/** Stores definitions values. */
	public static SpotAnimation[] definitions;

	/** Stores the current ID. */
	public int id;
	/** Stores the current model ID. */
	public int modelId;
	/** Stores the current animation ID. */
	public int animationId = -1;
	/** Stores the current sequence. */
	public AnimationSequence sequence;
	/** Stores original colors values. */
	public int[] originalColors = new int[RECOLOR_COUNT];
	/** Stores replacement colors values. */
	public int[] replacementColors = new int[RECOLOR_COUNT];
	/** Stores the current resize xy. */
	public int resizeXY = 128;
	/** Stores the current resize Z. */
	public int resizeZ = 128;
	/** Stores the current rotation. */
	public int rotation;
	/** Stores the current ambient. */
	public int ambient;
	/** Stores the current contrast. */
	public int contrast;

	/** Shared cache of unanimated, recoloured base models. */
	public static LruCache modelCache = new LruCache(30);

	/**
	 * Loads all spot-animation definitions from {@code spotanim.dat}.
	 * 
	 * @param archive the source archive
	 */
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

	/**
	 * Decodes one opcode-delimited spot-animation definition.
	 * 
	 * @param buffer the source buffer
	 */
	public void decode(Buffer buffer) {
		while (true) {
			int opcode = buffer.readUnsignedByte();
			if (opcode == OPCODE_END) {
				return;
			} else if (opcode == OPCODE_MODEL) {
				modelId = buffer.readUnsignedShort();
			} else if (opcode == OPCODE_ANIMATION) {
				animationId = buffer.readUnsignedShort();
				if (AnimationSequence.sequences != null) {
					sequence = AnimationSequence.sequences[animationId];
				}
			} else if (opcode == OPCODE_RESIZE_XY) {
				resizeXY = buffer.readUnsignedShort();
			} else if (opcode == OPCODE_RESIZE_Z) {
				resizeZ = buffer.readUnsignedShort();
			} else if (opcode == OPCODE_ROTATION) {
				rotation = buffer.readUnsignedShort();
			} else if (opcode == OPCODE_AMBIENT) {
				ambient = buffer.readUnsignedByte();
			} else if (opcode == OPCODE_CONTRAST) {
				contrast = buffer.readUnsignedByte();
			} else if (opcode >= RECOLOR_SOURCE_FIRST && opcode < RECOLOR_SOURCE_LIMIT) {
				originalColors[opcode - RECOLOR_SOURCE_FIRST] = buffer.readUnsignedShort();
			} else if (opcode >= RECOLOR_TARGET_FIRST && opcode < RECOLOR_TARGET_LIMIT) {
				replacementColors[opcode - RECOLOR_TARGET_FIRST] = buffer.readUnsignedShort();
			} else {
				System.out.println("Error unrecognised spotanim config code: " + opcode);
			}
		}
	}

	/**
	 * Returns the cached base model, loading and recolouring it when necessary.
	 * 
	 * @return the model
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
