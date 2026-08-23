package rs2.media;

/**
 * Accumulated vertex normal used by the software model-lighting pipeline.
 */
public class VertexNormal {

	public int x;

	public int y;

	public int z;

	/** Number of face normals accumulated into this vertex normal. */
	public int magnitude;
}
