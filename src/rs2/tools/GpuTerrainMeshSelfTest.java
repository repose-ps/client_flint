package rs2.tools;

import rs2.gpu.GpuSceneUploader;
import rs2.gpu.GpuTerrainMesh;
import rs2.scene.Scene;
import rs2.scene.SceneConstants;
import rs2.scene.tile.ComplexTile;

/** Cache-free structural checks for the Phase-2 full-scene terrain uploader. */
public final class GpuTerrainMeshSelfTest {

	private GpuTerrainMeshSelfTest() {
	}

	/** Runs the terrain upload checks and returns the assertion count. */
	public static int run() {
		SelfTestSupport test = new SelfTestSupport();
		testFullSceneIsNotDistanceTruncated(test);
		testShapedTileAndPlaneFiltering(test);
		return test.checks();
	}

	public static void main(String[] args) {
		int checks = run();
		System.out.println("GpuTerrainMeshSelfTest: PASS (" + checks + " checks, cache-free)");
	}

	private static void testFullSceneIsNotDistanceTruncated(SelfTestSupport test) {
		int[][][] heights = new int[1][SceneConstants.SIZE + 1][SceneConstants.SIZE + 1];
		Scene scene = new Scene(heights, 1, SceneConstants.SIZE, SceneConstants.SIZE);
		for (int x = 0; x < SceneConstants.SIZE; x++) {
			for (int y = 0; y < SceneConstants.SIZE; y++) {
				addPlainTile(scene, x, y, 0x3200 + ((x + y) & 0x7f));
			}
		}

		GpuTerrainMesh mesh = GpuSceneUploader.buildTerrain(scene, 0);
		int expectedSurfaces = SceneConstants.SIZE * SceneConstants.SIZE;
		int expectedTriangles = expectedSurfaces * 2;
		test.equal(mesh.surfaceCount(), expectedSurfaces, "GPU terrain uploads every 104x104 tile");
		test.equal(mesh.triangleCount(), expectedTriangles, "GPU terrain emits two triangles per plain tile");
		test.equal(mesh.vertexCount(), expectedTriangles * 3, "GPU terrain uses three vertices per triangle");

		float[] vertices = mesh.vertices();
		test.equal((int) vertices[0], SceneConstants.TILE_SIZE, "first plain triangle NE world X");
		test.equal((int) vertices[1], 0, "first plain triangle NE height");
		test.equal((int) vertices[2], SceneConstants.TILE_SIZE, "first plain triangle NE world Y");
		test.equal((int) vertices[GpuTerrainMesh.FLOATS_PER_VERTEX], 0, "first plain triangle NW world X");
	}

	private static void testShapedTileAndPlaneFiltering(SelfTestSupport test) {
		int[][][] heights = new int[1][2][2];
		heights[0][0][0] = 0;
		heights[0][1][0] = -32;
		heights[0][1][1] = -64;
		heights[0][0][1] = -16;
		Scene scene = new Scene(heights, 1, 1, 1);
		scene.addTile(0, 0, 0, 2, 1, -1, 0, -32, -64, -16, 0x3100, 0x3200, 0x3300, 0x3400,
				0x3500, 0x3600, 0x3700, 0x3800, 0, 0);

		ComplexTile shaped = scene.tiles[0][0][0].shapedTile;
		GpuTerrainMesh mesh = GpuSceneUploader.buildTerrain(scene, 0);
		test.equal(mesh.triangleCount(), shaped.triangleVertexA.length, "GPU shaped tile triangle count");
		test.equal(mesh.vertexCount(), shaped.triangleVertexA.length * 3, "GPU shaped tile vertex count");

		scene.tiles[0][0][0].logicHeight = 1;
		GpuTerrainMesh hidden = GpuSceneUploader.buildTerrain(scene, 0);
		test.equal(hidden.triangleCount(), 0, "GPU terrain honors tile logic-height roof filtering");
	}

	private static void addPlainTile(Scene scene, int x, int y, int colour) {
		scene.addTile(0, x, y, 0, 0, -1, 0, 0, 0, 0, colour, colour, colour, colour, 0, 0, 0, 0, 0, 0);
	}
}
