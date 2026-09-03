package rs2.media.model;

import rs2.collection.DualNode;

/**
 * Base type for objects that can supply a software-rendered model to the scene.
 *
 * <p>
 * The scene passes the same nine fixed-point camera/translation arguments on to
 * the returned model. {@link #modelHeight} is refreshed from that model just
 * before rendering, matching the revision-377 client.
 * </p>
 */
public class Renderable extends DualNode {

	/** Creates a new renderable with its default client state. */
	public Renderable() {
	}

	/** Per-vertex normals used by scene lighting/normal merging. */
	public VertexNormal[] vertexNormals;

	/** Vertical extent of the most recently rendered model. */
	public int modelHeight = 1000;

	/**
	 * Draws value.
	 *
	 * @param orientation the orientation
	 * @param pitchSine   the pitch sine
	 * @param pitchCosine the pitch cosine
	 * @param yawSine     the yaw sine
	 * @param yawCosine   the yaw cosine
	 * @param x           the x
	 * @param y           the y
	 * @param z           the z
	 * @param uid         the uid
	 */
	public void draw(int orientation, int pitchSine, int pitchCosine, int yawSine, int yawCosine, int x, int y, int z,
			int uid) {
		Model model = getModel();
		if (model != null) {
			modelHeight = model.modelHeight;
			model.draw(orientation, pitchSine, pitchCosine, yawSine, yawCosine, x, y, z, uid);
		}
	}

	/**
	 * Tests this renderable against one viewport-space mouse position without
	 * rasterizing it. This is used by the fixed-rate scene picker so interaction
	 * remains independent from presentation FPS.
	 *
	 * @param orientation    model orientation in revision-377 angle units
	 * @param pitchSine      camera pitch sine
	 * @param pitchCosine    camera pitch cosine
	 * @param yawSine        camera yaw sine
	 * @param yawCosine      camera yaw cosine
	 * @param x              camera-relative world X
	 * @param y              camera-relative height
	 * @param z              camera-relative world Y
	 * @param mouseX         mouse X relative to the viewport
	 * @param mouseY         mouse Y relative to the viewport
	 * @param viewportWidth  viewport width
	 * @param viewportHeight viewport height
	 * @return {@code true} when the current model is under the mouse
	 */
	public boolean hitTest(int orientation, int pitchSine, int pitchCosine, int yawSine, int yawCosine, int x, int y,
			int z, int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
		Model model = getModel();
		if (model == null)
			return false;
		modelHeight = model.modelHeight;
		return model.hitTest(orientation, pitchSine, pitchCosine, yawSine, yawCosine, x, y, z, mouseX, mouseY,
				viewportWidth, viewportHeight);
	}

	/**
	 * Returns the model currently represented by this renderable, or {@code null}
	 * when nothing should be drawn.
	 *
	 * @return the current model, or {@code null} when this renderable has no model
	 */
	protected Model getModel() {
		return null;
	}
}
