package rs2.tools;

import java.nio.ByteOrder;

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
		testHalfTurnModelWorldTransform(test);
		testOpaquePrioritySubmissionOrder(test);
		testWallDecorationPriorityMetadata(test);
		testEmptyOptionalFaceArrays(test);
		testTexturedModelStream(test);
		testTextureReferenceSelectionAndTransform(test);
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

	private static void testHalfTurnModelWorldTransform(SelfTestSupport test) {
		Scene scene = scene(3, 3, 1);
		Model model = triangleModel();
		int originX = 1 * 128 + 64;
		int originY = 1 * 128 + 64;
		test.check(scene.addGameObject(0, 1, 1, 1, 1, -32, model, Angle.HALF_TURN, 15, (byte) 0),
				"half-turn static game object inserted");

		GpuStaticSceneMesh mesh = GpuSceneUploader.buildStaticGeometry(scene);
		int[] vertices = mesh.vertices();
		test.equal((int) vertices[0], originX - 64, "half-turn negates local X instead of treating 1024 as zero");
		test.equal((int) vertices[1], -32, "half-turn preserves model local Y translation");
		test.equal((int) vertices[2], originY, "half-turn negates local Z around the world origin");
	}

	private static void testOpaquePrioritySubmissionOrder(SelfTestSupport test) {
		Scene scene = scene(2, 2, 1);
		Model model = new Model(0, new Model[0]);
		model.vertexCount = 6;
		model.verticesX = new int[] { 10, 20, 30, 110, 120, 130 };
		model.verticesY = new int[] { 0, -16, 0, 0, -16, 0 };
		model.verticesZ = new int[] { 0, 32, 64, 0, 32, 64 };
		model.triangleCount = 2;
		model.triangleVertexA = new int[] { 0, 3 };
		model.triangleVertexB = new int[] { 1, 4 };
		model.triangleVertexC = new int[] { 2, 5 };
		model.triangleShadeA = new int[] { 0x3200, 0x3300 };
		model.triangleShadeB = new int[] { 0x3210, 0x3310 };
		model.triangleShadeC = new int[] { 0x3220, 0x3320 };
		model.trianglePriorities = new int[] { 9, 1 };
		model.defaultTrianglePriority = 0;

		scene.addFloorDecoration(0, 0, 0, 0, 18, (byte) 0, model);
		GpuStaticSceneMesh mesh = GpuSceneUploader.buildStaticGeometry(scene);
		int[] vertices = mesh.vertices();
		int worldX = 64;
		test.equal(vertices[0], worldX + 10, "priority metadata keeps original cached face order");
		test.equal(packedAlpha(vertices[3]), 0x80 | 9,
				"floor decoration marks legacy priority 9 metadata with floor-class bit");
		test.equal(packedAlpha(vertices[15]), 0x80 | 1,
				"floor decoration marks legacy priority 1 metadata with floor-class bit");
	}

	private static void testWallDecorationPriorityMetadata(SelfTestSupport test) {
		Scene scene = scene(2, 2, 1);
		Model model = triangleModel();
		model.trianglePriorities = new int[] { 3 };
		scene.addWallDecoration(0, 0, 0, 0, 0, 0, 0, 19, (byte) 0, 1, model);

		GpuStaticSceneMesh mesh = GpuSceneUploader.buildStaticGeometry(scene);
		test.equal(packedAlpha(mesh.vertices()[3]), 0x40 | 3,
				"wall decoration marks legacy priority 3 metadata with wall-decoration class bit");
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


	private static void testTexturedModelStream(SelfTestSupport test) {
		Scene scene = scene(3, 3, 1);
		Model model = texturedTriangleModel();
		model.trianglePriorities = new int[] { 6 };
		scene.addFloorDecoration(0, 1, 1, 0, 11, (byte) 0, model);

		GpuStaticSceneMesh mesh = GpuSceneUploader.buildStaticGeometry(scene);
		test.equal(mesh.triangleCount(), 1, "textured fixture contributes one static triangle");
		test.equal(mesh.texturedTriangleCount(), 1, "valid model texture mapping enters projected texture stream");
		test.equal(mesh.untexturedTriangleCount(), 0, "textured fixture does not duplicate solid fallback geometry");
		test.equal(mesh.texturedVertexCount(), 3, "textured static face keeps compact three-vertex stream");
		test.equal(mesh.untexturedVertexCount(), 0, "textured-only fixture leaves solid stream empty");
		test.equal(mesh.texturedChunks().length, 1, "textured static fixture has its own culled draw range");
		test.equal(mesh.textureMappings().length, 12, "one textured triangle stores three ivec4 mapping records");
		test.equal(mesh.textureMappings()[3], 0, "texture mapping record preserves texture layer zero");
		test.equal(mesh.mappingByteSize(), 48, "projective texture mapping costs 48 bytes per textured triangle");
		test.equal(packedAlpha(mesh.texturedVertices()[3]), 0x80 | 6,
				"textured floor decoration carries marked legacy priority metadata in shade alpha");

		Model highAlphaTextured = texturedTriangleModel();
		highAlphaTextured.triangleAlpha = new int[] { 255 };
		Scene highAlphaTexturedScene = scene(2, 2, 1);
		highAlphaTexturedScene.addFloorDecoration(0, 0, 0, 0, 14, (byte) 0, highAlphaTextured);
		GpuStaticSceneMesh highAlphaTextureMesh = GpuSceneUploader.buildStaticGeometry(highAlphaTexturedScene);
		test.equal(highAlphaTextureMesh.texturedTriangleCount(), 1,
				"textured faces retain legacy rendering even when triangle alpha is 255");

		Model highAlphaSolid = triangleModel();
		highAlphaSolid.triangleAlpha = new int[] { 255 };
		Scene highAlphaSolidScene = scene(2, 2, 1);
		highAlphaSolidScene.addFloorDecoration(0, 0, 0, 0, 15, (byte) 0, highAlphaSolid);
		GpuStaticSceneMesh highAlphaSolidMesh = GpuSceneUploader.buildStaticGeometry(highAlphaSolidScene);
		test.equal(highAlphaSolidMesh.triangleCount(), 0,
				"near-invisible alpha-255 Gouraud faces retain the existing upload shortcut");

		Model malformed = texturedTriangleModel();
		malformed.texturedTriangleA = new int[0];
		Scene fallbackScene = scene(2, 2, 1);
		fallbackScene.addFloorDecoration(0, 0, 0, 0, 12, (byte) 0, malformed);
		GpuStaticSceneMesh fallback = GpuSceneUploader.buildStaticGeometry(fallbackScene);
		test.equal(fallback.texturedTriangleCount(), 0, "malformed texture mapping falls back instead of entering GPU projection");
		test.equal(fallback.untexturedTriangleCount(), 1, "malformed textured face retains average-color fallback geometry");
	}

	private static void testTextureReferenceSelectionAndTransform(SelfTestSupport test) {
		Scene scene = scene(3, 3, 1);
		Model model = texturedTriangleModel();
		model.vertexCount = 6;
		model.verticesX = new int[] { 64, -64, 0, 16, 80, 16 };
		model.verticesY = new int[] { 0, 0, -96, -16, -16, -80 };
		model.verticesZ = new int[] { 0, 0, 0, 24, 24, 88 };
		model.triangleDrawType = new int[] { 6 }; // draw type 2, texture-reference triangle 1
		model.texturedTriangleCount = 2;
		model.texturedTriangleA = new int[] { 0, 3 };
		model.texturedTriangleB = new int[] { 1, 4 };
		model.texturedTriangleC = new int[] { 2, 5 };

		int originX = 1 * 128 + 64;
		int originY = 1 * 128 + 64;
		test.check(scene.addGameObject(0, 1, 1, 1, 1, -32, model, Angle.QUARTER_TURN, 13, (byte) 0),
				"textured rotated game object inserted");

		GpuStaticSceneMesh mesh = GpuSceneUploader.buildStaticGeometry(scene);
		int[] mapping = mesh.textureMappings();
		test.equal(mesh.texturedTriangleCount(), 1, "draw-type texture reference selects one mapped face");
		test.equal(mapping.length, 12, "selected texture reference emits exactly one mapping record");
		test.equal(mapping[0], originX + 23, "mapping A uses referenced vertex after quarter-turn X transform");
		test.equal(mapping[1], -48, "mapping A preserves referenced vertex height translation");
		test.equal(mapping[2], originY - 16, "mapping A uses referenced vertex after quarter-turn Y transform");
		test.equal(mapping[3], 0, "mapping A carries the model texture id");
		test.equal(mapping[4], originX + 23, "mapping B uses texture-reference triangle index rather than face B");
		test.equal(mapping[5], -48, "mapping B preserves referenced vertex height");
		test.equal(mapping[6], originY - 80, "mapping B rotates the referenced local X into world Y");
		test.equal(mapping[8], originX + 87, "mapping C rotates referenced local Z into world X");
		test.equal(mapping[9], -112, "mapping C preserves referenced vertex height");
		test.equal(mapping[10], originY - 16, "mapping C rotates referenced local X into world Y");
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
		int vertices = eligibleVertices(mesh.chunks(), renderPlane);
		vertices += eligibleVertices(mesh.texturedChunks(), renderPlane);
		return vertices / 3;
	}

	private static int eligibleVertices(rs2.gpu.GpuSceneChunk[] chunks, int renderPlane) {
		int vertices = 0;
		for (rs2.gpu.GpuSceneChunk chunk : chunks) {
			if (chunk.visibleOnPlane(renderPlane)) {
				vertices += chunk.vertexCount();
			}
		}
		return vertices;
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
	private static int packedAlpha(int packedRgba) {
		return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? packedRgba >>> 24 & 0xff : packedRgba & 0xff;
	}

	private static Model texturedTriangleModel() {
		Model model = triangleModel();
		model.triangleShadeA = new int[] { 48 };
		model.triangleShadeB = new int[] { 64 };
		model.triangleShadeC = new int[] { 80 };
		model.triangleDrawType = new int[] { 2 };
		model.triangleColors = new int[] { 0 };
		model.texturedTriangleCount = 1;
		model.texturedTriangleA = new int[] { 0 };
		model.texturedTriangleB = new int[] { 1 };
		model.texturedTriangleC = new int[] { 2 };
		return model;
	}

}
