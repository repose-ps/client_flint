package rs2.tools;

import rs2.game.CameraController;
import rs2.game.WorldState;
import rs2.game.render.ScenePicker;
import rs2.media.model.Model;
import rs2.media.model.Renderable;
import rs2.scene.Scene;
import rs2.scene.SceneUid;

/** Cache-free checks for fixed-rate world interaction picking. */
public final class ScenePickerSelfTest {

    private static final int VIEWPORT_WIDTH = 320;
    private static final int VIEWPORT_HEIGHT = 240;
    private static final int CAMERA_X = 8 * 128 + 64;
    private static final int CAMERA_Y = 4 * 128 + 64;
    private static final int CAMERA_HEIGHT = -500;

    private ScenePickerSelfTest() {
    }

    /** Runs the picking regression checks and returns the assertion count. */
    public static int run() {
        SelfTestSupport test = new SelfTestSupport();
        testTerrainWalkPick(test);
        testRenderPlaneFiltering(test);
        testStaticModelHover(test);
        testGpuDeferredRenderableFiltering(test);
        return test.checks();
    }

    public static void main(String[] args) {
        int checks = run();
        System.out.println("ScenePickerSelfTest: PASS (" + checks + " checks, cache-free)");
    }

    private static void testTerrainWalkPick(SelfTestSupport test) {
        WorldState world = new WorldState(null);
        addPlainTile(world.scene, 0, 8, 8);
        CameraController camera = camera();
        ScenePicker picker = new ScenePicker();

        world.scene.setClick(VIEWPORT_WIDTH / 2, VIEWPORT_HEIGHT / 2);
        test.check(world.scene.tilePickPending(), "terrain walk pick queues until fixed interaction tick");
        picker.update(world, null, null, 0, 0, camera, VIEWPORT_WIDTH / 2, VIEWPORT_HEIGHT / 2, 0, 0,
                VIEWPORT_WIDTH, VIEWPORT_HEIGHT, false);

        test.check(!world.scene.tilePickPending(), "terrain walk pick is consumed exactly once");
        test.equal(Scene.pickedTileX, 8, "center terrain ray resolves expected tile X");
        test.equal(Scene.pickedTileY, 8, "center terrain ray resolves expected tile Y");
    }

    private static void testRenderPlaneFiltering(SelfTestSupport test) {
        WorldState world = new WorldState(null);
        addPlainTile(world.scene, 0, 8, 8);
        world.scene.tiles[0][8][8].logicHeight = 1;
        CameraController camera = camera();
        ScenePicker picker = new ScenePicker();

        world.scene.setClick(VIEWPORT_WIDTH / 2, VIEWPORT_HEIGHT / 2);
        picker.update(world, null, null, 0, 0, camera, VIEWPORT_WIDTH / 2, VIEWPORT_HEIGHT / 2, 0, 0,
                VIEWPORT_WIDTH, VIEWPORT_HEIGHT, false);
        test.equal(Scene.pickedTileX, -1, "terrain above render plane is not walk-picked");

        world.scene.setClick(VIEWPORT_WIDTH / 2, VIEWPORT_HEIGHT / 2);
        picker.update(world, null, null, 0, 1, camera, VIEWPORT_WIDTH / 2, VIEWPORT_HEIGHT / 2, 0, 0,
                VIEWPORT_WIDTH, VIEWPORT_HEIGHT, false);
        test.equal(Scene.pickedTileX, 8, "terrain becomes pickable when render plane includes it");
        test.equal(Scene.pickedTileY, 8, "render-plane terrain pick preserves tile Y");
    }

    private static void testStaticModelHover(SelfTestSupport test) {
        WorldState world = new WorldState(null);
        Model model = verticalTriangleModel();
        int uid = 8 | (8 << SceneUid.TILE_Y_SHIFT) | (123 << SceneUid.ENTITY_ID_SHIFT)
                | SceneUid.OBJECT_TYPE_BITS;
        world.scene.addFloorDecoration(0, 8, 8, 0, uid, (byte) 0, model);
        CameraController camera = camera();
        ScenePicker picker = new ScenePicker();

        picker.update(world, null, null, 0, 0, camera, VIEWPORT_WIDTH / 2, 90, 0, 0, VIEWPORT_WIDTH,
                VIEWPORT_HEIGHT, false);
        test.equal(Model.pickedCount, 1, "static model hover is resolved without software rasterization");
        test.equal(Model.pickedUids[0], uid, "static model hover preserves packed scene UID");
    }


    private static void testGpuDeferredRenderableFiltering(SelfTestSupport test) {
        WorldState world = new WorldState(null);
        Model model = verticalTriangleModel();
        Renderable deferred = new Renderable() {
            @Override
            protected Model getModel() {
                return model;
            }
        };
        int uid = 8 | (8 << SceneUid.TILE_Y_SHIFT) | (124 << SceneUid.ENTITY_ID_SHIFT)
                | SceneUid.OBJECT_TYPE_BITS;
        world.scene.addFloorDecoration(0, 8, 8, 0, uid, (byte) 0, deferred);
        CameraController camera = camera();
        ScenePicker picker = new ScenePicker();

        picker.update(world, null, null, 0, 0, camera, VIEWPORT_WIDTH / 2, 90, 0, 0, VIEWPORT_WIDTH,
                VIEWPORT_HEIGHT, true);
        test.equal(Model.pickedCount, 0, "GPU picker excludes renderables deferred by the static-only backend");

        picker.update(world, null, null, 0, 0, camera, VIEWPORT_WIDTH / 2, 90, 0, 0, VIEWPORT_WIDTH,
                VIEWPORT_HEIGHT, false);
        test.equal(Model.pickedCount, 1, "software picker retains dynamic/deferred renderable interaction");
        test.equal(Model.pickedUids[0], uid, "deferred renderable preserves packed UID when backend draws it");
    }

    private static CameraController camera() {
        CameraController camera = new CameraController();
        camera.x = CAMERA_X;
        camera.y = CAMERA_Y;
        camera.height = CAMERA_HEIGHT;
        camera.yaw = 0;
        camera.pitch = 256;
        return camera;
    }

    private static void addPlainTile(Scene scene, int plane, int x, int y) {
        int colour = 0x3200;
        scene.addTile(plane, x, y, 0, 0, -1, 0, 0, 0, 0, colour, colour, colour, colour, 0, 0, 0, 0, 0, 0);
    }

    private static Model verticalTriangleModel() {
        Model model = new Model(0, new Model[0]);
        model.vertexCount = 3;
        model.verticesX = new int[] { -160, 160, 0 };
        model.verticesY = new int[] { 0, 0, -260 };
        model.verticesZ = new int[] { 0, 0, 0 };
        model.triangleCount = 1;
        model.triangleVertexA = new int[] { 0 };
        model.triangleVertexB = new int[] { 1 };
        model.triangleVertexC = new int[] { 2 };
        model.triangleDrawType = null;
        model.calculateDiagonals();
        return model;
    }
}
