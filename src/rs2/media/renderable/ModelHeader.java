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
public class ModelHeader {

	public byte[] modelData;
	public int vertexCount;
	public int triangleCount;
	public int texturedTriangleCount;
	public int vertexDirectionOffset;
	public int xDataOffset;
	public int yDataOffset;
	public int zDataOffset;
	public int vertexSkinOffset;
	public int triangleDataOffset;
	public int triangleTypeOffset;
	public int colorDataOffset;
	public int texturePointerOffset;
	public int trianglePriorityOffset;
	public int triangleAlphaOffset;
	public int triangleSkinOffset;
	public int uvMapTriangleOffset;
}