package rs2.cache.media;

import rs2.cache.Archive;
import rs2.media.AnimationFrame;
import rs2.net.Buffer;

/**
 * Cache definition describing the frames and playback policy of an animation.
 */
public class AnimationSequence {

	private static final int INTERLEAVE_TERMINATOR = 0x98967f;

	/** Number of sequence definitions declared by {@code seq.dat}. */
	public static int count;

	/** Sequence definitions indexed by animation identifier. */
	public static AnimationSequence[] sequences;

	public int frameCount;
	public int[] primaryFrameIds;
	public int[] secondaryFrameIds;
	public int[] frameLengths;
	public int frameStep = -1;
	public int[] interleaveOrder;
	public boolean stretches;
	public int forcedPriority = 5;
	public int shieldOverride = -1;
	public int weaponOverride = -1;
	public int maximumLoops = 99;
	public int precedenceAnimating = -1;
	public int priority = -1;
	public int replayMode = 2;

	/** Reserved integer carried by opcode 12 in revision 377. */
	public int opcode12Value;

	/** Loads all animation sequence definitions from {@code seq.dat}. */
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

	/** Decodes one opcode-delimited sequence definition. */
	public void decode(Buffer buffer) {
		while (true) {
			int opcode = buffer.readUnsignedByte();
			switch (opcode) {
			case 0:
				applyDefaults();
				return;
			case 1:
				decodeFrames(buffer);
				break;
			case 2:
				frameStep = buffer.readUnsignedShort();
				break;
			case 3:
				int count = buffer.readUnsignedByte();
				interleaveOrder = new int[count + 1];
				for (int index = 0; index < count; index++) {
					interleaveOrder[index] = buffer.readUnsignedByte();
				}
				interleaveOrder[count] = INTERLEAVE_TERMINATOR;
				break;
			case 4:
				stretches = true;
				break;
			case 5:
				forcedPriority = buffer.readUnsignedByte();
				break;
			case 6:
				shieldOverride = buffer.readUnsignedShort();
				break;
			case 7:
				weaponOverride = buffer.readUnsignedShort();
				break;
			case 8:
				maximumLoops = buffer.readUnsignedByte();
				break;
			case 9:
				precedenceAnimating = buffer.readUnsignedByte();
				break;
			case 10:
				priority = buffer.readUnsignedByte();
				break;
			case 11:
				replayMode = buffer.readUnsignedByte();
				break;
			case 12:
				opcode12Value = buffer.readInt();
				break;
			default:
				System.out.println("Error unrecognised seq config code: " + opcode);
				break;
			}
		}
	}

	private void decodeFrames(Buffer buffer) {
		frameCount = buffer.readUnsignedByte();
		primaryFrameIds = new int[frameCount];
		secondaryFrameIds = new int[frameCount];
		frameLengths = new int[frameCount];

		for (int frame = 0; frame < frameCount; frame++) {
			primaryFrameIds[frame] = buffer.readUnsignedShort();
			secondaryFrameIds[frame] = buffer.readUnsignedShort();
			if (secondaryFrameIds[frame] == 65535) {
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