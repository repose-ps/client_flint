package rs2.scene.tile;

/**
 * A simple quadrilateral scene-tile surface.
 *
 * <p>
 * The four corner colours are interpolated when the tile is rasterized. More
 * complicated footprints are represented by {@link ComplexTile}.
 * </p>
 */
public class GenericTile {

	/** Colour at the first tile corner. */
	public int colourA;

	/** Colour at the second tile corner. */
	public int colourB;

	/** Colour at the third tile corner. */
	public int colourC;

	/** Colour at the fourth tile corner. */
	public int colourD;

	/** Texture id, or {@code -1} when the tile is untextured. */
	public int texture;

	/** Whether all four tile-corner heights are equal. */
	public boolean flat;

	/** Minimap colour for the tile surface. */
	public int rgbColour;

	/**
	 * Creates a new generic tile.
	 *
	 * @param colourA   the colour a
	 * @param colourB   the colour b
	 * @param colourC   the colour c
	 * @param colourD   the colour d
	 * @param texture   the texture
	 * @param rgbColour the RGB colour
	 * @param flat      the flat
	 */
	public GenericTile(int colourA, int colourB, int colourC, int colourD, int texture, int rgbColour, boolean flat) {
		this.colourA = colourA;
		this.colourB = colourB;
		this.colourC = colourC;
		this.colourD = colourD;
		this.texture = texture;
		this.flat = flat;
		this.rgbColour = rgbColour;
	}
}
