package rs2.gpu;

/** Immutable CPU-side terrain mesh ready for upload to an OpenGL vertex buffer. */
public final class GpuTerrainMesh {

	/** Number of floats stored for each unindexed terrain vertex. */
	public static final int FLOATS_PER_VERTEX = 6;

	private final float[] vertices;
	private final int surfaceCount;
	private final int triangleCount;

	GpuTerrainMesh(float[] vertices, int surfaceCount, int triangleCount) {
		this.vertices = vertices;
		this.surfaceCount = surfaceCount;
		this.triangleCount = triangleCount;
	}

	/** Returns interleaved {@code x,height,y,r,g,b} terrain vertices. */
	public float[] vertices() {
		return vertices;
	}

	/** Returns the number of tile surfaces contributing at least one triangle. */
	public int surfaceCount() {
		return surfaceCount;
	}

	/** Returns the number of uploaded triangles. */
	public int triangleCount() {
		return triangleCount;
	}

	/** Returns the number of unindexed vertices. */
	public int vertexCount() {
		return vertices.length / FLOATS_PER_VERTEX;
	}
}
