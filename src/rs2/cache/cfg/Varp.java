package rs2.cache.cfg;

import rs2.cache.Archive;
import rs2.net.Buffer;

/**
 * Definition of a client variable parameter (varp) loaded from
 * {@code varp.dat}.
 *
 * <p>
 * A varp describes metadata for one entry in the client's integer settings
 * array. Only opcode 5 is consumed directly by this client; names tied to other
 * opcodes deliberately describe the serialized attribute until its runtime
 * meaning can be established.
 * </p>
 */
public class Varp {

	/** Number of definitions declared by the cache. */
	public static int count;

	/** Definitions indexed by varp identifier. */
	public static Varp[] definitions;

	/** Number of identifiers collected by opcode 3. */
	public static int opcode3Count;

	/** Varp identifiers whose definitions contain opcode 3. */
	public static int[] opcode3Varps;

	/** Optional diagnostic name encoded by opcode 10. */
	public String debugName;

	public int opcode1Value;
	public int opcode2Value;
	public boolean opcode3Enabled;
	public boolean opcode4Enabled = true;

	/** Client behavior selector encoded by opcode 5. */
	public int clientCode;

	public boolean opcode6Enabled;
	public int opcode7Value;
	public int opcode8Or13Value;

	/** Set by opcodes 8, 11, and 13, and by linked varbit definitions. */
	public boolean varbitLinked;

	public int opcode12Value = -1;
	public boolean opcode14Enabled = true;

	/** Loads every varp definition from {@code varp.dat}. */
	public static void load(Archive archive) {
		Buffer buffer = new Buffer(archive.read("varp.dat"));
		opcode3Count = 0;
		count = buffer.readUnsignedShort();

		if (definitions == null) {
			definitions = new Varp[count];
		}
		if (opcode3Varps == null) {
			opcode3Varps = new int[count];
		}

		for (int id = 0; id < count; id++) {
			if (definitions[id] == null) {
				definitions[id] = new Varp();
			}
			definitions[id].decode(id, buffer);
		}

		if (buffer.position != buffer.payload.length) {
			System.out.println("varptype load mismatch");
		}
	}

	/** Decodes one opcode-delimited varp definition. */
	public void decode(int id, Buffer buffer) {
		while (true) {
			int opcode = buffer.readUnsignedByte();
			switch (opcode) {
			case 0:
				return;
			case 1:
				opcode1Value = buffer.readUnsignedByte();
				break;
			case 2:
				opcode2Value = buffer.readUnsignedByte();
				break;
			case 3:
				opcode3Enabled = true;
				opcode3Varps[opcode3Count++] = id;
				break;
			case 4:
				opcode4Enabled = false;
				break;
			case 5:
				clientCode = buffer.readUnsignedShort();
				break;
			case 6:
				opcode6Enabled = true;
				break;
			case 7:
				opcode7Value = buffer.readInt();
				break;
			case 8:
				opcode8Or13Value = 1;
				varbitLinked = true;
				break;
			case 10:
				debugName = buffer.readString();
				break;
			case 11:
				varbitLinked = true;
				break;
			case 12:
				opcode12Value = buffer.readInt();
				break;
			case 13:
				opcode8Or13Value = 2;
				varbitLinked = true;
				break;
			case 14:
				opcode14Enabled = false;
				break;
			default:
				System.out.println("Error unrecognised config code: " + opcode);
				break;
			}
		}
	}
}
