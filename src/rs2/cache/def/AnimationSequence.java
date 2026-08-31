package rs2.cache.def;

import rs2.media.animation.AnimationFrame;

import rs2.cache.Archive;
import rs2.media.animation.AnimationFrame;
import rs2.net.Buffer;

/**
 * Cache definition describing the frames and playback policy of an animation.
 */
public class AnimationSequence {
	/* Cache-format opcode values. */
	/** Opcode for end. */
	private static final int OPCODE_END = 0;
	/** Opcode for frames. */
	private static final int OPCODE_FRAMES = 1;
	/** Opcode for frame step. */
	private static final int OPCODE_FRAME_STEP = 2;
	/** Opcode for interleave. */
	private static final int OPCODE_INTERLEAVE = 3;
	/** Opcode for stretches. */
	private static final int OPCODE_STRETCHES = 4;
	/** Opcode for forced priority. */
	private static final int OPCODE_FORCED_PRIORITY = 5;
	/** Opcode for shield override. */
	private static final int OPCODE_SHIELD_OVERRIDE = 6;
	/** Opcode for weapon override. */
	private static final int OPCODE_WEAPON_OVERRIDE = 7;
	/** Opcode for maximum loops. */
	private static final int OPCODE_MAXIMUM_LOOPS = 8;
	/** Opcode for precedence animating. */
	private static final int OPCODE_PRECEDENCE_ANIMATING = 9;
	/** Opcode for priority. */
	private static final int OPCODE_PRIORITY = 10;
	/** Opcode for replay mode. */
	private static final int OPCODE_REPLAY_MODE = 11;
	/** Opcode for unknown 12. */
	private static final int OPCODE_UNKNOWN_12 = 12;


	/** Creates a new animation sequence with its default client state. */
	public AnimationSequence() {
	}

	/** Constant value for interleave terminator. */
	private static final int INTERLEAVE_TERMINATOR = 0x98967f;

	/** Number of sequence definitions declared by {@code seq.dat}. */
	public static int count;

	/** Sequence definitions indexed by animation identifier. */
	public static AnimationSequence[] sequences;

	/** Stores the current frame count. */
	public int frameCount;
	/** Stores primary frame IDs values. */
	public int[] primaryFrameIds;
	/** Stores secondary frame IDs values. */
	public int[] secondaryFrameIds;
	/** Stores frame lengths values. */
	public int[] frameLengths;
	/** Stores the current frame step. */
	public int frameStep = -1;
	/** Stores interleave order values. */
	public int[] interleaveOrder;
	/** Whether stretches is enabled or active. */
	public boolean stretches;
	/** Stores the current forced priority. */
	public int forcedPriority = 5;
	/** Stores the current shield override. */
	public int shieldOverride = -1;
	/** Stores the current weapon override. */
	public int weaponOverride = -1;
	/** Stores the current maximum loops. */
	public int maximumLoops = 99;
	/** Stores the current precedence animating. */
	public int precedenceAnimating = -1;
	/** Stores the current priority. */
	public int priority = -1;
	/** Stores the current replay mode. */
	public int replayMode = 2;

	/** Reserved integer carried by opcode 12 in revision 377. */
	public int opcode12Value;

	/**
	 * Loads all animation sequence definitions from {@code seq.dat}.
	 * @param archive the source archive
	 */
	public static void load(Archive archive) {
		Buffer buffer = new Buffer(archive.read("seq.dat"));
		count = buffer.readUnsignedShort();

		if (sequences == null) {
			sequences = new AnimationSequence[count];
		}

		for (int id = 0; id < count; id++) {
			if (sequences[id] == null) {
				sequences[id] = new AnimationSequence();
			}
			sequences[id].decode(buffer);
		}
	}

	/**
	 * Returns a frame's duration, resolving a zero duration from frame metadata. A
	 * duration of one cycle is used when no metadata is available.
	 * @param frame the animation frame
	 * @return the frame length
	 */
	public int getFrameLength(int frame) {
		int length = frameLengths[frame];
		if (length == 0) {
			AnimationFrame animationFrame = AnimationFrame.get(primaryFrameIds[frame]);
			if (animationFrame != null) {
				length = frameLengths[frame] = animationFrame.duration;
			}
		}
		return length == 0 ? 1 : length;
	}

	/**
	 * Decodes one opcode-delimited sequence definition.
	 * @param buffer the source buffer
	 */
	public void decode(Buffer buffer) {
		while (true) {
			int opcode = buffer.readUnsignedByte();
			switch (opcode) {
			case OPCODE_END:
				applyDefaults();
				return;
			case OPCODE_FRAMES:
				decodeFrames(buffer);
				break;
			case OPCODE_FRAME_STEP:
				frameStep = buffer.readUnsignedShort();
				break;
			case OPCODE_INTERLEAVE:
				int count = buffer.readUnsignedByte();
				interleaveOrder = new int[count + 1];
				for (int index = 0; index < count; index++) {
					interleaveOrder[index] = buffer.readUnsignedByte();
				}
				interleaveOrder[count] = INTERLEAVE_TERMINATOR;
				break;
			case OPCODE_STRETCHES:
				stretches = true;
				break;
			case OPCODE_FORCED_PRIORITY:
				forcedPriority = buffer.readUnsignedByte();
				break;
			case OPCODE_SHIELD_OVERRIDE:
				shieldOverride = buffer.readUnsignedShort();
				break;
			case OPCODE_WEAPON_OVERRIDE:
				weaponOverride = buffer.readUnsignedShort();
				break;
			case OPCODE_MAXIMUM_LOOPS:
				maximumLoops = buffer.readUnsignedByte();
				break;
			case OPCODE_PRECEDENCE_ANIMATING:
				precedenceAnimating = buffer.readUnsignedByte();
				break;
			case OPCODE_PRIORITY:
				priority = buffer.readUnsignedByte();
				break;
			case OPCODE_REPLAY_MODE:
				replayMode = buffer.readUnsignedByte();
				break;
			case OPCODE_UNKNOWN_12:
				opcode12Value = buffer.readInt();
				break;
			default:
				System.out.println("Error unrecognised seq config code: " + opcode);
				break;
			}
		}
	}

	/**
	 * Decodes frames.
	 *
	 * @param buffer the source buffer
	 */
	private void decodeFrames(Buffer buffer) {
		frameCount = buffer.readUnsignedByte();
		primaryFrameIds = new int[frameCount];
		secondaryFrameIds = new int[frameCount];
		frameLengths = new int[frameCount];

		for (int frame = 0; frame < frameCount; frame++) {
			primaryFrameIds[frame] = buffer.readUnsignedShort();
			secondaryFrameIds[frame] = buffer.readUnsignedShort();
			if (secondaryFrameIds[frame] == DefinitionConstants.NULL_REFERENCE_ID) {
				secondaryFrameIds[frame] = -1;
			}
			frameLengths[frame] = buffer.readUnsignedShort();
		}
	}

	/** Applies the format's derived defaults after all opcodes are decoded. */
	private void applyDefaults() {
		if (frameCount == 0) {
			frameCount = 1;
			primaryFrameIds = new int[] { -1 };
			secondaryFrameIds = new int[] { -1 };
			frameLengths = new int[] { -1 };
		}

		if (precedenceAnimating == -1) {
			precedenceAnimating = interleaveOrder == null ? 0 : 2;
		}
		if (priority == -1) {
			priority = interleaveOrder == null ? 0 : 2;
		}
	}
}
