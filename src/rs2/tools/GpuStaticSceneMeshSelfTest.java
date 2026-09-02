package rs2.tools;

import rs2.gpu.GpuSceneUploader;
import rs2.gpu.GpuStaticSceneMesh;
import rs2.media.Angle;
import rs2.media.Rasterizer3D;
import rs2.media.model.Model;
import rs2.media.model.Renderable;
import rs2.scene.Scene;

/** Cache-free structural checks for Phase-3 static scene model uploading. */
public final class GpuStaticSceneMeshSelfTest {

	private GpuStaticSceneMeshSelfTest() {
	}

	/** Runs the static model upload checks and returns the assertion count. */
	public static int run() {
		SelfTestSupport test = new SelfTestSupport();
		Rasterizer3D.setBrightness(0.8D);
		testFixturesAndInteractiveDedup(test);
		testModelWorldTransform(test);
		testEmptyOptionalFaceArrays(test);
		testPlaneFilteringAndDynamicDeferral(test);
		testStaticGeometryRevision(test);
		return test.checks();
	}

	public static void main(String[] args) {
		int checks = run();
		System.out.println("GpuStaticSceneMeshSelfTest: PASS (" + checks + " checks, cache-free)");
	}

	private static void testFixturesAndInteractiveDedup(SelfTestSupport test) {
		Scene scene = scene(4, 4, 1);
		Model model = triangleModel();
		scene.addWall(0, 1, 1, 0, 1, (byte) 0, model, null, 1, 0);
		scene.addFloorDecoration(0, 2, 1, 0, 2, (byte) 0, model);
		scene.addWallDecoration(0, 2, 2, 0, 0, 0, 0, 3, (byte) 0, 1, model);
		test.check(scene.addGameObject(0, 0, 2, 2, 2, 0, model, 0, 4, (byte) 0),
				"static multi-tile game object inserted");

		GpuStaticSceneMesh mesh = GpuSceneUploader.buildStaticGeometry(scene);
		test.equal(mesh.instanceCount(), 4, "static uploader includes walls/decorations/objects once");
		test.equal(mesh.triangleCount(), 4, "one triangle emitted per synthetic model instance");
		test.equal(mesh.vertexCount(), 12, "static triangles are unindexed GPU vertices");
		test.equal(mesh.skippedDynamicCount(), 0, "all fixture renderables are static Models");
		test.equal(mesh.uniqueModelCount(), 1, "shared static Model identity is measured once for reuse profiling");
		test.equal(mesh.byteSize(), mesh.vertexCount() * 16, "static geometry uses compact 16-byte core vertices");
		test.equal(mesh.chunks().length, 1, "small static fixture occupies one eight-tile draw chunk");
		test.equal(mesh.chunks()[0].vertexCount(), mesh.vertexCount(), "static chunk range covers the fixture VBO");
	}

	private static void testModelWorldTransform(SelfTestSupport test) {
		Scene scene = scene(3, 3, 1);
		Model model = triangleModel();
		int originX = 1 * 128 + 64;
		int originY = 1 * 128 + 64;
		test.check(scene.addGameObject(0, 1, 1, 1, 1, -32, model, Angle.QUARTER_TURN, 5, (byte) 0),
				"rotated static game object inserted");

		GpuStaticSceneMesh mesh = GpuSceneUploader.buildStaticGeometry(scene);
		int[] vertices = mesh.vertices();
		test.equal((int) vertices[0], originX, "quarter-turn rotates first local X into world origin X");
		test.equal((int) vertices[1], -32, "model local Y is translated by scene draw height");
		test.equal((int) vertices[2], originY - 64, "quarter-turn rotates local X onto negative world Y");
	}

	private static void testEmptyOptionalFaceArrays(SelfTestSupport test) {
		Scene scene = scene(2, 2, 1);
		Model model = triangleModel();
		model.triangleColors = new int[0];
		model.triangleDrawType = new int[0];
		model.triangleAlpha = new int[0];
		scene.addFloorDecoration(0, 0, 0, 0, 10, (byte) 0, model);

		GpuStaticSceneMesh mesh = GpuSceneUploader.buildStaticGeometry(scene);
		test.equal(mesh.instanceCount(), 1, "empty optional face arrays fall back instead of aborting upload");
		test.equal(mesh.triangleCount(), 1, "empty optional face arrays still emit valid geometry");
	}

	private static void testPlaneFilteringAndDynamicDeferral(SelfTestSupport test) {
		Scene scene = scene(2, 2, 1);
		scene.addFloorDecoration(0, 0, 0, 0, 6, (byte) 0, triangleModel());
		scene.addFloorDecoration(0, 1, 0, 0, 7, (byte) 0, new Renderable());
		scene.tiles[0][0][0].logicHeight = 1;

		GpuStaticSceneMesh cached = GpuSceneUploader.buildStaticGeometry(scene);
		test.equal(cached.instanceCount(), 1, "render-plane-independent static cache stores the model once");
		test.equal(eligibleTriangles(cached, 0), 0, "plane 0 submission hides static model above render plane");
		test.equal(eligibleTriangles(cached, 1), 1, "plane 1 submission includes logic-height-1 static model");
		test.equal(cached.skippedDynamicCount(), 1, "non-Model scene renderables remain deferred");
	}

	private static int eligibleTriangles(GpuStaticSceneMesh mesh, int renderPlane) {
		int vertices = 0;
		for (rs2.gpu.GpuSceneChunk chunk : mesh.chunks()) {
			if (chunk.visibleOnPlane(renderPlane)) {
				vertices += chunk.vertexCount();
			}
		}
		return vertices / 3;
	}

	private static void testStaticGeometryRevision(SelfTestSupport test) {
		Scene scene = scene(4, 4, 1);
		long before = scene.geometryRevision();
		scene.addFloorDecoration(0, 1, 1, 0, 8, (byte) 0, triangleModel());
		test.check(scene.geometryRevision() > before, "static floor decoration invalidates GPU scene mesh");

		long afterStatic = scene.geometryRevision();
		scene.addEntity(0, 128, 128, 0, new Renderable(), 9, 16, false, 0);
		test.equal(scene.geometryRevision(), afterStatic, "temporary actor/entity insertion does not rebuild static GPU mesh");
		scene.clearTemporaryObjects();
		test.equal(scene.geometryRevision(), afterStatic, "temporary entity cleanup does not rebuild static GPU mesh");

		scene.removeFloorDecoration(0, 1, 1);
		test.check(scene.geometryRevision() > afterStatic, "static fixture removal invalidates GPU scene mesh");
	}

	private static Scene scene(int width, int height, int planes) {
		return new Scene(new int[planes][width + 1][height + 1], planes, width, height);
	}

	private static Model triangleModel() {
		Model model = new Model(0, new Model[0]);
		model.vertexCount = 3;
		model.verticesX = new int[] { 64, -64, 0 };
		model.verticesY = new int[] { 0, 0, -96 };
		model.verticesZ = new int[] { 0, 0, 0 };
		model.triangleCount = 1;
		model.triangleVertexA = new int[] { 0 };
		model.triangleVertexB = new int[] { 1 };
		model.triangleVertexC = new int[] { 2 };
		model.triangleShadeA = new int[] { 0x3200 };
		model.triangleShadeB = new int[] { 0x3210 };
		model.triangleShadeC = new int[] { 0x3220 };
		model.triangleDrawType = null;
		model.triangleAlpha = null;
		model.triangleColors = null;
		return model;
	}
}
