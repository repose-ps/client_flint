package rs2.game.render;

/**
 * Backend boundary for rendering the 3D game world.
 *
 * <p>
 * Game-frame orchestration, actor insertion, overlays, UI composition and final
 * presentation remain owned by {@link GameRenderer}. Implementations own only
 * the world rasterization step.
 * </p>
 */
public interface WorldRenderer {

	/**
	 * Returns the backend implemented by this renderer.
	 *
	 * @return renderer backend
	 */
	RendererBackend backend();

	/**
	 * Renders one populated local-scene frame.
	 *
	 * @param frame immutable world-render inputs
	 */
	void render(WorldRenderFrame frame);
}
