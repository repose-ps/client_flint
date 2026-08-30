package rs2.media.model;

/**
 * Parsed metadata and segment offsets for a packed revision-377 model.
 *
 * <p>
 * The header retains the original byte array and points into its separately
 * encoded vertex, triangle, skin, color, alpha, and texture streams. Model
 * construction can therefore decode an individual model without first splitting
 * or copying every stream.
 * </p>
 */
public class ModelHeader {

	/** Creates a new model header with its default client state. */
	public ModelHeader() {
	}

	/** Stores model data values. */
	public byte[] modelData;
	/**
	 * Number of vertex entries.
	 */
	public int vertexCount;
	/**
	 * Number of triangle entries.
	 */
	public int triangleCount;
	/**
	 * Number of textured triangle entries.
	 */
	public int texturedTriangleCount;

	/** Stores the current vertex direction offset. */
	public int vertexDirectionOffset;

	/** Stores the current X data offset. */
	public int xDataOffset;

	/** Stores the current Y data offset. */
	public int yDataOffset;

	/** Stores the current Z data offset. */
	public int zDataOffset;

	/** Stores the current vertex skin offset. */
	public int vertexSkinOffset;

	/** Stores the current triangle data offset. */
	public int triangleDataOffset;

	/** Stores the current triangle type offset. */
	public int triangleTypeOffset;

	/** Stores the current color data offset. */
	public int colorDataOffset;

	/** Stores the current texture pointer offset. */
	public int texturePointerOffset;

	/** Stores the current triangle priority offset. */
	public int trianglePriorityOffset;

	/** Stores the current triangle alpha offset. */
	public int triangleAlphaOffset;

	/** Stores the current triangle skin offset. */
	public int triangleSkinOffset;

	/** Stores the current uv map triangle offset. */
	public int uvMapTriangleOffset;
}
