/**
 * High-level rendering coordinators and the pluggable 3D world-renderer boundary.
 *
 * <p>
 * {@link rs2.game.render.GameRenderer} owns frame orchestration, software UI
 * surfaces, overlays and presentation. {@link rs2.game.render.WorldRenderer}
 * isolates the 3D world rasterization step so the revision-377 software renderer
 * remains the golden/reference backend while a native GPU implementation is
 * developed independently.
 * </p>
 */
package rs2.game.render;
