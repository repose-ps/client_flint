package rs2.media.renderable;

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
public /**
		 * Initializes this instance.
		 */
class ModelHeader {

	/**
	 * Stores model data.
	 */
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
	/**
	 * Stores vertex direction offset.
	 */
	public int vertexDirectionOffset;
	/**
	 * Stores x data offset.
	 */
	public int xDataOffset;
	/**
	 * Stores y data offset.
	 */
	public int yDataOffset;
	/**
	 * Stores z data offset.
	 */
	public int zDataOffset;
	/**
	 * Stores vertex skin offset.
	 */
	public int vertexSkinOffset;
	/**
	 * Stores triangle data offset.
	 */
	public int triangleDataOffset;
	/**
	 * Stores triangle type offset.
	 */
	public int triangleTypeOffset;
	/**
	 * Stores color data offset.
	 */
	public int colorDataOffset;
	/**
	 * Stores texture pointer offset.
	 */
	public int texturePointerOffset;
	/**
	 * Stores triangle priority offset.
	 */
	public int trianglePriorityOffset;
	/**
	 * Stores triangle alpha offset.
	 */
	public int triangleAlphaOffset;
	/**
	 * Stores triangle skin offset.
	 */
	public int triangleSkinOffset;
	/**
	 * Stores uv map triangle offset.
	 */
	public int uvMapTriangleOffset;
}