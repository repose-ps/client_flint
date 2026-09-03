package rs2.tools;

import rs2.game.CameraController;
import rs2.shell.FrameTimingConfig;

/** Regression checks for decoupled render-rate configuration and camera interpolation. */
public final class FrameTimingSelfTest {

    private FrameTimingSelfTest() {
    }

    public static void main(String[] args) {
        int checks = run();
        System.out.println("FrameTimingSelfTest: PASS (" + checks + " checks, cache-free)");
    }

    public static int run() {
        SelfTestSupport test = new SelfTestSupport();
        testConfiguration(test);
        testCameraInterpolation(test);
        return test.checks();
    }

    private static void testConfiguration(SelfTestSupport test) {
        String old = System.getProperty(FrameTimingConfig.RENDER_FPS_PROPERTY);
        try {
            System.clearProperty(FrameTimingConfig.RENDER_FPS_PROPERTY);
            test.equal(FrameTimingConfig.configuredRenderFps(120), 120, "render FPS default");

            System.setProperty(FrameTimingConfig.RENDER_FPS_PROPERTY, "144");
            test.equal(FrameTimingConfig.configuredRenderFps(120), 144, "render FPS integer override");

            System.setProperty(FrameTimingConfig.RENDER_FPS_PROPERTY, "legacy");
            test.equal(FrameTimingConfig.configuredRenderFps(120), 50, "render FPS legacy alias");

            System.setProperty(FrameTimingConfig.RENDER_FPS_PROPERTY, "unlimited");
            test.equal(FrameTimingConfig.configuredRenderFps(120), 0, "render FPS unlimited alias");

            System.setProperty(FrameTimingConfig.RENDER_FPS_PROPERTY, "uncapped");
            test.equal(FrameTimingConfig.configuredRenderFps(120), 0, "render FPS uncapped alias");

            System.setProperty(FrameTimingConfig.RENDER_FPS_PROPERTY, "display");
            test.equal(FrameTimingConfig.configuredRenderFps(120, 165), 165, "render FPS display refresh alias");
            test.equal(FrameTimingConfig.configuredRenderFps(120, 0), 120, "render FPS display fallback");

            System.setProperty(FrameTimingConfig.RENDER_FPS_PROPERTY, "monitor");
            test.equal(FrameTimingConfig.configuredRenderFps(120, 144), 144, "render FPS monitor alias");

            boolean rejected = false;
            System.setProperty(FrameTimingConfig.RENDER_FPS_PROPERTY, "0");
            try {
                FrameTimingConfig.configuredRenderFps(120);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            test.check(rejected, "render FPS rejects numeric zero");
        } finally {
            if (old == null) {
                System.clearProperty(FrameTimingConfig.RENDER_FPS_PROPERTY);
            } else {
                System.setProperty(FrameTimingConfig.RENDER_FPS_PROPERTY, old);
            }
        }
    }

    private static void testCameraInterpolation(SelfTestSupport test) {
        CameraController camera = new CameraController();
        camera.x = 100;
        camera.height = -300;
        camera.y = 500;
        camera.pitch = 200;
        camera.yaw = 2040;
        camera.finishLogicCycle();

        camera.beginLogicCycle();
        camera.x = 200;
        camera.height = -100;
        camera.y = 700;
        camera.pitch = 300;
        camera.yaw = 8;

        CameraController.Snapshot halfway = camera.interpolatedSnapshot(0.5f);
        camera.restore(halfway);
        test.equal(camera.x, 150, "camera X interpolation");
        test.equal(camera.height, -200, "camera height interpolation");
        test.equal(camera.y, 600, "camera Y interpolation");
        test.equal(camera.pitch, 250, "camera pitch interpolation");
        test.equal(camera.yaw, 0, "camera yaw shortest-path interpolation");
    }
}
