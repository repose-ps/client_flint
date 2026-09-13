package rs2.tools;

/**
 * Convenience entry point for the validation target of the current renderer
 * development phase.
 *
 * <p>Keep this class stable as the Eclipse run target. Development patches can
 * update the current native/phase-specific test invoked here without requiring
 * a new run configuration each time.</p>
 */
public final class CurrentTest {

    private CurrentTest() {
    }

    /**
     * Runs the permanent cache-free regression gate followed by the current
     * native renderer validations.
     *
     * @param args optional revision-377 cache directory passed to ClientSelfTest
     * @throws Exception if the permanent regression gate fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("CurrentTest: Phase 4B static model textures");
        ClientSelfTest.main(args);
        GpuTerrainTextureSelfTest.main(new String[0]);
        GpuStaticTextureSelfTest.main(new String[0]);
        System.out.println("CurrentTest: PASS");
    }
}
