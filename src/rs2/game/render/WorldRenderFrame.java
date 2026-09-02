package rs2.game.render;

import rs2.scene.Scene;

/**
 * Immutable inputs required by a 3D world-rendering backend for one frame.
 *
 * @param scene          populated local scene to render
 * @param cameraX        camera world X
 * @param cameraY        camera world Y
 * @param cameraHeight   camera world height
 * @param renderPlane    selected scene render plane
 * @param yaw            camera yaw in revision-377 angle units
 * @param pitch          camera pitch in revision-377 angle units
 * @param mouseX         mouse X relative to the world viewport
 * @param mouseY         mouse Y relative to the world viewport
 * @param viewportX      viewport X in client-area coordinates
 * @param viewportY      viewport Y in client-area coordinates
 * @param viewportWidth  viewport width in pixels
 * @param viewportHeight viewport height in pixels
 */
public record WorldRenderFrame(Scene scene, int cameraX, int cameraY, int cameraHeight, int renderPlane, int yaw,
        int pitch, int mouseX, int mouseY, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
}
