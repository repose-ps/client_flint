package rs2.scene.tile;

public class ComplexTile {

	/** World-space x-coordinate for each mesh vertex. */
	public int[] vertexX;

	/** World-space height for each mesh vertex. */
	public int[] vertexY;

	/** World-space y-coordinate for each mesh vertex. */
	public int[] vertexZ;

	/** Packed HSL value for the first vertex in each triangle. */
	public int[] triangleHslA;

	/** Packed HSL value for the second vertex in each triangle. */
	public int[] triangleHslB;

	/** Packed HSL value for the third vertex in each triangle. */
	public int[] triangleHslC;

	/** Index of the first vertex in each triangle. */
	public int[] triangleVertexA;

	/** Index of the second vertex in each triangle. */
	public int[] triangleVertexB;

	/** Index of the third vertex in each triangle. */
	public int[] triangleVertexC;

	/** Per-triangle texture ids; {@code null} when the tile is untextured. */
	public int[] triangleTextures;

	/** Whether the four corner heights are equal. */
	public boolean flat;

	/** Index into the revision 377 tile-shape templates. */
	public int shape;

	/** Tile-shape rotation in quarter turns. */
	public int rotation;

	/** Minimap colour for the underlay. */
	public int underlayRgb;

	/** Minimap colour for the overlay. */
	public int overlayRgb;

	/** Reusable projected x-coordinates used by the software rasterizer. */
	public static final int[] SCREEN_X = new int[6];

	/** Reusable projected y-coordinates used by the software rasterizer. */
	public static final int[] SCREEN_Y = new int[6];

	/** Reusable view-space x-coordinates used by the software rasterizer. */
	public static final int[] VIEW_X = new int[6];

	/** Reusable view-space y-coordinates used by the software rasterizer. */
	public static final int[] VIEW_Y = new int[6];

	/** Reusable view-space depth values used by the software rasterizer. */
	public static final int[] VIEW_Z = new int[6];

	/**
	 * Vertex-position codes for each supported tile shape.
	 *
	 * <p>
	 * Codes 1-8 describe corners and edge midpoints. Codes 9-16 describe interior
	 * quarter-tile positions. The constructor rotates these codes before converting
	 * them to world coordinates.
	 * </p>
	 */
	public static final int SHAPE_VERTEX_TYPES[][] = { { 1, 3, 5, 7 }, { 1, 3, 5, 7 }, { 1, 3, 5, 7 },
			{ 1, 3, 5, 7, 6 }, { 1, 3, 5, 7, 6 }, { 1, 3, 5, 7, 6 }, { 1, 3, 5, 7, 6 }, { 1, 3, 5, 7, 2, 6 },
			{ 1, 3, 5, 7, 2, 8 }, { 1, 3, 5, 7, 2, 8 }, { 1, 3, 5, 7, 11, 12 }, { 1, 3, 5, 7, 11, 12 },
			{ 1, 3, 5, 7, 13, 14 } };

	/**
	 * Triangle definitions for each supported tile shape.
	 *
	 * <p>
	 * Every group of four values contains a colour-set selector followed by the
	 * triangle's three vertex indices.
	 * </p>
	 */
	public static final int SHAPE_TRIANGLES[][] = { { 0, 1, 2, 3, 0, 0, 1, 3 }, { 1, 1, 2, 3, 1, 0, 1, 3 },
			{ 0, 1, 2, 3, 1, 0, 1, 3 }, { 0, 0, 1, 2, 0, 0, 2, 4, 1, 0, 4, 3 }, { 0, 0, 1, 4, 0, 0, 4, 3, 1, 1, 2, 4 },
			{ 0, 0, 4, 3, 1, 0, 1, 2, 1, 0, 2, 4 }, { 0, 1, 2, 4, 1, 0, 1, 4, 1, 0, 4, 3 },
			{ 0, 4, 1, 2, 0, 4, 2, 5, 1, 0, 4, 5, 1, 0, 5, 3 }, { 0, 4, 1, 2, 0, 4, 2, 3, 0, 4, 3, 5, 1, 0, 4, 5 },
			{ 0, 0, 4, 5, 1, 4, 1, 2, 1, 4, 2, 3, 1, 4, 3, 5 },
			{ 0, 0, 1, 5, 0, 1, 4, 5, 0, 1, 2, 4, 1, 0, 5, 3, 1, 5, 4, 3, 1, 4, 2, 3 },
			{ 1, 0, 1, 5, 1, 1, 4, 5, 1, 1, 2, 4, 0, 0, 5, 3, 0, 5, 4, 3, 0, 4, 2, 3 },
			{ 1, 0, 5, 4, 1, 0, 1, 5, 0, 0, 4, 3, 0, 4, 5, 3, 0, 5, 2, 3, 0, 1, 2, 5 } };

