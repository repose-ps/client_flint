package rs2;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs every regression suite retained in the Step-6 source tree in an isolated JVM. */
public final class RegressionSuite {

    private static final String[] CACHE_FILES = {
            "main_file_cache.dat",
            "main_file_cache.idx0",
            "main_file_cache.idx1",
            "main_file_cache.idx2",
            "main_file_cache.idx3",
            "main_file_cache.idx4"
    };

    private RegressionSuite() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: RegressionSuite <revision-377-rscache-directory>");
        }

        Path cache = Path.of(args[0]).toAbsolutePath().normalize();
        validateCacheFixture(cache);

        List<Suite> suites = List.of(
                new Suite("rs2.concurrent.ThreadLifecycleSafetyTest"),
                new Suite("rs2.net.NetworkResourceRobustnessTest"),
                new Suite("rs2.cache.Revision377CacheSmokeTest", cache.toString()),
                new Suite("rs2.cache.BootstrapArchiveRecoveryTest", cache.toString()),
                new Suite("rs2.cache.Revision377OnDemandDecompressionTest", cache.toString()));

        for (Suite suite : suites) {
            runSuite(suite);
        }

        System.out.println("RegressionSuite: PASS (" + suites.size() + " suites)");
    }

    private static void validateCacheFixture(Path cache) {
        if (!Files.isDirectory(cache)) {
            throw new IllegalArgumentException("Revision-377 cache directory does not exist: " + cache);
        }
        for (String file : CACHE_FILES) {
            if (!Files.isRegularFile(cache.resolve(file))) {
                throw new IllegalArgumentException("Revision-377 cache fixture is missing " + file + ": " + cache);
            }
        }
    }

    private static void runSuite(Suite suite) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", executableName("java"));
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.add("-Djava.awt.headless=true");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(suite.className);
        command.addAll(suite.arguments);

        Process process = new ProcessBuilder(command)
                .directory(new File(System.getProperty("user.dir")))
                .inheritIO()
                .start();
        int exit = process.waitFor();
        if (exit != 0) {
            throw new AssertionError(suite.className + " failed with exit code " + exit);
        }
    }

    private static String executableName(String command) {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? command + ".exe" : command;
    }

    private static final class Suite {
        private final String className;
        private final List<String> arguments;

        private Suite(String className, String... arguments) {
            this.className = className;
            this.arguments = List.of(arguments);
        }
    }
}
