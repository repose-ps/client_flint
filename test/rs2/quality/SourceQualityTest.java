package rs2.quality;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Guards the source-hygiene and preservation documentation established in Refinement Step 8. */
public final class SourceQualityTest {

    private static final Pattern STALE_METHOD_NAME = Pattern.compile("\\bmethod\\d{2,}\\b");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("(?m)[ \\t]+$");
    private static int checks;

    private SourceQualityTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        checkLineEndingsAndWhitespace(root.resolve("src"));
        checkLineEndingsAndWhitespace(root.resolve("test"));
        checkNoMigrationBreadcrumbs(root.resolve("src"));
        checkPreservationDocumentation(root);
        checkLineEndingPolicy(root);

        System.out.println("SourceQualityTest: PASS (" + checks + " checks)");
    }

    private static void checkLineEndingsAndWhitespace(Path sourceRoot) throws IOException {
        for (Path file : javaFiles(sourceRoot)) {
            byte[] bytes = Files.readAllBytes(file);
            String text = new String(bytes, StandardCharsets.UTF_8);
            check(indexOf(bytes, (byte) '\r') == -1, file + " uses LF line endings");
            check(text.endsWith("\n"), file + " ends with a newline");
            check(!TRAILING_WHITESPACE.matcher(text).find(), file + " has no trailing whitespace");
        }
    }

    private static void checkNoMigrationBreadcrumbs(Path sourceRoot) throws IOException {
        for (Path file : javaFiles(sourceRoot)) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            check(!STALE_METHOD_NAME.matcher(text).find(), file + " has no stale methodNN identifiers");
            check(!text.contains("Initializes this instance."), file + " has no generated constructor boilerplate");
        }
    }

    private static void checkPreservationDocumentation(Path root) throws IOException {
        Path deadState = root.resolve("DEAD_STATE.md");
        check(Files.isRegularFile(deadState), "DEAD_STATE.md exists");
        String text = Files.readString(deadState, StandardCharsets.UTF_8);
        check(text.contains("Widget type 1 (`Widget.TYPE_UNKNOWN`)"), "type-1 widget uncertainty is classified");
        check(text.contains("Missing `headicons,0` widget sprite"), "missing widget sprite behavior is classified");
        check(text.contains("Sound track 1592 loop bounds"), "sound 1592 anomaly is classified");
        check(text.contains("Apparent inactivity alone is insufficient"), "dead-state deletion rule is documented");
    }

    private static void checkLineEndingPolicy(Path root) throws IOException {
        Path attributes = root.resolve(".gitattributes");
        check(Files.isRegularFile(attributes), ".gitattributes exists");
        String text = Files.readString(attributes, StandardCharsets.UTF_8);
        check(text.contains("*.java text eol=lf"), "Java LF policy is declared");
        check(text.contains("*.cmd text eol=lf"), "Windows command-file LF policy is declared");
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).sorted().forEach(files::add);
        }
        return files;
    }

    private static int indexOf(byte[] bytes, byte value) {
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == value) {
                return index;
            }
        }
        return -1;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
