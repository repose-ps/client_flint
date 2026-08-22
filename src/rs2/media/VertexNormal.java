package rs2.media;

/**
 * Accumulated vertex normal used by the software model-lighting pipeline.
 */
public /**
		 * Initializes this instance.
		 */
class VertexNormal {

	/**
	 * Stores x.
	 */
	public int x;
	/**
	 * Stores y.
	 */
	public int y;
	/**
	 * Stores z.
	 */
	public int z;

	/** Number of face normals accumulated into this vertex normal. */
	public int magnitude;
}