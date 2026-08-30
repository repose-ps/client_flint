package rs2.cache.def;

import rs2.cache.Archive;
import rs2.media.model.Model;
import rs2.net.Buffer;

/**
 * Definition of one selectable player-appearance kit loaded from
 * {@code idk.dat}.
 *
 * <p>
 * An identity kit supplies one or more body models, up to five chat-head
 * models, and as many as six model recolouring pairs. Body-part identifiers
 * distinguish male and female appearance slots in the character-design UI.
 * </p>
 */
public class IdentityKit {
	/* Cache-format opcode values. */
	/** Opcode for end. */
	private static final int OPCODE_END = 0;
	/** Opcode for body part. */
	private static final int OPCODE_BODY_PART = 1;
	/** Opcode for body models. */
	private static final int OPCODE_BODY_MODELS = 2;
	/** Opcode for non selectable. */
	private static final int OPCODE_NON_SELECTABLE = 3;
	/** First opcode in the recolor source range. */
	private static final int RECOLOR_SOURCE_FIRST = 40;
	/** Exclusive upper bound of the recolor source opcode range. */
	private static final int RECOLOR_SOURCE_LIMIT = 50;
	/** First opcode in the recolor target range. */
	private static final int RECOLOR_TARGET_FIRST = 50;
	/** Exclusive upper bound of the recolor target opcode range. */
	private static final int RECOLOR_TARGET_LIMIT = 60;
	/** First opcode in the head model range. */
	private static final int HEAD_MODEL_FIRST = 60;
	/** Exclusive upper bound of the head model opcode range. */
	private static final int HEAD_MODEL_LIMIT = 70;


	/** Creates a new identity kit with its default client state. */
	public IdentityKit() {
	}

	/** Constant value for recolor count. */
	private static final int RECOLOR_COUNT = 6;
	/** Constant value for head model count. */
	private static final int HEAD_MODEL_COUNT = 5;

	/** Number of identity-kit definitions declared by the cache. */
	public static int count;

	/** Definitions indexed by identity-kit identifier. */
	public static IdentityKit[] definitions;

	/** Body-part category used by the character-design interface. */
	public int bodyPartId = -1;

	/** Model identifiers used to assemble the full-body appearance. */
	public int[] bodyModelIds;

	/** Source colors for the kit's recolouring operations. */
	public int[] originalColors = new int[RECOLOR_COUNT];

	/** Replacement colors paired with {@link #originalColors}. */
	public int[] replacementColors = new int[RECOLOR_COUNT];

	/** Model identifiers used to assemble the player's chat head. */
	public int[] headModelIds = { -1, -1, -1, -1, -1 };

	/** Whether this kit must be omitted from player-customization choices. */
	public boolean nonSelectable;

	/**
	 * Loads all identity-kit definitions from {@code idk.dat}.
	 * @param archive the source archive
	 */
	public static void load(Archive archive) {
		Buffer buffer = new Buffer(archive.read("idk.dat"));
		count = buffer.readUnsignedShort();

		if (definitions == null) {
			definitions = new IdentityKit[count];
		}

		for (int id = 0; id < count; id++) {
			if (definitions[id] == null) {
				definitions[id] = new IdentityKit();
			}
			definitions[id].decode(buffer);
		}
	}

	/**
	 * Decodes one opcode-delimited identity-kit definition.
	 * @param buffer the source buffer
	 */
	public void decode(Buffer buffer) {
		while (true) {
			int opcode = buffer.readUnsignedByte();
			if (opcode == OPCODE_END) {
				return;
			} else if (opcode == OPCODE_BODY_PART) {
				bodyPartId = buffer.readUnsignedByte();
			} else if (opcode == OPCODE_BODY_MODELS) {
				int modelCount = buffer.readUnsignedByte();
				bodyModelIds = new int[modelCount];
				for (int index = 0; index < modelCount; index++) {
					bodyModelIds[index] = buffer.readUnsignedShort();
				}
			} else if (opcode == OPCODE_NON_SELECTABLE) {
				nonSelectable = true;
			} else if (opcode >= RECOLOR_SOURCE_FIRST && opcode < RECOLOR_SOURCE_LIMIT) {
				originalColors[opcode - RECOLOR_SOURCE_FIRST] = buffer.readUnsignedShort();
			} else if (opcode >= RECOLOR_TARGET_FIRST && opcode < RECOLOR_TARGET_LIMIT) {
				replacementColors[opcode - RECOLOR_TARGET_FIRST] = buffer.readUnsignedShort();
			} else if (opcode >= HEAD_MODEL_FIRST && opcode < HEAD_MODEL_LIMIT) {
				headModelIds[opcode - HEAD_MODEL_FIRST] = buffer.readUnsignedShort();
			} else {
				System.out.println("Error unrecognised config code: " + opcode);
			}
		}
	}

	/**
	 * Returns whether every body model required by this kit is available.
	 * @return whether are body models ready
	 */
	public boolean areBodyModelsReady() {
		if (bodyModelIds == null) {
			return true;
		}

		boolean ready = true;
		for (int modelId : bodyModelIds) {
			if (!Model.isLoaded(modelId)) {
				ready = false;
			}
		}
		return ready;
	}

	/**
	 * Builds and recolours the kit's combined full-body model.
	 * @return the constructed body model
	 */
	public Model buildBodyModel() {
		if (bodyModelIds == null) {
			return null;
		}

		Model[] models = new Model[bodyModelIds.length];
		for (int index = 0; index < bodyModelIds.length; index++) {
			models[index] = Model.getModel(bodyModelIds[index]);
		}

		Model model = models.length == 1 ? models[0] : new Model(models.length, models);
		recolor(model);
		return model;
	}

	/**
	 * Returns whether every chat-head model required by this kit is available.
	 * @return whether are head models ready
	 */
	public boolean areHeadModelsReady() {
		boolean ready = true;
		for (int modelId : headModelIds) {
			if (modelId != -1 && !Model.isLoaded(modelId)) {
				ready = false;
			}
		}
		return ready;
	}

	/**
	 * Builds and recolours the kit's combined chat-head model.
	 * @return the constructed head model
	 */
	public Model buildHeadModel() {
		Model[] models = new Model[HEAD_MODEL_COUNT];
		int modelCount = 0;
		for (int modelId : headModelIds) {
			if (modelId != -1) {
				models[modelCount++] = Model.getModel(modelId);
			}
		}

		Model model = new Model(modelCount, models);
		recolor(model);
		return model;
	}

	/**
	 * Applies the cache's consecutive recolouring pairs to a model.
	 * @param model the model
	 */
	private void recolor(Model model) {
		for (int index = 0; index < RECOLOR_COUNT; index++) {
			if (originalColors[index] == 0) {
				return;
			}
			model.recolor(originalColors[index], replacementColors[index]);
		}
	}
}
