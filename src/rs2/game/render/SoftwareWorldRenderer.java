package rs2.game.render;

import rs2.media.Rasterizer;
import rs2.media.model.Model;

/** Revision-377 software implementation of the world-rendering backend. */
final class SoftwareWorldRenderer implements WorldRenderer {

	/** {@inheritDoc} */
	@Override
	public RendererBackend backend() {
		return RendererBackend.SOFTWARE;
	}

	/**
	 * Runs the unchanged software picking, clear and scene-rasterization sequence.
	 *
	 * @param frame current world-render inputs
	 */
	@Override
	public void render(WorldRenderFrame frame) {
		Model.pickingEnabled = true;
		Model.pickedCount = 0;
		Model.mouseX = frame.mouseX();
		Model.mouseY = frame.mouseY();
		Rasterizer.resetPixels();
		frame.scene().render(frame.cameraX(), frame.cameraY(), frame.cameraHeight(), frame.renderPlane(), frame.yaw(),
				frame.pitch());
	}
}
