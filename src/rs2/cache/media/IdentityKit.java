package rs2.cache.media;

import rs2.Class50_Sub1_Sub4_Sub4;
import rs2.cache.Archive;
import rs2.net.Buffer;

/**
 * Definition of one selectable player-appearance kit loaded from {@code idk.dat}.
 *
 * <p>An identity kit supplies one or more body models, up to five chat-head
 * models, and as many as six model recolouring pairs. Body-part identifiers
 * distinguish male and female appearance slots in the character-design UI.</p>
 */
public class IdentityKit {

    private static final int RECOLOR_COUNT = 6;
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
    public int[] headModelIds = {-1, -1, -1, -1, -1};

    /** Whether this kit must be omitted from player-customization choices. */
    public boolean nonSelectable;

    /** Loads all identity-kit definitions from {@code idk.dat}. */
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

    /** Decodes one opcode-delimited identity-kit definition. */
    public void decode(Buffer buffer) {
        while (true) {
            int opcode = buffer.readUnsignedByte();
            if (opcode == 0) {
                return;
            } else if (opcode == 1) {
                bodyPartId = buffer.readUnsignedByte();
            } else if (opcode == 2) {
                int modelCount = buffer.readUnsignedByte();
                bodyModelIds = new int[modelCount];
                for (int index = 0; index < modelCount; index++) {
                    bodyModelIds[index] = buffer.readUnsignedShort();
                }
            } else if (opcode == 3) {
                nonSelectable = true;
            } else if (opcode >= 40 && opcode < 50) {
                originalColors[opcode - 40] = buffer.readUnsignedShort();
            } else if (opcode >= 50 && opcode < 60) {
                replacementColors[opcode - 50] = buffer.readUnsignedShort();
            } else if (opcode >= 60 && opcode < 70) {
                headModelIds[opcode - 60] = buffer.readUnsignedShort();
            } else {
                System.out.println("Error unrecognised config code: " + opcode);
            }
        }
    }

    /** Returns whether every body model required by this kit is available. */
    public boolean areBodyModelsReady() {
        if (bodyModelIds == null) {
            return true;
        }

        boolean ready = true;
        for (int modelId : bodyModelIds) {
            if (!Class50_Sub1_Sub4_Sub4.method578(modelId)) {
                ready = false;
            }
        }
        return ready;
    }

    /** Builds and recolours the kit's combined full-body model. */
    public Class50_Sub1_Sub4_Sub4 buildBodyModel() {
        if (bodyModelIds == null) {
            return null;
        }

        Class50_Sub1_Sub4_Sub4[] models = new Class50_Sub1_Sub4_Sub4[bodyModelIds.length];
        for (int index = 0; index < bodyModelIds.length; index++) {
            models[index] = Class50_Sub1_Sub4_Sub4.method577(bodyModelIds[index]);
        }

        Class50_Sub1_Sub4_Sub4 model = models.length == 1
                ? models[0]
                : new Class50_Sub1_Sub4_Sub4(models.length, models, (byte) -89);
        recolor(model);
        return model;
    }

    /** Returns whether every chat-head model required by this kit is available. */
    public boolean areHeadModelsReady() {
        boolean ready = true;
        for (int modelId : headModelIds) {
            if (modelId != -1 && !Class50_Sub1_Sub4_Sub4.method578(modelId)) {
                ready = false;
            }
        }
        return ready;
    }

    /** Builds and recolours the kit's combined chat-head model. */
    public Class50_Sub1_Sub4_Sub4 buildHeadModel() {
        Class50_Sub1_Sub4_Sub4[] models = new Class50_Sub1_Sub4_Sub4[HEAD_MODEL_COUNT];
        int modelCount = 0;
        for (int modelId : headModelIds) {
            if (modelId != -1) {
                models[modelCount++] = Class50_Sub1_Sub4_Sub4.method577(modelId);
            }
        }

        Class50_Sub1_Sub4_Sub4 model = new Class50_Sub1_Sub4_Sub4(modelCount, models, (byte) -89);
        recolor(model);
        return model;
    }

    /** Applies the cache's consecutive recolouring pairs to a model. */
    private void recolor(Class50_Sub1_Sub4_Sub4 model) {
        for (int index = 0; index < RECOLOR_COUNT; index++) {
            if (originalColors[index] == 0) {
                return;
            }
            model.method591(originalColors[index], replacementColors[index]);
        }
    }
}