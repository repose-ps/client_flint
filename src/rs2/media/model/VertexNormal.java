package rs2.media.model;

/**
 * Accumulated vertex normal used by the software model-lighting pipeline.
 */
public class VertexNormal {

	/** Creates a new vertex normal with its default client state. */
	public VertexNormal() {
	}

	/** Stores the current X. */
	public int x;

	/** Stores the current Y. */
	public int y;

	/** Stores the current Z. */
	public int z;

	/** Number of face normals accumulated into this vertex normal. */
	public int magnitude;
}
