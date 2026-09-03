package rs2.game.render;

import rs2.media.Rasterizer;

/** Revision-377 software implementation of the world-rendering backend. */
final class SoftwareWorldRenderer implements WorldRenderer {

	/** {@inheritDoc} */
	@Override
	public RendererBackend backend() {
		return RendererBackend.SOFTWARE;
	}

	/**
	 * Runs the software clear and scene-rasterization sequence. Picking is resolved
	 * by {@link ScenePicker} on the fixed logic tick.
	 *
	 * @param frame current world-render inputs
	 */
	@Override
	public void render(WorldRenderFrame frame) {
		Rasterizer.resetPixels();
		frame.scene().render(frame.cameraX(), frame.cameraY(), frame.cameraHeight(), frame.renderPlane(), frame.yaw(),
				frame.pitch());
	}
}
