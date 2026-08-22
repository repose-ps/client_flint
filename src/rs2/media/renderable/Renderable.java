package rs2.media.renderable;

import rs2.collection.DualNode;
import rs2.media.VertexNormal;

/**
 * Base type for objects that can supply a software-rendered model to the scene.
 *
 * <p>
 * The scene passes the same nine fixed-point camera/translation arguments on to
 * the returned model. {@link #modelHeight} is refreshed from that model just
 * before rendering, matching the revision-377 client.
 * </p>
 */
public /**
		 * Initializes this instance.
		 */
class Renderable extends DualNode {

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
	 * Returns the model currently represented by this renderable, or {@code null}
	 * when nothing should be drawn.
	 */
	protected Model getModel() {
		return null;
	}
}