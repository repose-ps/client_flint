package rs2.cache.cfg;

import rs2.cache.Archive;
import rs2.net.Buffer;

/**
 * Definition of a bit field packed into a parent {@link Varp} value.
 *
 * <p>
 * The lower bit is inclusive and the upper bit is exclusive, matching the mask
 * calculation used by the 377 client when reading and writing varbits.
 * </p>
 */
public class Varbit {

	/** Creates a new varbit with its default client state. */
	public Varbit() {
	}

	/** Number of definitions declared by the cache. */
	public static int count;

	/** Definitions indexed by varbit identifier. */
	public static Varbit[] definitions;

	/** Optional diagnostic name encoded by opcode 10. */
	public String debugName;

	/** Identifier of the varp containing this bit field. */
	public int varpId;

	/** Inclusive least-significant bit index. */
	public int leastSignificantBit;

	/** Exclusive most-significant bit index. */
	public int mostSignificantBit;

	/** Whether opcode 2 marks the parent varp as varbit-linked. */
	public boolean linkVarp;

	/** Reserved integer encoded by opcode 3. */
	public int opcode3Value = -1;

	/** Reserved integer encoded by opcode 4. */
	public int opcode4Value;

	/** Whether opcode5 enabled is enabled or active. */
	public boolean opcode5Enabled = true;

	/**
	 * Loads every varbit definition from {@code varbit.dat}.
	 * @param archive the source archive
	 */
	public static void load(Archive archive) {
		Buffer buffer = new Buffer(archive.read("varbit.dat"));
		count = buffer.readUnsignedShort();

		if (definitions == null) {
			definitions = new Varbit[count];
		}

		for (int id = 0; id < count; id++) {
			if (definitions[id] == null) {
				definitions[id] = new Varbit();
			}
			Varbit definition = definitions[id];
			definition.decode(buffer);
			if (definition.linkVarp) {
				Varp.definitions[definition.varpId].varbitLinked = true;
			}
		}

		if (buffer.position != buffer.payload.length) {
			System.out.println("varbit load mismatch");
		}
	}

	/**
	 * Decodes one opcode-delimited varbit definition.
	 * @param buffer the source buffer
	 */
	public void decode(Buffer buffer) {
		while (true) {
			int opcode = buffer.readUnsignedByte();
			switch (opcode) {
			case 0:
				return;
			case 1:
				varpId = buffer.readUnsignedShort();
				leastSignificantBit = buffer.readUnsignedByte();
				mostSignificantBit = buffer.readUnsignedByte();
				break;
			case 2:
				linkVarp = true;
				break;
			case 3:
				opcode3Value = buffer.readInt();
				break;
			case 4:
				opcode4Value = buffer.readInt();
				break;
			case 5:
				opcode5Enabled = false;
				break;
			case 10:
				debugName = buffer.readString();
				break;
			default:
				System.out.println("Error unrecognised config code: " + opcode);
				break;
			}
		}
	}
}
