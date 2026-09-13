package rs2.tools;

import rs2.game.render.WorldRenderFrame;
import rs2.gpu.GpuSceneChunk;
import rs2.gpu.GpuSceneUploader;
import rs2.gpu.GpuSceneVisibility;
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
		testTexturedTerrainMetadata(test);
		testShapedTileAndPlaneFiltering(test);
		testChunkFrustumVisibility(test);
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

		GpuTerrainMesh mesh = GpuSceneUploader.buildTerrain(scene);
		int expectedSurfaces = SceneConstants.SIZE * SceneConstants.SIZE;
		int expectedTriangles = expectedSurfaces * 2;
		test.equal(mesh.surfaceCount(), expectedSurfaces, "GPU terrain uploads every 104x104 tile");
		test.equal(mesh.triangleCount(), expectedTriangles, "GPU terrain emits two triangles per plain tile");
		test.equal(mesh.vertexCount(), expectedTriangles * 3, "GPU terrain uses three vertices per triangle");
		test.equal(mesh.chunks().length, 13 * 13, "104x104 terrain is partitioned into 13x13 eight-tile chunks");
		test.equal(mesh.chunks()[0].firstVertex(), 0, "first terrain chunk begins at VBO vertex zero");
		GpuSceneChunk lastChunk = mesh.chunks()[mesh.chunks().length - 1];
		test.equal(lastChunk.firstVertex() + lastChunk.vertexCount(), mesh.vertexCount(),
				"terrain chunk ranges cover the complete VBO");
		test.equal(mesh.byteSize(), mesh.vertexCount() * 24, "textured terrain uses compact 24-byte vertices");

		int[] vertices = mesh.vertices();
		test.equal((int) vertices[0], SceneConstants.TILE_SIZE, "first plain triangle NE world X");
		test.equal((int) vertices[1], 0, "first plain triangle NE height");
		test.equal((int) vertices[2], SceneConstants.TILE_SIZE, "first plain triangle NE world Y");
		test.equal((int) vertices[GpuTerrainMesh.WORDS_PER_VERTEX], 0, "first plain triangle NW world X");
	}


	private static void testTexturedTerrainMetadata(SelfTestSupport test) {
		int[][][] heights = new int[1][2][2];
		Scene scene = new Scene(heights, 1, 1, 1);
		// Shape 1 creates a textured GenericTile from inputValue2..5.
		scene.addTile(0, 0, 0, 1, 0, 7, 0, 0, 0, 0,
				0, 0, 0, 0, 16, 32, 48, 64, 0, 0);

		GpuTerrainMesh mesh = GpuSceneUploader.buildTerrain(scene);
		test.equal(mesh.triangleCount(), 2, "textured plain tile emits two triangles");
		test.equal(mesh.textureId(0), 7, "terrain vertex retains texture material id");
		test.equal(mesh.textureUFixed(0), 256, "textured NE vertex U is one tile");
		test.equal(mesh.textureVFixed(0), 256, "textured NE vertex V is one tile");
		test.equal(mesh.textureUFixed(1), 0, "textured NW vertex U starts at zero");
		test.equal(mesh.textureVFixed(1), 256, "textured NW vertex V is one tile");
		test.equal(mesh.textureId(3), 7, "second terrain triangle shares texture material id");
		test.equal(mesh.textureUFixed(3), 0, "textured SW vertex U starts at zero");
		test.equal(mesh.textureVFixed(3), 0, "textured SW vertex V starts at zero");
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
		GpuTerrainMesh mesh = GpuSceneUploader.buildTerrain(scene);
		test.equal(mesh.triangleCount(), shaped.triangleVertexA.length, "GPU shaped tile triangle count");
		test.equal(mesh.vertexCount(), shaped.triangleVertexA.length * 3, "GPU shaped tile vertex count");

		scene.tiles[0][0][0].logicHeight = 1;
		GpuTerrainMesh planeAware = GpuSceneUploader.buildTerrain(scene);
		test.equal(planeAware.triangleCount(), shaped.triangleVertexA.length,
				"render-plane-independent terrain cache retains roof-hidden geometry once");
		test.equal(eligibleTriangles(planeAware, 0), 0, "plane 0 submission omits logic-height-1 terrain ranges");
		test.equal(eligibleTriangles(planeAware, 1), shaped.triangleVertexA.length,
				"plane 1 submission includes logic-height-1 terrain ranges");
	}

	private static void testChunkFrustumVisibility(SelfTestSupport test) {
		Scene scene = sceneForVisibility();
		WorldRenderFrame frame = new WorldRenderFrame(scene, 512, 512, -100, 0, 0, 0, 0, 0, 0, 0, 512, 334);
		GpuSceneChunk inFront = new GpuSceneChunk(0, 3, 448, -128, 896, 576, 128, 1024);
		GpuSceneChunk behind = new GpuSceneChunk(0, 3, 448, -128, 0, 576, 128, 128);
		GpuSceneChunk farSide = new GpuSceneChunk(0, 3, 4096, -128, 896, 4224, 128, 1024);

		test.check(GpuSceneVisibility.isVisible(inFront, frame, 512, 334),
				"chunk intersecting the camera frustum remains visible");
		test.check(!GpuSceneVisibility.isVisible(behind, frame, 512, 334),
				"chunk completely behind the camera is culled");
		test.check(!GpuSceneVisibility.isVisible(farSide, frame, 512, 334),
				"chunk completely outside the horizontal frustum is culled");
	}

	private static int eligibleTriangles(GpuTerrainMesh mesh, int renderPlane) {
		int vertices = 0;
		for (GpuSceneChunk chunk : mesh.chunks()) {
			if (chunk.visibleOnPlane(renderPlane)) {
				vertices += chunk.vertexCount();
			}
		}
		return vertices / 3;
	}

	private static Scene sceneForVisibility() {
		return new Scene(new int[1][9][9], 1, 8, 8);
	}

	private static void addPlainTile(Scene scene, int x, int y, int colour) {
		scene.addTile(0, x, y, 0, 0, -1, 0, 0, 0, 0, colour, colour, colour, colour, 0, 0, 0, 0, 0, 0);
	}
}
