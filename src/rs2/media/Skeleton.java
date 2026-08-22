package rs2.media;

import rs2.net.Buffer;

/**
 * Shared skeletal transform map referenced by a group of animation frames.
 */
public class Skeleton {

	/** Number of transform entries in the skeleton. */
	public int transformCount;

	/**
	 * Transform type for each entry: origin, translation, rotation, scale, or
	 * alpha.
	 */
	public int[] transformTypes;

	/** Vertex or face group labels affected by each transform entry. */
	public int[][] labels;

	/**
	 * Decodes a skeleton from the trailing segment of an animation archive.
	 * 
	 * @param buffer the buffer
	 */
	public Skeleton(Buffer buffer) {
		transformCount = buffer.readUnsignedByte();
		transformTypes = new int[transformCount];
		labels = new int[transformCount][];

		for (int transform = 0; transform < transformCount; transform++) {
			transformTypes[transform] = buffer.readUnsignedByte();
		}

		for (int transform = 0; transform < transformCount; transform++) {
			int labelCount = buffer.readUnsignedByte();
			labels[transform] = new int[labelCount];
			for (int label = 0; label < labelCount; label++) {
				labels[transform][label] = buffer.readUnsignedByte();
			}
		}
	}
}