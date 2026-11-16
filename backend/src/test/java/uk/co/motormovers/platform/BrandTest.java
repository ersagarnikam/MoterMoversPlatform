package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import uk.co.motormovers.platform.common.Brand;

/**
 * The platform is the Professor Pat Preedy Programme (go-live task 84). Nothing the API writes for people (emails,
 * PDFs, notifications, messages, starter and demo content) may still say "Motor Movers", and each name is written
 * once, in {@link Brand}. Code names are not the brand and are allowed: the package {@code motormovers}, the demo
 * domains and the applied migrations, which are never edited.
 */
class BrandTest {

    private static final Path MAIN = Path.of("src", "main");
    /** The parent app, beside the API in the repository: it has no test runner of its own, so it is checked here. */
    private static final Path APP = Path.of("..", "mobile");
    private static final Pattern OLD_NAME = Pattern.compile("motor[ -]?movers", Pattern.CASE_INSENSITIVE);
    /** Code names that hold the old name: the package, the main class, the token issuer, the app and its folder. */
    private static final List<String> CODE_NAMES = List.of("motormovers", "MotorMoversApplication",
            "issuer(\"motor-movers\")", "name: motor-movers-platform", "/motor-movers-files}",
            "\"slug\": \"motor-movers-parent\"");

    @Test
    void nothingTheApiWritesSaysMotorMovers() throws IOException {
        assertThat(linesWith(MAIN, line -> OLD_NAME.matcher(withoutCodeNames(line)).find())).isEmpty();
    }

    @Test
    void nothingInTheParentAppSaysMotorMovers() throws IOException {
        assumeTrue(Files.isDirectory(APP.resolve("src")), "the parent app's sources are not here");
        assertThat(linesWith(APP.resolve("src"), line -> OLD_NAME.matcher(withoutCodeNames(line)).find())).isEmpty();
        assertThat(linesWith(APP.resolve("src"), line -> line.contains("Pat Preedy Programme")))
                .as("use BRAND from lib/brand.ts")
                .allMatch(found -> found.startsWith("brand.ts"));
        String config = Files.readString(APP.resolve("app.json"));
        assertThat(config).as("the store name").contains("\"name\": \"" + Brand.NAME + "\"");
        assertThat(OLD_NAME.matcher(withoutCodeNames(config)).find()).isFalse();
        assertThat(Files.readString(APP.resolve("src/lib/brand.ts"))).contains("BRAND = '" + Brand.NAME + "'");
        assertThat(Brand.NAME.length()).as("Apple's limit for an app's name").isLessThanOrEqualTo(30);
    }

    @Test
    void theNameInFullIsWrittenOnce() throws IOException {
        assertThat(linesWith(MAIN, line -> line.contains("Pat Preedy Programme")))
                .as("use Brand.NAME; application.yml holds only the default sender name")
                .allMatch(found -> found.startsWith("Brand.java") || found.startsWith("application.yml"));
        assertThat(Brand.NAME).isEqualTo("Professor Pat Preedy Programme");
    }

    @Test
    void numbersStartWithTheNewPrefix() throws IOException {
        Pattern oldPrefix = Pattern.compile("\\bMM-\\d|MM-\"");
        assertThat(linesWith(MAIN, line -> oldPrefix.matcher(line).find())).as("use Brand.REFERENCE_PREFIX").isEmpty();
        assertThat(linesWith(MAIN, line -> line.contains("\"PPP-")))
                .allMatch(found -> found.startsWith("Brand.java"));
        assertThat(Brand.REFERENCE_PREFIX).isEqualTo("PPP-");
    }

    @Test
    void theHomeProgrammesNameIsWrittenOnce() throws IOException {
        Pattern inText = Pattern.compile("\"[^\"]*Programme at Home");
        assertThat(linesWith(MAIN, line -> inText.matcher(line).find()))
                .as("use Brand.HOME in anything people read")
                .allMatch(found -> found.startsWith("Brand.java"));
    }

    private static String withoutCodeNames(String line) {
        String rest = line;
        for (String name : CODE_NAMES) {
            rest = rest.replace(name, "");
        }
        return rest;
    }

    /** "File.java:12: the line", for every matching line of the sources and resources under a folder. */
    private static List<String> linesWith(Path root, Predicate<String> matches) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> !f.toString().contains("migration"))
                    .filter(f -> f.toString().matches(".*[.](java|yml|json|properties|txt|html|ts|tsx)$"))
                    .flatMap(f -> matching(f, matches))
                    .toList();
        }
    }

    private static Stream<String> matching(Path file, Predicate<String> matches) {
        try {
            List<String> lines = Files.readAllLines(file);
            return IntStream.range(0, lines.size())
                    .filter(i -> matches.test(lines.get(i)))
                    .mapToObj(i -> file.getFileName() + ":" + (i + 1) + ": " + lines.get(i).strip());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
