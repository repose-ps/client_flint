package rs2.scene;

/**
 * Bit layout of the packed scene UID used by revision 377 for picked and placed
 * scene entities.
 *
 * <p>
 * Bits 0..6 store tile X, 7..13 tile Y, 14..28 the entity/definition id, 29..30
 * the entity category, and bit 31 marks a non-interactive entity.
 * </p>
 */
public final class SceneUid {

	/** Mask for a seven-bit tile coordinate. */
	public static final int TILE_COORDINATE_MASK = 0x7f;
	/** Shift of the tile-Y field. */
	public static final int TILE_Y_SHIFT = 7;
	/** Shift of the entity/definition-id field. */
	public static final int ENTITY_ID_SHIFT = 14;
	/** Mask for the fifteen-bit entity/definition-id field. */
	public static final int ENTITY_ID_MASK = 0x7fff;
	/** Shift of the two-bit entity-type field. */
	public static final int ENTITY_TYPE_SHIFT = 29;
	/** Mask for the two-bit entity-type field. */
	public static final int ENTITY_TYPE_MASK = 0x3;

	/** Packed type value for players. */
	public static final int TYPE_PLAYER = 0;
	/** Packed type value for NPCs. */
	public static final int TYPE_NPC = 1;
	/** Packed type value for world objects. */
	public static final int TYPE_OBJECT = 2;
	/** Packed type value for ground-item piles. */
	public static final int TYPE_GROUND_ITEM = 3;

	/** High-bit flag used for definitions that are not interactable. */
	public static final int NON_INTERACTIVE_FLAG = 0x80000000;
	/** Packed entity-type bits for NPCs. */
	public static final int NPC_TYPE_BITS = TYPE_NPC << ENTITY_TYPE_SHIFT;
	/** Packed entity-type bits for world objects. */
	public static final int OBJECT_TYPE_BITS = TYPE_OBJECT << ENTITY_TYPE_SHIFT;
	/** Packed entity-type bits for ground-item piles. */
	public static final int GROUND_ITEM_TYPE_BITS = TYPE_GROUND_ITEM << ENTITY_TYPE_SHIFT;

	/** Prevents instantiation. */
	private SceneUid() {
	}
}