	private static final int TILE_SIZE = 128;

	/**
	 * Builds a shaped tile from its corner heights and two colour sets.
	 *
	 * @param tileX           the tile x
	 * @param heightA         the height a
	 * @param heightB         the height b
	 * @param heightC         the height c
	 * @param heightD         the height d
	 * @param tileY           the tile y
	 * @param rotation        the rotation
	 * @param texture         the texture
	 * @param shape           the shape
	 * @param overlayColourA  the overlay colour a
	 * @param underlayColourA the underlay colour a
	 * @param overlayColourB  the overlay colour b
	 * @param underlayColourB the underlay colour b
	 * @param overlayColourC  the overlay colour c
	 * @param underlayColourC the underlay colour c
	 * @param overlayColourD  the overlay colour d
	 * @param underlayColourD the underlay colour d
	 * @param overlayRgb      the overlay rgb
	 * @param underlayRgb     the underlay rgb
	 */
	public ComplexTile(int tileX, int heightA, int heightB, int heightC, int heightD, int tileY, int rotation,
			int texture, int shape, int overlayColourA, int underlayColourA, int overlayColourB, int underlayColourB,
			int overlayColourC, int underlayColourC, int overlayColourD, int underlayColourD, int overlayRgb,
			int underlayRgb) {
		flat = true;
		if (heightA != heightB || heightA != heightD || heightA != heightC)
			flat = false;
		this.shape = shape;
		this.rotation = rotation;
		this.underlayRgb = underlayRgb;
		this.overlayRgb = overlayRgb;

		int halfTile = TILE_SIZE / 2;
		int quarterTile = TILE_SIZE / 4;
		int threeQuarterTile = (TILE_SIZE * 3) / 4;

		int vertexTypes[] = SHAPE_VERTEX_TYPES[shape];
		int vertexCount = vertexTypes.length;
		vertexX = new int[vertexCount];
		vertexY = new int[vertexCount];
		vertexZ = new int[vertexCount];
		int vertexOverlayColours[] = new int[vertexCount];
		int vertexUnderlayColours[] = new int[vertexCount];

		int worldX = tileX * TILE_SIZE;
		int worldY = tileY * TILE_SIZE;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int vertexType = vertexTypes[vertex];
			if ((vertexType & 1) == 0 && vertexType <= 8)
				vertexType = (vertexType - rotation - rotation - 1 & 7) + 1;
			if (vertexType > 8 && vertexType <= 12)
				vertexType = (vertexType - 9 - rotation & 3) + 9;
			if (vertexType > 12 && vertexType <= 16)
				vertexType = (vertexType - 13 - rotation & 3) + 13;

			int x;
			int y;
			int z;
			int overlayColour;
			int underlayColour;
			if (vertexType == 1) {
				x = worldX;
				y = worldY;
				z = heightA;
				overlayColour = overlayColourA;
				underlayColour = underlayColourA;
			} else if (vertexType == 2) {
				x = worldX + halfTile;
				y = worldY;
				z = heightA + heightB >> 1;
				overlayColour = overlayColourA + overlayColourB >> 1;
				underlayColour = underlayColourA + underlayColourB >> 1;
			} else if (vertexType == 3) {
				x = worldX + TILE_SIZE;
				y = worldY;
				z = heightB;
				overlayColour = overlayColourB;
				underlayColour = underlayColourB;
			} else if (vertexType == 4) {
				x = worldX + TILE_SIZE;
				y = worldY + halfTile;
				z = heightB + heightD >> 1;
				overlayColour = overlayColourB + overlayColourD >> 1;
				underlayColour = underlayColourB + underlayColourD >> 1;
			} else if (vertexType == 5) {
				x = worldX + TILE_SIZE;
				y = worldY + TILE_SIZE;
				z = heightD;
				overlayColour = overlayColourD;
				underlayColour = underlayColourD;
			} else if (vertexType == 6) {
				x = worldX + halfTile;
				y = worldY + TILE_SIZE;
				z = heightD + heightC >> 1;
				overlayColour = overlayColourD + overlayColourC >> 1;
				underlayColour = underlayColourD + underlayColourC >> 1;
			} else if (vertexType == 7) {
				x = worldX;
				y = worldY + TILE_SIZE;
				z = heightC;
				overlayColour = overlayColourC;
				underlayColour = underlayColourC;
			} else if (vertexType == 8) {
				x = worldX;
				y = worldY + halfTile;
				z = heightC + heightA >> 1;
				overlayColour = overlayColourC + overlayColourA >> 1;
				underlayColour = underlayColourC + underlayColourA >> 1;
			} else if (vertexType == 9) {
				x = worldX + halfTile;
				y = worldY + quarterTile;
				z = heightA + heightB >> 1;
				overlayColour = overlayColourA + overlayColourB >> 1;
				underlayColour = underlayColourA + underlayColourB >> 1;
			} else if (vertexType == 10) {
				x = worldX + threeQuarterTile;
				y = worldY + halfTile;
				z = heightB + heightD >> 1;
				overlayColour = overlayColourB + overlayColourD >> 1;
				underlayColour = underlayColourB + underlayColourD >> 1;
			} else if (vertexType == 11) {
				x = worldX + halfTile;
				y = worldY + threeQuarterTile;
				z = heightD + heightC >> 1;
				overlayColour = overlayColourD + overlayColourC >> 1;
				underlayColour = underlayColourD + underlayColourC >> 1;
			} else if (vertexType == 12) {
				x = worldX + quarterTile;
				y = worldY + halfTile;
				z = heightC + heightA >> 1;
				overlayColour = overlayColourC + overlayColourA >> 1;
				underlayColour = underlayColourC + underlayColourA >> 1;
			} else if (vertexType == 13) {
				x = worldX + quarterTile;
				y = worldY + quarterTile;
				z = heightA;
				overlayColour = overlayColourA;
				underlayColour = underlayColourA;
			} else if (vertexType == 14) {
				x = worldX + threeQuarterTile;
				y = worldY + quarterTile;
				z = heightB;
				overlayColour = overlayColourB;
				underlayColour = underlayColourB;
			} else if (vertexType == 15) {
				x = worldX + threeQuarterTile;
				y = worldY + threeQuarterTile;
				z = heightD;
				overlayColour = overlayColourD;
				underlayColour = underlayColourD;
			} else {
				x = worldX + quarterTile;
				y = worldY + threeQuarterTile;
				z = heightC;
				overlayColour = overlayColourC;
				underlayColour = underlayColourC;
			}
			vertexX[vertex] = x;
			vertexY[vertex] = z;
			vertexZ[vertex] = y;
			vertexOverlayColours[vertex] = overlayColour;
			vertexUnderlayColours[vertex] = underlayColour;
		}

