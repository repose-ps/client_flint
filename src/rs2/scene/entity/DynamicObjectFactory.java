package rs2.scene.entity;

import java.util.Objects;
import java.util.function.IntSupplier;

import rs2.cache.cfg.VarpProvider;

/**
 * Creates dynamic scene locations with only the runtime values they require.
 *
 * <p>
 * This factory keeps scene/region code independent of the application
 * {@code Client} while sharing one game-cycle and varp source across dynamic
 * objects.
 * </p>
 */
public final class DynamicObjectFactory {

	/** Current varp values used by morphing locations. */
	private final VarpProvider varpProvider;

	/** Current client cycle used by location animation timing. */
	private final IntSupplier gameCycleProvider;

	/**
	 * Creates a factory.
	 *
	 * @param varpProvider      current varp source
	 * @param gameCycleProvider current client-cycle source
	 */
	public DynamicObjectFactory(VarpProvider varpProvider, IntSupplier gameCycleProvider) {
		this.varpProvider = Objects.requireNonNull(varpProvider, "varpProvider");
		this.gameCycleProvider = Objects.requireNonNull(gameCycleProvider, "gameCycleProvider");
	}

	/**
	 * Creates one dynamic scene location.
	 *
	 * @param objectId           object definition ID
	 * @param type               location model type
	 * @param orientation        location orientation
	 * @param southWestHeight    south-west corner height
	 * @param southEastHeight    south-east corner height
	 * @param northEastHeight    north-east corner height
	 * @param northWestHeight    north-west corner height
	 * @param animationId        animation sequence ID, or {@code -1}
	 * @param randomizeAnimation whether to randomize a looping animation start
	 * @return dynamic location
	 */
	public DynamicObject create(int objectId, int type, int orientation, int southWestHeight, int southEastHeight,
			int northEastHeight, int northWestHeight, int animationId, boolean randomizeAnimation) {
		return new DynamicObject(objectId, type, orientation, southWestHeight, southEastHeight, northEastHeight,
				northWestHeight, animationId, randomizeAnimation, varpProvider, gameCycleProvider);
	}
}
