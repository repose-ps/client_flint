package rs2.game.render;

import rs2.scene.Scene;

/**
 * Immutable inputs required by a 3D world-rendering backend for one frame.
 *
 * <p>
 * Keeping this contract separate from {@link GameRenderer.SceneFrame} prevents a
 * native renderer from depending on UI, networking, actor-overlay, or other
 * high-level client orchestration state.
 * </p>
 *
 * @param scene        populated local scene to render
 * @param cameraX      camera world X
 * @param cameraY      camera world Y
 * @param cameraHeight camera world height
 * @param renderPlane  selected scene render plane
 * @param yaw          camera yaw in revision-377 angle units
 * @param pitch        camera pitch in revision-377 angle units
 * @param mouseX       mouse X relative to the world viewport
 * @param mouseY       mouse Y relative to the world viewport
 */
public record WorldRenderFrame(Scene scene, int cameraX, int cameraY, int cameraHeight, int renderPlane, int yaw,
		int pitch, int mouseX, int mouseY) {
}