		int triangleData[] = SHAPE_TRIANGLES[shape];
		int triangleCount = triangleData.length / 4;
		triangleVertexA = new int[triangleCount];
		triangleVertexB = new int[triangleCount];
		triangleVertexC = new int[triangleCount];
		triangleHslA = new int[triangleCount];
		triangleHslB = new int[triangleCount];
		triangleHslC = new int[triangleCount];
		if (texture != -1)
			triangleTextures = new int[triangleCount];

		int offset = 0;
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			int colourSet = triangleData[offset];
			int vertexA = triangleData[offset + 1];
			int vertexB = triangleData[offset + 2];
			int vertexC = triangleData[offset + 3];
			offset += 4;
			if (vertexA < 4)
				vertexA = vertexA - rotation & 3;
			if (vertexB < 4)
				vertexB = vertexB - rotation & 3;
			if (vertexC < 4)
				vertexC = vertexC - rotation & 3;
			triangleVertexA[triangle] = vertexA;
			triangleVertexB[triangle] = vertexB;
			triangleVertexC[triangle] = vertexC;
			if (colourSet == 0) {
				triangleHslA[triangle] = vertexOverlayColours[vertexA];
				triangleHslB[triangle] = vertexOverlayColours[vertexB];
				triangleHslC[triangle] = vertexOverlayColours[vertexC];
				if (triangleTextures != null)
					triangleTextures[triangle] = -1;
			} else {
				triangleHslA[triangle] = vertexUnderlayColours[vertexA];
				triangleHslB[triangle] = vertexUnderlayColours[vertexB];
				triangleHslC[triangle] = vertexUnderlayColours[vertexC];
				if (triangleTextures != null)
					triangleTextures[triangle] = texture;
			}
		}
	}

}
