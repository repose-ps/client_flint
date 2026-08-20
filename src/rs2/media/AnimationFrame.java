package rs2.media;

import java.util.Arrays;

import rs2.net.Buffer;

/**
 * One decoded skeletal-animation frame.
 *
 * <p>
 * Each transform references an entry in a shared {@link Skeleton} and supplies
 * X, Y, and Z operands. Depending on the skeleton transform type, those
 * operands represent translation, rotation, scale, or alpha changes.
 * </p>
 */
public class AnimationFrame {

	private static final int FOOTER_SIZE = 8;
	private static final int MAX_TRANSFORMS = 500;
	private static final int SCALE_TRANSFORM = 3;
	private static final int ALPHA_TRANSFORM = 5;
	private static final int DEFAULT_SCALE = 128;

	/** Decoded frames indexed by frame identifier. */
	public static AnimationFrame[] frames;

	/** Whether each frame contains no alpha transform and can share face alpha. */
	public static boolean[] hasNoAlphaTransform;

	/** Display duration read from the frame archive. */
	public int duration;

	/** Skeleton shared by every frame in the decoded archive group. */
	public Skeleton skeleton;

	public int transformCount;
	public int[] transformSkeletonLabels;
	public int[] transformXs;
	public int[] transformYs;
	public int[] transformZs;

	/**
	 * Allocates the global frame tables for identifiers through {@code maximumId}.
	 */
	public static void initialize(int maximumId) {
		frames = new AnimationFrame[maximumId + 1];
		hasNoAlphaTransform = new boolean[maximumId + 1];
		Arrays.fill(hasNoAlphaTransform, true);
	}

	/**
	 * Decodes a group of animation frames from the revision-377 packed layout.
	 *
	 * <p>
	 * The final eight bytes contain four segment lengths. Separate buffer views
	 * then consume frame headers, transform masks, smart operands, durations, and
	 * the shared skeleton without copying the source array.
	 * </p>
	 */
	public static void load(byte[] data) {
		Buffer footer = new Buffer(data);
		footer.position = data.length - FOOTER_SIZE;
		int headerLength = footer.readUnsignedShort();
		int maskLength = footer.readUnsignedShort();
		int operandLength = footer.readUnsignedShort();
		int durationLength = footer.readUnsignedShort();

		int offset = 0;
		Buffer headers = new Buffer(data);
		headers.position = offset;
		offset += headerLength + 2;
		Buffer masks = new Buffer(data);
		masks.position = offset;
		offset += maskLength;
		Buffer operands = new Buffer(data);
		operands.position = offset;
		offset += operandLength;
		Buffer durations = new Buffer(data);
		durations.position = offset;
		offset += durationLength;
		Buffer skeletonData = new Buffer(data);
		skeletonData.position = offset;

		Skeleton skeleton = new Skeleton(skeletonData);
		int frameCount = headers.readUnsignedShort();
		int[] labels = new int[MAX_TRANSFORMS];
		int[] x = new int[MAX_TRANSFORMS];
		int[] y = new int[MAX_TRANSFORMS];
		int[] z = new int[MAX_TRANSFORMS];

		for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
			int frameId = headers.readUnsignedShort();
			AnimationFrame frame = frames[frameId] = new AnimationFrame();
			frame.duration = durations.readUnsignedByte();
			frame.skeleton = skeleton;

			int skeletonEntryCount = headers.readUnsignedByte();
			int previousLabel = -1;
			int decodedTransforms = 0;

			for (int label = 0; label < skeletonEntryCount; label++) {
				int mask = masks.readUnsignedByte();
				if (mask == 0) {
					continue;
				}

				// Insert the nearest skipped origin transform required by this entry.
				if (skeleton.transformTypes[label] != 0) {
					for (int skipped = label - 1; skipped > previousLabel; skipped--) {
						if (skeleton.transformTypes[skipped] == 0) {
							labels[decodedTransforms] = skipped;
							x[decodedTransforms] = 0;
							y[decodedTransforms] = 0;
							z[decodedTransforms] = 0;
							decodedTransforms++;
							break;
						}
					}
				}

				labels[decodedTransforms] = label;
				int defaultValue = skeleton.transformTypes[label] == SCALE_TRANSFORM ? DEFAULT_SCALE : 0;
				x[decodedTransforms] = (mask & 1) != 0 ? operands.readSignedSmart() : defaultValue;
				y[decodedTransforms] = (mask & 2) != 0 ? operands.readSignedSmart() : defaultValue;
				z[decodedTransforms] = (mask & 4) != 0 ? operands.readSignedSmart() : defaultValue;
				previousLabel = label;
				decodedTransforms++;

				if (skeleton.transformTypes[label] == ALPHA_TRANSFORM) {
					hasNoAlphaTransform[frameId] = false;
				}
			}

			frame.transformCount = decodedTransforms;
			frame.transformSkeletonLabels = Arrays.copyOf(labels, decodedTransforms);
			frame.transformXs = Arrays.copyOf(x, decodedTransforms);
			frame.transformYs = Arrays.copyOf(y, decodedTransforms);
			frame.transformZs = Arrays.copyOf(z, decodedTransforms);
		}
	}

	/** Releases all decoded animation frames. */
	public static void clear() {
		frames = null;
	}

	/**
	 * Returns a decoded frame, or {@code null} before the frame table is loaded.
	 */
	public static AnimationFrame get(int frameId) {
		return frames == null ? null : frames[frameId];
	}

	/** Returns whether an optional animation-frame identifier is absent. */
	public static boolean isNull(int frameId) {
		return frameId == -1;
	}
}